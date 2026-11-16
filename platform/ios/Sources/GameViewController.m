#import "GameViewController.h"
#import "KeypadView.h"
#import "IOSHost.h"
#import "Paths.h"
#import "Controls.h"
#import "LogViewController.h"
#import "GameSession.h"
#import <QuartzCore/QuartzCore.h>
#import <GameController/GameController.h>
#include <math.h>
#include "j4i.h"

#define HW_KEY_MENU 10000

@interface GameViewController ()
- (void)showMenu:(UIView *)src;
@end

/* ---------------- screen view ---------------- */
@interface J4IScreenView : UIView
@property (nonatomic) int vmWidth, vmHeight;
@property (nonatomic, weak) GameSession *session;
@end

@implementation J4IScreenView {
    UITouch *_touch;
}

- (instancetype)initWithFrame:(CGRect)frame {
    if ((self = [super initWithFrame:frame])) {
        self.backgroundColor = [UIColor blackColor];
        self.layer.magnificationFilter = kCAFilterNearest;
        self.layer.contentsGravity = kCAGravityResize;
        self.multipleTouchEnabled = NO;
    }
    return self;
}

- (CGPoint)vmPoint:(UITouch *)t {
    CGPoint p = [t locationInView:self];
    int x = (int)(p.x * self.vmWidth / MAX(1, self.bounds.size.width));
    int y = (int)(p.y * self.vmHeight / MAX(1, self.bounds.size.height));
    return CGPointMake(MAX(0, MIN(self.vmWidth - 1, x)), MAX(0, MIN(self.vmHeight - 1, y)));
}

- (void)touchesBegan:(NSSet<UITouch *> *)touches withEvent:(UIEvent *)event {
    if (_touch) return;
    _touch = touches.anyObject;
    CGPoint p = [self vmPoint:_touch];
    [self.session postEvent:J4I_EV_POINTER_PRESSED a:(int)p.x b:(int)p.y];
}

- (void)touchesMoved:(NSSet<UITouch *> *)touches withEvent:(UIEvent *)event {
    if (!_touch || ![touches containsObject:_touch]) return;
    CGPoint p = [self vmPoint:_touch];
    [self.session postEvent:J4I_EV_POINTER_DRAGGED a:(int)p.x b:(int)p.y];
}

- (void)touchesEnded:(NSSet<UITouch *> *)touches withEvent:(UIEvent *)event {
    if (!_touch || ![touches containsObject:_touch]) return;
    CGPoint p = [self vmPoint:_touch];
    [self.session postEvent:J4I_EV_POINTER_RELEASED a:(int)p.x b:(int)p.y];
    _touch = nil;
}

- (void)touchesCancelled:(NSSet<UITouch *> *)touches withEvent:(UIEvent *)event {
    [self touchesEnded:touches withEvent:event];
}

@end

/* ---------------- controller ---------------- */
@implementation GameViewController {
    GameSession *_session;
    NSString *_file;
    int _w, _h;
    J4IScreenView *_screen;
    KeypadView *_keypad;
    UIButton *_menuButton;
    UILabel *_fpsLabel;
    NSTimer *_fpsTimer;
    BOOL _exiting, _switching;
    CGColorSpaceRef _rgb;
    NSString *_orient, *_scale, *_keypadMode;
    BOOL _smooth, _showFps;
    NSMutableSet<NSNumber *> *_padHeld;
}

- (instancetype)initWithSession:(GameSession *)session {
    if ((self = [super initWithNibName:nil bundle:nil])) {
        _session = session;
        _file = session.file;
        _w = session.width;
        _h = session.height;
        _rgb = CGColorSpaceCreateDeviceRGB();
        _orient = J4IGameSetting(_file, @"orient", @"auto");
        _scale = J4IGameSetting(_file, @"scale", @"fit");
        _smooth = [J4IGameSetting(_file, @"smooth", @"0") isEqualToString:@"1"];
        NSString *k = J4IGameSetting(_file, @"keypad", @"show");
        if ([k isEqualToString:@"1"]) k = @"show";
        if ([k isEqualToString:@"0"]) k = @"hide";
        _keypadMode = k;
        _showFps = [J4IGameSetting(_file, @"fps", @"0") isEqualToString:@"1"];
        _padHeld = [NSMutableSet set];
    }
    return self;
}

