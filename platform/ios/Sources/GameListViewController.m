#import "GameListViewController.h"
#import "GameViewController.h"
#import "GameSession.h"
#import "Paths.h"
#import "GameSettingsViewController.h"
#import "AppSettingsViewController.h"
#import <UniformTypeIdentifiers/UniformTypeIdentifiers.h>
#include "j4i.h"

static NSString *const kGamesChanged = @"J4IGamesChanged";
static const int kMaxSlots = 5;

@interface GameListViewController () <UIDocumentPickerDelegate>
@end

@implementation GameListViewController {
    NSArray<NSString *> *_jars;
    NSArray<GameSession *> *_running;
    NSMutableDictionary<NSString *, NSDictionary *> *_info;
}

+ (BOOL)importURL:(NSURL *)url {
    NSString *ext = url.pathExtension.lowercaseString;
    if (![ext isEqualToString:@"jar"] && ![ext isEqualToString:@"jad"] && ![ext isEqualToString:@"sf2"] && ![ext isEqualToString:@"dls"]) return NO;
    BOOL scoped = [url startAccessingSecurityScopedResource];
    NSFileManager *fm = [NSFileManager defaultManager];
    NSString *dst = [J4IDocumentsDir() stringByAppendingPathComponent:url.lastPathComponent];
    NSError *err = nil;
    BOOL ok = YES;
    if (![url.path isEqualToString:dst]) {
        [fm removeItemAtPath:dst error:nil];
        ok = [fm copyItemAtPath:url.path toPath:dst error:&err];
        if ([url.path containsString:@"/Inbox/"]) [fm removeItemAtPath:url.path error:nil];
    }
    if (scoped) [url stopAccessingSecurityScopedResource];
    if (!ok) NSLog(@"import failed: %@", err);
    [[NSNotificationCenter defaultCenter] postNotificationName:kGamesChanged object:nil];
    return ok;
}

- (void)viewDidLoad {
    [super viewDidLoad];
    self.title = @"J2ME4iOS";
    _info = [NSMutableDictionary dictionary];
    self.navigationItem.rightBarButtonItem = [[UIBarButtonItem alloc] initWithBarButtonSystemItem:UIBarButtonSystemItemAdd
                                                                                           target:self
                                                                                           action:@selector(pickFile)];
    self.navigationItem.leftBarButtonItem = [[UIBarButtonItem alloc] initWithImage:[UIImage systemImageNamed:@"gearshape"]
                                                                             style:UIBarButtonItemStylePlain
                                                                            target:self
                                                                            action:@selector(openAppSettings)];
    NSNotificationCenter *nc = [NSNotificationCenter defaultCenter];
    [nc addObserver:self selector:@selector(reload) name:kGamesChanged object:nil];
    [nc addObserver:self selector:@selector(reload) name:J4ISessionsChangedNotification object:nil];
    [self reload];
}

- (void)viewWillAppear:(BOOL)animated {
    [super viewWillAppear:animated];
    [self reload];
}

- (void)reload {
    NSArray *files = [[NSFileManager defaultManager] contentsOfDirectoryAtPath:J4IDocumentsDir() error:nil];
    NSMutableArray *jars = [NSMutableArray array];
    for (NSString *f in files)
        if ([f.pathExtension.lowercaseString isEqualToString:@"jar"]) [jars addObject:f];
    _jars = [jars sortedArrayUsingSelector:@selector(localizedCaseInsensitiveCompare:)];
    _running = [GameSession all];
    [self.tableView reloadData];
}

