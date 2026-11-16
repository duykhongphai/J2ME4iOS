#import "KeypadView.h"
#include "j4i.h"
#include <math.h>

#define KEY_MENU 10000
#define KEY_STICK 10001

typedef struct {
    const char *label;
    int code;
} KeySpec;

/* phone layout: D-pad block (3x3) and number block (4x3) */
static const KeySpec kDpad[9] = {
    {"◀︎ Mềm", J4I_KEY_SOFT_LEFT}, {"▲", J4I_KEY_UP}, {"Mềm ▶︎", J4I_KEY_SOFT_RIGHT},
    {"◀", J4I_KEY_LEFT}, {"OK", J4I_KEY_FIRE}, {"▶", J4I_KEY_RIGHT},
    {"*", '*'}, {"▼", J4I_KEY_DOWN}, {"#", '#'},
};
static const KeySpec kNum[12] = {
    {"1", '1'}, {"2", '2'}, {"3", '3'},
    {"4", '4'}, {"5", '5'}, {"6", '6'},
    {"7", '7'}, {"8", '8'}, {"9", '9'},
    {"C", J4I_KEY_CLEAR}, {"0", '0'}, {"☰", KEY_MENU},
};
/* gamepad layout */
static const KeySpec kPadTop[4] = {{"◀︎ Mềm", J4I_KEY_SOFT_LEFT}, {"C", J4I_KEY_CLEAR}, {"☰", KEY_MENU}, {"Mềm ▶︎", J4I_KEY_SOFT_RIGHT}};
static const KeySpec kPadMini[8] = {{"1", '1'}, {"3", '3'}, {"5", '5'}, {"*", '*'}, {"7", '7'}, {"9", '9'}, {"0", '0'}, {"#", '#'}};

@interface J4IKeyCell : UILabel
@property (nonatomic) int code;
@property (nonatomic) int pressCount;
@end

@implementation J4IKeyCell
@end

/** Round 8-way joystick. */
@interface J4IStick : UIView
@property (nonatomic) CGPoint knob; /* -1..1 */
@end

@implementation J4IStick
- (instancetype)initWithFrame:(CGRect)f {
    if ((self = [super initWithFrame:f])) {
        self.backgroundColor = [UIColor clearColor];
        self.userInteractionEnabled = NO;
        self.contentMode = UIViewContentModeRedraw;
    }
    return self;
}

- (void)setKnob:(CGPoint)k {
    _knob = k;
    [self setNeedsDisplay];
}

- (void)drawRect:(CGRect)r {
    CGRect b = CGRectInset(self.bounds, 2, 2);
    CGFloat d = MIN(b.size.width, b.size.height);
    CGRect circle = CGRectMake(CGRectGetMidX(b) - d / 2, CGRectGetMidY(b) - d / 2, d, d);
    [[UIColor colorWithWhite:0.22 alpha:1] setFill];
    [[UIBezierPath bezierPathWithOvalInRect:circle] fill];
    [[UIColor colorWithWhite:0.4 alpha:1] setStroke];
    UIBezierPath *ring = [UIBezierPath bezierPathWithOvalInRect:CGRectInset(circle, d * 0.18, d * 0.18)];
    ring.lineWidth = 1;
    [ring stroke];
    CGFloat kd = d * 0.36;
    CGPoint c = CGPointMake(CGRectGetMidX(circle) + self.knob.x * (d - kd) / 2, CGRectGetMidY(circle) + self.knob.y * (d - kd) / 2);
    [[UIColor colorWithRed:0.25 green:0.45 blue:0.8 alpha:1] setFill];
    [[UIBezierPath bezierPathWithOvalInRect:CGRectMake(c.x - kd / 2, c.y - kd / 2, kd, kd)] fill];
}
@end

