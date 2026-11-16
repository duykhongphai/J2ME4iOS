#import <Foundation/Foundation.h>

/// Game controller (MFi / Xbox / PlayStation) button -> MIDP key mapping, app wide.
/// Button ids: A B X Y LB RB LT RT Menu Options. D-pad and left stick are always the arrows,
/// the right stick gives 2/4/6/8.
NSArray<NSString *> *J4IPadButtons(void);
NSString *J4IPadButtonName(NSString *buttonId);
int J4IPadMapping(NSString *buttonId); /* 0 = not mapped */
void J4ISetPadMapping(NSString *buttonId, int code);
void J4IResetPadMappings(void);

/// Every MIDP key a button can be mapped to, and its display name.
NSArray<NSNumber *> *J4IMidpKeyCodes(void);
NSString *J4IMidpKeyName(int code);
