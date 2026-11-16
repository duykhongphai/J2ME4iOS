/*
 * J2ME4iOS desktop runner for Windows. Used to develop and test the VM core.
 *
 *   j4i.exe [options] game.jar
 *     --runtime PATH     j4i-runtime.jar (default: next to the exe)
 *     --main CLASS       run CLASS (static main or MIDlet) instead of the manifest MIDlet
 *     --args "a b"       arguments for static main
 *     --size WxH         virtual screen (default 240x320)
 *     --scale N          window zoom (default 2)
 *     --heap MB          Java heap (default 64)
 *     --data DIR         RecordStore directory (default ./rms/<jar name>)
 *     --trace N          VM trace level
 *     --headless         no window
 *     --script "T=act,..." timed actions (ms since start):
 *          k:CODE  press+release key   p:CODE press   r:CODE release
 *          t:X;Y   tap pointer          shot:FILE.png  quit
 */
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <mmsystem.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include <stdbool.h>
#include "j4i.h"

static CRITICAL_SECTION fb_lock;
static uint32_t *fb;
static int fb_w, fb_h;
static HWND hwnd;
static int scale = 2;
static volatile LONG vm_done;
static J4IConfig cfg;
static LARGE_INTEGER t_start, t_freq;

/* ---------------- host callbacks ---------------- */
static void host_log(void *u, const char *msg) {
    (void)u;
    double t = 0;
    LARGE_INTEGER now;
    QueryPerformanceCounter(&now);
    t = (double)(now.QuadPart - t_start.QuadPart) / (double)t_freq.QuadPart;
    printf("[%7.3f] %s\n", t, msg);
    fflush(stdout);
}

static void host_present(void *u, const uint32_t *argb, int w, int h) {
    (void)u;
    EnterCriticalSection(&fb_lock);
    if (fb_w != w || fb_h != h) {
        free(fb);
        fb = (uint32_t *)malloc((size_t)w * h * 4);
        fb_w = w;
        fb_h = h;
    }
    memcpy(fb, argb, (size_t)w * h * 4);
    LeaveCriticalSection(&fb_lock);
    if (hwnd) InvalidateRect(hwnd, NULL, FALSE);
}

/* GDI font rendering (VM thread only) */
typedef struct { int size, style; HFONT f; int ascent, descent; } FontEnt;
static FontEnt fonts[32];
static int nfonts;
static HDC gdc;
static HBITMAP gbmp;
static uint32_t *gbits;
#define GB 256

static FontEnt *get_font(int size, int style) {
    for (int i = 0; i < nfonts; i++)
        if (fonts[i].size == size && fonts[i].style == style) return &fonts[i];
    if (!gdc) {
        gdc = CreateCompatibleDC(NULL);
        BITMAPINFO bi;
        memset(&bi, 0, sizeof bi);
        bi.bmiHeader.biSize = sizeof(BITMAPINFOHEADER);
        bi.bmiHeader.biWidth = GB;
        bi.bmiHeader.biHeight = -GB;
        bi.bmiHeader.biPlanes = 1;
        bi.bmiHeader.biBitCount = 32;
        gbmp = CreateDIBSection(gdc, &bi, DIB_RGB_COLORS, (void **)&gbits, NULL, 0);
        SelectObject(gdc, gbmp);
        SetBkMode(gdc, TRANSPARENT);
        SetTextColor(gdc, RGB(255, 255, 255));
        SetTextAlign(gdc, TA_TOP | TA_LEFT);
    }
    if (nfonts == 32) nfonts = 0;
    FontEnt *e = &fonts[nfonts++];
    e->size = size;
    e->style = style;
    e->f = CreateFontW(-size, 0, 0, 0, (style & J4I_FONT_BOLD) ? FW_BOLD : FW_NORMAL, (style & J4I_FONT_ITALIC) ? TRUE : FALSE,
                       FALSE, FALSE, DEFAULT_CHARSET, OUT_DEFAULT_PRECIS, CLIP_DEFAULT_PRECIS, ANTIALIASED_QUALITY,
                       DEFAULT_PITCH, L"Tahoma");
    SelectObject(gdc, e->f);
    TEXTMETRICW tm;
    GetTextMetricsW(gdc, &tm);
    e->ascent = tm.tmAscent;
    e->descent = tm.tmDescent;
    return e;
}