@implementation KeypadView {
    NSMutableArray<J4IKeyCell *> *_dpad, *_num, *_padTop, *_padMini;
    J4IKeyCell *_fire;
    J4IStick *_stick;
    /* touch -> array of held codes (NSNumber) */
    NSMapTable<UITouch *, NSArray<NSNumber *> *> *_held;
    NSMutableDictionary<NSNumber *, NSNumber *> *_refs;
    UIImpactFeedbackGenerator *_haptic;
}

- (J4IKeyCell *)makeKey:(KeySpec)k into:(NSMutableArray *)list {
    J4IKeyCell *b = [[J4IKeyCell alloc] initWithFrame:CGRectZero];
    b.text = [NSString stringWithUTF8String:k.label];
    b.textColor = [UIColor whiteColor];
    b.textAlignment = NSTextAlignmentCenter;
    b.font = [UIFont systemFontOfSize:20 weight:UIFontWeightSemibold];
    b.adjustsFontSizeToFitWidth = YES;
    b.minimumScaleFactor = 0.5;
    b.backgroundColor = [UIColor colorWithWhite:0.22 alpha:1];
    if (k.code == J4I_KEY_FIRE) b.backgroundColor = [UIColor colorWithRed:0.15 green:0.35 blue:0.7 alpha:1];
    if (k.code == J4I_KEY_SOFT_LEFT || k.code == J4I_KEY_SOFT_RIGHT) b.backgroundColor = [UIColor colorWithWhite:0.32 alpha:1];
    b.layer.cornerRadius = 10;
    b.layer.masksToBounds = YES;
    b.code = k.code;
    [self addSubview:b];
    if (list) [list addObject:b];
    return b;
}

- (instancetype)initWithFrame:(CGRect)frame {
    if ((self = [super initWithFrame:frame])) {
        self.multipleTouchEnabled = YES;
        self.backgroundColor = [UIColor clearColor];
        _keyAlpha = 0.8;
        _haptics = YES;
        _dpad = [NSMutableArray array];
        _num = [NSMutableArray array];
        _padTop = [NSMutableArray array];
        _padMini = [NSMutableArray array];
        for (int i = 0; i < 9; i++) [self makeKey:kDpad[i] into:_dpad];
        for (int i = 0; i < 12; i++) [self makeKey:kNum[i] into:_num];
        for (int i = 0; i < 4; i++) [self makeKey:kPadTop[i] into:_padTop];
        for (int i = 0; i < 8; i++) [self makeKey:kPadMini[i] into:_padMini];
        _fire = [self makeKey:(KeySpec){"OK", J4I_KEY_FIRE} into:nil];
        _fire.layer.cornerRadius = 40;
        _stick = [[J4IStick alloc] initWithFrame:CGRectZero];
        [self addSubview:_stick];
        _held = [NSMapTable weakToStrongObjectsMapTable];
        _refs = [NSMutableDictionary dictionary];
        _haptic = [[UIImpactFeedbackGenerator alloc] initWithStyle:UIImpactFeedbackStyleLight];
    }
    return self;
}

- (void)setGamepad:(BOOL)g {
    if (g == _gamepad) return;
    [self releaseAll];
    _gamepad = g;
    [self setNeedsLayout];
}

/* ---------------- layout ---------------- */
static void layoutGrid(NSArray<J4IKeyCell *> *keys, int cols, int rows, CGRect r, CGFloat gap) {
    CGFloat w = (r.size.width - gap * (cols + 1)) / cols;
    CGFloat h = (r.size.height - gap * (rows + 1)) / rows;
    for (int i = 0; i < (int)keys.count; i++) {
        int c = i % cols, row = i / cols;
        keys[i].frame = CGRectMake(r.origin.x + gap + c * (w + gap), r.origin.y + gap + row * (h + gap), w, h);
    }
}

