#import <UIKit/UIKit.h>

@class GameSession;

/// Shows one running GameSession: screen, keypad, controllers and the in-game menu.
/// Dismissing it leaves the session running in the background.
@interface GameViewController : UIViewController
- (instancetype)initWithSession:(GameSession *)session;
@property (nonatomic, readonly) GameSession *session;

/* called by GameSession on the main thread */
- (void)sessionFrameReady;
- (void)sessionExited;
- (void)askText:(int)reqId title:(NSString *)title text:(NSString *)text maxLen:(int)maxLen constraints:(int)constraints;
@end
