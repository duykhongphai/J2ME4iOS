/* Software renderer behind javax.microedition.m3g (JSR-184), class j4i/M3G.
 *
 * Java does the scene graph, animation and state collection; this file does the
 * fixed-function pipeline for one submesh: vertex transform, OpenGL ES 1.x style
 * per-vertex lighting, fog, homogeneous clipping, and perspective-correct
 * rasterization with depth test, two texture units, alpha test and blending. */
#include "vm.h"
#include <math.h>
#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

#define NATIVE(name) static void name(Thread *t, Value *a, Value *ret)

/* ---- integer state (ist[]) ---- */
enum {
    I_CLIP_X0, I_CLIP_Y0, I_CLIP_X1, I_CLIP_Y1,
    I_VP_X, I_VP_Y, I_VP_W, I_VP_H,
    I_VCOUNT, I_COL_COMPS, I_DEFAULT_COLOR,
    I_HAS_MATERIAL, I_MAT_AMBIENT, I_MAT_DIFFUSE, I_MAT_EMISSIVE, I_MAT_SPECULAR, I_MAT_TRACKING,
    I_CULLING, I_WINDING, I_SHADING, I_TWO_SIDED, I_LOCAL_CAMERA,
    I_BLENDING, I_DEPTH_TEST, I_DEPTH_WRITE, I_COLOR_WRITE, I_ALPHA_WRITE,
    I_FOG_MODE, I_FOG_COLOR, I_NUM_LIGHTS, I_OPAQUE_TARGET,
    I_TEX0, /* 8 ints per unit: w, h, format bits(1 rgb, 2 alpha), wrapS, wrapT, filter, func, blendColor */
    I_TEX1 = I_TEX0 + 8,
    I_TC0_COMPS = I_TEX1 + 8, I_TC1_COMPS,
    I_COUNT
};

/* ---- float state (fst[]) ---- */
enum {
    F_MV = 0, F_PROJ = 16,
    F_POS_SCALE = 32, F_POS_BIAS = 33,
    F_NRM_SCALE = 36, F_ALPHA_THRESHOLD, F_DEPTH_FACTOR, F_DEPTH_UNITS,
    F_DEPTH_NEAR, F_DEPTH_FAR, F_SHININESS, F_FOG_NEAR, F_FOG_FAR, F_FOG_DENSITY, F_ALPHA_FACTOR,
    F_TC0 = 48,  /* scale, bias[3], matrix[16] */
    F_TC1 = 68,
    F_LIGHTS = 88, /* 16 floats per light */
    F_COUNT = F_LIGHTS + 16 * 8
};

/* M3G constants */
#define CULL_BACK 160
#define CULL_FRONT 161
#define SHADE_FLAT 164
#define WINDING_CCW 168
#define BLEND_ALPHA 64
#define BLEND_ALPHA_ADD 65
#define BLEND_MODULATE 66
#define BLEND_MODULATE_X2 67
#define BLEND_REPLACE 68
#define BLEND_MC_HALF 200
#define BLEND_MC_ADD 201
#define BLEND_MC_SUB 202
#define FOG_EXP 80
#define FOG_LINEAR 81
#define LIGHT_AMBIENT 128
#define LIGHT_DIRECTIONAL 129
#define LIGHT_OMNI 130
#define LIGHT_SPOT 131
#define FILTER_LINEAR 209
#define FUNC_ADD 224
#define FUNC_BLEND 225
#define FUNC_DECAL 226
#define FUNC_MODULATE 227
#define FUNC_REPLACE 228
#define WRAP_CLAMP 240
#define BG_REPEAT 33

typedef struct {
    float x, y, z, w;     /* clip space */
    float c[4];           /* color (front) */
    float cb[4];          /* color (back, two-sided lighting) */
    float tc[2][2];       /* texture coordinates (s,t) per unit */
    float fog;            /* 1 = no fog */
} Vtx;

typedef struct {
    const uint32_t *px;
    int w, h;
    bool rgb, alpha;
    int wrap_s, wrap_t, linear, func;
    float bc[3];
} Tex;

typedef struct {
    uint32_t *dst;
    float *depth;
    int tw, th;
    int sx0, sy0, sx1, sy1; /* scissor */
    float vx, vy, vw, vh, dnear, dfar;
    int blending, depth_test, depth_write, color_write, alpha_write, opaque;
    float alpha_threshold, depth_factor, depth_units;
    bool flat;
    int ntex;
    Tex tex[2];
    float fog_color[3];
    int fog;
} Raster;

static inline float clampf(float v, float lo, float hi) { return v < lo ? lo : (v > hi ? hi : v); }

static void mat_mul_vec(const float *m, const float *v, float *o) { /* row-major m * column v */
    for (int r = 0; r < 4; r++) o[r] = m[r * 4] * v[0] + m[r * 4 + 1] * v[1] + m[r * 4 + 2] * v[2] + m[r * 4 + 3] * v[3];
}

static void argb_to_f(uint32_t c, float *o) {
    o[0] = ((c >> 16) & 255) / 255.0f;
    o[1] = ((c >> 8) & 255) / 255.0f;
    o[2] = (c & 255) / 255.0f;
    o[3] = (c >> 24) / 255.0f;
}

