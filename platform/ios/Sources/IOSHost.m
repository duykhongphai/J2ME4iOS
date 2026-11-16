#import "IOSHost.h"
#import "Paths.h"
#import <UIKit/UIKit.h>
#import <CoreText/CoreText.h>
#import <ImageIO/ImageIO.h>
#import <AVFoundation/AVFoundation.h>
#import <AudioToolbox/AudioToolbox.h>

/* ---------------- fonts (VM threads) ---------------- */
static NSMutableDictionary<NSNumber *, id> *gFonts;

static CTFontRef makeFont(int size, int style);

/* several VM threads share the cache */
static CTFontRef fontFor(int size, int style) {
    @synchronized([UIFont class]) {
        if (!gFonts) gFonts = [NSMutableDictionary dictionary];
        NSNumber *key = @(size * 8 + (style & 3));
        id cached = gFonts[key];
        if (cached) return (__bridge CTFontRef)cached;
        CTFontRef f = makeFont(size, style);
        gFonts[key] = (__bridge_transfer id)f;
        return f;
    }
}

static CTFontRef makeFont(int size, int style) {
    CTFontRef f = CTFontCreateUIFontForLanguage(kCTFontUIFontSystem, size, NULL);
    CTFontSymbolicTraits tr = 0;
    if (style & J4I_FONT_BOLD) tr |= kCTFontBoldTrait;
    if (style & J4I_FONT_ITALIC) tr |= kCTFontItalicTrait;
    if (tr) {
        CTFontRef f2 = CTFontCreateCopyWithSymbolicTraits(f, size, NULL, tr, tr);
        if (f2) {
            CFRelease(f);
            f = f2;
        }
    }
    return f;
}

static void ios_font_metrics(void *u, int size, int style, int *ascent, int *descent) {
    CTFontRef f = fontFor(size, style);
    *ascent = (int)ceil(CTFontGetAscent(f));
    *descent = (int)ceil(CTFontGetDescent(f));
}

static int ios_glyph(void *u, int cp, int size, int style, uint8_t *alpha, int stride, int max_w, int h, int ascent) {
    CTFontRef font = fontFor(size, style);
    UniChar chars[2];
    CFIndex n = 1;
    if (cp >= 0x10000) {
        chars[0] = (UniChar)(0xD800 + ((cp - 0x10000) >> 10));
        chars[1] = (UniChar)(0xDC00 + ((cp - 0x10000) & 0x3FF));
        n = 2;
    } else {
        chars[0] = (UniChar)cp;
    }
    CGGlyph glyphs[2] = {0, 0};
    CTFontRef use = font, fallback = NULL;
    if (!CTFontGetGlyphsForCharacters(font, chars, glyphs, n)) {
        CFStringRef s = CFStringCreateWithCharacters(NULL, chars, n);
        fallback = CTFontCreateForString(font, s, CFRangeMake(0, n));
        CFRelease(s);
        if (fallback) {
            use = fallback;
            CTFontGetGlyphsForCharacters(use, chars, glyphs, n);
        }
    }
    CGSize adv = CGSizeZero;
    CTFontGetAdvancesForGlyphs(use, kCTFontOrientationHorizontal, glyphs, &adv, 1);
    int a = (int)lround(adv.width);
    CGColorSpaceRef cs = CGColorSpaceCreateDeviceGray();
    CGContextRef ctx = CGBitmapContextCreate(alpha, (size_t)max_w, (size_t)h, 8, (size_t)stride, cs, kCGImageAlphaNone);
    CGColorSpaceRelease(cs);
    if (ctx) {
        CGContextSetGrayFillColor(ctx, 1.0, 1.0);
        CGContextSetShouldAntialias(ctx, true);
        CGContextSetShouldSmoothFonts(ctx, false);
        CGPoint pos = CGPointMake(0, h - ascent);
        CTFontDrawGlyphs(use, glyphs, &pos, 1, ctx);
        CGContextRelease(ctx);
    }
    if (fallback) CFRelease(fallback);
    return a;
}

