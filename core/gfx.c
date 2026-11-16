/* Software rasterizer behind javax.microedition.lcdui.Graphics (ARGB8888), text and
 * image decoding. All coordinates are absolute target pixels; clip is [x0,x1)x[y0,y1). */
#include "vm.h"
#include <math.h>
#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

#define NATIVE(name) static void name(Thread *t, Value *a, Value *ret)

typedef struct {
    uint32_t *px;
    int w, h;
    int x0, y0, x1, y1; /* clip, already clamped */
} Target;

static bool target_from(Value *a, Target *tg) {
    Array *d = (Array *)a[0].l;
    if (!d) return false;
    tg->px = (uint32_t *)ARR_I(d);
    tg->w = a[1].i;
    tg->h = a[2].i;
    if ((int64_t)tg->w * tg->h > d->length) return false;
    tg->x0 = a[3].i < 0 ? 0 : a[3].i;
    tg->y0 = a[4].i < 0 ? 0 : a[4].i;
    tg->x1 = a[5].i > tg->w ? tg->w : a[5].i;
    tg->y1 = a[6].i > tg->h ? tg->h : a[6].i;
    return tg->x0 < tg->x1 && tg->y0 < tg->y1;
}

static inline uint32_t blend(uint32_t dst, uint32_t src) {
    uint32_t sa = src >> 24;
    if (sa == 255) return src;
    if (sa == 0) return dst;
    uint32_t da = dst >> 24;
    uint32_t ia = 255 - sa;
    if (da == 255) {
        uint32_t rb = ((src & 0xFF00FF) * sa + (dst & 0xFF00FF) * ia) >> 8;
        uint32_t g = ((src & 0x00FF00) * sa + (dst & 0x00FF00) * ia) >> 8;
        return 0xFF000000u | (rb & 0xFF00FF) | (g & 0x00FF00);
    }
    /* general "over" for translucent destinations */
    uint32_t oa = sa + da * ia / 255;
    if (oa == 0) return 0;
    uint32_t r = (((src >> 16) & 255) * sa + ((dst >> 16) & 255) * da * ia / 255) / oa;
    uint32_t g = (((src >> 8) & 255) * sa + ((dst >> 8) & 255) * da * ia / 255) / oa;
    uint32_t b = ((src & 255) * sa + (dst & 255) * da * ia / 255) / oa;
    return (oa << 24) | (r << 16) | (g << 8) | b;
}

static inline void plot(Target *tg, int x, int y, uint32_t c) {
    if (x < tg->x0 || x >= tg->x1 || y < tg->y0 || y >= tg->y1) return;
    uint32_t *p = &tg->px[y * tg->w + x];
    *p = (c >> 24) == 255 ? c : blend(*p, c);
}

static void hspan(Target *tg, int xa, int xb, int y, uint32_t c) { /* [xa, xb) */
    if (y < tg->y0 || y >= tg->y1) return;
    if (xa < tg->x0) xa = tg->x0;
    if (xb > tg->x1) xb = tg->x1;
    if (xa >= xb) return;
    uint32_t *p = &tg->px[y * tg->w + xa];
    int n = xb - xa;
    if ((c >> 24) == 255) {
        while (n--) *p++ = c;
    } else {
        while (n--) { *p = blend(*p, c); p++; }
    }
}

static void fill_rect(Target *tg, int x, int y, int w, int h, uint32_t c) {
    if (w <= 0 || h <= 0) return;
    int64_t xb = (int64_t)x + w, yb = (int64_t)y + h;
    int ya = y < tg->y0 ? tg->y0 : y;
    int ye = yb > tg->y1 ? tg->y1 : (int)yb;
    int xe = xb > tg->x1 ? tg->x1 : (int)xb;
    for (int yy = ya; yy < ye; yy++) hspan(tg, x, xe, yy, c);
}

