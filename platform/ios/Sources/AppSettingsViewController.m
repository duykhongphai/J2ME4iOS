#import "AppSettingsViewController.h"
#import "Controls.h"
#import "IOSHost.h"
#import "LogViewController.h"
#include <math.h>

enum { SEC_SOUND, SEC_PAD, SEC_OTHER, SEC_COUNT };

@implementation AppSettingsViewController

- (instancetype)init { return [super initWithStyle:UITableViewStyleInsetGrouped]; }

- (void)viewDidLoad {
    [super viewDidLoad];
    self.title = @"Cài đặt";
    self.navigationItem.rightBarButtonItem = [[UIBarButtonItem alloc] initWithBarButtonSystemItem:UIBarButtonSystemItemDone
                                                                                           target:self
                                                                                           action:@selector(done)];
}

- (void)done { [self dismissViewControllerAnimated:YES completion:nil]; }

- (void)bgAudioChanged:(UISwitch *)sw { [[NSUserDefaults standardUserDefaults] setBool:sw.on forKey:@"bgAudio"]; }

- (NSInteger)numberOfSectionsInTableView:(UITableView *)tableView { return SEC_COUNT; }

- (NSInteger)tableView:(UITableView *)tableView numberOfRowsInSection:(NSInteger)section {
    if (section == SEC_SOUND) return 3;
    if (section == SEC_PAD) return J4IPadButtons().count + 1;
    return 1;
}

- (NSString *)tableView:(UITableView *)tableView titleForHeaderInSection:(NSInteger)section {
    if (section == SEC_SOUND) return @"Âm thanh";
    if (section == SEC_PAD) return @"Tay cầm (MFi, Xbox, PlayStation)";
    return @"Khác";
}

- (NSString *)tableView:(UITableView *)tableView titleForFooterInSection:(NSInteger)section {
    if (section == SEC_SOUND)
        return J4IIOSHasSoundBank() ? @"Đã có soundfont trong thư mục J2ME4iOS: chế độ Tự động sẽ dùng nó cho nhạc MIDI."
                                    : @"Chưa có soundfont (.sf2/.dls): nhạc MIDI phát bằng bộ tổng hợp tích hợp.";
    if (section == SEC_PAD) return @"D-pad và cần trái là phím mũi tên, cần phải là phím 2/4/6/8, nút Home mở menu game.";
    return @"Log ghi lại lỗi của game lần chạy gần nhất (thiếu class, lỗi Java...). Gửi log khi báo lỗi.";
}

- (UITableViewCell *)tableView:(UITableView *)tableView cellForRowAtIndexPath:(NSIndexPath *)indexPath {
    UITableViewCell *cell = [[UITableViewCell alloc] initWithStyle:UITableViewCellStyleValue1 reuseIdentifier:nil];
    cell.accessoryType = UITableViewCellAccessoryDisclosureIndicator;
    if (indexPath.section == SEC_SOUND) {
        if (indexPath.row == 0) {
            cell.textLabel.text = @"Âm lượng";
            int v = (int)lroundf(J4IIOSMasterVolume() * 100);
            cell.detailTextLabel.text = v == 0 ? @"Tắt tiếng" : [NSString stringWithFormat:@"%d%%", v];
        } else if (indexPath.row == 2) {
            cell.textLabel.text = @"Game chạy nền vẫn phát tiếng";
            UISwitch *sw = [[UISwitch alloc] init];
            sw.on = [[NSUserDefaults standardUserDefaults] boolForKey:@"bgAudio"];
            [sw addTarget:self action:@selector(bgAudioChanged:) forControlEvents:UIControlEventValueChanged];
            cell.accessoryView = sw;
            cell.selectionStyle = UITableViewCellSelectionStyleNone;
        } else {
            cell.textLabel.text = @"Nhạc MIDI";
            NSString *m = [[NSUserDefaults standardUserDefaults] stringForKey:@"midiMode"] ?: @"auto";
            cell.detailTextLabel.text = [m isEqualToString:@"builtin"] ? @"Bộ tổng hợp tích hợp" : @"Tự động";
        }
    } else if (indexPath.section == SEC_PAD) {
        NSArray *buttons = J4IPadButtons();
        if (indexPath.row < (NSInteger)buttons.count) {
            NSString *b = buttons[indexPath.row];
            cell.textLabel.text = J4IPadButtonName(b);
            cell.detailTextLabel.text = J4IMidpKeyName(J4IPadMapping(b));
        } else {
            cell.textLabel.text = @"Khôi phục mặc định";
            cell.textLabel.textColor = [UIColor systemRedColor];
            cell.accessoryType = UITableViewCellAccessoryNone;
        }
    } else {
        cell.textLabel.text = @"Xem log";
    }
    return cell;
}

