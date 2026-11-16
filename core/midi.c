/* Built-in General MIDI synthesizer: renders a Standard MIDI File to a 16-bit mono WAV.
 * Lets MIDI music play on hosts without a soundfont. Instruments are grouped into the
 * 16 GM families (oscillator + ADSR + low-pass), channel 10 uses synthesized drums. */
#include "vm.h"
#include <math.h>
#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

#define RATE 22050
#define MAX_SECONDS 600

typedef struct {
    int64_t tick;
    int order, kind, ch, a, b;
} MEv; /* kind: 0 tempo, 1 program, 2 control, 3 bend, 4 note off, 5 note on */

typedef struct {
    int ch, note, vel, program;
    float volume, bend;
    double start, end;
} MNote;

enum { W_SINE, W_TRI, W_SQUARE, W_SAW };
typedef struct {
    int wave;
    float a, d, s, r, cutoff, gain, detune;
} Patch;

static const Patch PATCHES[16] = {
    {W_TRI, 0.004f, 0.6f, 0.25f, 0.25f, 5000, 0.9f, 0},    /* piano */
    {W_SINE, 0.002f, 0.35f, 0.05f, 0.25f, 8000, 0.8f, 0},  /* chromatic percussion */
    {W_SQUARE, 0.01f, 0.1f, 0.8f, 0.08f, 2200, 0.45f, 0},  /* organ */
    {W_SAW, 0.004f, 0.5f, 0.15f, 0.2f, 2000, 0.55f, 0},    /* guitar */
    {W_TRI, 0.004f, 0.3f, 0.6f, 0.1f, 1200, 1.0f, 0},      /* bass */
    {W_SAW, 0.08f, 0.2f, 0.8f, 0.3f, 2600, 0.45f, 6},      /* strings */
    {W_SAW, 0.06f, 0.2f, 0.8f, 0.35f, 2400, 0.45f, 8},     /* ensemble */
    {W_SAW, 0.03f, 0.15f, 0.75f, 0.15f, 3000, 0.5f, 0},    /* brass */
    {W_SQUARE, 0.03f, 0.1f, 0.75f, 0.12f, 2600, 0.45f, 0}, /* reed */
    {W_SINE, 0.04f, 0.1f, 0.85f, 0.15f, 6000, 0.8f, 0},    /* pipe */
    {W_SQUARE, 0.005f, 0.1f, 0.8f, 0.1f, 4000, 0.45f, 0},  /* synth lead */
    {W_SAW, 0.25f, 0.3f, 0.85f, 0.5f, 1500, 0.45f, 10},    /* synth pad */
    {W_TRI, 0.05f, 0.3f, 0.6f, 0.4f, 3000, 0.6f, 0},       /* synth effects */
    {W_TRI, 0.004f, 0.4f, 0.2f, 0.2f, 4000, 0.8f, 0},      /* ethnic */
    {W_SINE, 0.002f, 0.25f, 0.0f, 0.15f, 6000, 0.9f, 0},   /* percussive */
    {W_SAW, 0.01f, 0.3f, 0.3f, 0.3f, 1800, 0.4f, 0},       /* sound effects */
};

static int ev_cmp(const void *x, const void *y) {
    const MEv *a = (const MEv *)x, *b = (const MEv *)y;
    if (a->tick != b->tick) return a->tick < b->tick ? -1 : 1;
    if (a->kind != b->kind) return a->kind - b->kind;
    return a->order - b->order;
}

static int note_cmp(const void *x, const void *y) {
    const MNote *a = (const MNote *)x, *b = (const MNote *)y;
    return a->start < b->start ? -1 : (a->start > b->start ? 1 : 0);
}

static uint32_t be32(const uint8_t *p) { return ((uint32_t)p[0] << 24) | ((uint32_t)p[1] << 16) | ((uint32_t)p[2] << 8) | p[3]; }

