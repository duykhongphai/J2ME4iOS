/* j4i.Sys natives: tone synthesis, sandboxed file system, TCP sockets, inflate. */
#include "vm.h"
#include <math.h>
#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

#define NATIVE(name) static void name(Thread *t, Value *a, Value *ret)

/* ---------------- tone synthesis ---------------- */
#define SYNTH_RATE 22050

static void put16(uint8_t *p, int v) { p[0] = (uint8_t)v; p[1] = (uint8_t)(v >> 8); }
static void put32(uint8_t *p, uint32_t v) { p[0] = (uint8_t)v; p[1] = (uint8_t)(v >> 8); p[2] = (uint8_t)(v >> 16); p[3] = (uint8_t)(v >> 24); }

/* Square-ish wave with a short attack/release per note so consecutive notes do not click. */
uint8_t *synth_wav(const int *hz, const int *ms, const int *vol, int n, int *out_len) {
    int64_t total = 0;
    for (int i = 0; i < n; i++) total += ms[i] > 0 ? (int64_t)ms[i] * SYNTH_RATE / 1000 : 0;
    if (total > (int64_t)SYNTH_RATE * 300) total = (int64_t)SYNTH_RATE * 300;
    if (total < 1) total = 1;
    size_t bytes = 44 + (size_t)total * 2;
    uint8_t *w = (uint8_t *)calloc(1, bytes);
    memcpy(w, "RIFF", 4);
    put32(w + 4, (uint32_t)(bytes - 8));
    memcpy(w + 8, "WAVEfmt ", 8);
    put32(w + 16, 16);
    put16(w + 20, 1);
    put16(w + 22, 1);
    put32(w + 24, SYNTH_RATE);
    put32(w + 28, SYNTH_RATE * 2);
    put16(w + 32, 2);
    put16(w + 34, 16);
    memcpy(w + 36, "data", 4);
    put32(w + 40, (uint32_t)(total * 2));
    int16_t *pcm = (int16_t *)(w + 44);
    int64_t pos = 0;
    double phase = 0;
    for (int i = 0; i < n && pos < total; i++) {
        int64_t len = ms[i] > 0 ? (int64_t)ms[i] * SYNTH_RATE / 1000 : 0;
        if (pos + len > total) len = total - pos;
        double f = hz[i];
        int v = vol ? vol[i] : 100;
        if (v < 0) v = 0;
        if (v > 100) v = 100;
        double amp = 9000.0 * v / 100.0;
        int ramp = SYNTH_RATE / 400; /* 2.5 ms */
        if (ramp * 2 > len) ramp = (int)(len / 2);
        for (int64_t k = 0; k < len; k++) {
            double s = 0;
            if (f > 0 && f < SYNTH_RATE / 2) {
                phase += f / SYNTH_RATE;
                if (phase >= 1) phase -= floor(phase);
                /* band-limited-ish square: fundamental + odd harmonics */
                double x = 2 * M_PI * phase;
                s = sin(x) + sin(3 * x) / 3;
                if (f * 5 < SYNTH_RATE / 2) s += sin(5 * x) / 5;
                if (f * 7 < SYNTH_RATE / 2) s += sin(7 * x) / 7;
            }
            double e = 1;
            if (k < ramp) e = (double)k / ramp;
            else if (k >= len - ramp) e = (double)(len - k) / ramp;
            int smp = (int)(s * amp * e);
            if (smp > 32767) smp = 32767;
            if (smp < -32768) smp = -32768;
            int16_t le = (int16_t)smp;
            memcpy(&pcm[pos + k], &le, 2); /* little endian hosts only (all supported targets) */
        }
        pos += len;
    }
    *out_len = (int)bytes;
    return w;
}