static void line(Target *tg, int x1, int y1, int x2, int y2, uint32_t c, bool dotted) {
    if (!dotted && y1 == y2) {
        if (x1 > x2) { int t = x1; x1 = x2; x2 = t; }
        hspan(tg, x1, x2 + 1, y1, c);
        return;
    }
    int dx = abs(x2 - x1), sx = x1 < x2 ? 1 : -1;
    int dy = -abs(y2 - y1), sy = y1 < y2 ? 1 : -1;
    int err = dx + dy, n = 0;
    for (;;) {
        if (!dotted || ((n >> 1) & 1) == 0) plot(tg, x1, y1, c);
        n++;
        if (x1 == x2 && y1 == y2) break;
        int e2 = 2 * err;
        if (e2 >= dy) { err += dy; x1 += sx; }
        if (e2 <= dx) { err += dx; y1 += sy; }
        if (n > 100000) break;
    }
}

/* scanline polygon fill (even-odd), pixel centers */
static void fill_poly(Target *tg, const int *xs, const int *ys, int n, uint32_t c) {
    if (n < 3) return;
    int miny = ys[0], maxy = ys[0];
    for (int i = 1; i < n; i++) {
        if (ys[i] < miny) miny = ys[i];
        if (ys[i] > maxy) maxy = ys[i];
    }
    if (miny < tg->y0) miny = tg->y0;
    if (maxy >= tg->y1) maxy = tg->y1 - 1;
    double xbuf[64];
    double *xi = n <= 64 ? xbuf : (double *)malloc(sizeof(double) * n);
    for (int y = miny; y <= maxy; y++) {
        double cy = y + 0.5;
        int k = 0;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double ya = ys[i], yb = ys[j];
            if ((ya <= cy && yb > cy) || (yb <= cy && ya > cy)) {
                xi[k++] = xs[i] + (cy - ya) * (xs[j] - xs[i]) / (yb - ya);
            }
        }
        for (int i = 1; i < k; i++) {
            double v = xi[i];
            int j = i - 1;
            while (j >= 0 && xi[j] > v) { xi[j + 1] = xi[j]; j--; }
            xi[j + 1] = v;
        }
        for (int i = 0; i + 1 < k; i += 2) {
            int xa = (int)ceil(xi[i] - 0.5), xb = (int)ceil(xi[i + 1] - 0.5);
            hspan(tg, xa, xb, y, c);
        }
    }
    /* include edges so thin triangles remain visible (MIDP fills the outline too) */
    for (int i = 0, j = n - 1; i < n; j = i++) line(tg, xs[j], ys[j], xs[i], ys[i], c, false);
    if (xi != xbuf) free(xi);
}

static bool angle_in(double ang, int start, int arc) {
    if (arc >= 360 || arc <= -360) return true;
    if (arc < 0) { start += arc; arc = -arc; }
    double s = fmod((double)start, 360.0);
    if (s < 0) s += 360;
    double d = fmod(ang - s, 360.0);
    if (d < 0) d += 360;
    return d <= arc;
}

static void arc(Target *tg, int x, int y, int w, int h, int start, int arcang, uint32_t c, bool fill, bool dotted) {
    if (w < 0 || h < 0 || arcang == 0) return;
    if (fill) {
        if (w == 0 || h == 0) return;
        double rx = w / 2.0, ry = h / 2.0, cx = x + rx, cy = y + ry;
        for (int py = y; py < y + h; py++) {
            if (py < tg->y0 || py >= tg->y1) continue;
            double ny = (py + 0.5 - cy) / ry;
            for (int px = x; px < x + w; px++) {
                double nx = (px + 0.5 - cx) / rx;
                if (nx * nx + ny * ny > 1.0) continue;
                double ang = atan2(-ny, nx) * 180.0 / M_PI;
                if (angle_in(ang, start, arcang)) plot(tg, px, py, c);
            }
        }
        return;
    }
    double rx = w / 2.0, ry = h / 2.0, cx = x + rx, cy = y + ry;
    int s = start, e = arcang;
    if (e >= 360 || e <= -360) { s = 0; e = 360; }
    if (e < 0) { s += e; e = -e; }
    int steps = (int)((rx > ry ? rx : ry) * e / 30.0) + 8;
    int px = 0, py = 0;
    for (int i = 0; i <= steps; i++) {
        double t = (s + (double)e * i / steps) * M_PI / 180.0;
        int qx = (int)floor(cx + rx * cos(t) + 0.5), qy = (int)floor(cy - ry * sin(t) + 0.5);
        if (i == 0) plot(tg, qx, qy, c);
        else line(tg, px, py, qx, qy, c, dotted);
        px = qx;
        py = qy;
    }
}