- (void)present:(UIAlertController *)ac at:(NSIndexPath *)ip {
    UITableViewCell *cell = [self.tableView cellForRowAtIndexPath:ip];
    ac.popoverPresentationController.sourceView = cell ?: self.view;
    ac.popoverPresentationController.sourceRect = cell ? cell.bounds : self.view.bounds;
    [self presentViewController:ac animated:YES completion:nil];
}

- (void)tableView:(UITableView *)tableView didSelectRowAtIndexPath:(NSIndexPath *)indexPath {
    [tableView deselectRowAtIndexPath:indexPath animated:YES];
    if (indexPath.section == SEC_SOUND && indexPath.row == 0) {
        UIAlertController *ac = [UIAlertController alertControllerWithTitle:@"Âm lượng" message:nil preferredStyle:UIAlertControllerStyleActionSheet];
        for (NSNumber *v in @[@0, @25, @50, @75, @100]) {
            [ac addAction:[UIAlertAction actionWithTitle:v.intValue ? [NSString stringWithFormat:@"%d%%", v.intValue] : @"Tắt tiếng"
                                                   style:UIAlertActionStyleDefault
                                                 handler:^(UIAlertAction *a) {
                                                     J4IIOSSetMasterVolume(v.intValue / 100.0f);
                                                     [self.tableView reloadData];
                                                 }]];
        }
        [ac addAction:[UIAlertAction actionWithTitle:@"Huỷ" style:UIAlertActionStyleCancel handler:nil]];
        [self present:ac at:indexPath];
    } else if (indexPath.section == SEC_SOUND && indexPath.row == 2) {
        return;
    } else if (indexPath.section == SEC_SOUND) {
        UIAlertController *ac = [UIAlertController alertControllerWithTitle:@"Nhạc MIDI" message:nil preferredStyle:UIAlertControllerStyleActionSheet];
        NSArray *vals = @[@"auto", @"builtin"], *names = @[@"Tự động (soundfont nếu có)", @"Luôn dùng bộ tổng hợp tích hợp"];
        for (NSUInteger i = 0; i < vals.count; i++) {
            NSString *v = vals[i];
            [ac addAction:[UIAlertAction actionWithTitle:names[i]
                                                   style:UIAlertActionStyleDefault
                                                 handler:^(UIAlertAction *a) {
                                                     [[NSUserDefaults standardUserDefaults] setObject:v forKey:@"midiMode"];
                                                     [self.tableView reloadData];
                                                 }]];
        }
        [ac addAction:[UIAlertAction actionWithTitle:@"Huỷ" style:UIAlertActionStyleCancel handler:nil]];
        [self present:ac at:indexPath];
    } else if (indexPath.section == SEC_PAD) {
        NSArray *buttons = J4IPadButtons();
        if (indexPath.row >= (NSInteger)buttons.count) {
            J4IResetPadMappings();
            [self.tableView reloadData];
            return;
        }
        NSString *b = buttons[indexPath.row];
        UIAlertController *ac = [UIAlertController alertControllerWithTitle:J4IPadButtonName(b)
                                                                    message:@"Chọn phím điện thoại cho nút này"
                                                             preferredStyle:UIAlertControllerStyleActionSheet];
        for (NSNumber *code in J4IMidpKeyCodes()) {
            [ac addAction:[UIAlertAction actionWithTitle:J4IMidpKeyName(code.intValue)
                                                   style:UIAlertActionStyleDefault
                                                 handler:^(UIAlertAction *a) {
                                                     J4ISetPadMapping(b, code.intValue);
                                                     [self.tableView reloadData];
                                                 }]];
        }
        [ac addAction:[UIAlertAction actionWithTitle:@"Huỷ" style:UIAlertActionStyleCancel handler:nil]];
        [self present:ac at:indexPath];
    } else {
        [self.navigationController pushViewController:[[LogViewController alloc] init] animated:YES];
    }
}

@end
