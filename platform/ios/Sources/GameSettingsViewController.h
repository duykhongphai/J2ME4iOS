#import <UIKit/UIKit.h>

/// Per-game settings (screen, keypad, fonts, system properties). Values live in NSUserDefaults
/// through J4IGameSetting(); most take effect the next time the game starts.
@interface GameSettingsViewController : UITableViewController
- (instancetype)initWithJarFile:(NSString *)jarFileName title:(NSString *)title;
@end
