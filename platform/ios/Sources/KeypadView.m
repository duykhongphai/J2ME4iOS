#import "KeypadView.h"
#include "j4i.h"

#define KEY_MENU 10000

typedef struct {
    const char *label;
    int code;
} KeySpec;

/* D-pad block (3x3) and number block (4x3) */
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

@implementation KeypadView {
    NSMutableArray<UIButton *> *_dpad;
    NSMutableArray<UIButton *> *_num;
}

- (UIButton *)makeKey:(KeySpec)k {
    UIButton *b = [UIButton buttonWithType:UIButtonTypeCustom];
    [b setTitle:[NSString stringWithUTF8String:k.label] forState:UIControlStateNormal];
    [b setTitleColor:[UIColor whiteColor] forState:UIControlStateNormal];
    b.titleLabel.font = [UIFont systemFontOfSize:20 weight:UIFontWeightSemibold];
    b.titleLabel.adjustsFontSizeToFitWidth = YES;
    b.titleLabel.minimumScaleFactor = 0.5;
    b.backgroundColor = [UIColor colorWithWhite:0.22 alpha:1];
    if (k.code == J4I_KEY_FIRE) b.backgroundColor = [UIColor colorWithRed:0.15 green:0.35 blue:0.7 alpha:1];
    if (k.code == J4I_KEY_SOFT_LEFT || k.code == J4I_KEY_SOFT_RIGHT) b.backgroundColor = [UIColor colorWithWhite:0.32 alpha:1];
    b.layer.cornerRadius = 10;
    b.tag = k.code;
    b.exclusiveTouch = NO;
    [b addTarget:self action:@selector(down:) forControlEvents:UIControlEventTouchDown | UIControlEventTouchDragEnter];
    [b addTarget:self
                  action:@selector(up:)
        forControlEvents:UIControlEventTouchUpInside | UIControlEventTouchUpOutside | UIControlEventTouchCancel | UIControlEventTouchDragExit];
    [self addSubview:b];
    return b;
}

- (instancetype)initWithFrame:(CGRect)frame {
    if ((self = [super initWithFrame:frame])) {
        self.multipleTouchEnabled = YES;
        self.backgroundColor = [UIColor blackColor];
        _dpad = [NSMutableArray array];
        _num = [NSMutableArray array];
        for (int i = 0; i < 9; i++) [_dpad addObject:[self makeKey:kDpad[i]]];
        for (int i = 0; i < 12; i++) [_num addObject:[self makeKey:kNum[i]]];
    }
    return self;
}

- (void)down:(UIButton *)b {
    b.alpha = 0.5;
    if (b.tag == KEY_MENU) return;
    UIImpactFeedbackGenerator *fb = [[UIImpactFeedbackGenerator alloc] initWithStyle:UIImpactFeedbackStyleLight];
    [fb impactOccurred];
    if (self.onKey) self.onKey((int)b.tag, YES);
}

- (void)up:(UIButton *)b {
    b.alpha = 1.0;
    if (b.tag == KEY_MENU) {
        if (self.onMenu) self.onMenu(b);
        return;
    }
    if (self.onKey) self.onKey((int)b.tag, NO);
}

static void layoutGrid(NSArray<UIButton *> *keys, int cols, int rows, CGRect r, CGFloat gap) {
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
}

@end
