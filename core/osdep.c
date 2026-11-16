/* OS abstraction: locking, time, files. Windows + POSIX (iOS/macOS/Linux). */
#if !defined(_WIN32) && !defined(_GNU_SOURCE)
#define _GNU_SOURCE
#endif
#include "vm.h"

#ifdef _WIN32
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <direct.h>

static SRWLOCK g_lock = SRWLOCK_INIT;
static CONDITION_VARIABLE g_cond = CONDITION_VARIABLE_INIT;

void os_init(void) {}
void os_destroy(void) {}
void os_lock(void) { AcquireSRWLockExclusive(&g_lock); }
void os_unlock(void) { ReleaseSRWLockExclusive(&g_lock); }
void os_cond_wait(int timeout_ms) {
    SleepConditionVariableSRW(&g_cond, &g_lock, timeout_ms < 0 ? INFINITE : (DWORD)timeout_ms, 0);
}
void os_cond_signal(void) { WakeAllConditionVariable(&g_cond); }

int64_t os_now_ms(void) {
    FILETIME ft;
    GetSystemTimeAsFileTime(&ft);
    uint64_t t = ((uint64_t)ft.dwHighDateTime << 32) | ft.dwLowDateTime;
    return (int64_t)((t - 116444736000000000ULL) / 10000ULL);
}

int64_t os_nanos(void) {
    static LARGE_INTEGER freq;
    LARGE_INTEGER c;
    if (!freq.QuadPart) QueryPerformanceFrequency(&freq);
    QueryPerformanceCounter(&c);
    return (int64_t)((double)c.QuadPart * 1e9 / (double)freq.QuadPart);
}
int64_t os_mono_ms(void) { return os_nanos() / 1000000; }

static wchar_t *widen(const char *s) {
    int n = MultiByteToWideChar(CP_UTF8, 0, s, -1, NULL, 0);
    wchar_t *w = (wchar_t *)malloc(n * sizeof(wchar_t));
    MultiByteToWideChar(CP_UTF8, 0, s, -1, w, n);
    return w;
}

static FILE *os_fopen(const char *path, const char *mode) {
    wchar_t *wp = widen(path), *wm = widen(mode);
    FILE *f = _wfopen(wp, wm);
    free(wp);
    free(wm);
    return f;
}

int os_delete_file(const char *path) {
    wchar_t *wp = widen(path);
    int r = DeleteFileW(wp) ? 0 : -1;
    free(wp);
    return r;
}

int os_mkdirs(const char *path) {
    char buf[1024];
    size_t n = strlen(path);
    if (n >= sizeof buf) return -1;
    memcpy(buf, path, n + 1);
    for (size_t i = 1; i <= n; i++) {
        if (buf[i] == '/' || buf[i] == '\\' || buf[i] == 0) {
            char c = buf[i];
            buf[i] = 0;
            if (!(i == 2 && buf[1] == ':')) {
                wchar_t *wp = widen(buf);
                CreateDirectoryW(wp, NULL);
                free(wp);
            }
            buf[i] = c;
        }
    }
    return 0;
}

int os_list_dir(const char *path, os_dir_cb cb, void *ctx) {
    char pat[1024];
    snprintf(pat, sizeof pat, "%s\\*", path);
    wchar_t *wp = widen(pat);
    WIN32_FIND_DATAW fd;
    HANDLE h = FindFirstFileW(wp, &fd);
    free(wp);
    if (h == INVALID_HANDLE_VALUE) return -1;
    do {
        if (fd.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY) continue;
        char name[512];
        WideCharToMultiByte(CP_UTF8, 0, fd.cFileName, -1, name, sizeof name, NULL, NULL);
        cb(ctx, name);
    } while (FindNextFileW(h, &fd));
    FindClose(h);
    return 0;
}

int os_tz_offset_ms(void) {
    TIME_ZONE_INFORMATION tz;
    DWORD r = GetTimeZoneInformation(&tz);
    long bias = tz.Bias;
    if (r == TIME_ZONE_ID_DAYLIGHT) bias += tz.DaylightBias;
    return (int)(-bias * 60 * 1000);
}

