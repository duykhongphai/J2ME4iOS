#import "Controls.h"
#include "j4i.h"

NSArray<NSString *> *J4IPadButtons(void) { return @[@"A", @"B", @"X", @"Y", @"LB", @"RB", @"LT", @"RT", @"Menu", @"Options"]; }

NSString *J4IPadButtonName(NSString *b) {
    NSDictionary *names = @{@"A": @"Nút A", @"B": @"Nút B", @"X": @"Nút X", @"Y": @"Nút Y", @"LB": @"LB / L1", @"RB": @"RB / R1",
                            @"LT": @"LT / L2", @"RT": @"RT / R2", @"Menu": @"Menu / Start", @"Options": @"Options / Select"};
    return names[b] ?: b;
}

static int defaultMapping(NSString *b) {
    NSDictionary *d = @{@"A": @(J4I_KEY_FIRE), @"B": @(J4I_KEY_SOFT_RIGHT), @"X": @('*'), @"Y": @('#'), @"LB": @(J4I_KEY_SOFT_LEFT),
                        @"RB": @(J4I_KEY_SOFT_RIGHT), @"LT": @(J4I_KEY_CLEAR), @"RT": @('0'), @"Menu": @(J4I_KEY_SOFT_RIGHT),
                        @"Options": @(J4I_KEY_SOFT_LEFT)};
    return [d[b] intValue];
}

int J4IPadMapping(NSString *b) {
    NSString *key = [@"pad:" stringByAppendingString:b];
    NSUserDefaults *d = [NSUserDefaults standardUserDefaults];
    if ([d objectForKey:key]) return (int)[d integerForKey:key];
    return defaultMapping(b);
}

void J4ISetPadMapping(NSString *b, int code) {
    [[NSUserDefaults standardUserDefaults] setInteger:code forKey:[@"pad:" stringByAppendingString:b]];
}

void J4IResetPadMappings(void) {
    for (NSString *b in J4IPadButtons()) [[NSUserDefaults standardUserDefaults] removeObjectForKey:[@"pad:" stringByAppendingString:b]];
}

NSArray<NSNumber *> *J4IMidpKeyCodes(void) {
    return @[@(J4I_KEY_FIRE), @(J4I_KEY_SOFT_LEFT), @(J4I_KEY_SOFT_RIGHT), @(J4I_KEY_CLEAR), @(J4I_KEY_UP), @(J4I_KEY_DOWN), @(J4I_KEY_LEFT),
             @(J4I_KEY_RIGHT), @('0'), @('1'), @('2'), @('3'), @('4'), @('5'), @('6'), @('7'), @('8'), @('9'), @('*'), @('#'), @(0)];
}

NSString *J4IMidpKeyName(int code) {
    switch (code) {
    case 0: return @"(không dùng)";
    case J4I_KEY_FIRE: return @"OK / Bắn";
    case J4I_KEY_SOFT_LEFT: return @"Phím mềm trái";
    case J4I_KEY_SOFT_RIGHT: return @"Phím mềm phải";
    case J4I_KEY_CLEAR: return @"C (xoá)";
    case J4I_KEY_UP: return @"Lên";
    case J4I_KEY_DOWN: return @"Xuống";
    case J4I_KEY_LEFT: return @"Trái";
    case J4I_KEY_RIGHT: return @"Phải";
    default: return [NSString stringWithFormat:@"Phím %c", (char)code];
    }
}