NATIVE(Sys_synth) {
    Array *fa = (Array *)a[0].l, *da = (Array *)a[1].l, *va = (Array *)a[2].l;
    int n = a[3].i;
    if (!fa || !da || n < 0 || n > fa->length || n > da->length || (va && n > va->length)) {
        throw_new(t, "java/lang/IllegalArgumentException", NULL);
        return;
    }
    int len;
    uint8_t *w = synth_wav(ARR_I(fa), ARR_I(da), va ? ARR_I(va) : NULL, n, &len);
    Array *out = alloc_prim_array(t, 'B', len);
    if (out) memcpy(ARR_B(out), w, len);
    free(w);
    ret->l = (Object *)out;
}

/* ---------------- virtual file system ---------------- */
static void fs_root(char *out, size_t n) {
    snprintf(out, n, "%s/fs", vm.cfg.data_dir ? vm.cfg.data_dir : ".");
}

/* Maps "/root1/a/b" to "<data_dir>/fs/root1/a/b". Rejects ".." segments. */
static bool fs_map(Object *jpath, char *out, size_t n) {
    if (!jpath) return false;
    char *p = string_to_utf8(jpath);
    bool ok = true;
    for (char *s = p; *s; s++)
        if (*s == '\\') *s = '/';
    const char *seg = p;
    while (*seg) {
        while (*seg == '/') seg++;
        const char *e = seg;
        while (*e && *e != '/') e++;
        if (e - seg == 2 && seg[0] == '.' && seg[1] == '.') ok = false;
        if (e - seg >= 1 && memchr(seg, ':', e - seg)) ok = false;
        seg = e;
    }
    char root[900];
    fs_root(root, sizeof root);
    size_t pl = strlen(p);
    while (pl > 1 && p[pl - 1] == '/') p[--pl] = 0; /* strip trailing slash */
    snprintf(out, n, "%s%s%s", root, p[0] == '/' ? "" : "/", strcmp(p, "/") ? p : "");
    free(p);
    return ok;
}

static void fs_ensure_root(void) {
    if (vm.fs_ready) return;
    char root[1000];
    fs_root(root, sizeof root);
    char r1[1100];
    snprintf(r1, sizeof r1, "%s/root1", root);
    os_mkdirs(r1);
    vm.fs_ready = true;
}

NATIVE(Sys_fsType) {
    (void)t;
    char p[1200];
    fs_ensure_root();
    if (!fs_map(a[0].l, p, sizeof p)) { ret->i = 0; return; }
    int isdir = 0;
    ret->i = os_stat(p, &isdir, NULL, NULL) != 0 ? 0 : (isdir ? 2 : 1);
}

NATIVE(Sys_fsSize) {
    (void)t;
    char p[1200];
    int64_t sz = -1;
    if (fs_map(a[0].l, p, sizeof p) && os_stat(p, NULL, &sz, NULL) != 0) sz = -1;
    ret->j = sz;
}

NATIVE(Sys_fsModified) {
    (void)t;
    char p[1200];
    int64_t m = 0;
    if (fs_map(a[0].l, p, sizeof p) && os_stat(p, NULL, NULL, &m) != 0) m = 0;
    ret->j = m;
}

typedef struct { char **names; int n, cap; } FsList;
static void fs_collect(void *ctx, const char *name, bool is_dir) {
    FsList *l = (FsList *)ctx;
    if (l->n == l->cap) {
        l->cap = l->cap ? l->cap * 2 : 16;
        l->names = (char **)realloc(l->names, l->cap * sizeof(char *));
    }
    size_t nl = strlen(name);
    char *s = (char *)malloc(nl + 2);
    memcpy(s, name, nl);
    if (is_dir) s[nl++] = '/';
    s[nl] = 0;
    l->names[l->n++] = s;
}

NATIVE(Sys_fsList) {
    char p[1200];
    fs_ensure_root();
    if (!fs_map(a[0].l, p, sizeof p)) return;
    FsList l = {0};
    if (os_list_dir_ex(p, fs_collect, &l) != 0) return;
    Array *arr = alloc_array(t, vm.c_arr_String, l.n);
    for (int i = 0; arr && i < l.n; i++) ARR_L(arr)[i] = string_new_utf8(t, l.names[i]);
    for (int i = 0; i < l.n; i++) free(l.names[i]);
    free(l.names);
    ret->l = (Object *)arr;
}