static void host_font_metrics(void *u, int size, int style, int *ascent, int *descent) {
    (void)u;
    FontEnt *e = get_font(size, style);
    *ascent = e->ascent;
    *descent = e->descent;
}

static int host_glyph(void *u, int cp, int size, int style, uint8_t *alpha, int stride, int max_w, int h, int ascent) {
    (void)u;
    FontEnt *e = get_font(size, style);
    SelectObject(gdc, e->f);
    wchar_t ch[2];
    int n = 1;
    if (cp >= 0x10000) {
        ch[0] = (wchar_t)(0xD800 + ((cp - 0x10000) >> 10));
        ch[1] = (wchar_t)(0xDC00 + ((cp - 0x10000) & 0x3FF));
        n = 2;
    } else ch[0] = (wchar_t)cp;
    SIZE sz;
    GetTextExtentPoint32W(gdc, ch, n, &sz);
    int adv = sz.cx;
    int w = adv + 2 < max_w ? adv + 2 : max_w;
    int hh = h < GB ? h : GB;
    for (int y = 0; y < hh; y++) memset(gbits + y * GB, 0, (size_t)(w < GB ? w : GB) * 4);
    TextOutW(gdc, 0, ascent - e->ascent, ch, n);
    GdiFlush();
    for (int y = 0; y < hh; y++)
        for (int x = 0; x < w && x < GB; x++) {
            uint32_t p = gbits[y * GB + x];
            int r = (p >> 16) & 255, g = (p >> 8) & 255, b = p & 255;
            alpha[y * stride + x] = (uint8_t)((r + g + b) / 3);
        }
    return adv;
}

static void host_exited(void *u, int code) {
    (void)u;
    (void)code;
    InterlockedExchange(&vm_done, 1);
    if (hwnd) PostMessageW(hwnd, WM_CLOSE, 0, 0);
}

/* ---------------- PNG writer (stored deflate) ---------------- */
static uint32_t crc_table[256];
static void crc_init(void) {
    for (uint32_t n = 0; n < 256; n++) {
        uint32_t c = n;
        for (int k = 0; k < 8; k++) c = (c & 1) ? 0xedb88320u ^ (c >> 1) : c >> 1;
        crc_table[n] = c;
    }
}
static uint32_t crc(uint32_t c, const uint8_t *b, size_t n) {
    c ^= 0xffffffffu;
    for (size_t i = 0; i < n; i++) c = crc_table[(c ^ b[i]) & 0xff] ^ (c >> 8);
    return c ^ 0xffffffffu;
}
static void be32(uint8_t *p, uint32_t v) { p[0] = (uint8_t)(v >> 24); p[1] = (uint8_t)(v >> 16); p[2] = (uint8_t)(v >> 8); p[3] = (uint8_t)v; }
static void chunk(FILE *f, const char *type, const uint8_t *data, uint32_t len) {
    uint8_t hdr[8];
    be32(hdr, len);
    memcpy(hdr + 4, type, 4);
    fwrite(hdr, 1, 8, f);
    if (len) fwrite(data, 1, len, f);
    uint32_t c = crc(0, (const uint8_t *)type, 4);
    if (len) c = crc(c, data, len);
    uint8_t cb[4];
    be32(cb, c);
    fwrite(cb, 1, 4, f);
}