- (NSDictionary *)infoFor:(NSString *)file {
    NSDictionary *d = _info[file];
    if (d) return d;
    NSString *path = [J4IDocumentsDir() stringByAppendingPathComponent:file];
    char buf[256];
    NSMutableDictionary *m = [NSMutableDictionary dictionary];
    if (j4i_manifest_value(path.fileSystemRepresentation, "MIDlet-Name", buf, sizeof buf) > 0) m[@"name"] = [NSString stringWithUTF8String:buf] ?: file;
    if (j4i_manifest_value(path.fileSystemRepresentation, "MIDlet-Vendor", buf, sizeof buf) > 0) m[@"vendor"] = [NSString stringWithUTF8String:buf] ?: @"";
    int len = 0;
    uint8_t *icon = j4i_read_icon(path.fileSystemRepresentation, &len);
    if (icon) {
        UIImage *img = [UIImage imageWithData:[NSData dataWithBytes:icon length:len]];
        if (img) m[@"icon"] = img;
        free(icon);
    }
    _info[file] = m;
    return m;
}

- (NSString *)resolutionFor:(NSString *)file { return J4IGameSetting(file, @"res", @"240x320"); }

- (NSString *)displayName:(NSString *)file {
    NSString *custom = J4IGameSetting(file, @"name", @"");
    if (custom.length) return custom;
    return [self infoFor:file][@"name"] ?: file;
}

- (UIImage *)iconFor:(NSString *)file {
    UIImage *icon = [self infoFor:file][@"icon"];
    if (!icon) return [UIImage systemImageNamed:@"gamecontroller"];
    UIGraphicsImageRenderer *r = [[UIGraphicsImageRenderer alloc] initWithSize:CGSizeMake(36, 36)];
    return [r imageWithActions:^(UIGraphicsImageRendererContext *ctx) {
        CGContextSetInterpolationQuality(ctx.CGContext, kCGInterpolationNone);
        [icon drawInRect:CGRectMake(0, 0, 36, 36)];
    }];
}

#pragma mark - table

/* section 0 = running games (only when there are any), last section = all games */
- (BOOL)isRunningSection:(NSInteger)section { return _running.count > 0 && section == 0; }

- (NSInteger)numberOfSectionsInTableView:(UITableView *)tableView { return _running.count > 0 ? 2 : 1; }

- (NSInteger)tableView:(UITableView *)tableView numberOfRowsInSection:(NSInteger)section {
    return [self isRunningSection:section] ? _running.count : _jars.count;
}

- (NSString *)tableView:(UITableView *)tableView titleForHeaderInSection:(NSInteger)section {
    if ([self isRunningSection:section]) return @"Đang chạy";
    return _running.count > 0 ? @"Tất cả game" : nil;
}

- (NSString *)tableView:(UITableView *)tableView titleForFooterInSection:(NSInteger)section {
    if ([self isRunningSection:section]) return @"Các game này vẫn chạy nền. Chạm để quay lại.";
    return _jars.count == 0 ? @"Chưa có game. Bấm + để chọn file .jar (chọn kèm .jad nếu có), hoặc chép vào thư mục J2ME4iOS trong app Tệp (Files)."
                            : @"Chạm để chơi. Vuốt sang trái để xoá.";
}

- (UITableViewCell *)tableView:(UITableView *)tableView cellForRowAtIndexPath:(NSIndexPath *)indexPath {
    UITableViewCell *cell = [tableView dequeueReusableCellWithIdentifier:@"game"];
    if (!cell) cell = [[UITableViewCell alloc] initWithStyle:UITableViewCellStyleSubtitle reuseIdentifier:@"game"];
    cell.detailTextLabel.textColor = [UIColor secondaryLabelColor];
    cell.accessoryType = UITableViewCellAccessoryDisclosureIndicator;
    if ([self isRunningSection:indexPath.section]) {
        GameSession *s = _running[indexPath.row];
        cell.textLabel.text = s.title;
        NSString *proxy = J4IGameSetting([NSString stringWithFormat:@"%@#%d", s.file, s.slot], @"proxy", @"");
        cell.detailTextLabel.text = [NSString stringWithFormat:@"▶︎ đang chạy · khe lưu %d%@", s.slot, proxy.length ? @" · proxy" : @""];
        cell.detailTextLabel.textColor = [UIColor systemGreenColor];
        cell.imageView.image = [self iconFor:s.file];
        return cell;
    }
    NSString *file = _jars[indexPath.row];
    cell.textLabel.text = [self displayName:file];
    cell.detailTextLabel.text = [NSString stringWithFormat:@"%@ · %@%@", file, [self resolutionFor:file], J4IJadFor(file) ? @" · JAD" : @""];
    cell.imageView.image = [self iconFor:file];
    return cell;
}

