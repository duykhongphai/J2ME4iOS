#import <Foundation/Foundation.h>

static inline NSString *J4IDocumentsDir(void) {
    return NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, YES).firstObject;
}

/// Per-game RecordStore directory (outside Documents so it is not shown in Files).
static inline NSString *J4IDataDirFor(NSString *jarFileName) {
    NSString *base = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, YES).firstObject;
    return [[base stringByAppendingPathComponent:@"RMS"] stringByAppendingPathComponent:jarFileName];
}

/* Per-game display settings stored in NSUserDefaults as "<key>:<jar file name>".
 *   res     "240x320"
 *   orient  "auto" | "portrait" | "landscape"
 *   scale   "fit" | "integer" | "stretch"
 *   smooth  "0" | "1"
 *   keypad  "1" | "0" */
static inline NSString *J4IGameSetting(NSString *jarFileName, NSString *key, NSString *def) {
    NSString *v = [[NSUserDefaults standardUserDefaults] stringForKey:[NSString stringWithFormat:@"%@:%@", key, jarFileName]];
    return v.length ? v : def;
}

static inline void J4ISetGameSetting(NSString *jarFileName, NSString *key, NSString *value) {
    [[NSUserDefaults standardUserDefaults] setObject:value forKey:[NSString stringWithFormat:@"%@:%@", key, jarFileName]];
}

/// A .jad in Documents that belongs to the jar: same base name, or its MIDlet-Jar-URL names the jar.
static inline NSString *J4IJadFor(NSString *jarFileName) {
    NSString *docs = J4IDocumentsDir();
    NSString *same = [docs stringByAppendingPathComponent:[[jarFileName stringByDeletingPathExtension] stringByAppendingPathExtension:@"jad"]];
    if ([[NSFileManager defaultManager] fileExistsAtPath:same]) return same;
    for (NSString *f in [[NSFileManager defaultManager] contentsOfDirectoryAtPath:docs error:nil]) {
        if (![f.pathExtension.lowercaseString isEqualToString:@"jad"]) continue;
        NSString *path = [docs stringByAppendingPathComponent:f];
        NSString *text = [NSString stringWithContentsOfFile:path encoding:NSUTF8StringEncoding error:nil]
                             ?: [NSString stringWithContentsOfFile:path encoding:NSISOLatin1StringEncoding error:nil];
        for (NSString *line in [text componentsSeparatedByCharactersInSet:[NSCharacterSet newlineCharacterSet]]) {
            if (![line.lowercaseString hasPrefix:@"midlet-jar-url:"]) continue;
            NSString *url = [[line substringFromIndex:15] stringByTrimmingCharactersInSet:[NSCharacterSet whitespaceCharacterSet]];
            if ([url.lastPathComponent isEqualToString:jarFileName]) return path;
        }
    }
    return nil;
}