static void round_rect(Target *tg, int x, int y, int w, int h, int aw, int ah, uint32_t c, bool fill, bool dotted) {
    if (w < 0 || h < 0) return;
    if (aw < 0) aw = -aw;
    if (ah < 0) ah = -ah;
    if (aw > w) aw = w;
    if (ah > h) ah = h;
    if (fill) {
        double rx = aw / 2.0, ry = ah / 2.0;
        for (int yy = 0; yy < h; yy++) {
            double inset = 0;
            double cyy = yy + 0.5;
            double dy = 0;
            if (cyy < ry) dy = ry - cyy;
            else if (cyy > h - ry) dy = cyy - (h - ry);
            if (dy > 0 && ry > 0) {
                double k = dy / ry;
                inset = rx - rx * sqrt(k < 1 ? 1 - k * k : 0);
            }
            int ia = (int)floor(inset + 0.5);
            hspan(tg, x + ia, x + w - ia, y + yy, c);
        }
        return;
    }
    int hw = aw / 2, hh = ah / 2;
    line(tg, x + hw, y, x + w - hw, y, c, dotted);
    line(tg, x + hw, y + h, x + w - hw, y + h, c, dotted);
    line(tg, x, y + hh, x, y + h - hh, c, dotted);
    line(tg, x + w, y + hh, x + w, y + h - hh, c, dotted);
    if (aw > 0 && ah > 0) {
        arc(tg, x, y, aw, ah, 90, 90, c, false, dotted);
        arc(tg, x + w - aw, y, aw, ah, 0, 90, c, false, dotted);
        arc(tg, x, y + h - ah, aw, ah, 180, 90, c, false, dotted);
        arc(tg, x + w - aw, y + h - ah, aw, ah, 270, 90, c, false, dotted);
    }
}

/* ---------------- natives: primitives ---------------- */
/* args: 0 dst,1 dw,2 dh,3 x0,4 y0,5 x1,6 y1,7 argb, ... */
NATIVE(Gfx_fillRect) {
    Target tg;
    if (!target_from(a, &tg)) return;
    fill_rect(&tg, a[8].i, a[9].i, a[10].i, a[11].i, (uint32_t)a[7].i);
}

NATIVE(Gfx_drawLine) {
    Target tg;
    if (!target_from(a, &tg)) return;
    line(&tg, a[9].i, a[10].i, a[11].i, a[12].i, (uint32_t)a[7].i, a[8].i != 0);
}

NATIVE(Gfx_arc) {
    Target tg;
    if (!target_from(a, &tg)) return;
    arc(&tg, a[10].i, a[11].i, a[12].i, a[13].i, a[14].i, a[15].i, (uint32_t)a[7].i, a[8].i != 0, a[9].i != 0);
}

NATIVE(Gfx_roundRect) {
    Target tg;
    if (!target_from(a, &tg)) return;
    round_rect(&tg, a[10].i, a[11].i, a[12].i, a[13].i, a[14].i, a[15].i, (uint32_t)a[7].i, a[8].i != 0, a[9].i != 0);
}

NATIVE(Gfx_fillTriangle) {
    Target tg;
    if (!target_from(a, &tg)) return;
    int xs[3] = {a[8].i, a[10].i, a[12].i}, ys[3] = {a[9].i, a[11].i, a[13].i};
    fill_poly(&tg, xs, ys, 3, (uint32_t)a[7].i);
}