static int write_png(const char *path, const uint32_t *px, int w, int h) {
    FILE *f = fopen(path, "wb");
    if (!f) return -1;
    static const uint8_t sig[8] = {137, 80, 78, 71, 13, 10, 26, 10};
    fwrite(sig, 1, 8, f);
    uint8_t ihdr[13];
    be32(ihdr, w);
    be32(ihdr + 4, h);
    ihdr[8] = 8;
    ihdr[9] = 2;
    ihdr[10] = ihdr[11] = ihdr[12] = 0;
    chunk(f, "IHDR", ihdr, 13);
    size_t rawlen = (size_t)h * (1 + (size_t)w * 3);
    uint8_t *raw = (uint8_t *)malloc(rawlen);
    size_t p = 0;
    for (int y = 0; y < h; y++) {
        raw[p++] = 0;
        for (int x = 0; x < w; x++) {
            uint32_t c = px[y * w + x];
            raw[p++] = (uint8_t)(c >> 16);
            raw[p++] = (uint8_t)(c >> 8);
            raw[p++] = (uint8_t)c;
        }
    }
    size_t nblocks = (rawlen + 65534) / 65535;
    size_t zlen = 2 + rawlen + nblocks * 5 + 4;
    uint8_t *z = (uint8_t *)malloc(zlen);
    size_t q = 0;
    z[q++] = 0x78;
    z[q++] = 0x01;
    uint32_t a = 1, b = 0;
    for (size_t i = 0; i < rawlen; i++) {
        a = (a + raw[i]) % 65521;
        b = (b + a) % 65521;
    }
    for (size_t off = 0; off < rawlen; off += 65535) {
        size_t n = rawlen - off < 65535 ? rawlen - off : 65535;
        z[q++] = (off + n >= rawlen) ? 1 : 0;
        z[q++] = (uint8_t)n;
        z[q++] = (uint8_t)(n >> 8);
        z[q++] = (uint8_t)~n;
        z[q++] = (uint8_t)(~n >> 8);
        memcpy(z + q, raw + off, n);
        q += n;
    }
    be32(z + q, (b << 16) | a);
    q += 4;
    chunk(f, "IDAT", z, (uint32_t)q);
    chunk(f, "IEND", NULL, 0);
    fclose(f);
    free(raw);
    free(z);
    return 0;
}

static void screenshot(const char *path) {
    EnterCriticalSection(&fb_lock);
    if (fb) {
        write_png(path, fb, fb_w, fb_h);
        printf("[shot] %s (%dx%d)\n", path, fb_w, fb_h);
    } else printf("[shot] no frame yet for %s\n", path);
    fflush(stdout);
    LeaveCriticalSection(&fb_lock);
}

/* ---------------- script ---------------- */
static char *script;

static DWORD WINAPI script_thread(LPVOID p) {
    (void)p;
    char *s = _strdup(script);
    DWORD start = GetTickCount();
    for (char *tok = strtok(s, ","); tok; tok = strtok(NULL, ",")) {
        char *eq = strchr(tok, '=');
        if (!eq) continue;
        *eq = 0;
        DWORD at = (DWORD)atoi(tok);
        const char *act = eq + 1;
        while (!vm_done && GetTickCount() - start < at) Sleep(5);
        if (vm_done) break;
        if (!strncmp(act, "k:", 2)) {
            int code = atoi(act + 2);
            j4i_post_event(J4I_EV_KEY_PRESSED, code, 0);
            Sleep(60);
            j4i_post_event(J4I_EV_KEY_RELEASED, code, 0);
        } else if (!strncmp(act, "p:", 2)) j4i_post_event(J4I_EV_KEY_PRESSED, atoi(act + 2), 0);
        else if (!strncmp(act, "r:", 2)) j4i_post_event(J4I_EV_KEY_RELEASED, atoi(act + 2), 0);
        else if (!strncmp(act, "t:", 2)) {
            int x = atoi(act + 2);
            const char *semi = strchr(act, ';');
            int y = semi ? atoi(semi + 1) : 0;
            j4i_post_event(J4I_EV_POINTER_PRESSED, x, y);
            Sleep(50);
            j4i_post_event(J4I_EV_POINTER_RELEASED, x, y);
        } else if (!strncmp(act, "shot:", 5)) screenshot(act + 5);
        else if (!strcmp(act, "quit")) j4i_request_exit();
    }
    free(s);
    return 0;
}