/* inverse transpose of the upper 3x3 (for normals) */
static void normal_matrix(const float *m, float *n) {
    float a = m[0], b = m[1], c = m[2], d = m[4], e = m[5], f = m[6], g = m[8], h = m[9], i = m[10];
    float A = e * i - f * h, B = -(d * i - f * g), C = d * h - e * g;
    float D = -(b * i - c * h), E = a * i - c * g, F = -(a * h - b * g);
    float G = b * f - c * e, H = -(a * f - c * d), I = a * e - b * d;
    float det = a * A + b * B + c * C;
    if (fabsf(det) < 1e-20f) det = 1e-20f;
    float inv = 1.0f / det;
    /* inverse = adj^T / det; inverse transpose = adj / det (cofactor matrix) */
    n[0] = A * inv; n[1] = B * inv; n[2] = C * inv;
    n[3] = D * inv; n[4] = E * inv; n[5] = F * inv;
    n[6] = G * inv; n[7] = H * inv; n[8] = I * inv;
}

/* ---------------- texturing ---------------- */
static inline int wrap_coord(int i, int n, int mode) {
    if (mode == WRAP_CLAMP) return i < 0 ? 0 : (i >= n ? n - 1 : i);
    i %= n;
    return i < 0 ? i + n : i;
}

static void tex_sample(const Tex *tx, float s, float tt, float *o) {
    int w = tx->w, h = tx->h;
    if (!tx->linear) {
        int x = (int)floorf(s * w), y = (int)floorf(tt * h);
        argb_to_f(tx->px[wrap_coord(y, h, tx->wrap_t) * w + wrap_coord(x, w, tx->wrap_s)], o);
        return;
    }
    float fx = s * w - 0.5f, fy = tt * h - 0.5f;
    int x0 = (int)floorf(fx), y0 = (int)floorf(fy);
    float ax = fx - x0, ay = fy - y0;
    int xa = wrap_coord(x0, w, tx->wrap_s), xb = wrap_coord(x0 + 1, w, tx->wrap_s);
    int ya = wrap_coord(y0, h, tx->wrap_t), yb = wrap_coord(y0 + 1, h, tx->wrap_t);
    float c00[4], c10[4], c01[4], c11[4];
    argb_to_f(tx->px[ya * w + xa], c00);
    argb_to_f(tx->px[ya * w + xb], c10);
    argb_to_f(tx->px[yb * w + xa], c01);
    argb_to_f(tx->px[yb * w + xb], c11);
    for (int k = 0; k < 4; k++) {
        float top = c00[k] + (c10[k] - c00[k]) * ax, bot = c01[k] + (c11[k] - c01[k]) * ax;
        o[k] = top + (bot - top) * ay;
    }
}

static void tex_apply(const Tex *tx, const float *ct, float *cf) {
    switch (tx->func) {
    case FUNC_REPLACE:
        if (tx->rgb) { cf[0] = ct[0]; cf[1] = ct[1]; cf[2] = ct[2]; }
        if (tx->alpha) cf[3] = ct[3];
        break;
    case FUNC_DECAL:
        if (tx->rgb) {
            float at = tx->alpha ? ct[3] : 1.0f;
            for (int k = 0; k < 3; k++) cf[k] = cf[k] * (1 - at) + ct[k] * at;
        }
        break;
    case FUNC_BLEND:
        if (tx->rgb)
            for (int k = 0; k < 3; k++) cf[k] = cf[k] * (1 - ct[k]) + tx->bc[k] * ct[k];
        if (tx->alpha) cf[3] *= ct[3];
        break;
    case FUNC_ADD:
        if (tx->rgb)
            for (int k = 0; k < 3; k++) cf[k] = clampf(cf[k] + ct[k], 0, 1);
        if (tx->alpha) cf[3] *= ct[3];
        break;
    default: /* MODULATE */
        if (tx->rgb)
            for (int k = 0; k < 3; k++) cf[k] *= ct[k];
        if (tx->alpha) cf[3] *= ct[3];
        break;
    }
}