/* 8 xs,9 xoff,10 ys,11 yoff,12 n,13 tx,14 ty,15 fill */
NATIVE(Gfx_polygon) {
    Target tg;
    if (!target_from(a, &tg)) return;
    Array *xa = (Array *)a[8].l, *ya = (Array *)a[10].l;
    int xo = a[9].i, yo = a[11].i, n = a[12].i;
    if (!xa || !ya) { throw_new(t, "java/lang/NullPointerException", NULL); return; }
    if (n < 0 || xo < 0 || yo < 0 || xo + n > xa->length || yo + n > ya->length) {
        throw_new(t, "java/lang/ArrayIndexOutOfBoundsException", NULL);
        return;
    }
    int *xs = (int *)malloc(sizeof(int) * (n + 1)), *ys = (int *)malloc(sizeof(int) * (n + 1));
    for (int i = 0; i < n; i++) {
        xs[i] = ARR_I(xa)[xo + i] + a[13].i;
        ys[i] = ARR_I(ya)[yo + i] + a[14].i;
    }
    if (a[15].i) fill_poly(&tg, xs, ys, n, (uint32_t)a[7].i);
    else
        for (int i = 0; i < n; i++) line(&tg, xs[i], ys[i], xs[(i + 1) % n], ys[(i + 1) % n], (uint32_t)a[7].i, false);
    free(xs);
    free(ys);
}

/* 7 src,8 sw,9 sh,10 sx,11 sy,12 w,13 h,14 transform,15 dx,16 dy */
NATIVE(Gfx_drawRegion) {
    Target tg;
    if (!target_from(a, &tg)) return;
    Array *sa = (Array *)a[7].l;
    if (!sa) return;
    const uint32_t *src = (const uint32_t *)ARR_I(sa);
    int sw = a[8].i, sx = a[10].i, sy = a[11].i, w = a[12].i, h = a[13].i, tr = a[14].i, dx = a[15].i, dy = a[16].i;
    if (w <= 0 || h <= 0) return;
    int a0, a1, a2, b0, b1, b2, W, H;
    switch (tr) {
    default:
    case 0: a0 = 0; a1 = 1; a2 = 0; b0 = 0; b1 = 0; b2 = 1; break;
    case 2: a0 = w - 1; a1 = -1; a2 = 0; b0 = 0; b1 = 0; b2 = 1; break;
    case 3: a0 = w - 1; a1 = -1; a2 = 0; b0 = h - 1; b1 = 0; b2 = -1; break;
    case 1: a0 = 0; a1 = 1; a2 = 0; b0 = h - 1; b1 = 0; b2 = -1; break;
    case 5: a0 = 0; a1 = 0; a2 = 1; b0 = h - 1; b1 = -1; b2 = 0; break;
    case 6: a0 = w - 1; a1 = 0; a2 = -1; b0 = 0; b1 = 1; b2 = 0; break;
    case 7: a0 = w - 1; a1 = 0; a2 = -1; b0 = h - 1; b1 = -1; b2 = 0; break;
    case 4: a0 = 0; a1 = 0; a2 = 1; b0 = 0; b1 = 1; b2 = 0; break;
    }
    if (tr >= 4 && tr <= 7) { W = h; H = w; }
    else { W = w; H = h; }
    int u0 = tg.x0 - dx, u1 = tg.x1 - dx, v0 = tg.y0 - dy, v1 = tg.y1 - dy;
    if (u0 < 0) u0 = 0;
    if (v0 < 0) v0 = 0;
    if (u1 > W) u1 = W;
    if (v1 > H) v1 = H;
    if (u0 >= u1 || v0 >= v1) return;
    intptr_t base = (intptr_t)(sy + b0) * sw + sx + a0;
    intptr_t du = a1 + (intptr_t)b1 * sw, dv = a2 + (intptr_t)b2 * sw;
    for (int v = v0; v < v1; v++) {
        uint32_t *dp = &tg.px[(dy + v) * tg.w + dx + u0];
        const uint32_t *sp = src + base + u0 * du + v * dv;
        if (du == 1) {
            for (int u = u0; u < u1; u++, dp++, sp++) {
                uint32_t c = *sp;
                uint32_t al = c >> 24;
                if (al == 255) *dp = c;
                else if (al) *dp = blend(*dp, c);
            }
        } else {
            for (int u = u0; u < u1; u++, dp++, sp += du) {
                uint32_t c = *sp;
                uint32_t al = c >> 24;
                if (al == 255) *dp = c;
                else if (al) *dp = blend(*dp, c);
            }
        }
    }
}