/* ---------------- images (JPEG, GIF, BMP...) ---------------- */
static int ios_decode(void *u, const uint8_t *data, int len, uint32_t **out, int *w, int *h) {
    CFDataRef d = CFDataCreate(NULL, data, len);
    CGImageSourceRef src = CGImageSourceCreateWithData(d, NULL);
    CFRelease(d);
    if (!src) return 0;
    CGImageRef img = CGImageSourceCreateImageAtIndex(src, 0, NULL);
    CFRelease(src);
    if (!img) return 0;
    size_t W = CGImageGetWidth(img), H = CGImageGetHeight(img);
    uint32_t *buf = (uint32_t *)calloc(W * H, 4);
    CGColorSpaceRef cs = CGColorSpaceCreateDeviceRGB();
    CGContextRef ctx = CGBitmapContextCreate(buf, W, H, 8, W * 4, cs, kCGBitmapByteOrder32Little | kCGImageAlphaPremultipliedFirst);
    CGColorSpaceRelease(cs);
    if (!ctx) {
        CGImageRelease(img);
        free(buf);
        return 0;
    }
    CGContextDrawImage(ctx, CGRectMake(0, 0, W, H), img);
    CGContextRelease(ctx);
    CGImageRelease(img);
    for (size_t i = 0; i < W * H; i++) {
        uint32_t p = buf[i], a = p >> 24;
        if (a == 0 || a == 255) continue;
        uint32_t r = ((p >> 16) & 255) * 255 / a, g = ((p >> 8) & 255) * 255 / a, b = (p & 255) * 255 / a;
        buf[i] = (a << 24) | (MIN(r, 255u) << 16) | (MIN(g, 255u) << 8) | MIN(b, 255u);
    }
    *out = buf;
    *w = (int)W;
    *h = (int)H;
    return 1;
}

/* ---------------- log buffer ---------------- */
static NSMutableArray<NSString *> *gLog;
static const NSUInteger kLogMax = 600;

void J4IIOSLogAppend(const char *line) {
    NSString *s = [NSString stringWithUTF8String:line ? line : ""] ?: @"?";
    @synchronized([NSProcessInfo class]) {
        if (!gLog) gLog = [NSMutableArray array];
        [gLog addObject:s];
        if (gLog.count > kLogMax) [gLog removeObjectsInRange:NSMakeRange(0, gLog.count - kLogMax)];
    }
}

NSString *J4IIOSLogText(void) {
    @synchronized([NSProcessInfo class]) {
        return gLog.count ? [gLog componentsJoinedByString:@"\n"] : @"";
    }
}

void J4IIOSLogClear(void) {
    @synchronized([NSProcessInfo class]) {
        [gLog removeAllObjects];
    }
}

/* ---------------- audio ---------------- */
static NSMutableDictionary<NSNumber *, id> *gPlayers;
static NSMutableDictionary<NSNumber *, NSNumber *> *gLoops;
static NSMutableDictionary<NSNumber *, NSNumber *> *gLevels;
static NSMutableDictionary<NSNumber *, NSValue *> *gOwners; /* handle -> session pointer */
static NSMutableSet<NSValue *> *gMutedOwners;
static int gNextHandle;

static void audioInit(void) {
    static dispatch_once_t once;
    dispatch_once(&once, ^{
        gPlayers = [NSMutableDictionary dictionary];
        gLoops = [NSMutableDictionary dictionary];
        gLevels = [NSMutableDictionary dictionary];
        gOwners = [NSMutableDictionary dictionary];
        gMutedOwners = [NSMutableSet set];
    });
}
static float gMaster = -1;

float J4IIOSMasterVolume(void) {
    if (gMaster < 0) {
        NSUserDefaults *d = [NSUserDefaults standardUserDefaults];
        gMaster = [d objectForKey:@"masterVolume"] ? [d floatForKey:@"masterVolume"] : 1.0f;
    }
    return gMaster;
}

static float levelFor(int h) {
    audioInit();
    NSNumber *n;
    @synchronized(gPlayers) {
        n = gLevels[@(h)];
        NSValue *o = gOwners[@(h)];
        if (o && [gMutedOwners containsObject:o]) return 0;
    }
    return (n ? n.floatValue : 100.0f) / 100.0f * J4IIOSMasterVolume();
}