/* ---------------- pixel write ---------------- */
static inline void write_pixel(Raster *r, int idx, float *c) {
    uint32_t d = r->dst[idx];
    float dr = ((d >> 16) & 255) / 255.0f, dg = ((d >> 8) & 255) / 255.0f, db = (d & 255) / 255.0f, da = (d >> 24) / 255.0f;
    float o[4];
    float sa = c[3];
    switch (r->blending) {
    case BLEND_ALPHA:
        o[0] = c[0] * sa + dr * (1 - sa); o[1] = c[1] * sa + dg * (1 - sa); o[2] = c[2] * sa + db * (1 - sa); o[3] = sa * sa + da * (1 - sa);
        break;
    case BLEND_ALPHA_ADD:
        o[0] = dr + c[0] * sa; o[1] = dg + c[1] * sa; o[2] = db + c[2] * sa; o[3] = da + sa * sa;
        break;
    case BLEND_MODULATE:
        o[0] = c[0] * dr; o[1] = c[1] * dg; o[2] = c[2] * db; o[3] = sa * da;
        break;
    case BLEND_MODULATE_X2:
        o[0] = 2 * c[0] * dr; o[1] = 2 * c[1] * dg; o[2] = 2 * c[2] * db; o[3] = 2 * sa * da;
        break;
    case BLEND_MC_HALF: /* Mascot Capsule semi-transparent modes */
        o[0] = 0.5f * (c[0] + dr); o[1] = 0.5f * (c[1] + dg); o[2] = 0.5f * (c[2] + db); o[3] = da;
        break;
    case BLEND_MC_ADD:
        o[0] = c[0] + dr; o[1] = c[1] + dg; o[2] = c[2] + db; o[3] = da;
        break;
    case BLEND_MC_SUB:
        o[0] = dr - c[0]; o[1] = dg - c[1]; o[2] = db - c[2]; o[3] = da;
        break;
    default:
        o[0] = c[0]; o[1] = c[1]; o[2] = c[2]; o[3] = sa;
        break;
    }
    uint32_t out = d;
    if (r->color_write) {
        int ir = (int)(clampf(o[0], 0, 1) * 255 + 0.5f), ig = (int)(clampf(o[1], 0, 1) * 255 + 0.5f), ib = (int)(clampf(o[2], 0, 1) * 255 + 0.5f);
        out = (out & 0xFF000000u) | ((uint32_t)ir << 16) | ((uint32_t)ig << 8) | (uint32_t)ib;
    }
    if (r->opaque) out |= 0xFF000000u;
    else if (r->alpha_write) out = (out & 0x00FFFFFFu) | ((uint32_t)(clampf(o[3], 0, 1) * 255 + 0.5f) << 24);
    r->dst[idx] = out;
}

/* ---------------- triangle rasterization ---------------- */
typedef struct {
    float x, y, z, iw;
    float c[4], tc[2][2], fog;
} SV; /* screen-space vertex */

static void raster_tri(Raster *r, const SV *v0, const SV *v1, const SV *v2, const float *flat_c) {
    float area = (v1->x - v0->x) * (v2->y - v0->y) - (v2->x - v0->x) * (v1->y - v0->y);
    if (fabsf(area) < 1e-6f) return;
    if (area < 0) { /* make it counter-clockwise in screen space (y down) for the edge setup */
        const SV *tmp = v1;
        v1 = v2;
        v2 = tmp;
        area = -area;
    }
    float minx = fminf(v0->x, fminf(v1->x, v2->x)), maxx = fmaxf(v0->x, fmaxf(v1->x, v2->x));
    float miny = fminf(v0->y, fminf(v1->y, v2->y)), maxy = fmaxf(v0->y, fmaxf(v1->y, v2->y));
    int x0 = (int)floorf(minx), x1 = (int)ceilf(maxx), y0 = (int)floorf(miny), y1 = (int)ceilf(maxy);
    if (x0 < r->sx0) x0 = r->sx0;
    if (y0 < r->sy0) y0 = r->sy0;
    if (x1 > r->sx1) x1 = r->sx1;
    if (y1 > r->sy1) y1 = r->sy1;
    if (x0 >= x1 || y0 >= y1) return;

    /* edge functions E_i(p) for edges opposite to vertex i */
    float e0a = v1->y - v2->y, e0b = v2->x - v1->x, e0c = v1->x * v2->y - v2->x * v1->y;
    float e1a = v2->y - v0->y, e1b = v0->x - v2->x, e1c = v2->x * v0->y - v0->x * v2->y;
    float e2a = v0->y - v1->y, e2b = v1->x - v0->x, e2c = v0->x * v1->y - v1->x * v0->y;
    /* the sign convention here gives positive values inside for our winding */
    float sgn = (e0a * v0->x + e0b * v0->y + e0c) >= 0 ? 1.0f : -1.0f;
    e0a *= sgn; e0b *= sgn; e0c *= sgn;
    e1a *= sgn; e1b *= sgn; e1c *= sgn;
    e2a *= sgn; e2b *= sgn; e2c *= sgn;
    float inv_area = 1.0f / (e0a * v0->x + e0b * v0->y + e0c);
    /* top-left rule bias */
    float b0 = (e0a > 0 || (e0a == 0 && e0b > 0)) ? 0 : -1e-7f;
    float b1 = (e1a > 0 || (e1a == 0 && e1b > 0)) ? 0 : -1e-7f;
    float b2 = (e2a > 0 || (e2a == 0 && e2b > 0)) ? 0 : -1e-7f;

    /* depth slope for polygon offset */
    float zoff = 0;
    if (r->depth_factor != 0 || r->depth_units != 0) {
        float dzdx = fabsf(((v1->z - v0->z) * (v2->y - v0->y) - (v2->z - v0->z) * (v1->y - v0->y)) / area);
        float dzdy = fabsf(((v2->z - v0->z) * (v1->x - v0->x) - (v1->z - v0->z) * (v2->x - v0->x)) / area);
        zoff = r->depth_factor * fmaxf(dzdx, dzdy) + r->depth_units * (1.0f / 65536.0f);
    }

    const bool tex0 = r->ntex > 0, tex1 = r->ntex > 1;
    for (int y = y0; y < y1; y++) {
        float py = y + 0.5f;
        float px = x0 + 0.5f;
        float w0 = e0a * px + e0b * py + e0c;
        float w1 = e1a * px + e1b * py + e1c;
        float w2 = e2a * px + e2b * py + e2c;
        int row = y * r->tw;
        for (int x = x0; x < x1; x++, w0 += e0a, w1 += e1a, w2 += e2a) {
            if (w0 + b0 < 0 || w1 + b1 < 0 || w2 + b2 < 0) continue;
            float l0 = w0 * inv_area, l1 = w1 * inv_area, l2 = w2 * inv_area;
            float z = l0 * v0->z + l1 * v1->z + l2 * v2->z + zoff;
            int idx = row + x;
            if (r->depth_test && z > r->depth[idx]) continue;
            float iw = l0 * v0->iw + l1 * v1->iw + l2 * v2->iw;
            float pw = iw != 0 ? 1.0f / iw : 0;
            float p0 = l0 * v0->iw * pw, p1 = l1 * v1->iw * pw, p2 = l2 * v2->iw * pw;
            float c[4];
            if (flat_c) {
                c[0] = flat_c[0]; c[1] = flat_c[1]; c[2] = flat_c[2]; c[3] = flat_c[3];
            } else {
                for (int k = 0; k < 4; k++) c[k] = p0 * v0->c[k] + p1 * v1->c[k] + p2 * v2->c[k];
            }
            if (tex0) {
                float ct[4];
                tex_sample(&r->tex[0], p0 * v0->tc[0][0] + p1 * v1->tc[0][0] + p2 * v2->tc[0][0],
                           p0 * v0->tc[0][1] + p1 * v1->tc[0][1] + p2 * v2->tc[0][1], ct);
                tex_apply(&r->tex[0], ct, c);
                if (tex1) {
                    tex_sample(&r->tex[1], p0 * v0->tc[1][0] + p1 * v1->tc[1][0] + p2 * v2->tc[1][0],
                               p0 * v0->tc[1][1] + p1 * v1->tc[1][1] + p2 * v2->tc[1][1], ct);
                    tex_apply(&r->tex[1], ct, c);
                }
            }
            if (r->fog) {
                float f = p0 * v0->fog + p1 * v1->fog + p2 * v2->fog;
                for (int k = 0; k < 3; k++) c[k] = c[k] * f + r->fog_color[k] * (1 - f);
            }
            if (c[3] < r->alpha_threshold) continue;
            if (r->depth_write && r->depth_test) r->depth[idx] = z;
            write_pixel(r, idx, c);
        }
    }
}

