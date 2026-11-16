/* Extra OS services: file metadata, directories, background threads, TCP sockets. */
#if !defined(_WIN32) && !defined(_GNU_SOURCE)
#define _GNU_SOURCE
#endif
#include "vm.h"

#ifdef _WIN32
#define WIN32_LEAN_AND_MEAN
#include <winsock2.h>
#include <ws2tcpip.h>
#include <windows.h>
#pragma comment(lib, "ws2_32.lib")

static wchar_t *widen(const char *s) {
    int n = MultiByteToWideChar(CP_UTF8, 0, s, -1, NULL, 0);
    wchar_t *w = (wchar_t *)malloc(n * sizeof(wchar_t));
    MultiByteToWideChar(CP_UTF8, 0, s, -1, w, n);
    return w;
}

int os_stat(const char *path, int *is_dir, int64_t *size, int64_t *mtime_ms) {
    wchar_t *wp = widen(path);
    WIN32_FILE_ATTRIBUTE_DATA fa;
    BOOL ok = GetFileAttributesExW(wp, GetFileExInfoStandard, &fa);
    free(wp);
    if (!ok) return -1;
    if (is_dir) *is_dir = (fa.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY) != 0;
    if (size) *size = ((int64_t)fa.nFileSizeHigh << 32) | fa.nFileSizeLow;
    if (mtime_ms) {
        int64_t t = ((int64_t)fa.ftLastWriteTime.dwHighDateTime << 32) | fa.ftLastWriteTime.dwLowDateTime;
        *mtime_ms = (t - 116444736000000000LL) / 10000;
    }
    return 0;
}

int os_rename(const char *from, const char *to) {
    wchar_t *a = widen(from), *b = widen(to);
    int ok = MoveFileExW(a, b, 0);
    free(a);
    free(b);
    return ok ? 0 : -1;
}

int os_rmdir(const char *path) {
    wchar_t *wp = widen(path);
    int ok = RemoveDirectoryW(wp);
    free(wp);
    return ok ? 0 : -1;
}

int os_list_dir_ex(const char *path, os_dir_ex_cb cb, void *ctx) {
    char pat[1100];
    snprintf(pat, sizeof pat, "%s\\*", path);
    wchar_t *wp = widen(pat);
    WIN32_FIND_DATAW fd;
    HANDLE h = FindFirstFileW(wp, &fd);
    free(wp);
    if (h == INVALID_HANDLE_VALUE) return -1;
    do {
        if (!wcscmp(fd.cFileName, L".") || !wcscmp(fd.cFileName, L"..")) continue;
        char name[512];
        WideCharToMultiByte(CP_UTF8, 0, fd.cFileName, -1, name, sizeof name, NULL, NULL);
        cb(ctx, name, (fd.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY) != 0);
    } while (FindNextFileW(h, &fd));
    FindClose(h);
    return 0;
}

int os_write_at(const char *path, const void *data, size_t len, bool append) {
    wchar_t *wp = widen(path);
    FILE *f = _wfopen(wp, append ? L"ab" : L"wb");
    free(wp);
    if (!f) return -1;
    int r = (len && fwrite(data, 1, len, f) != len) ? -1 : 0;
    fclose(f);
    return r;
}

int os_truncate(const char *path, int64_t len) {
    wchar_t *wp = widen(path);
    HANDLE h = CreateFileW(wp, GENERIC_WRITE, 0, NULL, OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, NULL);
    free(wp);
    if (h == INVALID_HANDLE_VALUE) return -1;
    LARGE_INTEGER li;
    li.QuadPart = len;
    BOOL ok = SetFilePointerEx(h, li, NULL, FILE_BEGIN) && SetEndOfFile(h);
    CloseHandle(h);
    return ok ? 0 : -1;
}

int64_t os_free_space(const char *path) {
    ULARGE_INTEGER avail;
    wchar_t *wp = widen(path);
    BOOL ok = GetDiskFreeSpaceExW(wp, &avail, NULL, NULL);
    free(wp);
    return ok ? (int64_t)avail.QuadPart : 64 * 1024 * 1024;
}

typedef struct { void (*fn)(void *); void *arg; } ThreadStart;
static DWORD WINAPI thread_tramp(LPVOID p) {
    ThreadStart s = *(ThreadStart *)p;
    free(p);
    s.fn(s.arg);
    return 0;
}
int os_thread_start(void (*fn)(void *), void *arg) {
    ThreadStart *s = (ThreadStart *)malloc(sizeof *s);
    s->fn = fn;
    s->arg = arg;
    HANDLE h = CreateThread(NULL, 256 * 1024, thread_tramp, s, 0, NULL);
    if (!h) { free(s); return -1; }
    CloseHandle(h);
    return 0;
}

typedef SOCKET sock_t;
#define BAD_SOCK INVALID_SOCKET
#define sock_close closesocket
static void net_startup(void) {
    static bool done;
    if (!done) {
        WSADATA w;
        WSAStartup(MAKEWORD(2, 2), &w);
        done = true;
    }
}