#else /* POSIX */
#include <pthread.h>
#include <time.h>
#include <sys/time.h>
#include <sys/stat.h>
#include <unistd.h>
#include <dirent.h>
#include <errno.h>

static pthread_mutex_t g_lock = PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t g_cond = PTHREAD_COND_INITIALIZER;

void os_init(void) {}
void os_destroy(void) {}
void os_lock(void) { pthread_mutex_lock(&g_lock); }
void os_unlock(void) { pthread_mutex_unlock(&g_lock); }
void os_cond_wait(int timeout_ms) {
    if (timeout_ms < 0) { pthread_cond_wait(&g_cond, &g_lock); return; }
    struct timeval tv;
    gettimeofday(&tv, NULL);
    struct timespec ts;
    int64_t ns = (int64_t)tv.tv_usec * 1000 + (int64_t)timeout_ms * 1000000;
    ts.tv_sec = tv.tv_sec + (time_t)(ns / 1000000000);
    ts.tv_nsec = (long)(ns % 1000000000);
    pthread_cond_timedwait(&g_cond, &g_lock, &ts);
}
void os_cond_signal(void) { pthread_cond_broadcast(&g_cond); }

int64_t os_now_ms(void) {
    struct timeval tv;
    gettimeofday(&tv, NULL);
    return (int64_t)tv.tv_sec * 1000 + tv.tv_usec / 1000;
}
int64_t os_nanos(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (int64_t)ts.tv_sec * 1000000000 + ts.tv_nsec;
}
int64_t os_mono_ms(void) { return os_nanos() / 1000000; }

static FILE *os_fopen(const char *path, const char *mode) { return fopen(path, mode); }
int os_delete_file(const char *path) { return unlink(path); }

int os_mkdirs(const char *path) {
    char buf[1024];
    size_t n = strlen(path);
    if (n >= sizeof buf) return -1;
    memcpy(buf, path, n + 1);
    for (size_t i = 1; i <= n; i++) {
        if (buf[i] == '/' || buf[i] == 0) {
            char c = buf[i];
            buf[i] = 0;
            mkdir(buf, 0755);
            buf[i] = c;
        }
    }
    return 0;
}

int os_list_dir(const char *path, os_dir_cb cb, void *ctx) {
    DIR *d = opendir(path);
    if (!d) return -1;
    struct dirent *e;
    while ((e = readdir(d))) {
        if (e->d_name[0] == '.') continue;
        cb(ctx, e->d_name);
    }
    closedir(d);
    return 0;
}

int os_tz_offset_ms(void) {
    time_t now = time(NULL);
    struct tm lt;
    localtime_r(&now, &lt);
    return (int)(lt.tm_gmtoff * 1000);
}
#endif

uint8_t *os_read_file(const char *path, size_t *len) {
    FILE *f = os_fopen(path, "rb");
    if (!f) return NULL;
    fseek(f, 0, SEEK_END);
    long n = ftell(f);
    fseek(f, 0, SEEK_SET);
    if (n < 0) { fclose(f); return NULL; }
    uint8_t *buf = (uint8_t *)malloc(n + 1);
    if (n > 0 && fread(buf, 1, n, f) != (size_t)n) { free(buf); fclose(f); return NULL; }
    buf[n] = 0;
    fclose(f);
    *len = (size_t)n;
    return buf;
}

int os_write_file(const char *path, const void *data, size_t len) {
    char tmp[1100];
    snprintf(tmp, sizeof tmp, "%s.tmp", path);
    FILE *f = os_fopen(tmp, "wb");
    if (!f) return -1;
    if (len && fwrite(data, 1, len, f) != len) { fclose(f); return -1; }
    fclose(f);
#ifdef _WIN32
    {
        wchar_t *a = widen(tmp), *b = widen(path);
        int ok = MoveFileExW(a, b, MOVEFILE_REPLACE_EXISTING);
        free(a);
        free(b);
        return ok ? 0 : -1;
    }
#else
    return rename(tmp, path);
#endif
}
