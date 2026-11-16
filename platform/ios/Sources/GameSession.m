#import "GameSession.h"
#import "GameViewController.h"
#import "IOSHost.h"
#import "Paths.h"
#include <os/lock.h>

NSString *const J4ISessionsChangedNotification = @"J4ISessionsChanged";

static NSMutableArray<GameSession *> *gSessions;

@interface GameSession ()
- (void)vmPresent:(const uint32_t *)argb w:(int)w h:(int)h;
- (void)vmTextInput:(int)reqId title:(NSString *)title text:(NSString *)text maxLen:(int)maxLen constraints:(int)constraints;
- (void)vmExited;
@end

/* ---------------- host callbacks (VM thread), user = the session ---------------- */
static void s_log(void *u, const char *msg) {
    GameSession *s = (__bridge GameSession *)u;
    NSLog(@"[%@#%d] %s", s.file, s.slot, msg);
    J4IIOSLogAppend([NSString stringWithFormat:@"[%@ #%d] %s", s.title, s.slot, msg].UTF8String);
}

static void s_present(void *u, const uint32_t *argb, int w, int h) { [(__bridge GameSession *)u vmPresent:argb w:w h:h]; }

static void s_text_input(void *u, int req_id, const char *title, const char *text, int max_len, int constraints) {
    NSString *t = [NSString stringWithUTF8String:title] ?: @"";
    NSString *x = [NSString stringWithUTF8String:text] ?: @"";
    [(__bridge GameSession *)u vmTextInput:req_id title:t text:x maxLen:max_len constraints:constraints];
}

static void s_exited(void *u, int code) { [(__bridge GameSession *)u vmExited]; }

@implementation GameSession {
    J4IInstance *_inst;
    NSThread *_thread;
    os_unfair_lock _fbLock;
    uint32_t *_fb;
    int _fbW, _fbH;
    int _framePending;
    int _frames;
}

+ (void)initialize {
    if (self != [GameSession class]) return;
    gSessions = [NSMutableArray array];
    NSNotificationCenter *nc = [NSNotificationCenter defaultCenter];
    [nc addObserverForName:UIApplicationDidEnterBackgroundNotification
                    object:nil
                     queue:[NSOperationQueue mainQueue]
                usingBlock:^(NSNotification *n) {
                    for (GameSession *s in gSessions) [s postEvent:J4I_EV_PAUSE a:0 b:0];
                }];
    [nc addObserverForName:UIApplicationWillEnterForegroundNotification
                    object:nil
                     queue:[NSOperationQueue mainQueue]
                usingBlock:^(NSNotification *n) {
                    for (GameSession *s in gSessions) [s postEvent:J4I_EV_RESUME a:0 b:0];
                }];
}

+ (NSArray<GameSession *> *)all { return [gSessions copy]; }

+ (GameSession *)runningFile:(NSString *)file slot:(int)slot {
    for (GameSession *s in gSessions)
        if ([s.file isEqualToString:file] && s.slot == slot) return s;
    return nil;
}

+ (NSString *)dataDirForFile:(NSString *)file slot:(int)slot {
    if (slot <= 1) return J4IDataDirFor(file);
    return J4IDataDirFor([NSString stringWithFormat:@"%@#%d", file, slot]);
}

/* "key=value" lines from the per-game settings, plus the chosen platform string */
static NSString *systemProperties(NSString *file) {
    NSMutableString *s = [NSMutableString string];
    NSString *platform = J4IGameSetting(file, @"platform", @"");
    if (platform.length) [s appendFormat:@"microedition.platform=%@\n", platform];
    NSString *props = J4IGameSetting(file, @"props", @"");
    for (NSString *part in [props componentsSeparatedByCharactersInSet:[NSCharacterSet characterSetWithCharactersInString:@";\n"]]) {
        NSString *p = [part stringByTrimmingCharactersInSet:[NSCharacterSet whitespaceCharacterSet]];
        if (p.length) [s appendFormat:@"%@\n", p];
    }
    return s;
}

