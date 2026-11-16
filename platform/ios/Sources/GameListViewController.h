#import <UIKit/UIKit.h>

@interface GameListViewController : UITableViewController
/// Copies a .jar into Documents (from "Open in", Files or the document picker).
+ (BOOL)importURL:(NSURL *)url;
@end