/* ---------------- clipping ---------------- */
#define MAXPOLY 16

static void lerp_vtx(const Vtx *a, const Vtx *b, float t, Vtx *o) {
    o->x = a->x + (b->x - a->x) * t;
    o->y = a->y + (b->y - a->y) * t;
    o->z = a->z + (b->z - a->z) * t;
    o->w = a->w + (b->w - a->w) * t;
    for (int k = 0; k < 4; k++) o->c[k] = a->c[k] + (b->c[k] - a->c[k]) * t;
    for (int u = 0; u < 2; u++)
        for (int k = 0; k < 2; k++) o->tc[u][k] = a->tc[u][k] + (b->tc[u][k] - a->tc[u][k]) * t;
    o->fog = a->fog + (b->fog - a->fog) * t;
}

static float plane_dist(const Vtx *v, int p) {
    switch (p) {
    case 0: return v->w + v->x;
    case 1: return v->w - v->x;
    case 2: return v->w + v->y;
    case 3: return v->w - v->y;
    case 4: return v->w + v->z;
    default: return v->w - v->z;
    }
}

static int clip_poly(Vtx *in, int n, Vtx *tmp) {
    Vtx *src = in, *dst = tmp;
    for (int p = 0; p < 6 && n > 0; p++) {
        int m = 0;
        for (int i = 0; i < n; i++) {
            const Vtx *a = &src[i], *b = &src[(i + 1) % n];
            float da = plane_dist(a, p), db = plane_dist(b, p);
            if (da >= 0 && m < MAXPOLY) dst[m++] = *a;
            if ((da >= 0) != (db >= 0) && m < MAXPOLY) lerp_vtx(a, b, da / (da - db), &dst[m++]);
        }
        n = m;
        Vtx *s = src;
        src = dst;
        dst = s;
    }
    if (src != in)
        for (int i = 0; i < n; i++) in[i] = src[i];
    return n;
}

static void to_screen(const Raster *r, const Vtx *v, SV *s) {
    float iw = v->w != 0 ? 1.0f / v->w : 0;
    s->x = r->vx + (v->x * iw + 1) * 0.5f * r->vw;
    s->y = r->vy + (1 - v->y * iw) * 0.5f * r->vh;
    s->z = r->dnear + (v->z * iw + 1) * 0.5f * (r->dfar - r->dnear);
    s->iw = iw;
    for (int k = 0; k < 4; k++) s->c[k] = v->c[k];
    for (int u = 0; u < 2; u++)
        for (int k = 0; k < 2; k++) s->tc[u][k] = v->tc[u][k];
    s->fog = v->fog;
}

