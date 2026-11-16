#import "GameViewController.h"
#import "KeypadView.h"
#import "IOSHost.h"
#import "Paths.h"
#import <QuartzCore/QuartzCore.h>
#include <os/lock.h>
#include <stdatomic.h>
#include "j4i.h"

/* Frame buffer shared between the VM thread (writer) and the main thread (reader). */
static os_unfair_lock gFbLock = OS_UNFAIR_LOCK_INIT;
static uint32_t *gFb;
static int gFbW, gFbH;
static atomic_int gFramePending;
static __weak GameViewController *gCurrent;

@interface GameViewController ()
- (void)updateScreen;
- (void)vmExited;
- (void)askText:(int)reqId title:(NSString *)title text:(NSString *)text maxLen:(int)maxLen constraints:(int)constraints;
@end

/* ---------------- host callbacks (VM thread) ---------------- */
static void host_log(void *u, const char *msg) { NSLog(@"%s", msg); }

static void host_present(void *u, const uint32_t *argb, int w, int h) {
    os_unfair_lock_lock(&gFbLock);
    if (gFbW != w || gFbH != h) {
        free(gFb);
        gFb = (uint32_t *)malloc((size_t)w * h * 4);
        gFbW = w;
        gFbH = h;
    }
    memcpy(gFb, argb, (size_t)w * h * 4);
    os_unfair_lock_unlock(&gFbLock);
    if (atomic_exchange(&gFramePending, 1) == 0) {
        dispatch_async(dispatch_get_main_queue(), ^{
            atomic_store(&gFramePending, 0);
            [gCurrent updateScreen];
        });
    }
}

static void host_text_input(void *u, int req_id, const char *title, const char *text, int max_len, int constraints) {
    NSString *t = [NSString stringWithUTF8String:title] ?: @"";
    NSString *s = [NSString stringWithUTF8String:text] ?: @"";
    dispatch_async(dispatch_get_main_queue(), ^{
        GameViewController *g = gCurrent;
        if (g) [g askText:req_id title:t text:s maxLen:max_len constraints:constraints];
        else j4i_text_input_result(req_id, NULL);
    });
}

static void host_exited(void *u, int code) {
    dispatch_async(dispatch_get_main_queue(), ^{ [gCurrent vmExited]; });
}

/* ---------------- screen view ---------------- */
@interface J4IScreenView : UIView
@property (nonatomic) int vmWidth, vmHeight;
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
    j4i_post_event(J4I_EV_POINTER_PRESSED, (int)p.x, (int)p.y);
}

- (void)touchesMoved:(NSSet<UITouch *> *)touches withEvent:(UIEvent *)event {
    if (!_touch || ![touches containsObject:_touch]) return;
    CGPoint p = [self vmPoint:_touch];
    j4i_post_event(J4I_EV_POINTER_DRAGGED, (int)p.x, (int)p.y);
}

- (void)touchesEnded:(NSSet<UITouch *> *)touches withEvent:(UIEvent *)event {
    if (!_touch || ![touches containsObject:_touch]) return;
    CGPoint p = [self vmPoint:_touch];
    j4i_post_event(J4I_EV_POINTER_RELEASED, (int)p.x, (int)p.y);
    _touch = nil;
}

- (void)touchesCancelled:(NSSet<UITouch *> *)touches withEvent:(UIEvent *)event {
    [self touchesEnded:touches withEvent:event];
}

@end

/* ---------------- controller ---------------- */
@implementation GameViewController {
    NSString *_jar;
    int _w, _h;
    J4IScreenView *_screen;
    KeypadView *_keypad;
    NSThread *_vm;
    BOOL _started, _exiting;
    CGColorSpaceRef _rgb;
}

- (instancetype)initWithJarPath:(NSString *)path width:(int)w height:(int)h {
    if ((self = [super initWithNibName:nil bundle:nil])) {
        _jar = path;
        _w = w;
        _h = h;
        _rgb = CGColorSpaceCreateDeviceRGB();
    }
    return self;
}

- (void)dealloc {
    CGColorSpaceRelease(_rgb);
    [[NSNotificationCenter defaultCenter] removeObserver:self];
}

- (BOOL)prefersStatusBarHidden { return YES; }
- (BOOL)prefersHomeIndicatorAutoHidden { return YES; }
- (UIRectEdge)preferredScreenEdgesDeferringSystemGestures { return UIRectEdgeAll; }
- (BOOL)canBecomeFirstResponder { return YES; }