void J4IIOSSetMasterVolume(float v) {
    audioInit();
    gMaster = MAX(0.0f, MIN(1.0f, v));
    [[NSUserDefaults standardUserDefaults] setFloat:gMaster forKey:@"masterVolume"];
    NSDictionary *all;
    @synchronized(gPlayers) {
        all = [gPlayers copy];
    }
    for (NSNumber *k in all) {
        id p = all[k];
        if ([p isKindOfClass:[AVAudioPlayer class]]) ((AVAudioPlayer *)p).volume = levelFor(k.intValue);
    }
}

static NSURL *soundBank(void);

BOOL J4IIOSHasSoundBank(void) { return soundBank() != nil; }

static NSURL *soundBank(void) {
    NSFileManager *fm = [NSFileManager defaultManager];
    for (NSString *ext in @[@"sf2", @"dls"]) {
        NSArray *b = [[NSBundle mainBundle] URLsForResourcesWithExtension:ext subdirectory:nil];
        if (b.count) return b.firstObject;
    }
    for (NSString *f in [fm contentsOfDirectoryAtPath:J4IDocumentsDir() error:nil]) {
        NSString *e = f.pathExtension.lowercaseString;
        if ([e isEqualToString:@"sf2"] || [e isEqualToString:@"dls"])
            return [NSURL fileURLWithPath:[J4IDocumentsDir() stringByAppendingPathComponent:f]];
    }
    return nil;
}

static id playerFor(int h) {
    @synchronized(gPlayers) {
        return gPlayers[@(h)];
    }
}

static int ios_audio_create(void *u, const uint8_t *data, int len, const char *mime) {
    @autoreleasepool {
        audioInit();
        NSData *d = [NSData dataWithBytes:data length:len];
        BOOL midi = (len >= 4 && memcmp(data, "MThd", 4) == 0) || (mime && strstr(mime, "mid"));
        id player = nil;
        NSError *err = nil;
        if (midi) {
            AVMIDIPlayer *p = [[AVMIDIPlayer alloc] initWithData:d soundBankURL:soundBank() error:&err];
            [p prepareToPlay];
            player = p;
        } else {
            AVAudioPlayer *p = [[AVAudioPlayer alloc] initWithData:d error:&err];
            p.volume = [gMutedOwners containsObject:[NSValue valueWithPointer:u]] ? 0 : J4IIOSMasterVolume();
            [p prepareToPlay];
            player = p;
        }
        if (!player) {
            NSLog(@"J2ME4iOS audio: cannot play %s (%@)", mime ? mime : "?", err);
            return 0;
        }
        @synchronized(gPlayers) {
            int h = ++gNextHandle;
            gPlayers[@(h)] = player;
            gOwners[@(h)] = [NSValue valueWithPointer:u];
            return h;
        }
    }
}

static void playMidi(AVMIDIPlayer *p, int h) {
    p.currentPosition = 0;
    [p play:^{
        NSNumber *n;
        @synchronized(gPlayers) {
            n = gLoops[@(h)];
        }
        int left = n.intValue;
        if (left == -1 || left > 1) {
            @synchronized(gPlayers) {
                if (left > 1) gLoops[@(h)] = @(left - 1);
            }
            dispatch_async(dispatch_get_main_queue(), ^{
                if (playerFor(h) == p) playMidi(p, h);
            });
        }
    }];
}

static void ios_audio_play(void *u, int h, int loops) {
    id p = playerFor(h);
    if ([p isKindOfClass:[AVAudioPlayer class]]) {
        AVAudioPlayer *a = p;
        a.numberOfLoops = loops < 0 ? -1 : MAX(0, loops - 1);
        a.currentTime = 0;
        [a play];
    } else if ([p isKindOfClass:[AVMIDIPlayer class]]) {
        @synchronized(gPlayers) {
            gLoops[@(h)] = @(loops);
        }
        playMidi(p, h);
    }
}

static void ios_audio_stop(void *u, int h) {
    id p = playerFor(h);
    @synchronized(gPlayers) {
        gLoops[@(h)] = @(0);
    }
    [p stop];
}

