/* Small DEFLATE decoder (RFC 1951), used for JAR entries and PNG data. */
#include "vm.h"
#include <setjmp.h>

typedef struct {
    const uint8_t *in;
    size_t inlen, inpos;
    uint32_t bitbuf;
    int bitcnt;
    uint8_t *out;
    size_t outcap, outpos;
    int grow; /* output buffer may be realloc'd */
    jmp_buf err;
} Inf;

typedef struct {
    uint16_t count[16];
    uint16_t symbol[320];
} Huff;

static int bits(Inf *s, int need) {
    uint32_t val = s->bitbuf;
    while (s->bitcnt < need) {
        if (s->inpos >= s->inlen) longjmp(s->err, 1);
        val |= (uint32_t)s->in[s->inpos++] << s->bitcnt;
        s->bitcnt += 8;
    }
    s->bitbuf = val >> need;
    s->bitcnt -= need;
    return (int)(val & ((1u << need) - 1));
}

static void put(Inf *s, uint8_t b) {
    if (s->outpos >= s->outcap) {
        if (!s->grow) longjmp(s->err, 2);
        size_t nc = s->outcap ? s->outcap * 2 : 65536;
        uint8_t *n = (uint8_t *)realloc(s->out, nc);
        if (!n) longjmp(s->err, 3);
        s->out = n;
        s->outcap = nc;
    }
    s->out[s->outpos++] = b;
}

static int decode(Inf *s, const Huff *h) {
    int code = 0, first = 0, index = 0;
    for (int len = 1; len <= 15; len++) {
        code |= bits(s, 1);
        int count = h->count[len];
        if (code - count < first) return h->symbol[index + (code - first)];
        index += count;
        first += count;
        first <<= 1;
        code <<= 1;
    }
    longjmp(s->err, 4);
    return -1;
}

static int construct(Huff *h, const uint8_t *length, int n) {
    uint16_t offs[16];
    for (int len = 0; len <= 15; len++) h->count[len] = 0;
    for (int sym = 0; sym < n; sym++) h->count[length[sym]]++;
    if (h->count[0] == n) return 0;
    int left = 1;
    for (int len = 1; len <= 15; len++) {
        left <<= 1;
        left -= h->count[len];
        if (left < 0) return left;
    }
    offs[1] = 0;
    for (int len = 1; len < 15; len++) offs[len + 1] = offs[len] + h->count[len];
    for (int sym = 0; sym < n; sym++)
        if (length[sym] != 0) h->symbol[offs[length[sym]]++] = (uint16_t)sym;
    return left;
}

static const uint16_t lbase[29] = {3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 15, 17, 19, 23, 27, 31,
                                   35, 43, 51, 59, 67, 83, 99, 115, 131, 163, 195, 227, 258};
static const uint16_t lext[29] = {0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2,
                                  3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0};
static const uint16_t dbase[30] = {1, 2, 3, 4, 5, 7, 9, 13, 17, 25, 33, 49, 65, 97, 129,
                                   193, 257, 385, 513, 769, 1025, 1537, 2049, 3073, 4097,
                                   6145, 8193, 12289, 16385, 24577};
static const uint16_t dext[30] = {0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6,
                                  7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13};

static void codes(Inf *s, const Huff *lencode, const Huff *distcode) {
    for (;;) {
        int sym = decode(s, lencode);
        if (sym < 256) { put(s, (uint8_t)sym); continue; }
        if (sym == 256) return;
        sym -= 257;
        if (sym >= 29) longjmp(s->err, 5);
        int len = lbase[sym] + bits(s, lext[sym]);
        int dsym = decode(s, distcode);
        if (dsym >= 30) longjmp(s->err, 6);
        size_t dist = dbase[dsym] + bits(s, dext[dsym]);
        if (dist > s->outpos) longjmp(s->err, 7);
        while (len--) {
            uint8_t b = s->out[s->outpos - dist];
            put(s, b);
        }
    }
}