- (void)dealloc {
    CGColorSpaceRelease(_rgb);
    [_fpsTimer invalidate];
    [[NSNotificationCenter defaultCenter] removeObserver:self];
}

- (BOOL)prefersStatusBarHidden { return YES; }
- (BOOL)prefersHomeIndicatorAutoHidden { return YES; }
- (UIRectEdge)preferredScreenEdgesDeferringSystemGestures { return UIRectEdgeAll; }
- (BOOL)canBecomeFirstResponder { return YES; }
- (BOOL)shouldAutorotate { return YES; }

- (UIInterfaceOrientationMask)supportedInterfaceOrientations {
    if ([_orient isEqualToString:@"portrait"]) return UIInterfaceOrientationMaskPortrait;
    if ([_orient isEqualToString:@"landscape"]) return UIInterfaceOrientationMaskLandscape;
    return UIInterfaceOrientationMaskAllButUpsideDown;
}

- (UIInterfaceOrientation)preferredInterfaceOrientationForPresentation {
    if ([_orient isEqualToString:@"landscape"]) return UIInterfaceOrientationLandscapeRight;
    return UIInterfaceOrientationPortrait;
}

static UIColor *backgroundFor(NSString *name) {
    if ([name isEqualToString:@"gray"]) return [UIColor colorWithWhite:0.18 alpha:1];
    if ([name isEqualToString:@"white"]) return [UIColor colorWithWhite:0.92 alpha:1];
    if ([name isEqualToString:@"lcd"]) return [UIColor colorWithRed:0.55 green:0.62 blue:0.45 alpha:1];
    if ([name isEqualToString:@"blue"]) return [UIColor colorWithRed:0.08 green:0.12 blue:0.22 alpha:1];
    return [UIColor blackColor];
}

- (void)viewDidLoad {
    [super viewDidLoad];
    self.view.backgroundColor = backgroundFor(J4IGameSetting(_file, @"bg", @"black"));
    _keypad = [[KeypadView alloc] initWithFrame:CGRectZero];
    _keypad.gamepad = [J4IGameSetting(_file, @"keypadType", @"phone") isEqualToString:@"gamepad"];
    _keypad.keyAlpha = [J4IGameSetting(_file, @"keyAlpha", @"80") intValue] / 100.0;
    _keypad.haptics = ![J4IGameSetting(_file, @"haptics", @"1") isEqualToString:@"0"];
    __weak GameViewController *weakSelf = self;
    _keypad.onKey = ^(int code, BOOL down) {
        [weakSelf.session postEvent:(down ? J4I_EV_KEY_PRESSED : J4I_EV_KEY_RELEASED) a:code b:0];
    };
    _keypad.onMenu = ^(UIView *src) { [weakSelf showMenu:src]; };
    [self.view addSubview:_keypad];
    _screen = [[J4IScreenView alloc] initWithFrame:CGRectZero];
    _screen.vmWidth = _w;
    _screen.vmHeight = _h;
    _screen.session = _session;
    [self.view addSubview:_screen];
    [self applyFilter];

    _menuButton = [UIButton buttonWithType:UIButtonTypeCustom];
    [_menuButton setTitle:@"☰" forState:UIControlStateNormal];
    [_menuButton setTitleColor:[UIColor whiteColor] forState:UIControlStateNormal];
    _menuButton.titleLabel.font = [UIFont systemFontOfSize:18 weight:UIFontWeightSemibold];
    _menuButton.backgroundColor = [UIColor colorWithWhite:0.15 alpha:0.55];
    _menuButton.layer.cornerRadius = 8;
    [_menuButton addTarget:self action:@selector(menuButtonTapped:) forControlEvents:UIControlEventTouchUpInside];
    [self.view addSubview:_menuButton];

    _fpsLabel = [[UILabel alloc] initWithFrame:CGRectZero];
    _fpsLabel.font = [UIFont monospacedDigitSystemFontOfSize:11 weight:UIFontWeightSemibold];
    _fpsLabel.textColor = [UIColor greenColor];
    _fpsLabel.backgroundColor = [UIColor colorWithWhite:0 alpha:0.5];
    _fpsLabel.textAlignment = NSTextAlignmentCenter;
    _fpsLabel.hidden = !_showFps;
    [self.view addSubview:_fpsLabel];

    NSNotificationCenter *nc = [NSNotificationCenter defaultCenter];
    [nc addObserver:self selector:@selector(controllersChanged) name:GCControllerDidConnectNotification object:nil];
    [nc addObserver:self selector:@selector(controllersChanged) name:GCControllerDidDisconnectNotification object:nil];
    [nc addObserver:self selector:@selector(controllersChanged) name:GCKeyboardDidConnectNotification object:nil];
    [nc addObserver:self selector:@selector(controllersChanged) name:GCKeyboardDidDisconnectNotification object:nil];
    [self controllersChanged];
}

