/* PNG decoder -> ARGB8888. Supports all color types, bit depths, tRNS and Adam7. */
#include "vm.h"

static uint32_t be32(const uint8_t *p) { return ((uint32_t)p[0] << 24) | ((uint32_t)p[1] << 16) | ((uint32_t)p[2] << 8) | p[3]; }

static int paeth(int a, int b, int c) {
    int p = a + b - c;
    int pa = abs(p - a), pb = abs(p - b), pc = abs(p - c);
    if (pa <= pb && pa <= pc) return a;
    if (pb <= pc) return b;
    return c;
}

static bool unfilter(uint8_t *data, int rows, size_t rowbytes, int bpp) {
    uint8_t *prev = NULL;
    for (int y = 0; y < rows; y++) {
        uint8_t *row = data + y * (rowbytes + 1);
        int ft = row[0];
        uint8_t *cur = row + 1;
        switch (ft) {
        case 0: break;
        case 1:
            for (size_t i = bpp; i < rowbytes; i++) cur[i] += cur[i - bpp];
            break;
        case 2:
            if (prev) for (size_t i = 0; i < rowbytes; i++) cur[i] += prev[i];
            break;
        case 3:
            for (size_t i = 0; i < rowbytes; i++) {
                int a = i >= (size_t)bpp ? cur[i - bpp] : 0;
                int b = prev ? prev[i] : 0;
                cur[i] += (uint8_t)((a + b) >> 1);
            }
            break;
        case 4:
            for (size_t i = 0; i < rowbytes; i++) {
                int a = i >= (size_t)bpp ? cur[i - bpp] : 0;
                int b = prev ? prev[i] : 0;
                int c = (prev && i >= (size_t)bpp) ? prev[i - bpp] : 0;
                cur[i] += (uint8_t)paeth(a, b, c);
            }
            break;
        default: return false;
        }
        prev = cur;
    }
    return true;
}

typedef struct {
    int ctype, depth, channels;
    uint32_t pal[256];
    int npal;
    bool has_trns;
    int trns_gray, trns_r, trns_g, trns_b;
} PngInfo;

static int sample(const uint8_t *row, int x, int ch, const PngInfo *pi) {
    int d = pi->depth;
    if (d == 8) return row[x * pi->channels + ch];
    if (d == 16) return row[(x * pi->channels + ch) * 2]; /* high byte */
    /* sub-byte: only gray or palette (1 channel) */
    int bitpos = x * d;
    int byte = row[bitpos >> 3];
    int shift = 8 - d - (bitpos & 7);
    return (byte >> shift) & ((1 << d) - 1);
}

static int sample16(const uint8_t *row, int x, int ch, const PngInfo *pi) {
    if (pi->depth == 16) {
        const uint8_t *p = row + (x * pi->channels + ch) * 2;
        return (p[0] << 8) | p[1];
    }
    return sample(row, x, ch, pi);
}

static uint32_t to_argb(const uint8_t *row, int x, const PngInfo *pi) {
    int r, g, b, a = 255;
    switch (pi->ctype) {
    case 0: {
        int v = sample(row, x, 0, pi);
        if (pi->has_trns && sample16(row, x, 0, pi) == pi->trns_gray) a = 0;
        if (pi->depth < 8) v = v * 255 / ((1 << pi->depth) - 1);
        r = g = b = v;
        break;
    }
    case 2:
        r = sample(row, x, 0, pi);
        g = sample(row, x, 1, pi);
        b = sample(row, x, 2, pi);
        if (pi->has_trns && sample16(row, x, 0, pi) == pi->trns_r && sample16(row, x, 1, pi) == pi->trns_g &&
            sample16(row, x, 2, pi) == pi->trns_b) a = 0;
        break;
    case 3: {
        int i = sample(row, x, 0, pi);
        return i < 256 ? pi->pal[i] : 0xFF000000u;
    }
    case 4:
        r = g = b = sample(row, x, 0, pi);
        a = sample(row, x, 1, pi);
        break;
    case 6:
        r = sample(row, x, 0, pi);
        g = sample(row, x, 1, pi);
        b = sample(row, x, 2, pi);
        a = sample(row, x, 3, pi);
        break;
    default: r = g = b = 0;
    }
    return ((uint32_t)a << 24) | ((uint32_t)r << 16) | ((uint32_t)g << 8) | (uint32_t)b;
}

