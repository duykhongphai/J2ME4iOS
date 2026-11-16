#import "GameSettingsViewController.h"
#import "Paths.h"

typedef NS_ENUM(NSInteger, RowKind) { RowChoice, RowToggle, RowText };

@interface J4ISettingRow : NSObject
@property (nonatomic) RowKind kind;
@property (nonatomic, copy) NSString *key, *title, *def, *footer;
@property (nonatomic, copy) NSString *scope; /* settings owner instead of the jar file (e.g. "game.jar#2") */
@property (nonatomic, copy) NSArray<NSString *> *values, *names;
@end

@implementation J4ISettingRow
+ (instancetype)choice:(NSString *)key title:(NSString *)title def:(NSString *)def values:(NSArray *)values names:(NSArray *)names {
    J4ISettingRow *r = [J4ISettingRow new];
    r.kind = RowChoice;
    r.key = key;
    r.title = title;
    r.def = def;
    r.values = values;
    r.names = names;
    return r;
}

+ (instancetype)toggle:(NSString *)key title:(NSString *)title def:(NSString *)def {
    J4ISettingRow *r = [J4ISettingRow new];
    r.kind = RowToggle;
    r.key = key;
    r.title = title;
    r.def = def;
    return r;
}

+ (instancetype)text:(NSString *)key title:(NSString *)title {
    J4ISettingRow *r = [J4ISettingRow new];
    r.kind = RowText;
    r.key = key;
    r.title = title;
    r.def = @"";
    return r;
}
@end

@implementation GameSettingsViewController {
    NSString *_file;
    NSArray<NSString *> *_sectionTitles, *_sectionFooters;
    NSArray<NSArray<J4ISettingRow *> *> *_sections;
}

- (instancetype)initWithJarFile:(NSString *)jarFileName title:(NSString *)title {
    if ((self = [super initWithStyle:UITableViewStyleInsetGrouped])) {
        _file = jarFileName;
        self.title = title;
        [self buildRows];
    }
    return self;
}

- (void)buildRows {
    NSArray *res = @[@"128x128", @"128x160", @"176x208", @"176x220", @"208x208", @"240x320", @"320x240", @"240x400", @"352x416", @"360x640",
                     @"480x800", @"640x360"];
    NSMutableArray *fontVals = [NSMutableArray arrayWithObject:@"0"], *fontNames = [NSMutableArray arrayWithObject:@"Tự động"];
    for (int s = 8; s <= 28; s += 2) {
        [fontVals addObject:[NSString stringWithFormat:@"%d", s]];
        [fontNames addObject:[NSString stringWithFormat:@"%d px", s]];
    }
    NSArray *platforms = @[@"", @"Nokia6300/07.21", @"NokiaN73-1/3.0638.0.0.1", @"NokiaE71-1/300.21.012", @"SonyEricssonK800i/R1KG001",
                           @"SonyEricssonW810i/R4EA031", @"SAMSUNG-SGH-E250/1.0", @"MOT-V3/0E.42.29R", @"LG-KG800", @"SIE-S75/50"];
    NSArray *platformNames = @[@"Mặc định (Nokia 6300)", @"Nokia 6300", @"Nokia N73", @"Nokia E71", @"Sony Ericsson K800i", @"Sony Ericsson W810i",
                               @"Samsung SGH-E250", @"Motorola V3", @"LG KG800", @"Siemens S75"];
    _sectionTitles = @[@"Màn hình", @"Bàn phím", @"Chữ", @"Hệ thống", @"Mạng: proxy theo khe lưu"];
    _sectionFooters = @[
        @"Độ phân giải nên đúng với kích thước game được làm cho (thường ghi trong tên file).",
        @"Tự động: ẩn bàn phím ảo khi có tay cầm hoặc bàn phím rời. Độ mờ chỉ áp dụng khi xoay ngang.",
        @"Cỡ chữ \"Tự động\" dựa theo độ phân giải. Tắt khử răng cưa để chữ sắc nét giống điện thoại cũ.",
        @"Một số game kiểm tra tên máy (microedition.platform) để bật tính năng. Thuộc tính thêm: key=value, cách nhau bằng dấu ;",
        @"Mọi kết nối của game (socket, HTTP) ở khe đó đi qua proxy. Dạng: socks5://user:pass@host:port, socks5h://... (proxy tự phân giải tên miền) hoặc http://host:port (HTTP CONNECT). Để trống = kết nối trực tiếp. Có hiệu lực khi khởi động lại game.",
    ];
    _sections = @[
        @[
            [J4ISettingRow choice:@"res" title:@"Độ phân giải" def:@"240x320" values:res names:res],
            [J4ISettingRow choice:@"orient" title:@"Hướng màn hình" def:@"auto" values:@[@"auto", @"portrait", @"landscape"]
                            names:@[@"Tự động", @"Dọc", @"Ngang"]],
            [J4ISettingRow choice:@"scale" title:@"Kiểu phóng to" def:@"fit" values:@[@"fit", @"integer", @"stretch", @"native"]
                            names:@[@"Vừa khung", @"Số nguyên (nét nhất)", @"Kéo giãn", @"Gốc 1×"]],
            [J4ISettingRow toggle:@"smooth" title:@"Làm mịn ảnh" def:@"0"],
            [J4ISettingRow choice:@"bg" title:@"Màu nền" def:@"black" values:@[@"black", @"gray", @"blue", @"lcd", @"white"]
                            names:@[@"Đen", @"Xám", @"Xanh đậm", @"Xanh LCD", @"Trắng"]],
            [J4ISettingRow toggle:@"fps" title:@"Hiện FPS" def:@"0"],
        ],
        @[
            [J4ISettingRow choice:@"keypad" title:@"Bàn phím ảo" def:@"show" values:@[@"show", @"hide", @"auto"]
                            names:@[@"Luôn hiện", @"Ẩn (game cảm ứng)", @"Tự động"]],
            [J4ISettingRow choice:@"keypadType" title:@"Kiểu bàn phím" def:@"phone" values:@[@"phone", @"gamepad"]
                            names:@[@"Điện thoại", @"Tay cầm (cần 8 hướng)"]],
            [J4ISettingRow choice:@"keyAlpha" title:@"Độ đậm phím (ngang)" def:@"80" values:@[@"30", @"50", @"65", @"80", @"100"]
                            names:@[@"30%", @"50%", @"65%", @"80%", @"100%"]],
            [J4ISettingRow toggle:@"haptics" title:@"Rung nhẹ khi bấm" def:@"1"],
        ],
        @[
            [J4ISettingRow choice:@"fontS" title:@"Chữ nhỏ" def:@"0" values:fontVals names:fontNames],
            [J4ISettingRow choice:@"fontM" title:@"Chữ vừa" def:@"0" values:fontVals names:fontNames],
            [J4ISettingRow choice:@"fontL" title:@"Chữ lớn" def:@"0" values:fontVals names:fontNames],
            [J4ISettingRow toggle:@"aa" title:@"Khử răng cưa chữ" def:@"1"],
        ],
        @[
            [J4ISettingRow choice:@"platform" title:@"Tên máy" def:@"" values:platforms names:platformNames],
            [J4ISettingRow text:@"props" title:@"Thuộc tính thêm"],
        ],
        [self proxyRows],
    ];
}