/* Parses the file into a note list. Returns note count or -1. */
static int midi_parse(const uint8_t *d, int len, MNote **out, double *duration) {
    if (len < 14 || memcmp(d, "MThd", 4) != 0) return -1;
    uint32_t hlen = be32(d + 4);
    int ntracks = (d[10] << 8) | d[11];
    int division = (d[12] << 8) | d[13];
    if (division & 0x8000 || division == 0) return -1;
    int p = 8 + (int)hlen;
    MEv *ev = NULL;
    int nev = 0, cap = 0, order = 0;
#define PUSH(t, k, c, A, B)                                        \
    do {                                                           \
        if (nev == cap) {                                          \
            cap = cap ? cap * 2 : 1024;                            \
            ev = (MEv *)realloc(ev, sizeof(MEv) * cap);            \
        }                                                          \
        ev[nev].tick = (t); ev[nev].order = order++; ev[nev].kind = (k); \
        ev[nev].ch = (c); ev[nev].a = (A); ev[nev].b = (B); nev++; \
    } while (0)
    for (int t = 0; t < ntracks && p + 8 <= len; t++) {
        uint32_t tl = be32(d + p + 4);
        bool is_trk = memcmp(d + p, "MTrk", 4) == 0;
        p += 8;
        int end = (int)((int64_t)p + tl > len ? len : p + (int)tl);
        if (!is_trk) { p = end; continue; }
        int64_t tick = 0;
        int status = 0;
        while (p < end) {
            uint32_t v = 0;
            for (int k = 0; k < 4 && p < end; k++) {
                int c = d[p++];
                v = (v << 7) | (c & 0x7F);
                if (!(c & 0x80)) break;
            }
            tick += v;
            if (p >= end) break;
            if (d[p] & 0x80) status = d[p++];
            int type = status & 0xF0, ch = status & 0x0F;
            if (status == 0xFF) {
                if (p >= end) break;
                int meta = d[p++];
                uint32_t l = 0;
                for (int k = 0; k < 4 && p < end; k++) {
                    int c = d[p++];
                    l = (l << 7) | (c & 0x7F);
                    if (!(c & 0x80)) break;
                }
                if (meta == 0x51 && l == 3 && p + 3 <= end) PUSH(tick, 0, 0, (d[p] << 16) | (d[p + 1] << 8) | d[p + 2], 0);
                p += (int)l;
                if (meta == 0x2F) break;
            } else if (status == 0xF0 || status == 0xF7) {
                uint32_t l = 0;
                for (int k = 0; k < 4 && p < end; k++) {
                    int c = d[p++];
                    l = (l << 7) | (c & 0x7F);
                    if (!(c & 0x80)) break;
                }
                p += (int)l;
            } else if (p + 1 >= end && type != 0xC0 && type != 0xD0) {
                break;
            } else if (type == 0x90 || type == 0x80) {
                int note = d[p++], vel = d[p++];
                PUSH(tick, (type == 0x90 && vel > 0) ? 5 : 4, ch, note, vel);
            } else if (type == 0xC0) {
                PUSH(tick, 1, ch, d[p++], 0);
            } else if (type == 0xD0) {
                p += 1;
            } else if (type == 0xB0) {
                int c = d[p++], v2 = d[p++];
                PUSH(tick, 2, ch, c, v2);
            } else if (type == 0xE0) {
                int lsb = d[p++], msb = d[p++];
                PUSH(tick, 3, ch, ((msb << 7) | lsb) - 8192, 0);
            } else {
                p += 2;
            }
        }
        p = end;
    }
#undef PUSH
    qsort(ev, nev, sizeof(MEv), ev_cmp);
    int program[16] = {0}, vol[16], expr[16];
    float bend[16] = {0};
    for (int i = 0; i < 16; i++) { vol[i] = 100; expr[i] = 127; }
    double tempo = 500000, time = 0;
    int64_t last = 0;
    int open[16 * 128];
    for (int i = 0; i < 16 * 128; i++) open[i] = -1;
    MNote *notes = NULL;
    int nn = 0, ncap = 0;
    for (int i = 0; i < nev; i++) {
        MEv *e = &ev[i];
        time += (double)(e->tick - last) * tempo / division / 1e6;
        last = e->tick;
        if (time > MAX_SECONDS) break;
        switch (e->kind) {
        case 0: tempo = e->a > 0 ? e->a : tempo; break;
        case 1: program[e->ch] = e->a & 127; break;
        case 2:
            if (e->a == 7) vol[e->ch] = e->b;
            else if (e->a == 11) expr[e->ch] = e->b;
            else if (e->a == 121) { vol[e->ch] = 100; expr[e->ch] = 127; bend[e->ch] = 0; }
            else if (e->a == 120 || e->a == 123) {
                for (int n = 0; n < 128; n++) {
                    int k = e->ch * 128 + n;
                    if (open[k] >= 0) { notes[open[k]].end = time; open[k] = -1; }
                }
            }
            break;
        case 3: bend[e->ch] = (float)e->a / 8192.0f * 2; break;
        default: {
            int k = e->ch * 128 + (e->a & 127);
            if (open[k] >= 0) { notes[open[k]].end = time; open[k] = -1; }
            if (e->kind == 5) {
                if (nn == ncap) {
                    ncap = ncap ? ncap * 2 : 512;
                    notes = (MNote *)realloc(notes, sizeof(MNote) * ncap);
                }
                MNote *n = &notes[nn];
                n->ch = e->ch;
                n->note = e->a & 127;
                n->vel = e->b;
                n->program = program[e->ch];
                n->volume = (vol[e->ch] / 127.0f) * (expr[e->ch] / 127.0f);
                n->bend = bend[e->ch];
                n->start = time;
                n->end = -1;
                open[k] = nn++;
            }
        }
        }
    }
    for (int k = 0; k < 16 * 128; k++)
        if (open[k] >= 0) notes[open[k]].end = fmax(time, notes[open[k]].start + 0.25);
    free(ev);
    qsort(notes, nn, sizeof(MNote), note_cmp);
    double dur = time;
    for (int i = 0; i < nn; i++)
        if (notes[i].end > dur) dur = notes[i].end;
    *out = notes;
    *duration = dur;
    return nn;
}