- (void)present:(UIAlertController *)ac from:(NSIndexPath *)indexPath {
    UITableViewCell *cell = [self.tableView cellForRowAtIndexPath:indexPath];
    ac.popoverPresentationController.sourceView = cell ?: self.view;
    ac.popoverPresentationController.sourceRect = cell ? cell.bounds : self.view.bounds;
    [self presentViewController:ac animated:YES completion:nil];
}

- (void)tableView:(UITableView *)tableView didSelectRowAtIndexPath:(NSIndexPath *)indexPath {
    [tableView deselectRowAtIndexPath:indexPath animated:YES];
    if ([self isRunningSection:indexPath.section]) {
        [self show:_running[indexPath.row]];
        return;
    }
    NSString *file = _jars[indexPath.row];
    NSString *res = [self resolutionFor:file];
    UIAlertController *ac = [UIAlertController alertControllerWithTitle:[self displayName:file]
                                                                message:J4IJadFor(file) ? @"Có file JAD đi kèm" : nil
                                                         preferredStyle:UIAlertControllerStyleActionSheet];
    GameSession *first = nil;
    for (GameSession *s in _running)
        if ([s.file isEqualToString:file]) {
            first = s;
            break;
        }
    if (first) {
        [ac addAction:[UIAlertAction actionWithTitle:[NSString stringWithFormat:@"Quay lại game (khe %d)", first.slot]
                                               style:UIAlertActionStyleDefault
                                             handler:^(UIAlertAction *a) { [self show:first]; }]];
    } else {
        [ac addAction:[UIAlertAction actionWithTitle:[NSString stringWithFormat:@"Chơi (%@)", res]
                                               style:UIAlertActionStyleDefault
                                             handler:^(UIAlertAction *a) { [self play:file slot:1]; }]];
    }
    [ac addAction:[UIAlertAction actionWithTitle:first ? @"Mở thêm ở khe lưu khác…" : @"Chơi ở khe lưu khác…"
                                           style:UIAlertActionStyleDefault
                                         handler:^(UIAlertAction *a) { [self chooseSlot:file from:indexPath]; }]];
    [ac addAction:[UIAlertAction actionWithTitle:@"Cài đặt game…"
                                           style:UIAlertActionStyleDefault
                                         handler:^(UIAlertAction *a) { [self openSettings:file]; }]];
    [ac addAction:[UIAlertAction actionWithTitle:@"Đổi tên…"
                                           style:UIAlertActionStyleDefault
                                         handler:^(UIAlertAction *a) { [self rename:file]; }]];
    [ac addAction:[UIAlertAction actionWithTitle:@"Xoá dữ liệu lưu (mọi khe)"
                                           style:UIAlertActionStyleDestructive
                                         handler:^(UIAlertAction *a) { [self confirmClearData:file]; }]];
    [ac addAction:[UIAlertAction actionWithTitle:@"Huỷ" style:UIAlertActionStyleCancel handler:nil]];
    [self present:ac from:indexPath];
}

- (BOOL)slotHasData:(NSString *)file slot:(int)slot {
    NSArray *items = [[NSFileManager defaultManager] contentsOfDirectoryAtPath:[GameSession dataDirForFile:file slot:slot] error:nil];
    return items.count > 0;
}

