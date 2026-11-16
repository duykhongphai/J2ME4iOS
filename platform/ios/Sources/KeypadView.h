#import <UIKit/UIKit.h>

/// On-screen keypad. Fingers can slide between keys (the old key is released, the new one pressed).
/// Two layouts: a phone keypad (D-pad + 0-9) or a gamepad (8-way joystick + Fire + a few keys).
@interface KeypadView : UIView
/// Called on the main thread with a MIDP key code.
@property (nonatomic, copy) void (^onKey)(int code, BOOL down);
/// Called when the menu button is pressed.
@property (nonatomic, copy) void (^onMenu)(UIView *sourceView);
/// In landscape the screen sits in this rectangle and the keys go left and right of it.
@property (nonatomic) CGRect screenGap;
@property (nonatomic) BOOL landscape;
/// Gamepad layout instead of the phone keypad.
@property (nonatomic) BOOL gamepad;
/// Key opacity in landscape (keys may overlap the game there). 0.2 ... 1.
@property (nonatomic) CGFloat keyAlpha;
/// Light haptic tap on key press.
@property (nonatomic) BOOL haptics;
/// Releases every held key (e.g. when the keypad is hidden).
- (void)releaseAll;
@end