#else /* POSIX */
#include <pthread.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/statvfs.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <arpa/inet.h>
#include <netdb.h>
#include <unistd.h>
#include <dirent.h>
#include <signal.h>

int os_stat(const char *path, int *is_dir, int64_t *size, int64_t *mtime_ms) {
    struct stat st;
    if (stat(path, &st) != 0) return -1;
    if (is_dir) *is_dir = S_ISDIR(st.st_mode);
    if (size) *size = (int64_t)st.st_size;
    if (mtime_ms) *mtime_ms = (int64_t)st.st_mtime * 1000;
    return 0;
}

int os_rename(const char *from, const char *to) { return rename(from, to); }
int os_rmdir(const char *path) { return rmdir(path); }

int os_list_dir_ex(const char *path, os_dir_ex_cb cb, void *ctx) {
    DIR *d = opendir(path);
    if (!d) return -1;
    struct dirent *e;
    char full[1100];
    while ((e = readdir(d))) {
        if (!strcmp(e->d_name, ".") || !strcmp(e->d_name, "..")) continue;
        snprintf(full, sizeof full, "%s/%s", path, e->d_name);
        int isdir = 0;
        os_stat(full, &isdir, NULL, NULL);
        cb(ctx, e->d_name, isdir != 0);
    }
    closedir(d);
    return 0;
}

int os_write_at(const char *path, const void *data, size_t len, bool append) {
    FILE *f = fopen(path, append ? "ab" : "wb");
    if (!f) return -1;
    int r = (len && fwrite(data, 1, len, f) != len) ? -1 : 0;
    fclose(f);
    return r;
}

int os_truncate(const char *path, int64_t len) { return truncate(path, (off_t)len); }

int64_t os_free_space(const char *path) {
    struct statvfs s;
    if (statvfs(path, &s) != 0) return 64 * 1024 * 1024;
    return (int64_t)s.f_bavail * (int64_t)s.f_frsize;
}

typedef struct { void (*fn)(void *); void *arg; } ThreadStart;
static void *thread_tramp(void *p) {
    ThreadStart s = *(ThreadStart *)p;
    free(p);
    s.fn(s.arg);
    return NULL;
}
int os_thread_start(void (*fn)(void *), void *arg) {
    ThreadStart *s = (ThreadStart *)malloc(sizeof *s);
    s->fn = fn;
    s->arg = arg;
    pthread_t th;
    pthread_attr_t at;
    pthread_attr_init(&at);
    pthread_attr_setdetachstate(&at, PTHREAD_CREATE_DETACHED);
    pthread_attr_setstacksize(&at, 256 * 1024);
    int r = pthread_create(&th, &at, thread_tramp, s);
    pthread_attr_destroy(&at);
    if (r) { free(s); return -1; }
    return 0;
}

typedef int sock_t;
#define BAD_SOCK (-1)
#define sock_close close
static void net_startup(void) {
    static bool done;
    if (!done) {
        signal(SIGPIPE, SIG_IGN);
        done = true;
    }
}
#endif

/* ---------------- TCP connections ----------------
 * Each connection owns a worker thread that resolves, connects and then keeps
 * reading into a buffer. The VM thread only touches the buffer under a mutex,
 * so Java code never blocks the green-thread scheduler. */
typedef struct NetConn {
    int id;
    int refs;           /* VM + worker */
    int state;          /* 0 connecting, 1 connected, -1 failed */
    bool eof, error, closed;
    sock_t s;
    char host[256];
    int port;
    uint8_t *buf;
    size_t len, cap;
    char local[64], remote[300];
} NetConn;

#define NET_MAX 64
static NetConn *net_conns[NET_MAX];
static int net_next_id = 1;

static void net_release(NetConn *c) { /* lock held */
    if (--c->refs == 0) {
        free(c->buf);
        free(c);
    }
}