- (void)menuButtonTapped:(UIButton *)b {
    [self showMenu:b];
}

- (void)applyFilter {
    _screen.layer.magnificationFilter = _smooth ? kCAFilterLinear : kCAFilterNearest;
    _screen.layer.minificationFilter = _smooth ? kCAFilterLinear : kCAFilterNearest;
}

- (BOOL)showKeypad {
    if ([_keypadMode isEqualToString:@"hide"]) return NO;
    if ([_keypadMode isEqualToString:@"auto"]) return GCController.controllers.count == 0 && GCKeyboard.coalescedKeyboard == nil;
    return YES;
}

/* Places a fw x fh frame inside `area` according to the scale mode. */
- (CGRect)fitWidth:(int)fw height:(int)fh inRect:(CGRect)area topAligned:(BOOL)top {
    if (fw <= 0 || fh <= 0 || area.size.width <= 0 || area.size.height <= 0) return area;
    if ([_scale isEqualToString:@"stretch"]) return area;
    CGFloat s = MIN(area.size.width / fw, area.size.height / fh);
    if ([_scale isEqualToString:@"integer"] && s >= 1) s = floor(s);
    if ([_scale isEqualToString:@"native"]) s = MIN(1, s);
    CGFloat sw = fw * s, sh = fh * s;
    CGFloat x = area.origin.x + (area.size.width - sw) / 2;
    CGFloat y = top ? area.origin.y : area.origin.y + (area.size.height - sh) / 2;
    return CGRectMake(x, y, sw, sh);
}

- (void)viewDidLayoutSubviews {
    [super viewDidLayoutSubviews];
    CGRect safe = UIEdgeInsetsInsetRect(self.view.bounds, self.view.safeAreaInsets);
    BOOL landscape = safe.size.width > safe.size.height;
    int fw = _screen.vmWidth > 0 ? _screen.vmWidth : _w;
    int fh = _screen.vmHeight > 0 ? _screen.vmHeight : _h;
    BOOL keypad = [self showKeypad];
    if (!keypad && !_keypad.hidden) [_keypad releaseAll];
    _keypad.hidden = !keypad;
    _menuButton.hidden = keypad;
    _keypad.landscape = landscape;
    if (!keypad) {
        _screen.frame = [self fitWidth:fw height:fh inRect:safe topAligned:NO];
        CGFloat bs = 36;
        _menuButton.frame = CGRectMake(CGRectGetMaxX(safe) - bs - 4, safe.origin.y + 4, bs, bs);
        [self.view bringSubviewToFront:_menuButton];
    } else if (!landscape) {
        CGRect area = CGRectMake(safe.origin.x, safe.origin.y, safe.size.width, safe.size.height * 0.58);
        _screen.frame = [self fitWidth:fw height:fh inRect:area topAligned:YES];
        _keypad.frame = CGRectMake(safe.origin.x, CGRectGetMaxY(_screen.frame), safe.size.width,
                                   CGRectGetMaxY(safe) - CGRectGetMaxY(_screen.frame));
    } else {
        CGFloat aw = safe.size.width * 0.6;
        CGRect area = CGRectMake(safe.origin.x + (safe.size.width - aw) / 2, safe.origin.y, aw, safe.size.height);
        _screen.frame = [self fitWidth:fw height:fh inRect:area topAligned:NO];
        _keypad.frame = safe;
        _keypad.screenGap = [self.view convertRect:_screen.frame toView:_keypad];
    }
    _fpsLabel.frame = CGRectMake(_screen.frame.origin.x + 2, _screen.frame.origin.y + 2, 54, 16);
    [self.view bringSubviewToFront:_fpsLabel];
    [_keypad setNeedsLayout];
}