- (void)layoutSubviews {
    [super layoutSubviews];
    CGRect b = self.bounds;
    CGFloat gap = 6;
    BOOL pad = self.gamepad;
    for (J4IKeyCell *k in _dpad) k.hidden = pad;
    for (J4IKeyCell *k in _num) k.hidden = pad;
    for (J4IKeyCell *k in _padTop) k.hidden = !pad;
    for (J4IKeyCell *k in _padMini) k.hidden = !pad;
    _fire.hidden = !pad;
    _stick.hidden = !pad;
    CGFloat alpha = self.landscape ? MAX(0.2, MIN(1, self.keyAlpha)) : 1;
    for (UIView *v in self.subviews) v.alpha = alpha;

    if (!pad) {
        if (!self.landscape) {
            /* 7 rows: 3 d-pad rows + 4 number rows */
            CGFloat rowH = MIN(64, b.size.height / 7.0);
            CGFloat total = rowH * 7;
            CGFloat y0 = b.origin.y + (b.size.height - total) / 2;
            CGFloat maxW = MIN(b.size.width, 420);
            CGFloat x0 = b.origin.x + (b.size.width - maxW) / 2;
            layoutGrid(_dpad, 3, 3, CGRectMake(x0, y0, maxW, rowH * 3), gap);
            layoutGrid(_num, 3, 4, CGRectMake(x0, y0 + rowH * 3, maxW, rowH * 4), gap);
        } else {
            CGRect g = self.screenGap;
            CGFloat leftW = g.origin.x - b.origin.x, rightW = CGRectGetMaxX(b) - CGRectGetMaxX(g);
            CGFloat side = MIN(leftW, rightW);
            if (side < 60) side = MAX(leftW, rightW);
            CGFloat h = MIN(b.size.height, side * 1.3);
            layoutGrid(_dpad, 3, 3, CGRectMake(b.origin.x + (leftW - side) / 2, b.origin.y + (b.size.height - h * 0.75) / 2, side, h * 0.75), gap);
            layoutGrid(_num, 3, 4, CGRectMake(CGRectGetMaxX(g) + (rightW - side) / 2, b.origin.y + (b.size.height - h) / 2, side, h), gap);
        }
        return;
    }
    /* gamepad */
    if (!self.landscape) {
        CGFloat topH = MIN(52, b.size.height * 0.18);
        CGFloat maxW = MIN(b.size.width, 480);
        CGFloat x0 = b.origin.x + (b.size.width - maxW) / 2;
        layoutGrid(_padTop, 4, 1, CGRectMake(x0, b.origin.y + 4, maxW, topH), gap);
        CGFloat y = b.origin.y + topH + 12;
        CGFloat avail = CGRectGetMaxY(b) - y - 6;
        CGFloat d = MIN(avail, maxW * 0.5);
        _stick.frame = CGRectMake(x0 + 6, y + (avail - d) / 2, d, d);
        CGFloat rx = x0 + 6 + d + 12, rw = x0 + maxW - 6 - rx;
        CGFloat fireD = MIN(rw * 0.6, avail * 0.5);
        _fire.frame = CGRectMake(rx + (rw - fireD) / 2, y + 4, fireD, fireD);
        _fire.layer.cornerRadius = fireD / 2;
        CGFloat miniY = y + fireD + 12;
        layoutGrid(_padMini, 4, 2, CGRectMake(rx, miniY, rw, MAX(60, CGRectGetMaxY(b) - miniY - 4)), 4);
    } else {
        CGRect g = self.screenGap;
        CGFloat leftW = g.origin.x - b.origin.x, rightW = CGRectGetMaxX(b) - CGRectGetMaxX(g);
        CGFloat lw = MAX(leftW, 80), rw = MAX(rightW, 80);
        CGFloat topH = 44;
        layoutGrid([_padTop subarrayWithRange:NSMakeRange(0, 2)], 2, 1, CGRectMake(b.origin.x, b.origin.y + 4, lw, topH), gap);
        layoutGrid([_padTop subarrayWithRange:NSMakeRange(2, 2)], 2, 1, CGRectMake(CGRectGetMaxX(b) - rw, b.origin.y + 4, rw, topH), gap);
        CGFloat d = MIN(lw - 12, b.size.height - topH - 24);
        _stick.frame = CGRectMake(b.origin.x + (lw - d) / 2, CGRectGetMaxY(b) - d - 10, d, d);
        CGFloat fireD = MIN(rw * 0.55, (b.size.height - topH) * 0.45);
        _fire.frame = CGRectMake(CGRectGetMaxX(b) - rw + (rw - fireD) / 2, CGRectGetMaxY(b) - fireD - 12, fireD, fireD);
        _fire.layer.cornerRadius = fireD / 2;
        CGFloat my = b.origin.y + topH + 10, mh = _fire.frame.origin.y - 10 - my;
        layoutGrid(_padMini, 4, 2, CGRectMake(CGRectGetMaxX(b) - rw, my, rw, MAX(50, mh)), 4);
    }
}