- (void)chooseSlot:(NSString *)file from:(NSIndexPath *)indexPath {
    UIAlertController *ac = [UIAlertController alertControllerWithTitle:@"Khe lưu"
                                                                message:@"Mỗi khe có dữ liệu lưu (và proxy) riêng, ví dụ mỗi khe một tài khoản. "
                                                                        @"Có thể chạy nhiều khe cùng lúc."
                                                         preferredStyle:UIAlertControllerStyleActionSheet];
    for (int slot = 1; slot <= kMaxSlots; slot++) {
        GameSession *running = [GameSession runningFile:file slot:slot];
        NSString *state = running ? @"đang chạy" : ([self slotHasData:file slot:slot] ? @"có dữ liệu" : @"trống");
        NSString *proxy = J4IGameSetting([NSString stringWithFormat:@"%@#%d", file, slot], @"proxy", @"");
        NSString *t = [NSString stringWithFormat:@"Khe %d · %@%@", slot, state, proxy.length ? @" · proxy" : @""];
        int sl = slot;
        [ac addAction:[UIAlertAction actionWithTitle:t
                                               style:UIAlertActionStyleDefault
                                             handler:^(UIAlertAction *a) { [self play:file slot:sl]; }]];
    }
    [ac addAction:[UIAlertAction actionWithTitle:@"Huỷ" style:UIAlertActionStyleCancel handler:nil]];
    [self present:ac from:indexPath];
}

- (void)play:(NSString *)file slot:(int)slot {
    GameSession *s = [GameSession runningFile:file slot:slot];
    if (!s) {
        if ([GameSession all].count >= 4) {
            UIAlertController *ac = [UIAlertController alertControllerWithTitle:@"Đang chạy nhiều game"
                                                                        message:@"Mỗi game dùng khoảng 64 MB bộ nhớ. Chạy quá nhiều game cùng lúc "
                                                                                @"có thể làm iOS đóng app. Vẫn mở thêm?"
                                                                 preferredStyle:UIAlertControllerStyleAlert];
            [ac addAction:[UIAlertAction actionWithTitle:@"Huỷ" style:UIAlertActionStyleCancel handler:nil]];
            [ac addAction:[UIAlertAction actionWithTitle:@"Mở"
                                                   style:UIAlertActionStyleDefault
                                                 handler:^(UIAlertAction *a) { [self startFile:file slot:slot]; }]];
            [self presentViewController:ac animated:YES completion:nil];
            return;
        }
        [self startFile:file slot:slot];
        return;
    }
    [self show:s];
}

- (void)startFile:(NSString *)file slot:(int)slot {
    GameSession *s = [GameSession startJar:[J4IDocumentsDir() stringByAppendingPathComponent:file] slot:slot title:[self displayName:file]];
    [self show:s];
}

- (void)show:(GameSession *)s {
    if (!s.running) return;
    GameViewController *g = [[GameViewController alloc] initWithSession:s];
    g.modalPresentationStyle = UIModalPresentationFullScreen;
    [self presentViewController:g animated:YES completion:nil];
}

- (void)openSettings:(NSString *)file {
    GameSettingsViewController *s = [[GameSettingsViewController alloc] initWithJarFile:file title:[self displayName:file]];
    [self.navigationController pushViewController:s animated:YES];
}

- (void)openAppSettings {
    UINavigationController *nav = [[UINavigationController alloc] initWithRootViewController:[[AppSettingsViewController alloc] init]];
    [self presentViewController:nav animated:YES completion:nil];
}

- (void)rename:(NSString *)file {
    UIAlertController *ac = [UIAlertController alertControllerWithTitle:@"Đổi tên game"
                                                                message:@"Để trống để dùng tên trong file game."
                                                         preferredStyle:UIAlertControllerStyleAlert];
    NSString *cur = J4IGameSetting(file, @"name", @"");
    NSString *orig = [self infoFor:file][@"name"] ?: file;
    [ac addTextFieldWithConfigurationHandler:^(UITextField *tf) {
        tf.text = cur;
        tf.placeholder = orig;
        tf.clearButtonMode = UITextFieldViewModeWhileEditing;
    }];
    __weak UIAlertController *weakAc = ac;
    [ac addAction:[UIAlertAction actionWithTitle:@"Huỷ" style:UIAlertActionStyleCancel handler:nil]];
    [ac addAction:[UIAlertAction actionWithTitle:@"Lưu"
                                           style:UIAlertActionStyleDefault
                                         handler:^(UIAlertAction *a) {
                                             NSString *t = [weakAc.textFields.firstObject.text
                                                 stringByTrimmingCharactersInSet:[NSCharacterSet whitespaceAndNewlineCharacterSet]] ?: @"";
                                             J4ISetGameSetting(file, @"name", t);
                                             [self.tableView reloadData];
                                         }]];
    [self presentViewController:ac animated:YES completion:nil];
}

