#import <UIKit/UIKit.h>

/// Runs one MIDlet suite. Only one game can run at a time (the VM is a singleton).
@interface GameViewController : UIViewController
- (instancetype)initWithJarPath:(NSString *)path width:(int)w height:(int)h;
@end