/* ---------------- synthesis ---------------- */
typedef struct { float b0, b1, b2, a1, a2, x1, x2, y1, y2; } Biquad;

static void bq_init(Biquad *f, int type, float freq, float q) { /* 0 lowpass, 1 highpass, 2 bandpass */
    if (freq > RATE * 0.45f) freq = RATE * 0.45f;
    float w = 2 * (float)M_PI * freq / RATE, cs = cosf(w), sn = sinf(w), alpha = sn / (2 * q);
    float b0, b1, b2, a0 = 1 + alpha, a1 = -2 * cs, a2 = 1 - alpha;
    if (type == 0) { b0 = (1 - cs) / 2; b1 = 1 - cs; b2 = (1 - cs) / 2; }
    else if (type == 1) { b0 = (1 + cs) / 2; b1 = -(1 + cs); b2 = (1 + cs) / 2; }
    else { b0 = alpha; b1 = 0; b2 = -alpha; }
    f->b0 = b0 / a0; f->b1 = b1 / a0; f->b2 = b2 / a0; f->a1 = a1 / a0; f->a2 = a2 / a0;
    f->x1 = f->x2 = f->y1 = f->y2 = 0;
}

static inline float bq(Biquad *f, float x) {
    float y = f->b0 * x + f->b1 * f->x1 + f->b2 * f->x2 - f->a1 * f->y1 - f->a2 * f->y2;
    f->x2 = f->x1; f->x1 = x; f->y2 = f->y1; f->y1 = y;
    return y;
}

static inline float osc(int wave, double ph) { /* ph in [0,1) */
    switch (wave) {
    case W_SINE: return (float)sin(2 * M_PI * ph);
    case W_TRI: return (float)(ph < 0.5 ? 4 * ph - 1 : 3 - 4 * ph);
    case W_SQUARE: return ph < 0.5 ? 1.0f : -1.0f;
    default: return (float)(2 * ph - 1);
    }
}

static J4I_TLS uint32_t rng = 0x12345678u;
static inline float noise(void) {
    rng = rng * 1664525u + 1013904223u;
    return (float)((int32_t)rng) / 2147483648.0f;
}

