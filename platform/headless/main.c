/*
 * Portable headless runner (Linux/macOS/Windows) used by CI to run console tests:
 *   j4i-headless --runtime j4i-runtime.jar [--main Class] [--args "..."] app.jar
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "j4i.h"

static void host_log(void *u, const char *msg) {
    (void)u;
    printf("%s\n", msg);
    fflush(stdout);
}

static void host_present(void *u, const uint32_t *argb, int w, int h) {
    (void)u;
    (void)argb;
    (void)w;
    (void)h;
}

int main(int argc, char **argv) {
    J4IConfig cfg;
    memset(&cfg, 0, sizeof cfg);
    /* default: j4i-runtime.jar next to the executable */
    static char runtime[4096];
    snprintf(runtime, sizeof runtime, "%s", argv[0]);
    char *slash = strrchr(runtime, '/');
    char *bslash = strrchr(runtime, '\\');
    if (bslash && (!slash || bslash > slash)) slash = bslash;
    if (slash) strcpy(slash + 1, "j4i-runtime.jar");
    else strcpy(runtime, "j4i-runtime.jar");
    cfg.runtime_jar = runtime;
    cfg.data_dir = "rms";
    for (int i = 1; i < argc; i++) {
        const char *a = argv[i];
        if (!strcmp(a, "--runtime") && i + 1 < argc) cfg.runtime_jar = argv[++i];
        else if (!strcmp(a, "--main") && i + 1 < argc) cfg.main_class = argv[++i];
        else if (!strcmp(a, "--args") && i + 1 < argc) cfg.main_args = argv[++i];
        else if (!strcmp(a, "--data") && i + 1 < argc) cfg.data_dir = argv[++i];
        else if (!strcmp(a, "--trace") && i + 1 < argc) cfg.trace = atoi(argv[++i]);
        else if (!strcmp(a, "--headless")) continue;
        else if (a[0] != '-') cfg.game_jar = a;
        else {
            fprintf(stderr, "unknown option %s\n", a);
            return 2;
        }
    }
    if (!cfg.game_jar) {
        fprintf(stderr, "usage: j4i-headless [--runtime jar] [--main Class] app.jar\n");
        return 2;
    }
    cfg.host.log = host_log;
    cfg.host.present = host_present;
    return j4i_run(&cfg);
}