- (void)viewDidAppear:(BOOL)animated {
    [super viewDidAppear:animated];
    [self becomeFirstResponder];
    _session.viewController = self;
    /* the shown game plays sound; others are muted unless the user wants background audio */
    BOOL bgAudio = [[NSUserDefaults standardUserDefaults] boolForKey:@"bgAudio"];
    for (GameSession *s in [GameSession all]) s.audioMuted = (s != _session) && !bgAudio;
    if (!_session.running) {
        [self sessionExited];
        return;
    }
    [self sessionFrameReady];
    if (_session.pendingTextRequest) {
        void (^req)(GameViewController *) = _session.pendingTextRequest;
        _session.pendingTextRequest = nil;
        req(self);
    }
    [_session takeFrameCount];
    [_fpsTimer invalidate];
    __weak GameViewController *weakSelf = self;
    _fpsTimer = [NSTimer scheduledTimerWithTimeInterval:1.0
                                                repeats:YES
                                                  block:^(NSTimer *timer) {
                                                      GameViewController *s = weakSelf;
                                                      if (!s) {
                                                          [timer invalidate];
                                                          return;
                                                      }
                                                      s->_fpsLabel.text = [NSString stringWithFormat:@"%d fps", [s->_session takeFrameCount]];
                                                  }];
}

- (void)viewWillDisappear:(BOOL)animated {
    [super viewWillDisappear:animated];
    [_fpsTimer invalidate];
    _fpsTimer = nil;
    [_keypad releaseAll];
    for (NSNumber *c in _padHeld) [_session postEvent:J4I_EV_KEY_RELEASED a:c.intValue b:0];
    [_padHeld removeAllObjects];
    if (_session.viewController == self) _session.viewController = nil;
    if (_session.running && ![[NSUserDefaults standardUserDefaults] boolForKey:@"bgAudio"]) _session.audioMuted = YES;
}

- (void)sessionFrameReady {
    __block CGImageRef img = NULL;
    __block int w = 0, h = 0;
    CGColorSpaceRef rgb = _rgb;
    [_session withFrame:^(const uint32_t *argb, int fw, int fh) {
        CFDataRef data = CFDataCreate(NULL, (const UInt8 *)argb, (CFIndex)fw * fh * 4);
        CGDataProviderRef prov = CGDataProviderCreateWithCFData(data);
        img = CGImageCreate(fw, fh, 8, 32, (size_t)fw * 4, rgb, kCGBitmapByteOrder32Little | kCGImageAlphaNoneSkipFirst, prov, NULL, false,
                            kCGRenderingIntentDefault);
        CGDataProviderRelease(prov);
        CFRelease(data);
        w = fw;
        h = fh;
    }];
    if (!img) return;
    if (w != _screen.vmWidth || h != _screen.vmHeight) { /* the game changed its frame size */
        _screen.vmWidth = w;
        _screen.vmHeight = h;
        [self.view setNeedsLayout];
    }
    [CATransaction begin];
    [CATransaction setDisableActions:YES];
    _screen.layer.contents = (__bridge id)img;
    [CATransaction commit];
    CGImageRelease(img);
}

