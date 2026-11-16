/*
 * J2ME4iOS - public embedding API.
 *
 * The VM is a single global instance. A host (desktop window, iOS app) fills a
 * J4IConfig, calls j4i_run() on a dedicated thread (it blocks until the MIDlet
 * exits) and feeds input from its UI thread via j4i_post_event().
 */
#ifndef J4I_H
#define J4I_H

#include <stdint.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

/* Font style bits passed to the host glyph callbacks. */
#define J4I_FONT_BOLD      1
#define J4I_FONT_ITALIC    2
#define J4I_FONT_UNDERLINE 4

typedef struct J4IHost {
    void *user;

    /* Log one line (UTF-8, no trailing newline). Required. */
    void (*log)(void *user, const char *msg);

    /* A new frame is ready. Called on the VM thread; copy the pixels. Required. */
    void (*present)(void *user, const uint32_t *argb, int w, int h);

    /* Font metrics for a pixel size/style. */
    void (*font_metrics)(void *user, int size_px, int style, int *ascent, int *descent);

    /* Render one glyph into an 8-bit coverage buffer of max_w x h pixels
     * (row stride = stride), baseline at y = ascent. Return the advance width. */
    int (*glyph)(void *user, int codepoint, int size_px, int style,
                 uint8_t *alpha, int stride, int max_w, int h, int ascent);

    /* Optional decoder for non-PNG images (JPEG...). *argb_out must be malloc'd. */
    int (*decode_image)(void *user, const uint8_t *data, int len,
                        uint32_t **argb_out, int *w, int *h);

    /* Optional audio. Handles are host defined (> 0). */
    int  (*audio_create)(void *user, const uint8_t *data, int len, const char *mime);
    void (*audio_play)(void *user, int handle, int loops); /* loops: -1 = forever */
    void (*audio_stop)(void *user, int handle);
    void (*audio_close)(void *user, int handle);
    void (*audio_volume)(void *user, int handle, int level); /* 0..100 */
    int  (*audio_is_playing)(void *user, int handle);
    void (*tone)(void *user, int note, int duration_ms, int volume);

    void (*vibrate)(void *user, int ms);
    int  (*platform_request)(void *user, const char *url);

    /* Ask the user for text. Answer later with j4i_text_input_result(). */
    void (*text_input)(void *user, int req_id, const char *title, const char *text,
                       int max_len, int constraints);

    /* The VM has finished (MIDlet destroyed or fatal error). */
    void (*exited)(void *user, int code);
} J4IHost;

typedef struct J4IConfig {
    const char *runtime_jar;  /* path of j4i-runtime.jar (our CLDC/MIDP classes) */
    const char *game_jar;     /* the MIDlet suite */
    const char *data_dir;     /* writable directory for RecordStores (per game) */
    const char *main_class;   /* NULL: MIDlet-N from the manifest. Otherwise a MIDlet
                                 class or a class with static main(String[]) */
    const char *main_args;    /* space separated args for static main */
    int midlet_index;         /* which MIDlet-N to start (1-based, 0 = 1) */
    int screen_w, screen_h;   /* virtual screen, e.g. 240x320 */
    int heap_mb;              /* Java heap size, 0 = default (64) */
    int trace;                /* debug tracing level */
    J4IHost host;
} J4IConfig;

/* Event types for j4i_post_event(). Key codes follow MIDP: '0'..'9', '*', '#',
 * -1 up, -2 down, -3 left, -4 right, -5 fire, -6 left soft, -7 right soft, -8 clear. */
enum {
    J4I_EV_KEY_PRESSED = 1,
    J4I_EV_KEY_RELEASED = 2,
    J4I_EV_KEY_REPEATED = 3,
    J4I_EV_POINTER_PRESSED = 4,
    J4I_EV_POINTER_RELEASED = 5,
    J4I_EV_POINTER_DRAGGED = 6,
    J4I_EV_PAUSE = 7,
    J4I_EV_RESUME = 8,
    J4I_EV_EXIT = 9,
    J4I_EV_TEXT_INPUT = 10
};

enum {
    J4I_KEY_UP = -1, J4I_KEY_DOWN = -2, J4I_KEY_LEFT = -3, J4I_KEY_RIGHT = -4,
    J4I_KEY_FIRE = -5, J4I_KEY_SOFT_LEFT = -6, J4I_KEY_SOFT_RIGHT = -7, J4I_KEY_CLEAR = -8
};

/* Run the VM until the application exits. Returns an exit code. */
int  j4i_run(const J4IConfig *cfg);

/* Thread safe. */
void j4i_post_event(int type, int a, int b);
void j4i_text_input_result(int req_id, const char *utf8_or_null);
void j4i_request_exit(void);

/* Read a manifest attribute (e.g. "MIDlet-Name") from a jar. Returns length or -1. */
int  j4i_manifest_value(const char *jar_path, const char *key, char *out, int out_len);

/* Extract the MIDlet icon (PNG bytes, malloc'd) declared in the manifest. */
uint8_t *j4i_read_icon(const char *jar_path, int *len);

#ifdef __cplusplus
}
#endif
#endif