/* ---------------- window ---------------- */
static int map_key(WPARAM vk) {
    switch (vk) {
    case VK_UP: return J4I_KEY_UP;
    case VK_DOWN: return J4I_KEY_DOWN;
    case VK_LEFT: return J4I_KEY_LEFT;
    case VK_RIGHT: return J4I_KEY_RIGHT;
    case VK_RETURN: case VK_SPACE: return J4I_KEY_FIRE;
    case VK_F1: case 'Q': return J4I_KEY_SOFT_LEFT;
    case VK_F2: case 'W': return J4I_KEY_SOFT_RIGHT;
    case VK_BACK: return J4I_KEY_CLEAR;
    case VK_MULTIPLY: case 'E': return '*';
    case VK_DIVIDE: case 'R': return '#';
    }
    if (vk >= '0' && vk <= '9') return (int)vk;
    if (vk >= VK_NUMPAD0 && vk <= VK_NUMPAD9) return '0' + (int)(vk - VK_NUMPAD0);
    return 0;
}

static LRESULT CALLBACK wndproc(HWND h, UINT msg, WPARAM wp, LPARAM lp) {
    static bool down;
    switch (msg) {
    case WM_PAINT: {
        PAINTSTRUCT ps;
        HDC dc = BeginPaint(h, &ps);
        EnterCriticalSection(&fb_lock);
        if (fb) {
            BITMAPINFO bi;
            memset(&bi, 0, sizeof bi);
            bi.bmiHeader.biSize = sizeof(BITMAPINFOHEADER);
            bi.bmiHeader.biWidth = fb_w;
            bi.bmiHeader.biHeight = -fb_h;
            bi.bmiHeader.biPlanes = 1;
            bi.bmiHeader.biBitCount = 32;
            StretchDIBits(dc, 0, 0, fb_w * scale, fb_h * scale, 0, 0, fb_w, fb_h, fb, &bi, DIB_RGB_COLORS, SRCCOPY);
        }
        LeaveCriticalSection(&fb_lock);
        EndPaint(h, &ps);
        return 0;
    }
    case WM_KEYDOWN: {
        int k = map_key(wp);
        if (k) j4i_post_event((lp & (1 << 30)) ? J4I_EV_KEY_REPEATED : J4I_EV_KEY_PRESSED, k, 0);
        if (wp == VK_F12) screenshot("screenshot.png");
        return 0;
    }
    case WM_KEYUP: {
        int k = map_key(wp);
        if (k) j4i_post_event(J4I_EV_KEY_RELEASED, k, 0);
        return 0;
    }
    case WM_LBUTTONDOWN:
        down = true;
        SetCapture(h);
        j4i_post_event(J4I_EV_POINTER_PRESSED, (short)LOWORD(lp) / scale, (short)HIWORD(lp) / scale);
        return 0;
    case WM_MOUSEMOVE:
        if (down) j4i_post_event(J4I_EV_POINTER_DRAGGED, (short)LOWORD(lp) / scale, (short)HIWORD(lp) / scale);
        return 0;
    case WM_LBUTTONUP:
        down = false;
        ReleaseCapture();
        j4i_post_event(J4I_EV_POINTER_RELEASED, (short)LOWORD(lp) / scale, (short)HIWORD(lp) / scale);
        return 0;
    case WM_CLOSE:
        if (!vm_done) {
            j4i_request_exit();
            return 0;
        }
        DestroyWindow(h);
        return 0;
    case WM_DESTROY:
        PostQuitMessage(0);
        return 0;
    }
    return DefWindowProcW(h, msg, wp, lp);
}

/* ---------------- audio (dev harness): WAV only, one sound at a time via PlaySound ---------------- */
#define MAX_CLIPS 64
static struct { uint8_t *wav; int len; double seconds; } clips[MAX_CLIPS];
static int playing_clip;
static double playing_until; /* seconds since start, < 0 = looping */

static double now_s(void) {
    LARGE_INTEGER t;
    QueryPerformanceCounter(&t);
    return (double)(t.QuadPart - t_start.QuadPart) / t_freq.QuadPart;
}

