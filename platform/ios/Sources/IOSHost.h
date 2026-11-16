#import <Foundation/Foundation.h>
#include "j4i.h"

/// Fills the platform services (fonts, images, audio, vibration, URLs) of a J4IHost.
void J4IIOSHostFill(J4IHost *host);

/// Stops and releases every audio player (called when a game exits).
void J4IIOSAudioReset(void);