/* ---------------- in-game menu ---------------- */
- (void)showMenu:(UIView *)src {
    if (self.presentedViewController) return;
    NSString *title = [NSString stringWithFormat:@"%@ · khe lưu %d", _session.title, _session.slot];
    UIAlertController *ac = [UIAlertController alertControllerWithTitle:title message:nil preferredStyle:UIAlertControllerStyleActionSheet];
    __weak GameViewController *weakSelf = self;
    BOOL keypad = [self showKeypad];
    [ac addAction:[UIAlertAction actionWithTitle:@"Về danh sách (game vẫn chạy)"
                                           style:UIAlertActionStyleDefault
                                         handler:^(UIAlertAction *a) { [weakSelf leaveRunning]; }]];
    if ([GameSession all].count > 1) {
        [ac addAction:[UIAlertAction actionWithTitle:@"Chuyển sang game khác…"
                                               style:UIAlertActionStyleDefault
                                             handler:^(UIAlertAction *a) { [weakSelf chooseSession:src]; }]];
    }
    [ac addAction:[UIAlertAction actionWithTitle:keypad ? @"Ẩn bàn phím ảo" : @"Hiện bàn phím ảo"
                                           style:UIAlertActionStyleDefault
                                         handler:^(UIAlertAction *a) { [weakSelf setKeypadShown:!keypad]; }]];
    [ac addAction:[UIAlertAction actionWithTitle:_keypad.gamepad ? @"Bàn phím kiểu điện thoại" : @"Bàn phím kiểu tay cầm"
                                           style:UIAlertActionStyleDefault
                                         handler:^(UIAlertAction *a) { [weakSelf toggleKeypadType]; }]];
    NSString *scaleName = [_scale isEqualToString:@"integer"] ? @"Số nguyên"
                        : [_scale isEqualToString:@"stretch"] ? @"Kéo giãn"
                        : [_scale isEqualToString:@"native"] ? @"Gốc 1×"
                                                              : @"Vừa khung";
    [ac addAction:[UIAlertAction actionWithTitle:[NSString stringWithFormat:@"Kiểu phóng to: %@", scaleName]
                                           style:UIAlertActionStyleDefault
                                         handler:^(UIAlertAction *a) { [weakSelf cycleScale]; }]];
    [ac addAction:[UIAlertAction actionWithTitle:_smooth ? @"Tắt làm mịn ảnh" : @"Bật làm mịn ảnh"
                                           style:UIAlertActionStyleDefault
                                         handler:^(UIAlertAction *a) { [weakSelf toggleSmooth]; }]];
    [ac addAction:[UIAlertAction actionWithTitle:[NSString stringWithFormat:@"Âm lượng: %d%%", (int)lroundf(J4IIOSMasterVolume() * 100)]
                                           style:UIAlertActionStyleDefault
                                         handler:^(UIAlertAction *a) { [weakSelf chooseVolume:src]; }]];
    [ac addAction:[UIAlertAction actionWithTitle:_showFps ? @"Ẩn FPS" : @"Hiện FPS"
                                           style:UIAlertActionStyleDefault
                                         handler:^(UIAlertAction *a) { [weakSelf toggleFps]; }]];
    [ac addAction:[UIAlertAction actionWithTitle:@"Xem log"
                                           style:UIAlertActionStyleDefault
                                         handler:^(UIAlertAction *a) { [weakSelf showLog]; }]];
    [ac addAction:[UIAlertAction actionWithTitle:@"Thoát game"
                                           style:UIAlertActionStyleDestructive
                                         handler:^(UIAlertAction *a) { [weakSelf requestExit]; }]];
    [ac addAction:[UIAlertAction actionWithTitle:@"Huỷ" style:UIAlertActionStyleCancel handler:nil]];
    ac.popoverPresentationController.sourceView = src ?: self.view;
    ac.popoverPresentationController.sourceRect = src ? src.bounds : CGRectMake(CGRectGetMidX(self.view.bounds), 40, 1, 1);
    [self presentViewController:ac animated:YES completion:nil];
}

- (void)setKeypadShown:(BOOL)shown {
    _keypadMode = shown ? @"show" : @"hide";
    J4ISetGameSetting(_file, @"keypad", _keypadMode);
    [self.view setNeedsLayout];
}

- (void)toggleKeypadType {
    _keypad.gamepad = !_keypad.gamepad;
    J4ISetGameSetting(_file, @"keypadType", _keypad.gamepad ? @"gamepad" : @"phone");
    [self.view setNeedsLayout];
}