static int win_audio_create(void *u, const uint8_t *data, int len, const char *mime) {
    (void)u;
    (void)mime;
    if (len < 44 || memcmp(data, "RIFF", 4) || memcmp(data + 8, "WAVE", 4)) return 0;
    for (int h = 1; h < MAX_CLIPS; h++) {
        if (clips[h].wav) continue;
        clips[h].wav = (uint8_t *)malloc(len);
        memcpy(clips[h].wav, data, len);
        clips[h].len = len;
        uint32_t byterate = data[28] | (data[29] << 8) | (data[30] << 16) | ((uint32_t)data[31] << 24);
        clips[h].seconds = byterate ? (double)(len - 44) / byterate : 0;
        return h;
    }
    return 0;
}

static void win_audio_play(void *u, int h, int loops) {
    (void)u;
    if (h <= 0 || h >= MAX_CLIPS || !clips[h].wav) return;
    PlaySoundA((LPCSTR)clips[h].wav, NULL, SND_MEMORY | SND_ASYNC | SND_NODEFAULT | (loops != 1 ? SND_LOOP : 0));
    playing_clip = h;
    playing_until = loops != 1 ? -1 : now_s() + clips[h].seconds;
}

static void win_audio_stop(void *u, int h) {
    (void)u;
    if (h == playing_clip) {
        PlaySoundA(NULL, NULL, 0);
        playing_clip = 0;
    }
}

static void win_audio_close(void *u, int h) {
    win_audio_stop(u, h);
    if (h > 0 && h < MAX_CLIPS) {
        free(clips[h].wav);
        clips[h].wav = NULL;
    }
}

static int win_audio_is_playing(void *u, int h) {
    (void)u;
    if (h != playing_clip || !h) return 0;
    return playing_until < 0 || now_s() < playing_until;
}

static DWORD WINAPI vm_thread(LPVOID p) {
    (void)p;
    j4i_run(&cfg);
    InterlockedExchange(&vm_done, 1);
    return 0;
}

static const char *base_name(const char *p) {
    const char *b = p;
    for (const char *s = p; *s; s++)
        if (*s == '/' || *s == '\\') b = s + 1;
    return b;
}

