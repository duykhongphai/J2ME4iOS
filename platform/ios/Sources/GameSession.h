#import <UIKit/UIKit.h>
#include "j4i.h"

@class GameViewController;

/// Posted on the main thread when a session starts or ends.
extern NSString *const J4ISessionsChangedNotification;

/// One running game: its own VM (on its own thread), frame buffer, audio and save slot.
/// Several sessions can run at once; a GameViewController shows one of them.
@interface GameSession : NSObject
@property (nonatomic, readonly) NSString *jarPath, *file, *title;
@property (nonatomic, readonly) int slot, width, height;
@property (nonatomic, readonly) BOOL running;
/// The controller currently showing this session (nil while it runs in the background).
@property (nonatomic, weak) GameViewController *viewController;

/// Every running session, oldest first.
+ (NSArray<GameSession *> *)all;
/// The session of this game in this slot, if running.
+ (GameSession *)runningFile:(NSString *)file slot:(int)slot;
/// Starts a new VM. Settings are read from NSUserDefaults (see Paths.h).
+ (GameSession *)startJar:(NSString *)jarPath slot:(int)slot title:(NSString *)title;

/// Save data directory of a game slot (slot 1 = the game's original directory).
+ (NSString *)dataDirForFile:(NSString *)file slot:(int)slot;

- (void)postEvent:(int)type a:(int)a b:(int)b;
- (void)textResult:(int)reqId text:(NSString *)text; /* nil = cancelled */
- (void)requestExit;

/// Calls block with the latest frame (under the frame lock). Returns NO if there is none.
- (BOOL)withFrame:(void (^)(const uint32_t *argb, int w, int h))block;
/// Frames presented since the last call.
- (int)takeFrameCount;

/// A text-input request that arrived while no controller was showing the session.
@property (nonatomic, copy) void (^pendingTextRequest)(GameViewController *vc);

/// Mutes this session's sounds (used for background sessions).
@property (nonatomic) BOOL audioMuted;
@end