/* 7 rgb,8 off,9 scan,10 x,11 y,12 w,13 h,14 alpha */
NATIVE(Gfx_drawRGB) {
    Target tg;
    if (!target_from(a, &tg)) return;
    Array *ra = (Array *)a[7].l;
    if (!ra) { throw_new(t, "java/lang/NullPointerException", NULL); return; }
    int off = a[8].i, scan = a[9].i, x = a[10].i, y = a[11].i, w = a[12].i, h = a[13].i;
    bool alpha = a[14].i != 0;
    if (w <= 0 || h <= 0) return;
    int64_t lo = off, hi = off + (int64_t)(h - 1) * scan + (w - 1);
    if (scan < 0) { lo = off + (int64_t)(h - 1) * scan; hi = off + w - 1; }
    if (lo < 0 || hi >= ra->length) { throw_new(t, "java/lang/ArrayIndexOutOfBoundsException", NULL); return; }
    const uint32_t *rgb = (const uint32_t *)ARR_I(ra);
    for (int r = 0; r < h; r++) {
        int py = y + r;
        if (py < tg.y0 || py >= tg.y1) continue;
        for (int c = 0; c < w; c++) {
            int px = x + c;
            if (px < tg.x0 || px >= tg.x1) continue;
            uint32_t s = rgb[off + (int64_t)r * scan + c];
            uint32_t *d = &tg.px[py * tg.w + px];
            if (!alpha) *d = s | 0xFF000000u;
            else *d = blend(*d, s);
        }
    }
}

/* 7 sx,8 sy,9 w,10 h,11 dx,12 dy */
NATIVE(Gfx_copyArea) {
    Target tg;
    if (!target_from(a, &tg)) return;
    int sx = a[7].i, sy = a[8].i, w = a[9].i, h = a[10].i, dx = a[11].i, dy = a[12].i;
    if (w <= 0 || h <= 0) return;
    if (sx < 0 || sy < 0 || sx + w > tg.w || sy + h > tg.h) { throw_new(t, "java/lang/IllegalArgumentException", NULL); return; }
    uint32_t *tmp = (uint32_t *)malloc((size_t)w * h * 4);
    for (int r = 0; r < h; r++) memcpy(tmp + r * w, tg.px + (sy + r) * tg.w + sx, (size_t)w * 4);
    for (int r = 0; r < h; r++) {
        int py = dy + r;
        if (py < tg.y0 || py >= tg.y1) continue;
        for (int c = 0; c < w; c++) {
            int px = dx + c;
            if (px < tg.x0 || px >= tg.x1) continue;
            tg.px[py * tg.w + px] = tmp[r * w + c];
        }
    }
    free(tmp);
}

/* ---------------- fonts ---------------- */
typedef struct Glyph {
    int cp, size, style;
    int adv, h;
    uint8_t *alpha; /* adv+2 x h */
    int aw;
    struct Glyph *next;
} Glyph;

#define GLYPH_BUCKETS 2048
static J4I_TLS Glyph *glyphs[GLYPH_BUCKETS];
typedef struct { int size, style, ascent, descent; } FontMetric;
static J4I_TLS FontMetric metrics[32];
static J4I_TLS int n_metrics;

static FontMetric *font_metric(int size, int style) {
    style &= 3;
    for (int i = 0; i < n_metrics; i++)
        if (metrics[i].size == size && metrics[i].style == style) return &metrics[i];
    if (n_metrics == 32) n_metrics = 0;
    FontMetric *m = &metrics[n_metrics++];
    m->size = size;
    m->style = style;
    if (vm.cfg.host.font_metrics) vm.cfg.host.font_metrics(vm.cfg.host.user, size, style, &m->ascent, &m->descent);
    else {
        m->ascent = size * 4 / 5;
        m->descent = size - m->ascent;
    }
    if (m->ascent <= 0) m->ascent = 1;
    if (m->descent < 0) m->descent = 0;
    return m;
}