static void net_worker(void *arg) {
    NetConn *c = (NetConn *)arg;
    struct addrinfo hints, *res = NULL;
    memset(&hints, 0, sizeof hints);
    hints.ai_family = AF_UNSPEC;
    hints.ai_socktype = SOCK_STREAM;
    char port[16];
    snprintf(port, sizeof port, "%d", c->port);
    sock_t s = BAD_SOCK;
    if (getaddrinfo(c->host, port, &hints, &res) == 0) {
        for (struct addrinfo *ai = res; ai; ai = ai->ai_next) {
            s = socket(ai->ai_family, ai->ai_socktype, ai->ai_protocol);
            if (s == BAD_SOCK) continue;
            if (connect(s, ai->ai_addr, (int)ai->ai_addrlen) == 0) break;
            sock_close(s);
            s = BAD_SOCK;
        }
        freeaddrinfo(res);
    }
    os_lock();
    if (s == BAD_SOCK || c->closed) {
        if (s != BAD_SOCK) sock_close(s);
        c->state = -1;
        net_release(c);
        os_unlock();
        events_wakeup();
        return;
    }
    c->s = s;
    c->state = 1;
    {
        struct sockaddr_storage sa;
        socklen_t sl = sizeof sa;
        char addr[64] = "0.0.0.0";
        int p = 0;
        if (getsockname(s, (struct sockaddr *)&sa, &sl) == 0) {
            if (sa.ss_family == AF_INET) {
                struct sockaddr_in *in = (struct sockaddr_in *)&sa;
                inet_ntop(AF_INET, &in->sin_addr, addr, sizeof addr);
                p = ntohs(in->sin_port);
            } else if (sa.ss_family == AF_INET6) {
                struct sockaddr_in6 *in6 = (struct sockaddr_in6 *)&sa;
                inet_ntop(AF_INET6, &in6->sin6_addr, addr, sizeof addr);
                p = ntohs(in6->sin6_port);
            }
        }
        snprintf(c->local, sizeof c->local, "%s:%d", addr, p);
    }
    os_unlock();
    events_wakeup();
    int one = 1;
    setsockopt(s, IPPROTO_TCP, TCP_NODELAY, (const char *)&one, sizeof one);
    uint8_t tmp[8192];
    for (;;) {
        int n = (int)recv(s, (char *)tmp, sizeof tmp, 0);
        os_lock();
        if (c->closed) { os_unlock(); break; }
        if (n <= 0) {
            if (n == 0) c->eof = true;
            else c->error = true;
            os_unlock();
            break;
        }
        if (c->len + n > c->cap) {
            size_t nc = c->cap ? c->cap * 2 : 16384;
            while (nc < c->len + n) nc *= 2;
            c->buf = (uint8_t *)realloc(c->buf, nc);
            c->cap = nc;
        }
        memcpy(c->buf + c->len, tmp, n);
        c->len += n;
        os_unlock();
        events_wakeup();
    }
    os_lock();
    net_release(c);
    os_unlock();
    events_wakeup();
}

int net_connect(const char *host, int port) {
    net_startup();
    os_lock();
    int slot = -1;
    for (int i = 0; i < NET_MAX; i++)
        if (!net_conns[i]) { slot = i; break; }
    if (slot < 0) { os_unlock(); return -1; }
    NetConn *c = (NetConn *)calloc(1, sizeof *c);
    c->id = (net_next_id++ << 8) | slot;
    c->refs = 2;
    c->s = BAD_SOCK;
    snprintf(c->host, sizeof c->host, "%s", host);
    c->port = port;
    snprintf(c->remote, sizeof c->remote, "%s:%d", host, port);
    net_conns[slot] = c;
    int id = c->id;
    os_unlock();
    if (os_thread_start(net_worker, c) != 0) {
        os_lock();
        c->state = -1;
        net_release(c);
        os_unlock();
    }
    return id;
}

static NetConn *net_get(int id) { /* lock held */
    if (id <= 0) return NULL;
    NetConn *c = net_conns[id & 0xFF];
    return (c && c->id == id) ? c : NULL;
}

int net_state(int id) {
    os_lock();
    NetConn *c = net_get(id);
    int r = c ? c->state : -1;
    os_unlock();
    return r;
}

int net_read(int id, uint8_t *dst, int len) {
    os_lock();
    NetConn *c = net_get(id);
    int r;
    if (!c) r = -2;
    else if (c->len > 0) {
        r = (int)(c->len < (size_t)len ? c->len : (size_t)len);
        memcpy(dst, c->buf, r);
        memmove(c->buf, c->buf + r, c->len - r);
        c->len -= r;
    } else if (c->state < 0 || c->error) r = -2;
    else if (c->eof) r = -1;
    else r = 0;
    os_unlock();
    return r;
}

int net_available(int id) {
    os_lock();
    NetConn *c = net_get(id);
    int r = c ? (int)c->len : 0;
    os_unlock();
    return r;
}

int net_write(int id, const uint8_t *src, int len) {
    os_lock();
    NetConn *c = net_get(id);
    sock_t s = (c && c->state == 1 && !c->closed) ? c->s : BAD_SOCK;
    os_unlock();
    if (s == BAD_SOCK) return -1;
    int done = 0;
    while (done < len) {
        int n = (int)send(s, (const char *)src + done, len - done, 0);
        if (n <= 0) return done ? done : -1;
        done += n;
    }
    return done;
}

void net_close(int id) {
    os_lock();
    NetConn *c = net_get(id);
    if (!c) { os_unlock(); return; }
    net_conns[id & 0xFF] = NULL;
    c->closed = true;
    if (c->s != BAD_SOCK) {
#ifdef _WIN32
        shutdown(c->s, SD_BOTH);
#else
        shutdown(c->s, SHUT_RDWR);
#endif
        sock_close(c->s);
        c->s = BAD_SOCK;
    }
    net_release(c);
    os_unlock();
}

int net_address(int id, bool local, char *out, int outlen) {
    os_lock();
    NetConn *c = net_get(id);
    int r = -1;
    if (c) {
        snprintf(out, outlen, "%s", local ? c->local : c->remote);
        r = 0;
    }
    os_unlock();
    return r;
}

void net_shutdown_all(void) {
    for (int i = 0; i < NET_MAX; i++) {
        os_lock();
        NetConn *c = net_conns[i];
        int id = c ? c->id : 0;
        os_unlock();
        if (id) net_close(id);
    }
}
