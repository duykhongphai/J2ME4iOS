/* Minimal ZIP/JAR reader. Tolerates the broken archives some J2ME obfuscators emit. */
#include "vm.h"

typedef struct ZipEntry {
    const char *name;
    uint32_t name_len;
    uint32_t data_off; /* offset of local header */
    uint32_t csize, usize;
    uint16_t method;
    bool from_local; /* sizes taken from local header scan */
} ZipEntry;

struct Zip {
    uint8_t *data;
    size_t size;
    ZipEntry *entries;
    int n, cap;
    int *tab;
    int tab_size;
};

static uint16_t rd16(const uint8_t *p) { return (uint16_t)(p[0] | (p[1] << 8)); }
static uint32_t rd32(const uint8_t *p) { return (uint32_t)p[0] | ((uint32_t)p[1] << 8) | ((uint32_t)p[2] << 16) | ((uint32_t)p[3] << 24); }

static uint32_t name_hash(const char *s, size_t n) {
    uint32_t h = 5381;
    for (size_t i = 0; i < n; i++) h = h * 33 + (unsigned char)s[i];
    return h;
}

static void add_entry(Zip *z, const char *name, uint32_t nlen, uint32_t off, uint32_t cs, uint32_t us, uint16_t method, bool local) {
    while (nlen && name[0] == '/') { name++; nlen--; }
    if (nlen == 0 || name[nlen - 1] == '/') return;
    if (z->n == z->cap) {
        z->cap = z->cap ? z->cap * 2 : 64;
        z->entries = (ZipEntry *)realloc(z->entries, z->cap * sizeof(ZipEntry));
    }
    ZipEntry *e = &z->entries[z->n++];
    e->name = name;
    e->name_len = nlen;
    e->data_off = off;
    e->csize = cs;
    e->usize = us;
    e->method = method;
    e->from_local = local;
}

static bool parse_central(Zip *z) {
    if (z->size < 22) return false;
    size_t lim = z->size > 65557 ? z->size - 65557 : 0;
    size_t eocd = (size_t)-1;
    for (size_t i = z->size - 22 + 1; i-- > lim;) {
        if (rd32(z->data + i) == 0x06054b50) { eocd = i; break; }
    }
    if (eocd == (size_t)-1) return false;
    uint32_t count = rd16(z->data + eocd + 10);
    uint32_t cd_off = rd32(z->data + eocd + 16);
    size_t p = cd_off;
    for (uint32_t i = 0; i < count; i++) {
        if (p + 46 > z->size || rd32(z->data + p) != 0x02014b50) return z->n > 0;
        uint16_t method = rd16(z->data + p + 10);
        uint32_t cs = rd32(z->data + p + 20), us = rd32(z->data + p + 24);
        uint16_t nl = rd16(z->data + p + 28), xl = rd16(z->data + p + 30), cl = rd16(z->data + p + 32);
        uint32_t lo = rd32(z->data + p + 42);
        if (p + 46 + nl > z->size) return z->n > 0;
        add_entry(z, (const char *)z->data + p + 46, nl, lo, cs, us, method, false);
        p += 46 + nl + xl + cl;
    }
    return z->n > 0;
}

static void parse_local(Zip *z) {
    size_t p = 0;
    while (p + 30 <= z->size) {
        if (rd32(z->data + p) != 0x04034b50) { p++; continue; }
        uint16_t flags = rd16(z->data + p + 6);
        uint16_t method = rd16(z->data + p + 8);
        uint32_t cs = rd32(z->data + p + 18), us = rd32(z->data + p + 22);
        uint16_t nl = rd16(z->data + p + 26), xl = rd16(z->data + p + 28);
        if (p + 30 + nl > z->size) break;
        bool unknown = (flags & 8) && cs == 0;
        add_entry(z, (const char *)z->data + p + 30, nl, (uint32_t)p, unknown ? 0xFFFFFFFFu : cs, us, method, true);
        p += 30 + nl + xl + (unknown ? 0 : cs);
    }
}

static void build_index(Zip *z) {
    z->tab_size = 16;
    while (z->tab_size < z->n * 2) z->tab_size <<= 1;
    z->tab = (int *)malloc(z->tab_size * sizeof(int));
    for (int i = 0; i < z->tab_size; i++) z->tab[i] = -1;
    for (int i = 0; i < z->n; i++) {
        uint32_t h = name_hash(z->entries[i].name, z->entries[i].name_len) & (z->tab_size - 1);
        while (z->tab[h] >= 0) {
            ZipEntry *o = &z->entries[z->tab[h]];
            if (o->name_len == z->entries[i].name_len && memcmp(o->name, z->entries[i].name, o->name_len) == 0) break;
            h = (h + 1) & (z->tab_size - 1);
        }
        if (z->tab[h] < 0) z->tab[h] = i; /* first wins */
    }
}

Zip *zip_open_file(const char *path) {
    size_t len;
    uint8_t *data = os_read_file(path, &len);
    if (!data) return NULL;
    Zip *z = (Zip *)calloc(1, sizeof(Zip));
    z->data = data;
    z->size = len;
    if (!parse_central(z)) {
        z->n = 0;
        parse_local(z);
    }
    if (z->n == 0) { zip_close(z); return NULL; }
    build_index(z);
    return z;
}

void zip_close(Zip *z) {
    if (!z) return;
    free(z->data);
    free(z->entries);
    free(z->tab);
    free(z);
}

static ZipEntry *find(Zip *z, const char *name) {
    while (*name == '/') name++;
    size_t n = strlen(name);
    uint32_t h = name_hash(name, n) & (z->tab_size - 1);
    while (z->tab[h] >= 0) {
        ZipEntry *e = &z->entries[z->tab[h]];
        if (e->name_len == n && memcmp(e->name, name, n) == 0) return e;
        h = (h + 1) & (z->tab_size - 1);
    }
    return NULL;
}

bool zip_has(Zip *z, const char *name) { return z && find(z, name) != NULL; }

uint8_t *zip_read(Zip *z, const char *name, int *len) {
    if (!z) return NULL;
    ZipEntry *e = find(z, name);
    if (!e) return NULL;
    size_t lh = e->data_off;
    if (lh + 30 > z->size || rd32(z->data + lh) != 0x04034b50) return NULL;
    size_t start = lh + 30 + rd16(z->data + lh + 26) + rd16(z->data + lh + 28);
    if (start > z->size) return NULL;
    size_t avail = z->size - start;
    size_t cs = e->csize == 0xFFFFFFFFu ? avail : e->csize;
    if (cs > avail) cs = avail;
    if (e->method == 0) {
        uint8_t *out = (uint8_t *)malloc(cs + 1);
        memcpy(out, z->data + start, cs);
        out[cs] = 0;
        *len = (int)cs;
        return out;
    }
    if (e->method != 8) return NULL;
    if (e->usize && e->csize != 0xFFFFFFFFu) {
        uint8_t *out = (uint8_t *)malloc((size_t)e->usize + 1);
        size_t got = 0;
        int r = inflate_raw(z->data + start, cs, out, e->usize, &got);
        if (r == 0 || got == e->usize) {
            out[got] = 0;
            *len = (int)got;
            return out;
        }
        free(out);
    }
    uint8_t *out = NULL;
    size_t got = 0;
    inflate_raw_alloc(z->data + start, cs, &out, &got);
    if (!out) return NULL;
    out = (uint8_t *)realloc(out, got + 1);
    out[got] = 0;
    *len = (int)got;
    return out;
}