+ (GameSession *)startJar:(NSString *)jarPath slot:(int)slot title:(NSString *)title {
    GameSession *s = [[GameSession alloc] init];
    s->_jarPath = jarPath;
    s->_file = jarPath.lastPathComponent;
    s->_slot = MAX(1, slot);
    s->_title = title ?: s->_file;
    int w = 240, h = 320;
    sscanf(J4IGameSetting(s->_file, @"res", @"240x320").UTF8String, "%dx%d", &w, &h);
    s->_width = w;
    s->_height = h;
    s->_fbLock = OS_UNFAIR_LOCK_INIT;
    s->_inst = j4i_instance_new();
    s->_running = YES;
    [gSessions addObject:s];
    [s launch];
    [[NSNotificationCenter defaultCenter] postNotificationName:J4ISessionsChangedNotification object:nil];
    return s;
}

- (void)launch {
    NSString *file = _file;
    NSString *runtime = [[NSBundle mainBundle] pathForResource:@"j4i-runtime" ofType:@"jar"];
    NSString *dataDir = [GameSession dataDirForFile:file slot:_slot];
    [[NSFileManager defaultManager] createDirectoryAtPath:dataDir withIntermediateDirectories:YES attributes:nil error:nil];
    NSString *jad = J4IJadFor(file);
    NSString *props = systemProperties(file);
    NSString *proxy = J4IGameSetting([NSString stringWithFormat:@"%@#%d", file, _slot], @"proxy", @"");
    char *rt = strdup(runtime.fileSystemRepresentation);
    char *jar = strdup(_jarPath.fileSystemRepresentation);
    char *dd = strdup(dataDir.fileSystemRepresentation);
    char *jd = jad ? strdup(jad.fileSystemRepresentation) : NULL;
    char *sp = props.length ? strdup(props.UTF8String) : NULL;
    char *px = proxy.length ? strdup(proxy.UTF8String) : NULL;
    int w = _width, h = _height;
    int fs = [J4IGameSetting(file, @"fontS", @"0") intValue], fm = [J4IGameSetting(file, @"fontM", @"0") intValue],
        fl = [J4IGameSetting(file, @"fontL", @"0") intValue];
    int noAA = [J4IGameSetting(file, @"aa", @"1") isEqualToString:@"0"];
    NSString *midiMode = [[NSUserDefaults standardUserDefaults] stringForKey:@"midiMode"] ?: @"auto";
    int synth = [midiMode isEqualToString:@"builtin"] || !J4IIOSHasSoundBank();
    J4IIOSLogAppend([NSString stringWithFormat:@"[%@ #%d] start %dx%d%@%@%@", _title, _slot, w, h, jad ? @" + JAD" : @"",
                                               synth ? @", built-in MIDI synth" : @", soundfont MIDI", px ? @", proxy" : @""].UTF8String);
    J4IInstance *inst = _inst;
    GameSession *me = self; /* the thread keeps the session alive until the VM is gone */
    _thread = [[NSThread alloc] initWithBlock:^{
        J4IConfig cfg;
        memset(&cfg, 0, sizeof cfg);
        cfg.runtime_jar = rt;
        cfg.game_jar = jar;
        cfg.data_dir = dd;
        cfg.jad_path = jd;
        cfg.sys_props = sp;
        cfg.proxy = px;
        cfg.screen_w = w;
        cfg.screen_h = h;
        cfg.heap_mb = 64;
        cfg.font_small = fs;
        cfg.font_medium = fm;
        cfg.font_large = fl;
        cfg.font_no_aa = noAA;
        cfg.midi_synth = synth;
        J4IIOSHostFill(&cfg.host);
        cfg.host.user = (__bridge void *)me;
        cfg.host.log = s_log;
        cfg.host.present = s_present;
        cfg.host.text_input = s_text_input;
        cfg.host.exited = s_exited;
        j4i_instance_run(inst, &cfg);
        J4IIOSAudioResetOwner((__bridge void *)me);
        free(rt);
        free(jar);
        free(dd);
        free(jd);
        free(sp);
        free(px);
        dispatch_async(dispatch_get_main_queue(), ^{
            j4i_instance_free(inst);
            me->_inst = NULL;
            os_unfair_lock_lock(&me->_fbLock);
            free(me->_fb);
            me->_fb = NULL;
            os_unfair_lock_unlock(&me->_fbLock);
        });
    }];
    _thread.stackSize = 16 * 1024 * 1024;
    _thread.qualityOfService = NSQualityOfServiceUserInteractive;
    _thread.name = [NSString stringWithFormat:@"J2ME VM %@ #%d", _file, _slot];
    [_thread start];
}