static void draw_clipped(Raster *r, const Vtx *a, const Vtx *b, const Vtx *c, bool back, const float *flat_c) {
    Vtx poly[MAXPOLY], tmp[MAXPOLY];
    poly[0] = *a;
    poly[1] = *b;
    poly[2] = *c;
    if (back)
        for (int i = 0; i < 3; i++) memcpy(poly[i].c, poly[i].cb, sizeof poly[i].c);
    bool inside = true;
    for (int i = 0; i < 3 && inside; i++)
        for (int p = 0; p < 6; p++)
            if (plane_dist(&poly[i], p) < 0) { inside = false; break; }
    int n = inside ? 3 : clip_poly(poly, 3, tmp);
    if (n < 3) return;
    SV sv[MAXPOLY];
    for (int i = 0; i < n; i++) to_screen(r, &poly[i], &sv[i]);
    for (int i = 1; i + 1 < n; i++) raster_tri(r, &sv[0], &sv[i], &sv[i + 1], flat_c);
}

/* ---------------- vertex processing ---------------- */
static void light_vertex(const int *ist, const float *fst, const float *ev, const float *n, const float *vcol, float *out) {
    float amb_m[4], dif_m[4], emi[4], spe[4];
    argb_to_f((uint32_t)ist[I_MAT_AMBIENT], amb_m);
    argb_to_f((uint32_t)ist[I_MAT_DIFFUSE], dif_m);
    argb_to_f((uint32_t)ist[I_MAT_EMISSIVE], emi);
    argb_to_f((uint32_t)ist[I_MAT_SPECULAR], spe);
    if (ist[I_MAT_TRACKING] && vcol) {
        memcpy(amb_m, vcol, sizeof amb_m);
        memcpy(dif_m, vcol, sizeof dif_m);
    }
    float acc[3] = {emi[0], emi[1], emi[2]};
    float shin = fst[F_SHININESS];
    int nl = ist[I_NUM_LIGHTS];
    for (int i = 0; i < nl && i < 8; i++) {
        const float *L = &fst[F_LIGHTS + i * 16];
        int type = (int)L[0];
        const float *lc = &L[1];
        if (type == LIGHT_AMBIENT) {
            for (int k = 0; k < 3; k++) acc[k] += amb_m[k] * lc[k];
            continue;
        }
        float ldir[3], att = 1, spot = 1;
        if (type == LIGHT_DIRECTIONAL) {
            ldir[0] = -L[7]; ldir[1] = -L[8]; ldir[2] = -L[9];
        } else {
            ldir[0] = L[4] - ev[0]; ldir[1] = L[5] - ev[1]; ldir[2] = L[6] - ev[2];
            float d = sqrtf(ldir[0] * ldir[0] + ldir[1] * ldir[1] + ldir[2] * ldir[2]);
            if (d > 0) { ldir[0] /= d; ldir[1] /= d; ldir[2] /= d; }
            float den = L[10] + L[11] * d + L[12] * d * d;
            att = den > 0 ? 1.0f / den : 1.0f;
            if (type == LIGHT_SPOT) {
                float cosang = -(ldir[0] * L[7] + ldir[1] * L[8] + ldir[2] * L[9]);
                float cutoff = cosf(L[13] * (float)M_PI / 180.0f);
                spot = cosang < cutoff ? 0 : powf(fmaxf(cosang, 0), L[14]);
            }
        }
        float f = att * spot;
        if (f <= 0) continue;
        float ndl = n[0] * ldir[0] + n[1] * ldir[1] + n[2] * ldir[2];
        if (ndl <= 0) continue;
        for (int k = 0; k < 3; k++) acc[k] += f * dif_m[k] * lc[k] * ndl;
        float h[3];
        if (ist[I_LOCAL_CAMERA]) {
            float vl = sqrtf(ev[0] * ev[0] + ev[1] * ev[1] + ev[2] * ev[2]);
            if (vl <= 0) vl = 1;
            h[0] = ldir[0] - ev[0] / vl; h[1] = ldir[1] - ev[1] / vl; h[2] = ldir[2] - ev[2] / vl;
        } else {
            h[0] = ldir[0]; h[1] = ldir[1]; h[2] = ldir[2] + 1;
        }
        float hl = sqrtf(h[0] * h[0] + h[1] * h[1] + h[2] * h[2]);
        if (hl > 0) {
            float ndh = (n[0] * h[0] + n[1] * h[1] + n[2] * h[2]) / hl;
            if (ndh > 0) {
                float s = f * (shin > 0 ? powf(ndh, shin) : 1.0f);
                for (int k = 0; k < 3; k++) acc[k] += s * spe[k] * lc[k];
            }
        }
    }
    out[0] = clampf(acc[0], 0, 1);
    out[1] = clampf(acc[1], 0, 1);
    out[2] = clampf(acc[2], 0, 1);
    out[3] = dif_m[3];
}