NATIVE(Sys_fsRead) {
    char p[1200];
    if (!fs_map(a[0].l, p, sizeof p)) return;
    int isdir = 0;
    if (os_stat(p, &isdir, NULL, NULL) != 0 || isdir) return;
    size_t len;
    uint8_t *d = os_read_file(p, &len);
    if (!d) return;
    Array *b = alloc_prim_array(t, 'B', (int)len);
    if (b) memcpy(ARR_B(b), d, len);
    free(d);
    ret->l = (Object *)b;
}

NATIVE(Sys_fsWrite) {
    char p[1200];
    Array *b = (Array *)a[1].l;
    int off = a[2].i, len = a[3].i;
    fs_ensure_root();
    if (!fs_map(a[0].l, p, sizeof p)) { ret->i = 0; return; }
    if (!b && len > 0) { throw_new(t, "java/lang/NullPointerException", NULL); return; }
    if (len < 0 || off < 0 || (b && off + len > b->length)) {
        throw_new(t, "java/lang/ArrayIndexOutOfBoundsException", NULL);
        return;
    }
    ret->i = os_write_at(p, b ? ARR_B(b) + off : NULL, (size_t)len, a[4].i != 0) == 0;
}

NATIVE(Sys_fsMkdir) {
    (void)t;
    char p[1200];
    fs_ensure_root();
    ret->i = fs_map(a[0].l, p, sizeof p) && os_mkdirs(p) == 0;
}

NATIVE(Sys_fsDelete) {
    (void)t;
    char p[1200];
    int isdir = 0;
    if (!fs_map(a[0].l, p, sizeof p) || os_stat(p, &isdir, NULL, NULL) != 0) { ret->i = 0; return; }
    ret->i = (isdir ? os_rmdir(p) : os_delete_file(p)) == 0;
}

NATIVE(Sys_fsRename) {
    (void)t;
    char p[1200], q[1200];
    ret->i = fs_map(a[0].l, p, sizeof p) && fs_map(a[1].l, q, sizeof q) && os_rename(p, q) == 0;
}

NATIVE(Sys_fsTruncate) {
    (void)t;
    char p[1200];
    ret->i = fs_map(a[0].l, p, sizeof p) && os_truncate(p, a[1].j) == 0;
}

NATIVE(Sys_fsFree) {
    (void)t;
    (void)a;
    char root[1000];
    fs_ensure_root();
    fs_root(root, sizeof root);
    ret->j = os_free_space(root);
}

/* ---------------- sockets ---------------- */
NATIVE(Sys_netConnect) {
    (void)t;
    if (!a[0].l) { ret->i = -1; return; }
    char *h = string_to_utf8(a[0].l);
    ret->i = net_connect(h, a[1].i);
    free(h);
}

NATIVE(Sys_netState) { (void)t; ret->i = net_state(a[0].i); }

static bool range_ok(Thread *t, Array *b, int off, int len) {
    if (!b) { throw_new(t, "java/lang/NullPointerException", NULL); return false; }
    if (off < 0 || len < 0 || off + len > b->length) {
        throw_new(t, "java/lang/ArrayIndexOutOfBoundsException", NULL);
        return false;
    }
    return true;
}

NATIVE(Sys_netRead) {
    Array *b = (Array *)a[1].l;
    if (!range_ok(t, b, a[2].i, a[3].i)) return;
    ret->i = a[3].i == 0 ? 0 : net_read(a[0].i, (uint8_t *)ARR_B(b) + a[2].i, a[3].i);
}

NATIVE(Sys_netWrite) {
    Array *b = (Array *)a[1].l;
    if (!range_ok(t, b, a[2].i, a[3].i)) return;
    ret->i = net_write(a[0].i, (const uint8_t *)ARR_B(b) + a[2].i, a[3].i);
}

