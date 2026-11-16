#import <Foundation/Foundation.h>

static inline NSString *J4IDocumentsDir(void) {
    return NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, YES).firstObject;
}

/// Per-game RecordStore directory (outside Documents so it is not shown in Files).
static inline NSString *J4IDataDirFor(NSString *jarFileName) {
    NSString *base = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, YES).firstObject;
    return [[base stringByAppendingPathComponent:@"RMS"] stringByAppendingPathComponent:jarFileName];
}