- (void)cycleScale {
    if ([_scale isEqualToString:@"fit"]) _scale = @"integer";
    else if ([_scale isEqualToString:@"integer"]) _scale = @"stretch";
    else if ([_scale isEqualToString:@"stretch"]) _scale = @"native";
    else _scale = @"fit";
    J4ISetGameSetting(_file, @"scale", _scale);
    [self.view setNeedsLayout];
}

- (void)toggleSmooth {
    _smooth = !_smooth;
    J4ISetGameSetting(_file, @"smooth", _smooth ? @"1" : @"0");
    [self applyFilter];
}

- (void)toggleFps {
    _showFps = !_showFps;
    _fpsLabel.hidden = !_showFps;
    J4ISetGameSetting(_file, @"fps", _showFps ? @"1" : @"0");
}

- (void)chooseVolume:(UIView *)src {
    UIAlertController *ac = [UIAlertController alertControllerWithTitle:@"Âm lượng" message:nil preferredStyle:UIAlertControllerStyleActionSheet];
    int cur = (int)lroundf(J4IIOSMasterVolume() * 100);
    for (NSNumber *v in @[@0, @25, @50, @75, @100]) {
        NSString *t = v.intValue == 0 ? @"Tắt tiếng" : [NSString stringWithFormat:@"%d%%", v.intValue];
        if (v.intValue == cur) t = [t stringByAppendingString:@" ✓"];
        [ac addAction:[UIAlertAction actionWithTitle:t
                                               style:UIAlertActionStyleDefault
                                             handler:^(UIAlertAction *a) { J4IIOSSetMasterVolume(v.intValue / 100.0f); }]];
    }
    [ac addAction:[UIAlertAction actionWithTitle:@"Huỷ" style:UIAlertActionStyleCancel handler:nil]];
    ac.popoverPresentationController.sourceView = src ?: self.view;
    ac.popoverPresentationController.sourceRect = src ? src.bounds : CGRectMake(CGRectGetMidX(self.view.bounds), 40, 1, 1);
    [self presentViewController:ac animated:YES completion:nil];
}

- (void)showLog {
    UINavigationController *nav = [[UINavigationController alloc] initWithRootViewController:[[LogViewController alloc] init]];
    [self presentViewController:nav animated:YES completion:nil];
}

- (void)requestExit {
    if (_exiting) return;
    _exiting = YES;
    [_session requestExit];
}

- (void)sessionExited {
    [_fpsTimer invalidate];
    _fpsTimer = nil;
    if (self.presentedViewController) [self.presentedViewController dismissViewControllerAnimated:NO completion:nil];
    if (self.presentingViewController && !_switching) [self dismissViewControllerAnimated:YES completion:nil];
}

/** Back to the game list; the game keeps running. */
- (void)leaveRunning {
    [self dismissViewControllerAnimated:YES completion:nil];
}

/** Shows another running session in place of this one. */
- (void)switchTo:(GameSession *)other {
    UIViewController *presenter = self.presentingViewController;
    if (!presenter || !other.running) return;
    _switching = YES;
    [self dismissViewControllerAnimated:NO
                             completion:^{
                                 GameViewController *g = [[GameViewController alloc] initWithSession:other];
                                 g.modalPresentationStyle = UIModalPresentationFullScreen;
                                 [presenter presentViewController:g animated:NO completion:nil];
                             }];
}

- (void)chooseSession:(UIView *)src {
    UIAlertController *ac = [UIAlertController alertControllerWithTitle:@"Chuyển sang game đang chạy"
                                                                message:nil
                                                         preferredStyle:UIAlertControllerStyleActionSheet];
    __weak GameViewController *weakSelf = self;
    for (GameSession *s in [GameSession all]) {
        if (s == _session) continue;
        [ac addAction:[UIAlertAction actionWithTitle:[NSString stringWithFormat:@"%@ (khe %d)", s.title, s.slot]
                                               style:UIAlertActionStyleDefault
                                             handler:^(UIAlertAction *a) { [weakSelf switchTo:s]; }]];
    }
    [ac addAction:[UIAlertAction actionWithTitle:@"Huỷ" style:UIAlertActionStyleCancel handler:nil]];
    ac.popoverPresentationController.sourceView = src ?: self.view;
    ac.popoverPresentationController.sourceRect = src ? src.bounds : CGRectMake(CGRectGetMidX(self.view.bounds), 40, 1, 1);
    [self presentViewController:ac animated:YES completion:nil];
}

