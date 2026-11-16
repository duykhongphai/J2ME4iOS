#import "LogViewController.h"
#import "IOSHost.h"

@implementation LogViewController {
    UITextView *_text;
}

- (void)viewDidLoad {
    [super viewDidLoad];
    self.title = @"Log";
    self.view.backgroundColor = [UIColor systemBackgroundColor];
    _text = [[UITextView alloc] initWithFrame:self.view.bounds];
    _text.autoresizingMask = UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight;
    _text.editable = NO;
    _text.font = [UIFont monospacedSystemFontOfSize:11 weight:UIFontWeightRegular];
    _text.alwaysBounceVertical = YES;
    [self.view addSubview:_text];
    if (self.navigationController.viewControllers.firstObject == self)
        self.navigationItem.leftBarButtonItem = [[UIBarButtonItem alloc] initWithBarButtonSystemItem:UIBarButtonSystemItemDone
                                                                                              target:self
                                                                                              action:@selector(done)];
    UIBarButtonItem *share = [[UIBarButtonItem alloc] initWithBarButtonSystemItem:UIBarButtonSystemItemAction
                                                                           target:self
                                                                           action:@selector(share:)];
    UIBarButtonItem *copy = [[UIBarButtonItem alloc] initWithTitle:@"Chép" style:UIBarButtonItemStylePlain target:self action:@selector(copyText)];
    UIBarButtonItem *clear = [[UIBarButtonItem alloc] initWithTitle:@"Xoá" style:UIBarButtonItemStylePlain target:self action:@selector(clearLog)];
    self.navigationItem.rightBarButtonItems = @[share, copy, clear];
    [self reload];
}

- (void)reload {
    NSString *t = J4IIOSLogText();
    _text.text = t.length ? t : @"(chưa có log)";
    if (t.length) [_text scrollRangeToVisible:NSMakeRange(t.length - 1, 1)];
}

- (void)done { [self dismissViewControllerAnimated:YES completion:nil]; }

- (void)copyText {
    [UIPasteboard generalPasteboard].string = J4IIOSLogText();
    UIAlertController *ac = [UIAlertController alertControllerWithTitle:@"Đã chép log" message:nil preferredStyle:UIAlertControllerStyleAlert];
    [ac addAction:[UIAlertAction actionWithTitle:@"OK" style:UIAlertActionStyleDefault handler:nil]];
    [self presentViewController:ac animated:YES completion:nil];
}

- (void)share:(UIBarButtonItem *)item {
    UIActivityViewController *a = [[UIActivityViewController alloc] initWithActivityItems:@[J4IIOSLogText()] applicationActivities:nil];
    a.popoverPresentationController.barButtonItem = item;
    [self presentViewController:a animated:YES completion:nil];
}

- (void)clearLog {
    J4IIOSLogClear();
    [self reload];
}

@end