static void ios_audio_close(void *u, int h) {
    id p = playerFor(h);
    [p stop];
    @synchronized(gPlayers) {
        [gPlayers removeObjectForKey:@(h)];
        [gLoops removeObjectForKey:@(h)];
        [gLevels removeObjectForKey:@(h)];
        [gOwners removeObjectForKey:@(h)];
    }
}

static void ios_audio_volume(void *u, int h, int level) {
    @synchronized(gPlayers) {
        gLevels[@(h)] = @(level);
    }
    id p = playerFor(h);
    if ([p isKindOfClass:[AVAudioPlayer class]]) ((AVAudioPlayer *)p).volume = levelFor(h);
}

static int ios_audio_is_playing(void *u, int h) {
    id p = playerFor(h);
    if ([p isKindOfClass:[AVAudioPlayer class]]) return ((AVAudioPlayer *)p).playing;
    if ([p isKindOfClass:[AVMIDIPlayer class]]) {
        NSNumber *n;
        @synchronized(gPlayers) {
            n = gLoops[@(h)];
        }
        return ((AVMIDIPlayer *)p).playing || n.intValue == -1;
    }
    return 0;
}

void J4IIOSAudioReset(void) {
    audioInit();
    NSArray *all;
    @synchronized(gPlayers) {
        all = gPlayers.allValues;
        [gPlayers removeAllObjects];
        [gLoops removeAllObjects];
        [gLevels removeAllObjects];
        [gOwners removeAllObjects];
    }
    for (id p in all) [p stop];
}

void J4IIOSAudioResetOwner(void *owner) {
    audioInit();
    NSValue *o = [NSValue valueWithPointer:owner];
    NSMutableArray *stop = [NSMutableArray array];
    @synchronized(gPlayers) {
        for (NSNumber *h in gOwners.allKeys) {
            if (![gOwners[h] isEqual:o]) continue;
            id p = gPlayers[h];
            if (p) [stop addObject:p];
            [gPlayers removeObjectForKey:h];
            [gLoops removeObjectForKey:h];
            [gLevels removeObjectForKey:h];
            [gOwners removeObjectForKey:h];
        }
        [gMutedOwners removeObject:o];
    }
    for (id p in stop) [p stop];
}

void J4IIOSSetOwnerMuted(void *owner, BOOL muted) {
    audioInit();
    NSValue *o = [NSValue valueWithPointer:owner];
    NSMutableDictionary *mine = [NSMutableDictionary dictionary];
    @synchronized(gPlayers) {
        if (muted) [gMutedOwners addObject:o];
        else [gMutedOwners removeObject:o];
        for (NSNumber *h in gOwners)
            if ([gOwners[h] isEqual:o] && gPlayers[h]) mine[h] = gPlayers[h];
    }
    for (NSNumber *h in mine) {
        id p = mine[h];
        if ([p isKindOfClass:[AVAudioPlayer class]]) ((AVAudioPlayer *)p).volume = levelFor(h.intValue);
        else if (muted && [p isKindOfClass:[AVMIDIPlayer class]]) [p stop];
    }
}

/* ---------------- misc ---------------- */
static void ios_vibrate(void *u, int ms) { AudioServicesPlaySystemSound(kSystemSoundID_Vibrate); }

static int ios_platform_request(void *u, const char *url) {
    NSString *s = [NSString stringWithUTF8String:url];
    dispatch_async(dispatch_get_main_queue(), ^{
        NSURL *nu = [NSURL URLWithString:s];
        if (nu) [[UIApplication sharedApplication] openURL:nu options:@{} completionHandler:nil];
    });
    return 0;
}

void J4IIOSHostFill(J4IHost *host) {
    host->font_metrics = ios_font_metrics;
    host->glyph = ios_glyph;
    host->decode_image = ios_decode;
    host->audio_create = ios_audio_create;
    host->audio_play = ios_audio_play;
    host->audio_stop = ios_audio_stop;
    host->audio_close = ios_audio_close;
    host->audio_volume = ios_audio_volume;
    host->audio_is_playing = ios_audio_is_playing;
    host->vibrate = ios_vibrate;
    host->platform_request = ios_platform_request;
}