- (void)postEvent:(int)type a:(int)a b:(int)b {
    if (_running && _inst) j4i_instance_post_event(_inst, type, a, b);
}

- (void)textResult:(int)reqId text:(NSString *)text {
    if (_running && _inst) j4i_instance_text_input_result(_inst, reqId, text ? text.UTF8String : NULL);
}

- (void)requestExit {
    if (_running && _inst) j4i_instance_request_exit(_inst);
}

- (void)setAudioMuted:(BOOL)m {
    _audioMuted = m;
    J4IIOSSetOwnerMuted((__bridge void *)self, m);
}

/* ---- VM thread ---- */
- (void)vmPresent:(const uint32_t *)argb w:(int)w h:(int)h {
    os_unfair_lock_lock(&_fbLock);
    if (_fbW != w || _fbH != h || !_fb) {
        free(_fb);
        _fb = (uint32_t *)malloc((size_t)w * h * 4);
        _fbW = w;
        _fbH = h;
    }
    if (_fb) memcpy(_fb, argb, (size_t)w * h * 4);
    os_unfair_lock_unlock(&_fbLock);
    __atomic_fetch_add(&_frames, 1, __ATOMIC_RELAXED);
    if (__atomic_exchange_n(&_framePending, 1, __ATOMIC_ACQ_REL) == 0) {
        __weak GameSession *weakSelf = self;
        dispatch_async(dispatch_get_main_queue(), ^{
            GameSession *s = weakSelf;
            if (!s) return;
            __atomic_store_n(&s->_framePending, 0, __ATOMIC_RELEASE);
            [s.viewController sessionFrameReady];
        });
    }
}

- (void)vmTextInput:(int)reqId title:(NSString *)title text:(NSString *)text maxLen:(int)maxLen constraints:(int)constraints {
    dispatch_async(dispatch_get_main_queue(), ^{
        GameViewController *vc = self.viewController;
        if (vc) [vc askText:reqId title:title text:text maxLen:maxLen constraints:constraints];
        else
            self.pendingTextRequest = ^(GameViewController *v) {
                [v askText:reqId title:title text:text maxLen:maxLen constraints:constraints];
            };
    });
}

- (void)vmExited {
    dispatch_async(dispatch_get_main_queue(), ^{
        self->_running = NO;
        [gSessions removeObject:self];
        [self.viewController sessionExited];
        [[NSNotificationCenter defaultCenter] postNotificationName:J4ISessionsChangedNotification object:nil];
    });
}

/* ---- main thread ---- */
- (BOOL)withFrame:(void (^)(const uint32_t *argb, int w, int h))block {
    os_unfair_lock_lock(&_fbLock);
    BOOL ok = _fb != NULL && _fbW > 0 && _fbH > 0;
    if (ok) block(_fb, _fbW, _fbH);
    os_unfair_lock_unlock(&_fbLock);
    return ok;
}

- (int)takeFrameCount { return __atomic_exchange_n(&_frames, 0, __ATOMIC_RELAXED); }

@end