- (void)viewDidLoad {
    [super viewDidLoad];
    self.view.backgroundColor = [UIColor blackColor];
    _keypad = [[KeypadView alloc] initWithFrame:CGRectZero];
    __weak GameViewController *weakSelf = self;
    _keypad.onKey = ^(int code, BOOL down) {
        j4i_post_event(down ? J4I_EV_KEY_PRESSED : J4I_EV_KEY_RELEASED, code, 0);
    };
    _keypad.onMenu = ^(UIView *src) { [weakSelf showMenu:src]; };
    [self.view addSubview:_keypad];
    _screen = [[J4IScreenView alloc] initWithFrame:CGRectZero];
    _screen.vmWidth = _w;
    _screen.vmHeight = _h;
    [self.view addSubview:_screen];

    NSNotificationCenter *nc = [NSNotificationCenter defaultCenter];
    [nc addObserver:self selector:@selector(appBackground) name:UIApplicationDidEnterBackgroundNotification object:nil];
    [nc addObserver:self selector:@selector(appForeground) name:UIApplicationWillEnterForegroundNotification object:nil];
}

- (void)viewDidLayoutSubviews {
    [super viewDidLayoutSubviews];
    CGRect safe = UIEdgeInsetsInsetRect(self.view.bounds, self.view.safeAreaInsets);
    BOOL landscape = safe.size.width > safe.size.height;
    _keypad.landscape = landscape;
    if (!landscape) {
        CGFloat sw = safe.size.width;
        CGFloat sh = sw * _h / _w;
        CGFloat maxH = safe.size.height * 0.58;
        if (sh > maxH) {
            sh = maxH;
            sw = sh * _w / _h;
        }
        /* integer scaling looks sharper when it fits */
        CGFloat scale = floor(sw / _w);
        if (scale >= 1 && scale * _w >= sw * 0.85) {
            sw = scale * _w;
            sh = scale * _h;
        }
        _screen.frame = CGRectMake(safe.origin.x + (safe.size.width - sw) / 2, safe.origin.y, sw, sh);
        _keypad.frame = CGRectMake(safe.origin.x, CGRectGetMaxY(_screen.frame), safe.size.width,
                                   CGRectGetMaxY(safe) - CGRectGetMaxY(_screen.frame));
    } else {
        CGFloat sh = safe.size.height;
        CGFloat sw = sh * _w / _h;
        if (sw > safe.size.width * 0.6) {
            sw = safe.size.width * 0.6;
            sh = sw * _h / _w;
        }
        _screen.frame = CGRectMake(safe.origin.x + (safe.size.width - sw) / 2, safe.origin.y + (safe.size.height - sh) / 2, sw, sh);
        _keypad.frame = safe;
        _keypad.screenGap = [self.view convertRect:_screen.frame toView:_keypad];
    }
    [_keypad setNeedsLayout];
}

- (void)viewDidAppear:(BOOL)animated {
    [super viewDidAppear:animated];
    [self becomeFirstResponder];
    if (_started) return;
    _started = YES;
    gCurrent = self;
    [self startVM];
}

- (void)startVM {
    NSString *runtime = [[NSBundle mainBundle] pathForResource:@"j4i-runtime" ofType:@"jar"];
    NSString *dataDir = J4IDataDirFor(_jar.lastPathComponent);
    [[NSFileManager defaultManager] createDirectoryAtPath:dataDir withIntermediateDirectories:YES attributes:nil error:nil];
    char *rt = strdup(runtime.fileSystemRepresentation);
    char *jar = strdup(_jar.fileSystemRepresentation);
    char *dd = strdup(dataDir.fileSystemRepresentation);
    int w = _w, h = _h;
    _vm = [[NSThread alloc] initWithBlock:^{
        J4IConfig cfg;
        memset(&cfg, 0, sizeof cfg);
        cfg.runtime_jar = rt;
        cfg.game_jar = jar;
        cfg.data_dir = dd;
        cfg.screen_w = w;
        cfg.screen_h = h;
        cfg.heap_mb = 64;
        cfg.host.log = host_log;
        cfg.host.present = host_present;
        cfg.host.text_input = host_text_input;
        cfg.host.exited = host_exited;
        J4IIOSHostFill(&cfg.host);
        j4i_run(&cfg);
        J4IIOSAudioReset();
        free(rt);
        free(jar);
        free(dd);
    }];
    _vm.stackSize = 16 * 1024 * 1024;
    _vm.qualityOfService = NSQualityOfServiceUserInteractive;
    _vm.name = @"J2ME VM";
    [_vm start];
}

