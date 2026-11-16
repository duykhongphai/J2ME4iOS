#import <Foundation/Foundation.h>
#include "j4i.h"

/// Fills the platform services (fonts, images, audio, vibration, URLs) of a J4IHost.
void J4IIOSHostFill(J4IHost *host);

/// Stops and releases every audio player.
void J4IIOSAudioReset(void);

/// Audio players belong to the J4IHost.user that created them (one per running game).
/// Stops and releases one game's players (called when that game exits).
void J4IIOSAudioResetOwner(void *owner);
/// Silences one game's players (background games), keeping their state.
void J4IIOSSetOwnerMuted(void *owner, BOOL muted);

/// YES if a .sf2/.dls soundfont is available for AVMIDIPlayer.
BOOL J4IIOSHasSoundBank(void);

/// App-wide volume 0..1 applied on top of each player's own level. Stored in NSUserDefaults.
float J4IIOSMasterVolume(void);
void J4IIOSSetMasterVolume(float v);

/// In-memory log of the VM (last lines), shown by the log viewer. Thread safe.
void J4IIOSLogAppend(const char *line);
NSString *J4IIOSLogText(void);
void J4IIOSLogClear(void);