static void stored(Inf *s) {
    s->bitbuf = 0;
    s->bitcnt = 0;
    if (s->inpos + 4 > s->inlen) longjmp(s->err, 1);
    unsigned len = s->in[s->inpos] | (s->in[s->inpos + 1] << 8);
    unsigned nlen = s->in[s->inpos + 2] | (s->in[s->inpos + 3] << 8);
    s->inpos += 4;
    if (len != (~nlen & 0xffff)) longjmp(s->err, 8);
    if (s->inpos + len > s->inlen) longjmp(s->err, 1);
    for (unsigned i = 0; i < len; i++) put(s, s->in[s->inpos++]);
}

static void fixed(Inf *s) {
    static J4I_TLS Huff lc, dc;
    static J4I_TLS int built;
    if (!built) {
        uint8_t l[288];
        int i;
        for (i = 0; i < 144; i++) l[i] = 8;
        for (; i < 256; i++) l[i] = 9;
        for (; i < 280; i++) l[i] = 7;
        for (; i < 288; i++) l[i] = 8;
        construct(&lc, l, 288);
        for (i = 0; i < 30; i++) l[i] = 5;
        construct(&dc, l, 30);
        built = 1;
    }
    codes(s, &lc, &dc);
}

static void dynamic(Inf *s) {
    static const uint8_t order[19] = {16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15};
    uint8_t lengths[320];
    Huff lencode, distcode;
    int nlen = bits(s, 5) + 257;
    int ndist = bits(s, 5) + 1;
    int ncode = bits(s, 4) + 4;
    if (nlen > 286 || ndist > 30) longjmp(s->err, 9);
    int index;
    for (index = 0; index < ncode; index++) lengths[order[index]] = (uint8_t)bits(s, 3);
    for (; index < 19; index++) lengths[order[index]] = 0;
    if (construct(&lencode, lengths, 19) != 0) longjmp(s->err, 10);
    index = 0;
    while (index < nlen + ndist) {
        int sym = decode(s, &lencode);
        if (sym < 16) lengths[index++] = (uint8_t)sym;
        else {
            int len = 0, rep;
            if (sym == 16) {
                if (index == 0) longjmp(s->err, 11);
                len = lengths[index - 1];
                rep = 3 + bits(s, 2);
            } else if (sym == 17) rep = 3 + bits(s, 3);
            else rep = 11 + bits(s, 7);
            if (index + rep > nlen + ndist) longjmp(s->err, 12);
            while (rep--) lengths[index++] = (uint8_t)len;
        }
    }
    int err = construct(&lencode, lengths, nlen);
    if (err < 0 || (err > 0 && nlen - lencode.count[0] != 1)) longjmp(s->err, 13);
    err = construct(&distcode, lengths + nlen, ndist);
    if (err < 0 || (err > 0 && ndist - distcode.count[0] != 1)) longjmp(s->err, 14);
    codes(s, &lencode, &distcode);
}

static int run(Inf *s) {
    int r = setjmp(s->err);
    if (r) return r;
    int last;
    do {
        last = bits(s, 1);
        int type = bits(s, 2);
        if (type == 0) stored(s);
        else if (type == 1) fixed(s);
        else if (type == 2) dynamic(s);
        else return 15;
    } while (!last);
    return 0;
}

int inflate_raw(const uint8_t *src, size_t srclen, uint8_t *dst, size_t dstcap, size_t *outlen) {
    Inf s;
    memset(&s, 0, sizeof s);
    s.in = src;
    s.inlen = srclen;
    s.out = dst;
    s.outcap = dstcap;
    int r = run(&s);
    if (outlen) *outlen = s.outpos;
    /* A truncated stream that already filled the buffer is fine for our use. */
    if (r == 1 && s.outpos == dstcap) r = 0;
    return r;
}

int inflate_raw_alloc(const uint8_t *src, size_t srclen, uint8_t **out, size_t *outlen) {
    Inf s;
    memset(&s, 0, sizeof s);
    s.in = src;
    s.inlen = srclen;
    s.grow = 1;
    int r = run(&s);
    *out = s.out;
    *outlen = s.outpos;
    return r;
}