static bool tex_setup(Tex *tx, const int *ist, int base, Array *pix) {
    if (!pix) return false;
    tx->w = ist[base];
    tx->h = ist[base + 1];
    if (tx->w <= 0 || tx->h <= 0 || pix->length < tx->w * tx->h) return false;
    tx->px = (const uint32_t *)ARR_I(pix);
    tx->rgb = (ist[base + 2] & 1) != 0;
    tx->alpha = (ist[base + 2] & 2) != 0;
    tx->wrap_s = ist[base + 3];
    tx->wrap_t = ist[base + 4];
    tx->linear = ist[base + 5] == FILTER_LINEAR;
    tx->func = ist[base + 6];
    uint32_t bc = (uint32_t)ist[base + 7];
    tx->bc[0] = ((bc >> 16) & 255) / 255.0f;
    tx->bc[1] = ((bc >> 8) & 255) / 255.0f;
    tx->bc[2] = (bc & 255) / 255.0f;
    return true;
}

static bool raster_setup(Raster *r, Array *target, Array *depth, int tw, int th, const int *ist, const float *fst) {
    if (!target || !depth || tw <= 0 || th <= 0 || target->length < tw * th || depth->length < tw * th) return false;
    r->dst = (uint32_t *)ARR_I(target);
    r->depth = ARR_F(depth);
    r->tw = tw;
    r->th = th;
    r->vx = (float)ist[I_VP_X];
    r->vy = (float)ist[I_VP_Y];
    r->vw = (float)ist[I_VP_W];
    r->vh = (float)ist[I_VP_H];
    r->sx0 = ist[I_CLIP_X0] > ist[I_VP_X] ? ist[I_CLIP_X0] : ist[I_VP_X];
    r->sy0 = ist[I_CLIP_Y0] > ist[I_VP_Y] ? ist[I_CLIP_Y0] : ist[I_VP_Y];
    r->sx1 = ist[I_CLIP_X1] < ist[I_VP_X] + ist[I_VP_W] ? ist[I_CLIP_X1] : ist[I_VP_X] + ist[I_VP_W];
    r->sy1 = ist[I_CLIP_Y1] < ist[I_VP_Y] + ist[I_VP_H] ? ist[I_CLIP_Y1] : ist[I_VP_Y] + ist[I_VP_H];
    if (r->sx0 < 0) r->sx0 = 0;
    if (r->sy0 < 0) r->sy0 = 0;
    if (r->sx1 > tw) r->sx1 = tw;
    if (r->sy1 > th) r->sy1 = th;
    r->dnear = fst ? fst[F_DEPTH_NEAR] : 0;
    r->dfar = fst ? fst[F_DEPTH_FAR] : 1;
    return r->sx0 < r->sx1 && r->sy0 < r->sy1;
}

/* drawTriangles(int[] target, float[] depth, int tw, int th, int[] ist, float[] fst,
 *               short[] pos, float[] posF, short[] nrm, float[] nrmF, short[] col,
 *               short[] tc0, int[] tex0, short[] tc1, int[] tex1, int[] idx, int nidx) */