static void voice(float *buf, int64_t total, const MNote *n, const Patch *p, int64_t s0) {
    float vel = powf(n->vel / 127.0f, 1.6f) * n->volume;
    float peak = 0.22f * p->gain * vel;
    if (peak <= 0.001f) return;
    double freq = 440.0 * pow(2.0, (n->note + n->bend - 69) / 12.0);
    if (freq >= RATE / 2) return;
    double dur = fmax(n->end - n->start, 0.02);
    double off = fmax(dur, p->a);
    double tail = p->r * 2 + 0.05;
    int64_t len = (int64_t)((off + tail) * RATE);
    if (s0 + len > total) len = total - s0;
    if (len <= 0) return;
    Biquad lp;
    bq_init(&lp, 0, fminf(18000, p->cutoff * (0.6f + vel)), 0.707f);
    int nosc = p->detune != 0 ? 2 : 1;
    double inc[2];
    double ph[2] = {0, 0.25};
    for (int k = 0; k < nosc; k++) {
        double cents = nosc == 2 ? (k == 0 ? -p->detune : p->detune) : 0;
        inc[k] = freq * pow(2.0, cents / 1200.0) / RATE;
    }
    float level = nosc == 2 ? peak * 0.7f : peak;
    float sustain = level * p->s;
    float dtc = p->d / 3 * RATE, rtc = p->r / 3 * RATE;
    float a_samples = p->a * RATE;
    int64_t off_s = (int64_t)(off * RATE);
    float env = 0, env_at_off = 0;
    for (int64_t i = 0; i < len; i++) {
        if (i < off_s) {
            if (i < a_samples) env = level * (float)i / a_samples;
            else env = sustain + (level - sustain) * expf(-(float)(i - a_samples) / (dtc > 1 ? dtc : 1));
            env_at_off = env;
        } else {
            env = env_at_off * expf(-(float)(i - off_s) / (rtc > 1 ? rtc : 1));
        }
        float s = 0;
        for (int k = 0; k < nosc; k++) {
            s += osc(p->wave, ph[k]);
            ph[k] += inc[k];
            if (ph[k] >= 1) ph[k] -= floor(ph[k]);
        }
        buf[s0 + i] += bq(&lp, s) * env;
    }
}

static void drum(float *buf, int64_t total, int note, float velocity, int64_t s0) {
    float v = powf(velocity / 127.0f, 1.4f) * 0.5f;
    if (v <= 0.002f) return;
    if (note == 35 || note == 36) { /* kick: falling sine */
        int64_t len = (int64_t)(0.25 * RATE);
        double ph = 0;
        for (int64_t i = 0; i < len && s0 + i < total; i++) {
            double t = (double)i / RATE;
            double f = t < 0.12 ? 140 * pow(45.0 / 140.0, t / 0.12) : 45;
            ph += f / RATE;
            float amp = v * 1.6f * powf(0.001f / (v * 1.6f), (float)(t / 0.22));
            if (t > 0.22) amp = 0;
            buf[s0 + i] += (float)sin(2 * M_PI * ph) * amp;
        }
        return;
    }
    if (note >= 41 && note <= 50 && note != 42 && note != 44 && note != 46 && note != 49) { /* toms */
        double base = 80 + (note - 41) * 22;
        int64_t len = (int64_t)(0.32 * RATE);
        double ph = 0;
        for (int64_t i = 0; i < len && s0 + i < total; i++) {
            double t = (double)i / RATE;
            double f = t < 0.15 ? base * 1.6 * pow(1 / 1.6, t / 0.15) : base;
            ph += f / RATE;
            float amp = t > 0.3 ? 0 : v * 1.2f * powf(0.001f / (v * 1.2f), (float)(t / 0.3));
            buf[s0 + i] += (float)sin(2 * M_PI * ph) * amp;
        }
        return;
    }
    Biquad f;
    float len_s = 0.08f;
    if (note == 38 || note == 40 || note == 37 || note == 39) { bq_init(&f, 2, 1800, 0.8f); len_s = 0.16f; }
    else if (note == 42 || note == 44) { bq_init(&f, 1, 7000, 0.707f); len_s = 0.05f; }
    else if (note == 46) { bq_init(&f, 1, 6500, 0.707f); len_s = 0.25f; }
    else if (note == 49 || note == 57 || note == 52 || note == 55) { bq_init(&f, 1, 4500, 0.707f); len_s = 0.7f; }
    else if (note == 51 || note == 53 || note == 59) { bq_init(&f, 1, 5500, 0.707f); len_s = 0.35f; }
    else { bq_init(&f, 2, 3000, 0.707f); len_s = 0.06f; }
    int64_t len = (int64_t)((len_s + 0.02f) * RATE);
    for (int64_t i = 0; i < len && s0 + i < total; i++) {
        float t = (float)i / RATE;
        float amp = t > len_s ? 0 : v * powf(0.001f / v, t / len_s);
        buf[s0 + i] += bq(&f, noise()) * amp;
    }
}