- (void)updateScreen {
    os_unfair_lock_lock(&gFbLock);
    int w = gFbW, h = gFbH;
    if (!gFb || w <= 0 || h <= 0) {
        os_unfair_lock_unlock(&gFbLock);
        return;
    }
    CFDataRef data = CFDataCreate(NULL, (const UInt8 *)gFb, (CFIndex)w * h * 4);
    os_unfair_lock_unlock(&gFbLock);
    CGDataProviderRef prov = CGDataProviderCreateWithCFData(data);
    CGImageRef img = CGImageCreate(w, h, 8, 32, (size_t)w * 4, _rgb, kCGBitmapByteOrder32Little | kCGImageAlphaNoneSkipFirst, prov, NULL,
                                   false, kCGRenderingIntentDefault);
    [CATransaction begin];
    [CATransaction setDisableActions:YES];
    _screen.layer.contents = (__bridge id)img;
    [CATransaction commit];
    CGImageRelease(img);
    CGDataProviderRelease(prov);
    CFRelease(data);
}

- (void)showMenu:(UIView *)src {
    UIAlertController *ac = [UIAlertController alertControllerWithTitle:nil message:nil preferredStyle:UIAlertControllerStyleActionSheet];
    __weak GameViewController *weakSelf = self;
    [ac addAction:[UIAlertAction actionWithTitle:@"Thoát game"
                                           style:UIAlertActionStyleDestructive
                                         handler:^(UIAlertAction *a) { [weakSelf requestExit]; }]];
    [ac addAction:[UIAlertAction actionWithTitle:@"Huỷ" style:UIAlertActionStyleCancel handler:nil]];
    ac.popoverPresentationController.sourceView = src;
    ac.popoverPresentationController.sourceRect = src.bounds;
    [self presentViewController:ac animated:YES completion:nil];
}

- (void)requestExit {
    if (_exiting) return;
    _exiting = YES;
    j4i_request_exit();
}

- (void)vmExited {
    gCurrent = nil;
    os_unfair_lock_lock(&gFbLock);
    free(gFb);
    gFb = NULL;
    gFbW = gFbH = 0;
    os_unfair_lock_unlock(&gFbLock);
    if (self.presentedViewController) [self.presentedViewController dismissViewControllerAnimated:NO completion:nil];
    [self dismissViewControllerAnimated:YES completion:nil];
}

- (void)askText:(int)reqId title:(NSString *)title text:(NSString *)text maxLen:(int)maxLen constraints:(int)constraints {
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
                                         handler:^(UIAlertAction *a) { j4i_text_input_result(reqId, NULL); }]];
    [ac addAction:[UIAlertAction actionWithTitle:@"OK"
                                           style:UIAlertActionStyleDefault
                                         handler:^(UIAlertAction *a) {
                                             NSString *s = weakAc.textFields.firstObject.text ?: @"";
                                             if (maxLen > 0 && (int)s.length > maxLen) s = [s substringToIndex:maxLen];
                                             j4i_text_input_result(reqId, s.UTF8String);
                                         }]];
    [self presentViewController:ac animated:YES completion:nil];
}

- (void)appBackground {
    j4i_post_event(J4I_EV_PAUSE, 0, 0);
}

- (void)appForeground {
    j4i_post_event(J4I_EV_RESUME, 0, 0);
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
    if (@available(iOS 13.4, *)) {
        for (UIPress *p in presses) {
            int code = p.key ? mapKey(p.key) : 0;
            if (code) {
                j4i_post_event(J4I_EV_KEY_PRESSED, code, 0);
                handled = YES;
            }
        }
    }
    if (!handled) [super pressesBegan:presses withEvent:event];
}

- (void)pressesEnded:(NSSet<UIPress *> *)presses withEvent:(UIPressesEvent *)event {
    BOOL handled = NO;
    if (@available(iOS 13.4, *)) {
        for (UIPress *p in presses) {
            int code = p.key ? mapKey(p.key) : 0;
            if (code) {
                j4i_post_event(J4I_EV_KEY_RELEASED, code, 0);
                handled = YES;
            }
        }
    }
    if (!handled) [super pressesEnded:presses withEvent:event];
}

@end