- (void)askText:(int)reqId title:(NSString *)title text:(NSString *)text maxLen:(int)maxLen constraints:(int)constraints {
    GameSession *sess = _session;
    UIAlertController *ac = [UIAlertController alertControllerWithTitle:title.length ? title : @"Nhập chữ"
                                                                message:nil
                                                         preferredStyle:UIAlertControllerStyleAlert];
    int kind = constraints & 0xFFFF;
    [ac addTextFieldWithConfigurationHandler:^(UITextField *tf) {
        tf.text = text;
        if (kind == 2) tf.keyboardType = UIKeyboardTypeNumberPad;
        else if (kind == 3) tf.keyboardType = UIKeyboardTypePhonePad;
        else if (kind == 5) tf.keyboardType = UIKeyboardTypeDecimalPad;
        else if (kind == 1) tf.keyboardType = UIKeyboardTypeEmailAddress;
        else if (kind == 4) tf.keyboardType = UIKeyboardTypeURL;
        tf.secureTextEntry = (constraints & 0x10000) != 0;
        tf.autocorrectionType = UITextAutocorrectionTypeNo;
    }];
    __weak UIAlertController *weakAc = ac;
    [ac addAction:[UIAlertAction actionWithTitle:@"Huỷ"
                                           style:UIAlertActionStyleCancel
                                         handler:^(UIAlertAction *a) { [sess textResult:reqId text:nil]; }]];
    [ac addAction:[UIAlertAction actionWithTitle:@"OK"
                                           style:UIAlertActionStyleDefault
                                         handler:^(UIAlertAction *a) {
                                             NSString *s = weakAc.textFields.firstObject.text ?: @"";
                                             if (maxLen > 0 && (int)s.length > maxLen) s = [s substringToIndex:maxLen];
                                             [sess textResult:reqId text:s];
                                         }]];
    if (self.presentedViewController) [self.presentedViewController dismissViewControllerAnimated:NO completion:nil];
    [self presentViewController:ac animated:YES completion:nil];
}


/* ---------------- game controllers ---------------- */
- (void)controllersChanged {
    for (GCController *c in GCController.controllers) {
        GCExtendedGamepad *pad = c.extendedGamepad;
        if (!pad) continue;
        __weak GameViewController *weakSelf = self;
        pad.valueChangedHandler = ^(GCExtendedGamepad *gamepad, GCControllerElement *element) {
            [weakSelf gamepadChanged:gamepad element:element];
        };
    }
    [self.view setNeedsLayout];
}