uint32_t *png_decode(const uint8_t *data, int len, int *wout, int *hout) {
    static const uint8_t sig[8] = {137, 80, 78, 71, 13, 10, 26, 10};
    if (len < 33 || memcmp(data, sig, 8) != 0) return NULL;
    PngInfo pi;
    memset(&pi, 0, sizeof pi);
    int w = 0, h = 0, interlace = 0;
    uint8_t *idat = NULL;
    size_t idat_len = 0, idat_cap = 0;
    int pos = 8;
    for (int i = 0; i < 256; i++) pi.pal[i] = 0xFF000000u;
    while (pos + 8 <= len) {
        uint32_t clen = be32(data + pos);
        const uint8_t *type = data + pos + 4;
        const uint8_t *cd = data + pos + 8;
        if (clen > (uint32_t)(len - pos - 8)) clen = (uint32_t)(len - pos - 8);
        if (!memcmp(type, "IHDR", 4) && clen >= 13) {
            w = (int)be32(cd);
            h = (int)be32(cd + 4);
            pi.depth = cd[8];
            pi.ctype = cd[9];
            interlace = cd[12];
        } else if (!memcmp(type, "PLTE", 4)) {
            pi.npal = (int)(clen / 3);
            if (pi.npal > 256) pi.npal = 256;
            for (int i = 0; i < pi.npal; i++)
                pi.pal[i] = 0xFF000000u | ((uint32_t)cd[i * 3] << 16) | ((uint32_t)cd[i * 3 + 1] << 8) | cd[i * 3 + 2];
        } else if (!memcmp(type, "tRNS", 4)) {
            pi.has_trns = true;
            if (pi.ctype == 3) {
                for (uint32_t i = 0; i < clen && i < 256; i++)
                    pi.pal[i] = (pi.pal[i] & 0x00FFFFFFu) | ((uint32_t)cd[i] << 24);
            } else if (pi.ctype == 0 && clen >= 2) {
                pi.trns_gray = (cd[0] << 8) | cd[1];
            } else if (pi.ctype == 2 && clen >= 6) {
                pi.trns_r = (cd[0] << 8) | cd[1];
                pi.trns_g = (cd[2] << 8) | cd[3];
                pi.trns_b = (cd[4] << 8) | cd[5];
            }
        } else if (!memcmp(type, "IDAT", 4)) {
            if (idat_len + clen > idat_cap) {
                idat_cap = (idat_len + clen) * 2;
                idat = (uint8_t *)realloc(idat, idat_cap);
            }
            memcpy(idat + idat_len, cd, clen);
            idat_len += clen;
        } else if (!memcmp(type, "IEND", 4)) {
            break;
        }
        pos += 12 + (int)clen;
    }
    if (w <= 0 || h <= 0 || w > 8192 || h > 8192 || !idat || idat_len < 2) { free(idat); return NULL; }
    switch (pi.ctype) {
    case 0: pi.channels = 1; break;
    case 2: pi.channels = 3; break;
    case 3: pi.channels = 1; break;
    case 4: pi.channels = 2; break;
    case 6: pi.channels = 4; break;
    default: free(idat); return NULL;
    }
    int bpp_bits = pi.channels * pi.depth;
    int bpp = (bpp_bits + 7) / 8;

    static const int ax[7] = {0, 4, 0, 2, 0, 1, 0}, ay[7] = {0, 0, 4, 0, 2, 0, 1};
    static const int dx[7] = {8, 8, 4, 4, 2, 2, 1}, dy[7] = {8, 8, 8, 4, 4, 2, 2};
    int passes = interlace ? 7 : 1;
    size_t total = 0;
    for (int p = 0; p < passes; p++) {
        int pw = interlace ? (w - ax[p] + dx[p] - 1) / dx[p] : w;
        int ph = interlace ? (h - ay[p] + dy[p] - 1) / dy[p] : h;
        if (pw <= 0 || ph <= 0) continue;
        total += (size_t)ph * (((size_t)pw * bpp_bits + 7) / 8 + 1);
    }
    uint8_t *raw = (uint8_t *)calloc(1, total + 1);
    size_t got = 0;
    /* skip 2-byte zlib header */
    inflate_raw(idat + 2, idat_len - 2, raw, total, &got);
    free(idat);
    uint32_t *out = (uint32_t *)malloc((size_t)w * h * 4);
    size_t off = 0;
    for (int p = 0; p < passes; p++) {
        int pw = interlace ? (w - ax[p] + dx[p] - 1) / dx[p] : w;
        int ph = interlace ? (h - ay[p] + dy[p] - 1) / dy[p] : h;
        if (pw <= 0 || ph <= 0) continue;
        size_t rowbytes = ((size_t)pw * bpp_bits + 7) / 8;
        unfilter(raw + off, ph, rowbytes, bpp);
        for (int y = 0; y < ph; y++) {
            const uint8_t *row = raw + off + y * (rowbytes + 1) + 1;
            int oy = interlace ? ay[p] + y * dy[p] : y;
            for (int x = 0; x < pw; x++) {
                int ox = interlace ? ax[p] + x * dx[p] : x;
                out[oy * w + ox] = to_argb(row, x, &pi);
            }
        }
        off += (size_t)ph * (rowbytes + 1);
    }
    free(raw);
    *wout = w;
    *hout = h;
    return out;
}