int main(int argc, char **argv) {
    QueryPerformanceFrequency(&t_freq);
    QueryPerformanceCounter(&t_start);
    InitializeCriticalSection(&fb_lock);
    crc_init();
    SetConsoleOutputCP(CP_UTF8);

    static char runtime[MAX_PATH], data_dir[MAX_PATH];
    GetModuleFileNameA(NULL, runtime, MAX_PATH);
    char *slash = strrchr(runtime, '\\');
    if (slash) strcpy(slash + 1, "j4i-runtime.jar");

    bool headless = false;
    char *props = NULL;
    memset(&cfg, 0, sizeof cfg);
    cfg.midi_synth = 1;
    cfg.runtime_jar = runtime;
    cfg.screen_w = 240;
    cfg.screen_h = 320;
    for (int i = 1; i < argc; i++) {
        const char *a = argv[i];
        if (!strcmp(a, "--runtime") && i + 1 < argc) cfg.runtime_jar = argv[++i];
        else if (!strcmp(a, "--main") && i + 1 < argc) cfg.main_class = argv[++i];
        else if (!strcmp(a, "--args") && i + 1 < argc) cfg.main_args = argv[++i];
        else if (!strcmp(a, "--size") && i + 1 < argc) sscanf(argv[++i], "%dx%d", &cfg.screen_w, &cfg.screen_h);
        else if (!strcmp(a, "--scale") && i + 1 < argc) scale = atoi(argv[++i]);
        else if (!strcmp(a, "--heap") && i + 1 < argc) cfg.heap_mb = atoi(argv[++i]);
        else if (!strcmp(a, "--data") && i + 1 < argc) cfg.data_dir = argv[++i];
        else if (!strcmp(a, "--trace") && i + 1 < argc) cfg.trace = atoi(argv[++i]);
        else if (!strcmp(a, "--midlet") && i + 1 < argc) cfg.midlet_index = atoi(argv[++i]);
        else if (!strcmp(a, "--headless")) headless = true;
        else if (!strcmp(a, "--script") && i + 1 < argc) script = argv[++i];
        else if (!strcmp(a, "--jad") && i + 1 < argc) cfg.jad_path = argv[++i];
        else if (!strcmp(a, "--proxy") && i + 1 < argc) cfg.proxy = argv[++i];
        else if (!strcmp(a, "--prop") && i + 1 < argc) {
            size_t ol = props ? strlen(props) : 0, nl = strlen(argv[i + 1]);
            props = (char *)realloc(props, ol + nl + 2);
            memcpy(props + ol, argv[++i], nl);
            props[ol + nl] = '\n';
            props[ol + nl + 1] = 0;
            cfg.sys_props = props;
        } else if (!strcmp(a, "--font") && i + 1 < argc) sscanf(argv[++i], "%d,%d,%d", &cfg.font_small, &cfg.font_medium, &cfg.font_large);
        else if (!strcmp(a, "--no-aa")) cfg.font_no_aa = 1;
        else if (!strcmp(a, "--host-midi")) cfg.midi_synth = 0;
        else if (a[0] != '-') cfg.game_jar = a;
        else {
            fprintf(stderr, "unknown option %s\n", a);
            return 2;
        }
    }
    if (!cfg.game_jar && !cfg.main_class) {
        fprintf(stderr, "usage: j4i [options] game.jar\n");
        return 2;
    }
    if (!cfg.data_dir) {
        snprintf(data_dir, sizeof data_dir, "rms/%s", cfg.game_jar ? base_name(cfg.game_jar) : "default");
        cfg.data_dir = data_dir;
    }
    cfg.host.log = host_log;
    cfg.host.present = host_present;
    cfg.host.font_metrics = host_font_metrics;
    cfg.host.glyph = host_glyph;
    cfg.host.exited = host_exited;
    if (!headless) {
        cfg.host.audio_create = win_audio_create;
        cfg.host.audio_play = win_audio_play;
        cfg.host.audio_stop = win_audio_stop;
        cfg.host.audio_close = win_audio_close;
        cfg.host.audio_is_playing = win_audio_is_playing;
    }

    if (script) CloseHandle(CreateThread(NULL, 0, script_thread, NULL, 0, NULL));

    if (headless) {
        int code = j4i_run(&cfg);
        vm_done = 1;
        return code;
    }

    WNDCLASSW wc;
    memset(&wc, 0, sizeof wc);
    wc.lpfnWndProc = wndproc;
    wc.hInstance = GetModuleHandleW(NULL);
    wc.lpszClassName = L"J2ME4iOS";
    wc.hCursor = LoadCursor(NULL, IDC_ARROW);
    wc.hbrBackground = (HBRUSH)GetStockObject(BLACK_BRUSH);
    RegisterClassW(&wc);
    RECT r = {0, 0, cfg.screen_w * scale, cfg.screen_h * scale};
    AdjustWindowRect(&r, WS_OVERLAPPED | WS_CAPTION | WS_SYSMENU | WS_MINIMIZEBOX, FALSE);
    hwnd = CreateWindowW(L"J2ME4iOS", L"J2ME4iOS", WS_OVERLAPPED | WS_CAPTION | WS_SYSMENU | WS_MINIMIZEBOX,
                         CW_USEDEFAULT, CW_USEDEFAULT, r.right - r.left, r.bottom - r.top, NULL, NULL, wc.hInstance, NULL);
    ShowWindow(hwnd, SW_SHOW);
    HANDLE th = CreateThread(NULL, 0, vm_thread, NULL, 0, NULL);
    MSG msg;
    while (GetMessageW(&msg, NULL, 0, 0) > 0) {
        TranslateMessage(&msg);
        DispatchMessageW(&msg);
    }
    WaitForSingleObject(th, 3000);
    return 0;
}
