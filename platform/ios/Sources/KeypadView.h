#import <UIKit/UIKit.h>

/// Phone style keypad: soft keys, D-pad and 0-9 * #. Supports multi-touch.
@interface KeypadView : UIView
/// Called on the main thread with a MIDP key code.
@property (nonatomic, copy) void (^onKey)(int code, BOOL down);
/// Called when the menu (exit) button is pressed.
@property (nonatomic, copy) void (^onMenu)(UIView *sourceView);
/// In landscape the screen sits in this rectangle and the keys go left and right of it.
@property (nonatomic) CGRect screenGap;
@property (nonatomic) BOOL landscape;
@end