static Glyph *glyph_get(int cp, int size, int style) {
    style &= 3;
    unsigned hsh = ((unsigned)cp * 31u + (unsigned)size * 7u + (unsigned)style) & (GLYPH_BUCKETS - 1);
    for (Glyph *g = glyphs[hsh]; g; g = g->next)
        if (g->cp == cp && g->size == size && g->style == style) return g;
    FontMetric *fm = font_metric(size, style);
    Glyph *g = (Glyph *)calloc(1, sizeof(Glyph));
    g->cp = cp;
    g->size = size;
    g->style = style;
    g->h = fm->ascent + fm->descent;
    g->aw = size * 2 + 4;
    g->alpha = (uint8_t *)calloc((size_t)g->aw * g->h, 1);
    if (vm.cfg.host.glyph) {
        g->adv = vm.cfg.host.glyph(vm.cfg.host.user, cp, size, style, g->alpha, g->aw, g->aw, g->h, fm->ascent);
    } else {
        g->adv = cp == ' ' ? size / 3 : size / 2;
        if (cp != ' ')
            for (int y = 2; y < fm->ascent; y++)
                for (int x = 1; x < g->adv - 1; x++)
                    if (y == 2 || y == fm->ascent - 1 || x == 1 || x == g->adv - 2) g->alpha[y * g->aw + x] = 255;
    }
    if (g->adv < 0) g->adv = 0;
    if (g->adv > g->aw) g->adv = g->aw;
    if (vm.cfg.font_no_aa) /* crisp phone-style text */
        for (int i = 0; i < g->aw * g->h; i++) g->alpha[i] = g->alpha[i] >= 110 ? 255 : 0;
    g->next = glyphs[hsh];
    glyphs[hsh] = g;
    return g;
}

void font_cache_free(void) {
    for (int i = 0; i < GLYPH_BUCKETS; i++) {
        Glyph *g = glyphs[i];
        while (g) {
            Glyph *n = g->next;
            free(g->alpha);
            free(g);
            g = n;
        }
        glyphs[i] = NULL;
    }
    n_metrics = 0;
}

static int text_width(const uint16_t *s, int n, int size, int style) {
    int w = 0;
    for (int i = 0; i < n; i++) w += glyph_get(s[i], size, style)->adv;
    return w;
}

/* 7 argb,8 str,9 off,10 len,11 x,12 y,13 size,14 style */
NATIVE(Gfx_drawString) {
    Target tg;
    if (!target_from(a, &tg)) return;
    Object *str = a[8].l;
    if (!str) { throw_new(t, "java/lang/NullPointerException", NULL); return; }
    int n;
    uint16_t *s = string_chars(str, &n);
    int off = a[9].i, len = a[10].i;
    if (off < 0 || len < 0 || off + len > n) { throw_new(t, "java/lang/StringIndexOutOfBoundsException", NULL); return; }
    uint32_t c = (uint32_t)a[7].i;
    int x = a[11].i, y = a[12].i, size = a[13].i, style = a[14].i;
    uint32_t rgb = c & 0xFFFFFF, ca = c >> 24;
    int x_start = x;
    for (int i = 0; i < len; i++) {
        Glyph *g = glyph_get(s[off + i], size, style);
        if (x >= tg.x1) break;
        if (x + g->aw > tg.x0) {
            for (int gy = 0; gy < g->h; gy++) {
                int py = y + gy;
                if (py < tg.y0 || py >= tg.y1) continue;
                const uint8_t *row = g->alpha + gy * g->aw;
                for (int gx = 0; gx < g->aw; gx++) {
                    uint32_t al = row[gx];
                    if (!al) continue;
                    int px = x + gx;
                    if (px < tg.x0 || px >= tg.x1) continue;
                    al = al * ca / 255;
                    uint32_t *d = &tg.px[py * tg.w + px];
                    *d = blend(*d, (al << 24) | rgb);
                }
            }
        }
        x += g->adv;
    }
    if (style & J4I_FONT_UNDERLINE) {
        FontMetric *fm = font_metric(size, style);
        hspan(&tg, x_start, x, y + fm->ascent + 1, c);
    }
}