/* ---------------- touch handling ---------------- */
- (NSArray<NSNumber *> *)codesAt:(CGPoint)p {
    if (!_stick.hidden && _stick.alpha > 0) {
        CGRect f = _stick.frame;
        CGFloat r = MIN(f.size.width, f.size.height) / 2;
        CGPoint c = CGPointMake(CGRectGetMidX(f), CGRectGetMidY(f));
        CGFloat dx = p.x - c.x, dy = p.y - c.y;
        if (dx * dx + dy * dy <= r * r * 1.44) {
            CGFloat len = sqrt(dx * dx + dy * dy);
            _stick.knob = CGPointMake(len > r ? dx / len : dx / r, len > r ? dy / len : dy / r);
            if (len < r * 0.25) return @[@(KEY_STICK)];
            double ang = atan2(-dy, dx) * 180 / M_PI; /* 0 = right, 90 = up */
            if (ang < 0) ang += 360;
            int sector = (int)floor((ang + 22.5) / 45) % 8;
            switch (sector) {
            case 0: return @[@(KEY_STICK), @(J4I_KEY_RIGHT)];
            case 1: return @[@(KEY_STICK), @(J4I_KEY_UP), @(J4I_KEY_RIGHT)];
            case 2: return @[@(KEY_STICK), @(J4I_KEY_UP)];
            case 3: return @[@(KEY_STICK), @(J4I_KEY_UP), @(J4I_KEY_LEFT)];
            case 4: return @[@(KEY_STICK), @(J4I_KEY_LEFT)];
            case 5: return @[@(KEY_STICK), @(J4I_KEY_DOWN), @(J4I_KEY_LEFT)];
            case 6: return @[@(KEY_STICK), @(J4I_KEY_DOWN)];
            default: return @[@(KEY_STICK), @(J4I_KEY_DOWN), @(J4I_KEY_RIGHT)];
            }
        }
    }
    for (UIView *v in self.subviews) {
        if (v.hidden || ![v isKindOfClass:[J4IKeyCell class]]) continue;
        if (CGRectContainsPoint(CGRectInset(v.frame, -3, -3), p)) return @[@(((J4IKeyCell *)v).code)];
    }
    return @[];
}

- (J4IKeyCell *)cellFor:(int)code {
    for (UIView *v in self.subviews)
        if (!v.hidden && [v isKindOfClass:[J4IKeyCell class]] && ((J4IKeyCell *)v).code == code) return (J4IKeyCell *)v;
    return nil;
}

- (void)press:(int)code {
    if (code == KEY_STICK) return;
    int n = _refs[@(code)].intValue;
    _refs[@(code)] = @(n + 1);
    J4IKeyCell *c = [self cellFor:code];
    c.layer.opacity = 0.5;
    if (n > 0 || code == KEY_MENU) return;
    if (self.haptics) [_haptic impactOccurred];
    if (self.onKey) self.onKey(code, YES);
}