NATIVE(M3G_drawTriangles) {
    (void)ret;
    Array *target = (Array *)a[0].l, *depth = (Array *)a[1].l;
    int tw = a[2].i, th = a[3].i;
    Array *ista = (Array *)a[4].l, *fsta = (Array *)a[5].l;
    Array *pos = (Array *)a[6].l, *posF = (Array *)a[7].l, *nrm = (Array *)a[8].l, *nrmF = (Array *)a[9].l, *col = (Array *)a[10].l;
    Array *tca[2] = {(Array *)a[11].l, (Array *)a[13].l};
    Array *texa[2] = {(Array *)a[12].l, (Array *)a[14].l};
    Array *idxa = (Array *)a[15].l;
    int nidx = a[16].i;
    if (!ista || !fsta || ista->length < I_COUNT || fsta->length < F_COUNT || !idxa || nidx > idxa->length) {
        throw_new(t, "java/lang/IllegalArgumentException", "M3G state");
        return;
    }
    const int *ist = ARR_I(ista);
    const float *fst = ARR_F(fsta);
    Raster r;
    memset(&r, 0, sizeof r);
    if (!raster_setup(&r, target, depth, tw, th, ist, fst)) return;
    int nv = ist[I_VCOUNT];
    if (nv <= 0) return;
    if ((pos && pos->length < nv * 3) || (posF && posF->length < nv * 3) || (!pos && !posF)) return;
    if (nrm && nrm->length < nv * 3) nrm = NULL;
    if (nrmF && nrmF->length < nv * 3) nrmF = NULL;
    int ccomps = ist[I_COL_COMPS];
    if (col && col->length < nv * ccomps) col = NULL;

    r.blending = ist[I_BLENDING];
    r.depth_test = ist[I_DEPTH_TEST];
    r.depth_write = ist[I_DEPTH_WRITE];
    r.color_write = ist[I_COLOR_WRITE];
    r.alpha_write = ist[I_ALPHA_WRITE];
    r.opaque = ist[I_OPAQUE_TARGET];
    r.alpha_threshold = fst[F_ALPHA_THRESHOLD];
    r.depth_factor = fst[F_DEPTH_FACTOR];
    r.depth_units = fst[F_DEPTH_UNITS];
    r.flat = ist[I_SHADING] == SHADE_FLAT;
    r.fog = ist[I_FOG_MODE] == FOG_LINEAR || ist[I_FOG_MODE] == FOG_EXP;
    {
        uint32_t fc = (uint32_t)ist[I_FOG_COLOR];
        r.fog_color[0] = ((fc >> 16) & 255) / 255.0f;
        r.fog_color[1] = ((fc >> 8) & 255) / 255.0f;
        r.fog_color[2] = (fc & 255) / 255.0f;
    }
    int tcomps[2] = {ist[I_TC0_COMPS], ist[I_TC1_COMPS]};
    r.ntex = 0;
    for (int u = 0; u < 2; u++) {
        if (!texa[u] || !tca[u] || tca[u]->length < nv * tcomps[u] || tcomps[u] < 2) break;
        if (!tex_setup(&r.tex[r.ntex], ist, u == 0 ? I_TEX0 : I_TEX1, texa[u])) break;
        r.ntex++;
    }

    /* transform all vertices */
    Vtx *vs = (Vtx *)malloc(sizeof(Vtx) * nv);
    if (!vs) return;
    const float *mv = &fst[F_MV], *pj = &fst[F_PROJ];
    float nm[9];
    normal_matrix(mv, nm);
    float ps = fst[F_POS_SCALE];
    const float *pb = &fst[F_POS_BIAS];
    float ns = fst[F_NRM_SCALE];
    bool lit = ist[I_HAS_MATERIAL] != 0;
    bool two = lit && ist[I_TWO_SIDED];
    float alpha_factor = fst[F_ALPHA_FACTOR];
    float defc[4];
    argb_to_f((uint32_t)ist[I_DEFAULT_COLOR], defc);
    for (int i = 0; i < nv; i++) {
        Vtx *v = &vs[i];
        float p[4];
        if (posF) {
            p[0] = ARR_F(posF)[i * 3]; p[1] = ARR_F(posF)[i * 3 + 1]; p[2] = ARR_F(posF)[i * 3 + 2];
        } else {
            p[0] = ARR_S(pos)[i * 3] * ps + pb[0];
            p[1] = ARR_S(pos)[i * 3 + 1] * ps + pb[1];
            p[2] = ARR_S(pos)[i * 3 + 2] * ps + pb[2];
        }
        p[3] = 1;
        float e[4], c[4];
        mat_mul_vec(mv, p, e);
        mat_mul_vec(pj, e, c);
        v->x = c[0]; v->y = c[1]; v->z = c[2]; v->w = c[3];
        float vc[4];
        bool hasvc = col != NULL;
        if (col) {
            const int16_t *cp = ARR_S(col) + i * ccomps;
            vc[0] = (cp[0] & 255) / 255.0f;
            vc[1] = (cp[1] & 255) / 255.0f;
            vc[2] = (cp[2] & 255) / 255.0f;
            vc[3] = ccomps >= 4 ? (cp[3] & 255) / 255.0f : 1.0f;
        } else memcpy(vc, defc, sizeof vc);
        if (lit) {
            float n[3] = {0, 0, 1};
            if (nrmF || nrm) {
                float on[3];
                if (nrmF) { on[0] = ARR_F(nrmF)[i * 3]; on[1] = ARR_F(nrmF)[i * 3 + 1]; on[2] = ARR_F(nrmF)[i * 3 + 2]; }
                else { on[0] = ARR_S(nrm)[i * 3] * ns; on[1] = ARR_S(nrm)[i * 3 + 1] * ns; on[2] = ARR_S(nrm)[i * 3 + 2] * ns; }
                n[0] = nm[0] * on[0] + nm[1] * on[1] + nm[2] * on[2];
                n[1] = nm[3] * on[0] + nm[4] * on[1] + nm[5] * on[2];
                n[2] = nm[6] * on[0] + nm[7] * on[1] + nm[8] * on[2];
                float l = sqrtf(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]);
                if (l > 0) { n[0] /= l; n[1] /= l; n[2] /= l; }
            }
            light_vertex(ist, fst, e, n, hasvc || ist[I_MAT_TRACKING] ? vc : NULL, v->c);
            if (two) {
                float bn[3] = {-n[0], -n[1], -n[2]};
                light_vertex(ist, fst, e, bn, hasvc || ist[I_MAT_TRACKING] ? vc : NULL, v->cb);
            }
        } else memcpy(v->c, vc, sizeof v->c);
        v->c[3] *= alpha_factor;
        if (!two) memcpy(v->cb, v->c, sizeof v->c);
        else v->cb[3] *= alpha_factor;
        for (int u = 0; u < r.ntex; u++) {
            const float *tf = &fst[u == 0 ? F_TC0 : F_TC1];
            const int16_t *tp = ARR_S(tca[u]) + i * tcomps[u];
            float tcv[4] = {tp[0] * tf[0] + tf[1], tp[1] * tf[0] + tf[2], tcomps[u] > 2 ? tp[2] * tf[0] + tf[3] : 0, 1};
            float o[4];
            mat_mul_vec(&tf[4], tcv, o);
            float q = o[3] != 0 ? o[3] : 1;
            v->tc[u][0] = o[0] / q;
            v->tc[u][1] = o[1] / q;
        }
        v->fog = 1;
        if (r.fog) {
            float d = -e[2];
            if (ist[I_FOG_MODE] == FOG_LINEAR) {
                float nr = fst[F_FOG_NEAR], fr = fst[F_FOG_FAR];
                v->fog = fr != nr ? clampf((fr - d) / (fr - nr), 0, 1) : 1;
            } else v->fog = clampf(expf(-fst[F_FOG_DENSITY] * d), 0, 1);
        }
    }

    /* triangles */
    const int *idx = ARR_I(idxa);
    int cull = ist[I_CULLING];
    bool ccw = ist[I_WINDING] == WINDING_CCW;
    for (int k = 0; k + 2 < nidx; k += 3) {
        int i0 = idx[k], i1 = idx[k + 1], i2 = idx[k + 2];
        if (i0 < 0 || i1 < 0 || i2 < 0 || i0 >= nv || i1 >= nv || i2 >= nv) continue;
        const Vtx *A = &vs[i0], *B = &vs[i1], *C = &vs[i2];
        /* homogeneous orientation test (valid even when vertices are behind the eye) */
        float det = A->x * (B->y * C->w - C->y * B->w) - B->x * (A->y * C->w - C->y * A->w) + C->x * (A->y * B->w - B->y * A->w);
        bool front = ccw ? det > 0 : det < 0;
        if (cull == CULL_BACK && !front) continue;
        if (cull == CULL_FRONT && front) continue;
        bool back = !front && two;
        float fc[4];
        if (r.flat) memcpy(fc, back ? C->cb : C->c, sizeof fc);
        draw_clipped(&r, A, B, C, back, r.flat ? fc : NULL);
    }
    free(vs);
}