NATIVE(Sys_netAvailable) { (void)t; ret->i = net_available(a[0].i); }
NATIVE(Sys_netClose) { (void)t; (void)ret; net_close(a[0].i); }

NATIVE(Sys_netAddress) {
    char buf[320];
    if (net_address(a[0].i, a[1].i != 0, buf, sizeof buf) == 0) ret->l = string_new_utf8(t, buf);
}

/* ---------------- inflate ---------------- */
NATIVE(Sys_inflate) {
    Array *b = (Array *)a[0].l;
    int off = a[1].i, len = a[2].i, expect = a[3].i;
    if (!range_ok(t, b, off, len)) return;
    const uint8_t *src = (const uint8_t *)ARR_B(b) + off;
    if (len >= 2 && (src[0] & 0x0F) == 8 && ((src[0] << 8) | src[1]) % 31 == 0) { /* zlib header */
        src += 2;
        len -= 2;
    }
    uint8_t *out = NULL;
    size_t outlen = 0;
    if (expect > 0) {
        out = (uint8_t *)malloc(expect);
        if (inflate_raw(src, (size_t)len, out, (size_t)expect, &outlen) != 0) { free(out); out = NULL; }
    } else if (inflate_raw_alloc(src, (size_t)len, &out, &outlen) != 0) out = NULL;
    if (!out) return;
    Array *r = alloc_prim_array(t, 'B', (int)outlen);
    if (r) memcpy(ARR_B(r), out, outlen);
    free(out);
    ret->l = (Object *)r;
}

void natives_io_register(void) {
#define R(n, d, f) native_register("j4i/Sys", n, d, f)
    R("synth", "([I[I[II)[B", Sys_synth);
    R("fsType", "(Ljava/lang/String;)I", Sys_fsType);
    R("fsSize", "(Ljava/lang/String;)J", Sys_fsSize);
    R("fsModified", "(Ljava/lang/String;)J", Sys_fsModified);
    R("fsList", "(Ljava/lang/String;)[Ljava/lang/String;", Sys_fsList);
    R("fsRead", "(Ljava/lang/String;)[B", Sys_fsRead);
    R("fsWrite", "(Ljava/lang/String;[BIIZ)Z", Sys_fsWrite);
    R("fsMkdir", "(Ljava/lang/String;)Z", Sys_fsMkdir);
    R("fsDelete", "(Ljava/lang/String;)Z", Sys_fsDelete);
    R("fsRename", "(Ljava/lang/String;Ljava/lang/String;)Z", Sys_fsRename);
    R("fsTruncate", "(Ljava/lang/String;J)Z", Sys_fsTruncate);
    R("fsFree", "()J", Sys_fsFree);
    R("netConnect", "(Ljava/lang/String;I)I", Sys_netConnect);
    R("netState", "(I)I", Sys_netState);
    R("netRead", "(I[BII)I", Sys_netRead);
    R("netWrite", "(I[BII)I", Sys_netWrite);
    R("netAvailable", "(I)I", Sys_netAvailable);
    R("netClose", "(I)V", Sys_netClose);
    R("netAddress", "(IZ)Ljava/lang/String;", Sys_netAddress);
    R("inflate", "([BIII)[B", Sys_inflate);
#undef R
    void natives_io_register2(void);
    natives_io_register2();
}

/* renderMidi(byte[] smf) -> WAV bytes or null (built-in synth, used by tests and tools) */
static void Sys_renderMidi(Thread *t, Value *a, Value *ret) {
    Array *b = (Array *)a[0].l;
    if (!b) { throw_new(t, "java/lang/NullPointerException", NULL); return; }
    int wl;
    uint8_t *w = midi_render_wav((const uint8_t *)ARR_B(b), b->length, &wl);
    if (!w) return;
    Array *r = alloc_prim_array(t, 'B', wl);
    if (r) memcpy(ARR_B(r), w, wl);
    free(w);
    ret->l = (Object *)r;
}

void natives_io_register2(void) { native_register("j4i/Sys", "renderMidi", "([B)[B", Sys_renderMidi); }
