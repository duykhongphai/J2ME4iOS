/* Metadata arena, symbol table, logging and UTF helpers. */
#include "vm.h"
#include <stdarg.h>

/* ---------------- metadata arena ---------------- */
typedef struct Chunk {
    struct Chunk *next;
    size_t used, cap;
    _Alignas(16) unsigned char data[];
} Chunk;

static Chunk *chunks;
#define CHUNK_SIZE (256 * 1024)

void *meta_alloc(size_t n) {
    n = (n + 15) & ~(size_t)15;
    if (n > CHUNK_SIZE / 4) {
        Chunk *c = (Chunk *)calloc(1, sizeof(Chunk) + n);
        if (!c) vm_fatal("out of native memory");
        c->cap = c->used = n;
        if (chunks) { c->next = chunks->next; chunks->next = c; }
        else chunks = c;
        return c->data;
    }
    if (!chunks || chunks->used + n > chunks->cap) {
        Chunk *c = (Chunk *)calloc(1, sizeof(Chunk) + CHUNK_SIZE);
        if (!c) vm_fatal("out of native memory");
        c->cap = CHUNK_SIZE;
        c->next = chunks;
        chunks = c;
    }
    void *p = chunks->data + chunks->used;
    chunks->used += n;
    return p;
}

char *meta_strdup(const char *s) {
    size_t n = strlen(s);
    char *p = (char *)meta_alloc(n + 1);
    memcpy(p, s, n + 1);
    return p;
}

void meta_free_all(void) {
    while (chunks) {
        Chunk *n = chunks->next;
        free(chunks);
        chunks = n;
    }
}

/* ---------------- symbols ---------------- */
static Symbol **sym_tab;
static int sym_tab_size, sym_count;

static uint32_t hash_bytes(const char *s, size_t len) {
    uint32_t h = 2166136261u;
    for (size_t i = 0; i < len; i++) { h ^= (unsigned char)s[i]; h *= 16777619u; }
    return h;
}

Symbol *sym_intern(const char *s, size_t len) {
    if (!sym_tab) {
        sym_tab_size = 4096;
        sym_tab = (Symbol **)calloc(sym_tab_size, sizeof(Symbol *));
    }
    uint32_t h = hash_bytes(s, len);
    int idx = h & (sym_tab_size - 1);
    for (Symbol *e = sym_tab[idx]; e; e = e->next)
        if (e->hash == h && e->len == len && memcmp(e->str, s, len) == 0) return e;
    if (sym_count * 2 > sym_tab_size) {
        int ns = sym_tab_size * 2;
        Symbol **nt = (Symbol **)calloc(ns, sizeof(Symbol *));
        for (int i = 0; i < sym_tab_size; i++) {
            Symbol *e = sym_tab[i];
            while (e) {
                Symbol *nx = e->next;
                int j = e->hash & (ns - 1);
                e->next = nt[j];
                nt[j] = e;
                e = nx;
            }
        }
        free(sym_tab);
        sym_tab = nt;
        sym_tab_size = ns;
        idx = h & (sym_tab_size - 1);
    }
    Symbol *e = (Symbol *)meta_alloc(sizeof(Symbol) + len + 1);
    e->hash = h;
    e->len = (uint32_t)len;
    memcpy(e->str, s, len);
    e->str[len] = 0;
    e->next = sym_tab[idx];
    sym_tab[idx] = e;
    sym_count++;
    return e;
}

Symbol *sym(const char *s) { return sym_intern(s, strlen(s)); }

void sym_free_all(void) {
    free(sym_tab);
    sym_tab = NULL;
    sym_tab_size = sym_count = 0;
}

/* ---------------- logging ---------------- */
void vm_log(const char *fmt, ...) {
    char buf[2048];
    va_list ap;
    va_start(ap, fmt);
    vsnprintf(buf, sizeof buf, fmt, ap);
    va_end(ap);
    if (vm.cfg.host.log) vm.cfg.host.log(vm.cfg.host.user, buf);
    else fprintf(stderr, "%s\n", buf);
}