/* clear(int[] target, float[] depth, int tw, int th, int[] ist(clip+viewport), int argb,
 *       boolean color, boolean depth, float depthValue, int[] img, int iw, int ih, int imgFormat,
 *       int cropX, int cropY, int cropW, int cropH, int modeX, int modeY, boolean opaque) */
NATIVE(M3G_clear) {
    (void)ret;
    (void)t;
    Array *target = (Array *)a[0].l, *depth = (Array *)a[1].l;
    int tw = a[2].i, th = a[3].i;
    Array *ista = (Array *)a[4].l;
    uint32_t color = (uint32_t)a[5].i;
    bool clear_color = a[6].i != 0, clear_depth = a[7].i != 0;
    float dval = a[8].f;
    Array *img = (Array *)a[9].l;
    int iw = a[10].i, ih = a[11].i, ifmt = a[12].i;
    int cx = a[13].i, cy = a[14].i, cw = a[15].i, ch = a[16].i, mx = a[17].i, my = a[18].i;
    bool opaque = a[19].i != 0;
    if (!ista || ista->length < I_VP_H + 1) return;
    Raster r;
    memset(&r, 0, sizeof r);
    if (!raster_setup(&r, target, depth, tw, th, ARR_I(ista), NULL)) return;
    if (img && (iw <= 0 || ih <= 0 || img->length < iw * ih)) img = NULL;
    if (img && (cw == 0 || ch == 0)) return;
    for (int y = r.sy0; y < r.sy1; y++) {
        for (int x = r.sx0; x < r.sx1; x++) {
            int idx = y * tw + x;
            if (clear_depth) r.depth[idx] = dval;
            if (!clear_color && !img) continue;
            uint32_t c = color;
            if (img) {
                float fx = cx + (x + 0.5f - r.vx) * cw / r.vw;
                float fy = cy + (y + 0.5f - r.vy) * ch / r.vh;
                int ix = (int)floorf(fx), iy = (int)floorf(fy);
                bool in = true;
                if (mx == BG_REPEAT) { ix %= iw; if (ix < 0) ix += iw; }
                else if (ix < 0 || ix >= iw) in = false;
                if (my == BG_REPEAT) { iy %= ih; if (iy < 0) iy += ih; }
                else if (iy < 0 || iy >= ih) in = false;
                if (in) {
                    uint32_t p = (uint32_t)ARR_I(img)[iy * iw + ix];
                    if (ifmt & 2) { /* image has alpha: blend over the background color */
                        uint32_t al = p >> 24, ia = 255 - al;
                        uint32_t rr = (((p >> 16) & 255) * al + ((c >> 16) & 255) * ia) / 255;
                        uint32_t gg = (((p >> 8) & 255) * al + ((c >> 8) & 255) * ia) / 255;
                        uint32_t bb = ((p & 255) * al + (c & 255) * ia) / 255;
                        c = (c & 0xFF000000u) | (rr << 16) | (gg << 8) | bb;
                        if (al > (c >> 24)) c = (c & 0x00FFFFFFu) | (al << 24);
                    } else c = p | 0xFF000000u;
                } else if (!clear_color) continue;
            }
            r.dst[idx] = opaque ? (c | 0xFF000000u) : c;
        }
    }
}

void m3g_register(void) {
    native_register("j4i/M3G", "drawTriangles", "([I[FII[I[F[S[F[S[F[S[S[I[S[I[II)V", M3G_drawTriangles);
    native_register("j4i/M3G", "clear", "([I[FII[IIZZF[IIIIIIIIIIZ)V", M3G_clear);
}
