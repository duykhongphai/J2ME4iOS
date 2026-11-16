/*
 * Portable headless runner (Linux/macOS/Windows) used by CI to run console tests:
 *   j4i-headless --runtime j4i-runtime.jar [--main Class] [--args "..."] app.jar
 * --instances N runs N VMs at the same time (one thread each); instance i logs to
 * <log-prefix>.<i>.txt and keeps its data in <data>/slot<i>.
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "j4i.h"
#ifdef _WIN32
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#else
#include <pthread.h>
#endif

static void host_log(void *u, const char *msg) {
    FILE *f = u ? (FILE *)u : stdout;
    fprintf(f, "%s\n", msg);
    fflush(f);
}

static void host_present(void *u, const uint32_t *argb, int w, int h) {
    (void)u;
    (void)argb;
    (void)w;
    (void)h;
}

typedef struct {
    J4IConfig cfg;
    J4IInstance *inst;
    char data[1024];
    int code;
} Job;

#ifdef _WIN32
static DWORD WINAPI job_main(LPVOID p) {
#else
static void *job_main(void *p) {
#endif
    Job *j = (Job *)p;
    j->code = j4i_instance_run(j->inst, &j->cfg);
#ifdef _WIN32
    return 0;
#else
    return NULL;
#endif
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
    cfg.midi_synth = 1;
    char *props = NULL;
    int instances = 1;
    const char *log_prefix = "instance";
    for (int i = 1; i < argc; i++) {
        const char *a = argv[i];
        if (!strcmp(a, "--runtime") && i + 1 < argc) cfg.runtime_jar = argv[++i];
        else if (!strcmp(a, "--main") && i + 1 < argc) cfg.main_class = argv[++i];
        else if (!strcmp(a, "--args") && i + 1 < argc) cfg.main_args = argv[++i];
        else if (!strcmp(a, "--data") && i + 1 < argc) cfg.data_dir = argv[++i];
        else if (!strcmp(a, "--trace") && i + 1 < argc) cfg.trace = atoi(argv[++i]);
        else if (!strcmp(a, "--headless")) continue;
        else if (!strcmp(a, "--jad") && i + 1 < argc) cfg.jad_path = argv[++i];
        else if (!strcmp(a, "--proxy") && i + 1 < argc) cfg.proxy = argv[++i];
        else if (!strcmp(a, "--instances") && i + 1 < argc) instances = atoi(argv[++i]);
        else if (!strcmp(a, "--log-prefix") && i + 1 < argc) log_prefix = argv[++i];
        else if (!strcmp(a, "--prop") && i + 1 < argc) {
            size_t ol = props ? strlen(props) : 0, nl = strlen(argv[i + 1]);
            props = (char *)realloc(props, ol + nl + 2);
            memcpy(props + ol, argv[++i], nl);
            props[ol + nl] = '\n';
            props[ol + nl + 1] = 0;
            cfg.sys_props = props;
        } else if (!strcmp(a, "--font") && i + 1 < argc) sscanf(argv[++i], "%d,%d,%d", &cfg.font_small, &cfg.font_medium, &cfg.font_large);
        else if (!strcmp(a, "--no-aa")) cfg.font_no_aa = 1;
        else if (a[0] != '-') cfg.game_jar = a;
        else {
            fprintf(stderr, "unknown option %s\n", a);
            return 2;
        }
    }
    if (!cfg.game_jar) {
        fprintf(stderr, "usage: j4i-headless [--runtime jar] [--main Class] [--instances N] app.jar\n");
        return 2;
    }
    cfg.host.log = host_log;
    cfg.host.present = host_present;
    if (instances <= 1) return j4i_run(&cfg);

    if (instances > 16) instances = 16;
    Job jobs[16];
#ifdef _WIN32
    HANDLE th[16];
#else
    pthread_t th[16];
#endif
    for (int i = 0; i < instances; i++) {
        Job *j = &jobs[i];
        j->cfg = cfg;
        snprintf(j->data, sizeof j->data, "%s/slot%d", cfg.data_dir, i + 1);
        j->cfg.data_dir = j->data;
        char lp[1100];
        snprintf(lp, sizeof lp, "%s.%d.txt", log_prefix, i + 1);
        j->cfg.host.user = fopen(lp, "w");
        j->inst = j4i_instance_new();
#ifdef _WIN32
        th[i] = CreateThread(NULL, 8 * 1024 * 1024, job_main, j, 0, NULL);
#else
        pthread_attr_t at;
        pthread_attr_init(&at);
        pthread_attr_setstacksize(&at, 8 * 1024 * 1024);
        pthread_create(&th[i], &at, job_main, j);
        pthread_attr_destroy(&at);
#endif
    }
    int worst = 0;
    for (int i = 0; i < instances; i++) {
#ifdef _WIN32
        WaitForSingleObject(th[i], INFINITE);
        CloseHandle(th[i]);
#else
        pthread_join(th[i], NULL);
#endif
        if (jobs[i].code != 0) worst = jobs[i].code;
        if (jobs[i].cfg.host.user) fclose((FILE *)jobs[i].cfg.host.user);
        j4i_instance_free(jobs[i].inst);
    }
    printf("%d instances finished\n", instances);
    return worst;
}