static void put16(uint8_t *p, int v) { p[0] = (uint8_t)v; p[1] = (uint8_t)(v >> 8); }
static void put32(uint8_t *p, uint32_t v) { p[0] = (uint8_t)v; p[1] = (uint8_t)(v >> 8); p[2] = (uint8_t)(v >> 16); p[3] = (uint8_t)(v >> 24); }

uint8_t *midi_render_wav(const uint8_t *data, int len, int *out_len) {
    MNote *notes = NULL;
    double duration = 0;
    int nn = midi_parse(data, len, &notes, &duration);
    if (nn < 0) return NULL;
    if (duration > MAX_SECONDS) duration = MAX_SECONDS;
    int64_t total = (int64_t)((duration + 0.05) * RATE);
    if (total < RATE / 20) total = RATE / 20;
    float *buf = (float *)calloc((size_t)total, sizeof(float));
    if (!buf) { free(notes); return NULL; }
    for (int i = 0; i < nn; i++) {
        const MNote *n = &notes[i];
        int64_t s0 = (int64_t)(n->start * RATE);
        if (s0 >= total) continue;
        if (n->ch == 9) drum(buf, total, n->note, n->vel * n->volume, s0);
        else voice(buf, total, n, &PATCHES[(n->program >> 3) & 15], s0);
    }
    free(notes);
    /* gentle compression: soft clip above a knee, like the web version's compressor */
    size_t bytes = 44 + (size_t)total * 2;
    uint8_t *w = (uint8_t *)malloc(bytes);
    if (!w) { free(buf); return NULL; }
    memcpy(w, "RIFF", 4);
    put32(w + 4, (uint32_t)(bytes - 8));
    memcpy(w + 8, "WAVEfmt ", 8);
    put32(w + 16, 16);
    put16(w + 20, 1);
    put16(w + 22, 1);
    put32(w + 24, RATE);
    put32(w + 28, RATE * 2);
    put16(w + 32, 2);
    put16(w + 34, 16);
    memcpy(w + 36, "data", 4);
    put32(w + 40, (uint32_t)(total * 2));
    for (int64_t i = 0; i < total; i++) {
        float x = buf[i] * 1.4f;
        float y = x / (1 + fabsf(x) * 0.6f); /* soft knee */
        int s = (int)(y * 30000);
        if (s > 32767) s = 32767;
        if (s < -32768) s = -32768;
        w[44 + i * 2] = (uint8_t)s;
        w[45 + i * 2] = (uint8_t)(s >> 8);
    }
    free(buf);
    *out_len = (int)bytes;
    return w;
}

/* ---------------- render cache ----------------
 * Games often create a new Player for the same tune; keep the last renders. */
#define MIDI_CACHE 6
static J4I_TLS struct {
    uint32_t hash;
    int src_len;
    uint8_t *wav;
    int wav_len;
} midi_cache[MIDI_CACHE];
static J4I_TLS int midi_cache_next;

static uint32_t fnv(const uint8_t *d, int n) {
    uint32_t h = 2166136261u;
    for (int i = 0; i < n; i++) h = (h ^ d[i]) * 16777619u;
    return h;
}

const uint8_t *midi_cached_wav(const uint8_t *data, int len, int *out_len) {
    uint32_t h = fnv(data, len);
    for (int i = 0; i < MIDI_CACHE; i++)
        if (midi_cache[i].wav && midi_cache[i].hash == h && midi_cache[i].src_len == len) {
            *out_len = midi_cache[i].wav_len;
            return midi_cache[i].wav;
        }
    int wl;
    uint8_t *w = midi_render_wav(data, len, &wl);
    if (!w) return NULL;
    int slot = midi_cache_next;
    midi_cache_next = (midi_cache_next + 1) % MIDI_CACHE;
    free(midi_cache[slot].wav);
    midi_cache[slot].hash = h;
    midi_cache[slot].src_len = len;
    midi_cache[slot].wav = w;
    midi_cache[slot].wav_len = wl;
    *out_len = wl;
    return w;
}

void midi_cache_free(void) {
    for (int i = 0; i < MIDI_CACHE; i++) {
        free(midi_cache[i].wav);
        midi_cache[i].wav = NULL;
    }
    midi_cache_next = 0;
}