NATIVE(Gfx_stringWidth) {
    Object *str = a[0].l;
    if (!str) { throw_new(t, "java/lang/NullPointerException", NULL); return; }
    int n;
    uint16_t *s = string_chars(str, &n);
    int off = a[1].i, len = a[2].i;
    if (off < 0 || len < 0 || off + len > n) { throw_new(t, "java/lang/StringIndexOutOfBoundsException", NULL); return; }
    ret->i = text_width(s + off, len, a[3].i, a[4].i);
}

NATIVE(Gfx_charsWidth) {
    Array *c = (Array *)a[0].l;
    if (!c) { throw_new(t, "java/lang/NullPointerException", NULL); return; }
    int off = a[1].i, len = a[2].i;
    if (off < 0 || len < 0 || off + len > c->length) { throw_new(t, "java/lang/ArrayIndexOutOfBoundsException", NULL); return; }
    ret->i = text_width(ARR_C(c) + off, len, a[3].i, a[4].i);
}

NATIVE(Gfx_fontInfo) {
    (void)t;
    (void)ret;
    FontMetric *m = font_metric(a[0].i, a[1].i);
    Array *out = (Array *)a[2].l;
    ARR_I(out)[0] = m->ascent;
    ARR_I(out)[1] = m->descent;
    ARR_I(out)[2] = m->ascent + m->descent;
}

/* ---------------- image decoding ---------------- */
NATIVE(Gfx_decode) {
    Array *data = (Array *)a[0].l;
    int off = a[1].i, len = a[2].i;
    Array *info = (Array *)a[3].l;
    if (!data) { throw_new(t, "java/lang/NullPointerException", NULL); return; }
    if (off < 0 || len < 0 || off + len > data->length) { throw_new(t, "java/lang/ArrayIndexOutOfBoundsException", NULL); return; }
    int w = 0, h = 0;
    uint32_t *px = png_decode((const uint8_t *)ARR_B(data) + off, len, &w, &h);
    if (!px && vm.cfg.host.decode_image) {
        if (!vm.cfg.host.decode_image(vm.cfg.host.user, (const uint8_t *)ARR_B(data) + off, len, &px, &w, &h)) px = NULL;
    }
    if (!px) return;
    Array *out = alloc_prim_array(t, 'I', w * h);
    if (out) {
        memcpy(ARR_I(out), px, (size_t)w * h * 4);
        bool has_alpha = false;
        for (int i = 0; i < w * h; i++)
            if ((px[i] >> 24) != 255) { has_alpha = true; break; }
        ARR_I(info)[0] = w;
        ARR_I(info)[1] = h;
        ARR_I(info)[2] = has_alpha;
    }
    free(px);
    ret->l = (Object *)out;
}

void gfx_register(void) {
#define R(n, d, f) native_register("j4i/Gfx", n, d, f)
    R("fillRect", "([IIIIIIIIIIII)V", Gfx_fillRect);
    R("drawLine", "([IIIIIIIIZIIII)V", Gfx_drawLine);
    R("arc", "([IIIIIIIIZZIIIIII)V", Gfx_arc);
    R("roundRect", "([IIIIIIIIZZIIIIII)V", Gfx_roundRect);
    R("fillTriangle", "([IIIIIIIIIIIIII)V", Gfx_fillTriangle);
    R("polygon", "([IIIIIIII[II[IIIIIZ)V", Gfx_polygon);
    R("drawRegion", "([IIIIIII[IIIIIIIIII)V", Gfx_drawRegion);
    R("drawRGB", "([IIIIIII[IIIIIIIZ)V", Gfx_drawRGB);
    R("copyArea", "([IIIIIIIIIIIII)V", Gfx_copyArea);
    R("drawString", "([IIIIIIIILjava/lang/String;IIIIII)V", Gfx_drawString);
    R("stringWidth", "(Ljava/lang/String;IIII)I", Gfx_stringWidth);
    R("charsWidth", "([CIIII)I", Gfx_charsWidth);
    R("fontInfo", "(II[I)V", Gfx_fontInfo);
    R("decode", "([BII[I)[I", Gfx_decode);
#undef R
}