- (NSArray<J4ISettingRow *> *)proxyRows {
    NSMutableArray *rows = [NSMutableArray array];
    for (int slot = 1; slot <= 5; slot++) {
        J4ISettingRow *r = [J4ISettingRow text:@"proxy" title:[NSString stringWithFormat:@"Proxy khe %d", slot]];
        r.scope = [NSString stringWithFormat:@"%@#%d", _file, slot];
        [rows addObject:r];
    }
    return rows;
}

- (NSString *)value:(J4ISettingRow *)r {
    NSString *v = [[NSUserDefaults standardUserDefaults] stringForKey:[NSString stringWithFormat:@"%@:%@", r.key, r.scope ?: _file]];
    if (!v) v = r.def;
    if ([r.key isEqualToString:@"keypad"]) { /* older builds stored 1/0 */
        if ([v isEqualToString:@"1"]) v = @"show";
        if ([v isEqualToString:@"0"]) v = @"hide";
    }
    return v;
}

- (void)setValue:(NSString *)v forRow:(J4ISettingRow *)r {
    [[NSUserDefaults standardUserDefaults] setObject:v forKey:[NSString stringWithFormat:@"%@:%@", r.key, r.scope ?: _file]];
}

- (NSInteger)numberOfSectionsInTableView:(UITableView *)tableView { return _sections.count; }

- (NSInteger)tableView:(UITableView *)tableView numberOfRowsInSection:(NSInteger)section { return _sections[section].count; }

- (NSString *)tableView:(UITableView *)tableView titleForHeaderInSection:(NSInteger)section { return _sectionTitles[section]; }

- (NSString *)tableView:(UITableView *)tableView titleForFooterInSection:(NSInteger)section { return _sectionFooters[section]; }

- (UITableViewCell *)tableView:(UITableView *)tableView cellForRowAtIndexPath:(NSIndexPath *)indexPath {
    J4ISettingRow *r = _sections[indexPath.section][indexPath.row];
    UITableViewCell *cell = [[UITableViewCell alloc] initWithStyle:UITableViewCellStyleValue1 reuseIdentifier:nil];
    cell.textLabel.text = r.title;
    NSString *v = [self value:r];
    if (r.kind == RowToggle) {
        UISwitch *sw = [[UISwitch alloc] init];
        sw.on = [v isEqualToString:@"1"];
        sw.tag = indexPath.section * 100 + indexPath.row;
        [sw addTarget:self action:@selector(switched:) forControlEvents:UIControlEventValueChanged];
        cell.accessoryView = sw;
        cell.selectionStyle = UITableViewCellSelectionStyleNone;
    } else if (r.kind == RowChoice) {
        NSUInteger i = [r.values indexOfObject:v];
        cell.detailTextLabel.text = i == NSNotFound ? v : r.names[i];
        cell.accessoryType = UITableViewCellAccessoryDisclosureIndicator;
    } else {
        cell.detailTextLabel.text = v.length ? v : @"(trống)";
        cell.accessoryType = UITableViewCellAccessoryDisclosureIndicator;
    }
    return cell;
}