- (BOOL)isRunning:(NSString *)file {
    for (GameSession *s in [GameSession all])
        if ([s.file isEqualToString:file]) return YES;
    return NO;
}

- (void)alert:(NSString *)title message:(NSString *)msg {
    UIAlertController *ac = [UIAlertController alertControllerWithTitle:title message:msg preferredStyle:UIAlertControllerStyleAlert];
    [ac addAction:[UIAlertAction actionWithTitle:@"OK" style:UIAlertActionStyleDefault handler:nil]];
    [self presentViewController:ac animated:YES completion:nil];
}

- (void)confirmClearData:(NSString *)file {
    if ([self isRunning:file]) {
        [self alert:@"Game đang chạy" message:@"Thoát game trước khi xoá dữ liệu lưu."];
        return;
    }
    UIAlertController *ac = [UIAlertController alertControllerWithTitle:@"Xoá dữ liệu lưu?"
                                                                message:@"Mọi điểm số, màn chơi đã lưu ở tất cả các khe của game này sẽ mất, không khôi phục được."
                                                         preferredStyle:UIAlertControllerStyleAlert];
    [ac addAction:[UIAlertAction actionWithTitle:@"Huỷ" style:UIAlertActionStyleCancel handler:nil]];
    [ac addAction:[UIAlertAction actionWithTitle:@"Xoá"
                                           style:UIAlertActionStyleDestructive
                                         handler:^(UIAlertAction *a) {
                                             for (int slot = 1; slot <= kMaxSlots; slot++)
                                                 [[NSFileManager defaultManager] removeItemAtPath:[GameSession dataDirForFile:file slot:slot] error:nil];
                                         }]];
    [self presentViewController:ac animated:YES completion:nil];
}

- (BOOL)tableView:(UITableView *)tableView canEditRowAtIndexPath:(NSIndexPath *)indexPath {
    return ![self isRunningSection:indexPath.section];
}

- (void)tableView:(UITableView *)tableView
    commitEditingStyle:(UITableViewCellEditingStyle)editingStyle
     forRowAtIndexPath:(NSIndexPath *)indexPath {
    if (editingStyle != UITableViewCellEditingStyleDelete || [self isRunningSection:indexPath.section]) return;
    NSString *file = _jars[indexPath.row];
    if ([self isRunning:file]) {
        [self alert:@"Game đang chạy" message:@"Thoát game trước khi xoá."];
        return;
    }
    [[NSFileManager defaultManager] removeItemAtPath:[J4IDocumentsDir() stringByAppendingPathComponent:file] error:nil];
    for (int slot = 1; slot <= kMaxSlots; slot++)
        [[NSFileManager defaultManager] removeItemAtPath:[GameSession dataDirForFile:file slot:slot] error:nil];
    [_info removeObjectForKey:file];
    [self reload];
}

#pragma mark - import

- (void)pickFile {
    UIDocumentPickerViewController *p = [[UIDocumentPickerViewController alloc] initForOpeningContentTypes:@[UTTypeData] asCopy:YES];
    p.delegate = self;
    p.allowsMultipleSelection = YES;
    [self presentViewController:p animated:YES completion:nil];
}

- (void)documentPicker:(UIDocumentPickerViewController *)controller didPickDocumentsAtURLs:(NSArray<NSURL *> *)urls {
    int bad = 0;
    for (NSURL *u in urls)
        if (![GameListViewController importURL:u]) bad++;
    if (bad) [self alert:@"Không nhập được" message:@"Chỉ nhận file .jar (game J2ME), .jad (đi kèm game) và soundfont .sf2/.dls."];
    [self reload];
}

@end