- (void)releaseCode:(int)code inside:(BOOL)inside {
    if (code == KEY_STICK) {
        _stick.knob = CGPointZero;
        return;
    }
    int n = _refs[@(code)].intValue - 1;
    if (n < 0) n = 0;
    _refs[@(code)] = @(n);
    if (n > 0) return;
    J4IKeyCell *c = [self cellFor:code];
    c.layer.opacity = 1;
    if (code == KEY_MENU) {
        if (inside && self.onMenu) self.onMenu(c ?: self);
        return;
    }
    if (self.onKey) self.onKey(code, NO);
}

- (void)update:(UITouch *)t codes:(NSArray<NSNumber *> *)now {
    NSArray<NSNumber *> *old = [_held objectForKey:t] ?: @[];
    for (NSNumber *c in old)
        if (![now containsObject:c]) [self releaseCode:c.intValue inside:NO];
    for (NSNumber *c in now)
        if (![old containsObject:c]) [self press:c.intValue];
    [_held setObject:now forKey:t];
}

- (void)touchesBegan:(NSSet<UITouch *> *)touches withEvent:(UIEvent *)event {
    for (UITouch *t in touches) [self update:t codes:[self codesAt:[t locationInView:self]]];
}

- (void)touchesMoved:(NSSet<UITouch *> *)touches withEvent:(UIEvent *)event {
    for (UITouch *t in touches) {
        NSArray *old = [_held objectForKey:t] ?: @[];
        /* a finger that started on the joystick keeps steering it while it slides */
        if ([old containsObject:@(KEY_STICK)]) {
            CGRect f = _stick.frame;
            CGPoint p = [t locationInView:self], c = CGPointMake(CGRectGetMidX(f), CGRectGetMidY(f));
            CGFloat r = MIN(f.size.width, f.size.height) / 2;
            CGFloat dx = p.x - c.x, dy = p.y - c.y, len = sqrt(dx * dx + dy * dy);
            if (len > r) p = CGPointMake(c.x + dx / len * r * 0.99, c.y + dy / len * r * 0.99);
            [self update:t codes:[self codesAt:p]];
        } else if ([old containsObject:@(KEY_MENU)]) {
            continue; /* the menu key fires on release only */
        } else {
            [self update:t codes:[self codesAt:[t locationInView:self]]];
        }
    }
}

- (void)endTouches:(NSSet<UITouch *> *)touches cancelled:(BOOL)cancelled {
    for (UITouch *t in touches) {
        NSArray<NSNumber *> *old = [_held objectForKey:t] ?: @[];
        BOOL inside = !cancelled && [[self codesAt:[t locationInView:self]] isEqualToArray:old];
        for (NSNumber *c in old) [self releaseCode:c.intValue inside:inside];
        [_held removeObjectForKey:t];
    }
}

- (void)touchesEnded:(NSSet<UITouch *> *)touches withEvent:(UIEvent *)event { [self endTouches:touches cancelled:NO]; }

- (void)touchesCancelled:(NSSet<UITouch *> *)touches withEvent:(UIEvent *)event { [self endTouches:touches cancelled:YES]; }

- (void)releaseAll {
    for (NSNumber *code in _refs.allKeys) {
        if (_refs[code].intValue <= 0) continue;
        _refs[code] = @(1);
        [self releaseCode:code.intValue inside:NO];
    }
    [_held removeAllObjects];
    _stick.knob = CGPointZero;
}

/* touches that miss every key fall through to views behind (the game screen in landscape) */
- (UIView *)hitTest:(CGPoint)point withEvent:(UIEvent *)event {
    if (self.hidden || self.alpha < 0.01 || !self.userInteractionEnabled) return nil;
    if (!CGRectContainsPoint(self.bounds, point)) return nil;
    if ([self codesAt:point].count == 0) return nil;
    _stick.knob = CGPointZero;
    return self;
}

@end