- (void)gamepadChanged:(GCExtendedGamepad *)g element:(GCControllerElement *)element {
    if (element == g.buttonHome && g.buttonHome.pressed) {
        [self showMenu:nil];
        return;
    }
    NSMutableSet<NSNumber *> *now = [NSMutableSet set];
    NSDictionary<NSString *, GCControllerButtonInput *> *buttons = @{
        @"A": g.buttonA, @"B": g.buttonB, @"X": g.buttonX, @"Y": g.buttonY,
        @"LB": g.leftShoulder, @"RB": g.rightShoulder, @"LT": g.leftTrigger, @"RT": g.rightTrigger, @"Menu": g.buttonMenu,
    };
    for (NSString *bid in buttons) {
        GCControllerButtonInput *b = buttons[bid];
        int code = J4IPadMapping(bid);
        if (b.pressed && code) [now addObject:@(code)];
    }
    if (g.buttonOptions.pressed && J4IPadMapping(@"Options")) [now addObject:@(J4IPadMapping(@"Options"))];
    float lx = g.leftThumbstick.xAxis.value, ly = g.leftThumbstick.yAxis.value;
    if (g.dpad.up.pressed || ly > 0.5f) [now addObject:@(J4I_KEY_UP)];
    if (g.dpad.down.pressed || ly < -0.5f) [now addObject:@(J4I_KEY_DOWN)];
    if (g.dpad.left.pressed || lx < -0.5f) [now addObject:@(J4I_KEY_LEFT)];
    if (g.dpad.right.pressed || lx > 0.5f) [now addObject:@(J4I_KEY_RIGHT)];
    float rx = g.rightThumbstick.xAxis.value, ry = g.rightThumbstick.yAxis.value;
    if (ry > 0.5f) [now addObject:@('2')];
    if (ry < -0.5f) [now addObject:@('8')];
    if (rx < -0.5f) [now addObject:@('4')];
    if (rx > 0.5f) [now addObject:@('6')];
    for (NSNumber *c in [_padHeld copy])
        if (![now containsObject:c]) {
            [_session postEvent:J4I_EV_KEY_RELEASED a:c.intValue b:0];
            [_padHeld removeObject:c];
        }
    for (NSNumber *c in now)
        if (![_padHeld containsObject:c]) {
            [_session postEvent:J4I_EV_KEY_PRESSED a:c.intValue b:0];
            [_padHeld addObject:c];
        }
}

/* ---------------- hardware keyboard (iPad / Bluetooth) ---------------- */
static int mapKey(UIKey *k) {
    switch (k.keyCode) {
    case UIKeyboardHIDUsageKeyboardUpArrow: return J4I_KEY_UP;
    case UIKeyboardHIDUsageKeyboardDownArrow: return J4I_KEY_DOWN;
    case UIKeyboardHIDUsageKeyboardLeftArrow: return J4I_KEY_LEFT;
    case UIKeyboardHIDUsageKeyboardRightArrow: return J4I_KEY_RIGHT;
    case UIKeyboardHIDUsageKeyboardReturnOrEnter: case UIKeyboardHIDUsageKeyboardSpacebar: return J4I_KEY_FIRE;
    case UIKeyboardHIDUsageKeyboardF1: case UIKeyboardHIDUsageKeyboardQ: return J4I_KEY_SOFT_LEFT;
    case UIKeyboardHIDUsageKeyboardF2: case UIKeyboardHIDUsageKeyboardW: return J4I_KEY_SOFT_RIGHT;
    case UIKeyboardHIDUsageKeyboardDeleteOrBackspace: return J4I_KEY_CLEAR;
    case UIKeyboardHIDUsageKeyboardE: return '*';
    case UIKeyboardHIDUsageKeyboardR: return '#';
    case UIKeyboardHIDUsageKeyboardEscape: case UIKeyboardHIDUsageKeyboardM: return HW_KEY_MENU;
    default: break;
    }
    NSString *c = k.charactersIgnoringModifiers;
    if (c.length == 1) {
        unichar ch = [c characterAtIndex:0];
        if ((ch >= '0' && ch <= '9') || ch == '*' || ch == '#') return ch;
    }
    return 0;
}

- (void)pressesBegan:(NSSet<UIPress *> *)presses withEvent:(UIPressesEvent *)event {
    BOOL handled = NO;
    for (UIPress *p in presses) {
        int code = p.key ? mapKey(p.key) : 0;
        if (code == HW_KEY_MENU) {
            [self showMenu:nil];
            handled = YES;
        } else if (code) {
            [_session postEvent:J4I_EV_KEY_PRESSED a:code b:0];
            handled = YES;
        }
    }
    if (!handled) [super pressesBegan:presses withEvent:event];
}

- (void)pressesEnded:(NSSet<UIPress *> *)presses withEvent:(UIPressesEvent *)event {
    BOOL handled = NO;
    for (UIPress *p in presses) {
        int code = p.key ? mapKey(p.key) : 0;
        if (code == HW_KEY_MENU) {
            handled = YES;
        } else if (code) {
            [_session postEvent:J4I_EV_KEY_RELEASED a:code b:0];
            handled = YES;
        }
    }
    if (!handled) [super pressesEnded:presses withEvent:event];
}

@end
