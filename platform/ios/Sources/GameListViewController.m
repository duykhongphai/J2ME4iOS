#import "GameListViewController.h"
#import "GameViewController.h"
#import "Paths.h"
#import <UniformTypeIdentifiers/UniformTypeIdentifiers.h>
#include "j4i.h"

static NSString *const kGamesChanged = @"J4IGamesChanged";

@interface GameListViewController () <UIDocumentPickerDelegate>
@end

@implementation GameListViewController {
    NSArray<NSString *> *_jars;
    NSMutableDictionary<NSString *, NSDictionary *> *_info;
}

+ (BOOL)importURL:(NSURL *)url {
    NSString *ext = url.pathExtension.lowercaseString;
    if (![ext isEqualToString:@"jar"]) return NO;
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
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(reload) name:kGamesChanged object:nil];
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

- (NSString *)resolutionFor:(NSString *)file {
    return J4IGameSetting(file, @"res", @"240x320");
}

#pragma mark - table

- (NSInteger)tableView:(UITableView *)tableView numberOfRowsInSection:(NSInteger)section {
    return _jars.count;
}

- (NSString *)tableView:(UITableView *)tableView titleForFooterInSection:(NSInteger)section {
    return _jars.count == 0 ? @"Chưa có game. Bấm + để chọn file .jar, hoặc chép .jar vào thư mục J2ME4iOS trong app Tệp (Files)."
                            : @"Chạm để chơi. Vuốt sang trái để xoá.";
}

- (UITableViewCell *)tableView:(UITableView *)tableView cellForRowAtIndexPath:(NSIndexPath *)indexPath {
    UITableViewCell *cell = [tableView dequeueReusableCellWithIdentifier:@"game"];
    if (!cell) cell = [[UITableViewCell alloc] initWithStyle:UITableViewCellStyleSubtitle reuseIdentifier:@"game"];
    NSString *file = _jars[indexPath.row];
    NSDictionary *info = [self infoFor:file];
    cell.textLabel.text = info[@"name"] ?: file;
    cell.detailTextLabel.text = [NSString stringWithFormat:@"%@ · %@", file, [self resolutionFor:file]];
    cell.detailTextLabel.textColor = [UIColor secondaryLabelColor];
    UIImage *icon = info[@"icon"];
    if (icon) {
        UIGraphicsImageRenderer *r = [[UIGraphicsImageRenderer alloc] initWithSize:CGSizeMake(36, 36)];
        cell.imageView.image = [r imageWithActions:^(UIGraphicsImageRendererContext *ctx) {
            CGContextSetInterpolationQuality(ctx.CGContext, kCGInterpolationNone);
            [icon drawInRect:CGRectMake(0, 0, 36, 36)];
        }];
    } else {
        cell.imageView.image = [UIImage systemImageNamed:@"gamecontroller"];
    }
    cell.accessoryType = UITableViewCellAccessoryDisclosureIndicator;
    return cell;
}

- (void)tableView:(UITableView *)tableView didSelectRowAtIndexPath:(NSIndexPath *)indexPath {
    [tableView deselectRowAtIndexPath:indexPath animated:YES];
    NSString *file = _jars[indexPath.row];
    NSString *res = [self resolutionFor:file];
    UIAlertController *ac = [UIAlertController alertControllerWithTitle:[self infoFor:file][@"name"] ?: file
                                                                message:nil
                                                         preferredStyle:UIAlertControllerStyleActionSheet];
    [ac addAction:[UIAlertAction actionWithTitle:[NSString stringWithFormat:@"Chơi (%@)", res]
                                           style:UIAlertActionStyleDefault
                                         handler:^(UIAlertAction *a) { [self play:file]; }]];
    [ac addAction:[UIAlertAction actionWithTitle:@"Đổi độ phân giải…"
                                           style:UIAlertActionStyleDefault
                                         handler:^(UIAlertAction *a) { [self chooseResolution:file from:indexPath]; }]];
    NSArray *orientKeys = @[@"auto", @"portrait", @"landscape"];
    NSArray *orientNames = @[@"Tự động", @"Dọc", @"Ngang"];
    NSString *orient = J4IGameSetting(file, @"orient", @"auto");
    NSUInteger oi = [orientKeys indexOfObject:orient];
    [ac addAction:[UIAlertAction actionWithTitle:[NSString stringWithFormat:@"Hướng màn hình: %@…", orientNames[oi == NSNotFound ? 0 : oi]]
                                           style:UIAlertActionStyleDefault
                                         handler:^(UIAlertAction *a) {
                                             [self choose:@"Hướng màn hình khi chơi" key:@"orient" values:orientKeys names:orientNames file:file from:indexPath];
                                         }]];
    NSArray *scaleKeys = @[@"fit", @"integer", @"stretch"];
    NSArray *scaleNames = @[@"Vừa khung", @"Số nguyên (nét nhất)", @"Kéo giãn"];
    NSUInteger si = [scaleKeys indexOfObject:J4IGameSetting(file, @"scale", @"fit")];
    [ac addAction:[UIAlertAction actionWithTitle:[NSString stringWithFormat:@"Kiểu phóng to: %@…", scaleNames[si == NSNotFound ? 0 : si]]
                                           style:UIAlertActionStyleDefault
                                         handler:^(UIAlertAction *a) {
                                             [self choose:@"Kiểu phóng to màn hình game" key:@"scale" values:scaleKeys names:scaleNames file:file from:indexPath];
                                         }]];
    BOOL keypad = ![J4IGameSetting(file, @"keypad", @"1") isEqualToString:@"0"];
    [ac addAction:[UIAlertAction actionWithTitle:keypad ? @"Ẩn bàn phím ảo (game cảm ứng)" : @"Hiện bàn phím ảo"
                                           style:UIAlertActionStyleDefault
                                         handler:^(UIAlertAction *a) {
                                             J4ISetGameSetting(file, @"keypad", keypad ? @"0" : @"1");
                                             [self.tableView reloadData];
                                         }]];
    [ac addAction:[UIAlertAction actionWithTitle:@"Xoá dữ liệu lưu (RMS)"
                                           style:UIAlertActionStyleDestructive
                                         handler:^(UIAlertAction *a) {
                                             [[NSFileManager defaultManager] removeItemAtPath:J4IDataDirFor(file) error:nil];
                                         }]];
    [ac addAction:[UIAlertAction actionWithTitle:@"Huỷ" style:UIAlertActionStyleCancel handler:nil]];
    UITableViewCell *cell = [tableView cellForRowAtIndexPath:indexPath];
    ac.popoverPresentationController.sourceView = cell ?: self.view;
    ac.popoverPresentationController.sourceRect = cell ? cell.bounds : self.view.bounds;
    [self presentViewController:ac animated:YES completion:nil];
}