- (void)switched:(UISwitch *)sw {
    J4ISettingRow *r = _sections[sw.tag / 100][sw.tag % 100];
    [self setValue:sw.on ? @"1" : @"0" forRow:r];
}

- (void)tableView:(UITableView *)tableView didSelectRowAtIndexPath:(NSIndexPath *)indexPath {
    [tableView deselectRowAtIndexPath:indexPath animated:YES];
    J4ISettingRow *r = _sections[indexPath.section][indexPath.row];
    UITableViewCell *cell = [tableView cellForRowAtIndexPath:indexPath];
    if (r.kind == RowChoice) {
        UIAlertController *ac = [UIAlertController alertControllerWithTitle:r.title message:nil preferredStyle:UIAlertControllerStyleActionSheet];
        NSString *cur = [self value:r];
        for (NSUInteger i = 0; i < r.values.count; i++) {
            NSString *val = r.values[i];
            NSString *name = [val isEqualToString:cur] ? [r.names[i] stringByAppendingString:@" ✓"] : r.names[i];
            [ac addAction:[UIAlertAction actionWithTitle:name
                                                   style:UIAlertActionStyleDefault
                                                 handler:^(UIAlertAction *a) {
                                                     [self setValue:val forRow:r];
                                                     [self.tableView reloadData];
                                                 }]];
        }
        if ([r.key isEqualToString:@"platform"] || [r.key isEqualToString:@"res"]) {
            [ac addAction:[UIAlertAction actionWithTitle:@"Tuỳ chỉnh…"
                                                   style:UIAlertActionStyleDefault
                                                 handler:^(UIAlertAction *a) { [self editText:r]; }]];
        }
        [ac addAction:[UIAlertAction actionWithTitle:@"Huỷ" style:UIAlertActionStyleCancel handler:nil]];
        ac.popoverPresentationController.sourceView = cell ?: self.view;
        ac.popoverPresentationController.sourceRect = cell ? cell.bounds : self.view.bounds;
        [self presentViewController:ac animated:YES completion:nil];
    } else if (r.kind == RowText) {
        [self editText:r];
    }
}

- (void)editText:(J4ISettingRow *)r {
    NSString *msg = nil;
    if ([r.key isEqualToString:@"props"]) msg = @"Ví dụ: com.nokia.mid.dateformat=dd/MM/yyyy; device.model=K800";
    else if ([r.key isEqualToString:@"res"]) msg = @"Rộng x cao, ví dụ 240x320";
    else if ([r.key isEqualToString:@"proxy"]) msg = @"socks5://user:pass@host:1080, socks5h://host:1080 hoặc http://host:8080. Để trống để tắt.";
    UIAlertController *ac = [UIAlertController alertControllerWithTitle:r.title message:msg preferredStyle:UIAlertControllerStyleAlert];
    NSString *cur = [self value:r];
    [ac addTextFieldWithConfigurationHandler:^(UITextField *tf) {
        tf.text = cur;
        tf.autocorrectionType = UITextAutocorrectionTypeNo;
        tf.autocapitalizationType = UITextAutocapitalizationTypeNone;
        tf.clearButtonMode = UITextFieldViewModeWhileEditing;
    }];
    __weak UIAlertController *weakAc = ac;
    [ac addAction:[UIAlertAction actionWithTitle:@"Huỷ" style:UIAlertActionStyleCancel handler:nil]];
    [ac addAction:[UIAlertAction actionWithTitle:@"Lưu"
                                           style:UIAlertActionStyleDefault
                                         handler:^(UIAlertAction *a) {
                                             NSString *t = [weakAc.textFields.firstObject.text
                                                 stringByTrimmingCharactersInSet:[NSCharacterSet whitespaceAndNewlineCharacterSet]] ?: @"";
                                             if ([r.key isEqualToString:@"res"]) {
                                                 int w = 0, h = 0;
                                                 if (sscanf(t.lowercaseString.UTF8String, "%dx%d", &w, &h) != 2 || w < 64 || h < 64 || w > 2048 || h > 2048)
                                                     return;
                                                 t = [NSString stringWithFormat:@"%dx%d", w, h];
                                             }
                                             if ([r.key isEqualToString:@"proxy"] && t.length &&
                                                 !([t hasPrefix:@"socks5://"] || [t hasPrefix:@"socks5h://"] || [t hasPrefix:@"socks://"] ||
                                                   [t hasPrefix:@"http://"]))
                                                 return;
                                             [self setValue:t forRow:r];
                                             [self.tableView reloadData];
                                         }]];
    [self presentViewController:ac animated:YES completion:nil];
}

@end