void vm_fatal(const char *fmt, ...) {
    char buf[2048];
    va_list ap;
    va_start(ap, fmt);
    vsnprintf(buf, sizeof buf, fmt, ap);
    va_end(ap);
    vm_log("FATAL: %s", buf);
    abort();
}

/* ---------------- UTF helpers ---------------- */
int mutf8_to_utf16(const uint8_t *s, int len, uint16_t *out) {
    int n = 0, i = 0;
    while (i < len) {
        unsigned c = s[i];
        uint16_t ch;
        if (c < 0x80) { ch = (uint16_t)c; i++; }
        else if ((c & 0xE0) == 0xC0 && i + 1 < len) {
            ch = (uint16_t)(((c & 0x1F) << 6) | (s[i + 1] & 0x3F));
            i += 2;
        } else if ((c & 0xF0) == 0xE0 && i + 2 < len) {
            ch = (uint16_t)(((c & 0x0F) << 12) | ((s[i + 1] & 0x3F) << 6) | (s[i + 2] & 0x3F));
            i += 3;
        } else { ch = (uint16_t)c; i++; }
        if (out) out[n] = ch;
        n++;
    }
    return n;
}

int utf8_to_utf16(const uint8_t *s, int len, uint16_t *out) {
    int n = 0, i = 0;
    while (i < len) {
        unsigned c = s[i];
        uint32_t cp;
        int need = 0;
        if (c < 0x80) { cp = c; }
        else if ((c & 0xE0) == 0xC0) { cp = c & 0x1F; need = 1; }
        else if ((c & 0xF0) == 0xE0) { cp = c & 0x0F; need = 2; }
        else if ((c & 0xF8) == 0xF0) { cp = c & 0x07; need = 3; }
        else { cp = c; need = -1; }
        bool ok = need >= 0;
        for (int k = 1; ok && k <= need; k++) {
            if (i + k >= len || (s[i + k] & 0xC0) != 0x80) ok = false;
            else cp = (cp << 6) | (s[i + k] & 0x3F);
        }
        if (!ok) { cp = c; need = 0; } /* latin-1 fallback */
        i += need + 1;
        if (cp >= 0x10000) {
            cp -= 0x10000;
            if (out) { out[n] = (uint16_t)(0xD800 + (cp >> 10)); out[n + 1] = (uint16_t)(0xDC00 + (cp & 0x3FF)); }
            n += 2;
        } else {
            if (out) out[n] = (uint16_t)cp;
            n++;
        }
    }
    return n;
}

int utf16_to_utf8(const uint16_t *s, int len, char *out) {
    int n = 0;
    for (int i = 0; i < len; i++) {
        uint32_t c = s[i];
        if (c >= 0xD800 && c < 0xDC00 && i + 1 < len && s[i + 1] >= 0xDC00 && s[i + 1] < 0xE000) {
            c = 0x10000 + ((c - 0xD800) << 10) + (s[i + 1] - 0xDC00);
            i++;
        }
        if (c < 0x80) { if (out) out[n] = (char)c; n++; }
        else if (c < 0x800) {
            if (out) { out[n] = (char)(0xC0 | (c >> 6)); out[n + 1] = (char)(0x80 | (c & 0x3F)); }
            n += 2;
        } else if (c < 0x10000) {
            if (out) {
                out[n] = (char)(0xE0 | (c >> 12));
                out[n + 1] = (char)(0x80 | ((c >> 6) & 0x3F));
                out[n + 2] = (char)(0x80 | (c & 0x3F));
            }
            n += 3;
        } else {
            if (out) {
                out[n] = (char)(0xF0 | (c >> 18));
                out[n + 1] = (char)(0x80 | ((c >> 12) & 0x3F));
                out[n + 2] = (char)(0x80 | ((c >> 6) & 0x3F));
                out[n + 3] = (char)(0x80 | (c & 0x3F));
            }
            n += 4;
        }
    }
    return n;
}