- (void)choose:(NSString *)title
           key:(NSString *)key
        values:(NSArray<NSString *> *)values
         names:(NSArray<NSString *> *)names
          file:(NSString *)file
          from:(NSIndexPath *)indexPath {
    UIAlertController *ac = [UIAlertController alertControllerWithTitle:title message:nil preferredStyle:UIAlertControllerStyleActionSheet];
    NSString *cur = J4IGameSetting(file, key, values.firstObject);
    for (NSUInteger i = 0; i < values.count; i++) {
        NSString *v = values[i];
        NSString *label = [v isEqualToString:cur] ? [names[i] stringByAppendingString:@" ✓"] : names[i];
        [ac addAction:[UIAlertAction actionWithTitle:label
                                               style:UIAlertActionStyleDefault
                                             handler:^(UIAlertAction *a) {
                                                 J4ISetGameSetting(file, key, v);
                                                 [self.tableView reloadData];
                                             }]];
    }
    [ac addAction:[UIAlertAction actionWithTitle:@"Huỷ" style:UIAlertActionStyleCancel handler:nil]];
    UITableViewCell *cell = [self.tableView cellForRowAtIndexPath:indexPath];
    ac.popoverPresentationController.sourceView = cell ?: self.view;
    ac.popoverPresentationController.sourceRect = cell ? cell.bounds : self.view.bounds;
    [self presentViewController:ac animated:YES completion:nil];
}

- (void)chooseResolution:(NSString *)file from:(NSIndexPath *)indexPath {
    NSArray *opts = @[@"128x128", @"128x160", @"176x208", @"176x220", @"208x208", @"240x320", @"320x240", @"240x400", @"360x640", @"480x800"];
    UIAlertController *ac = [UIAlertController alertControllerWithTitle:@"Độ phân giải màn hình game"
                                                                message:@"Chọn đúng kích thước game được làm cho (thường ghi trong tên file)."
                                                         preferredStyle:UIAlertControllerStyleActionSheet];
    for (NSString *o in opts) {
        [ac addAction:[UIAlertAction actionWithTitle:o
                                               style:UIAlertActionStyleDefault
                                             handler:^(UIAlertAction *a) {
                                                 J4ISetGameSetting(file, @"res", o);
                                                 [self.tableView reloadData];
                                             }]];
    }
    [ac addAction:[UIAlertAction actionWithTitle:@"Huỷ" style:UIAlertActionStyleCancel handler:nil]];
    UITableViewCell *cell = [self.tableView cellForRowAtIndexPath:indexPath];
    ac.popoverPresentationController.sourceView = cell ?: self.view;
    ac.popoverPresentationController.sourceRect = cell ? cell.bounds : self.view.bounds;
    [self presentViewController:ac animated:YES completion:nil];
}

- (void)play:(NSString *)file {
    int w = 240, h = 320;
    sscanf([self resolutionFor:file].UTF8String, "%dx%d", &w, &h);
    GameViewController *g = [[GameViewController alloc] initWithJarPath:[J4IDocumentsDir() stringByAppendingPathComponent:file]
                                                                   width:w
                                                                  height:h];
    g.modalPresentationStyle = UIModalPresentationFullScreen;
    [self presentViewController:g animated:YES completion:nil];
}

- (BOOL)tableView:(UITableView *)tableView canEditRowAtIndexPath:(NSIndexPath *)indexPath {
    return YES;
}

- (void)tableView:(UITableView *)tableView
    commitEditingStyle:(UITableViewCellEditingStyle)editingStyle
     forRowAtIndexPath:(NSIndexPath *)indexPath {
    if (editingStyle != UITableViewCellEditingStyleDelete) return;
    NSString *file = _jars[indexPath.row];
    [[NSFileManager defaultManager] removeItemAtPath:[J4IDocumentsDir() stringByAppendingPathComponent:file] error:nil];
    [[NSFileManager defaultManager] removeItemAtPath:J4IDataDirFor(file) error:nil];
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
    if (bad) {
        UIAlertController *ac = [UIAlertController alertControllerWithTitle:@"Không nhập được"
                                                                    message:@"Chỉ hỗ trợ file .jar (game J2ME/MIDP)."
                                                             preferredStyle:UIAlertControllerStyleAlert];
        [ac addAction:[UIAlertAction actionWithTitle:@"OK" style:UIAlertActionStyleDefault handler:nil]];
        [self presentViewController:ac animated:YES completion:nil];
    }
    [self reload];
}

@end
