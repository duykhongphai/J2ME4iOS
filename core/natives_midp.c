/* j4i.Sys natives for events, screen, storage, audio and other host services. */
#include "vm.h"

#define NATIVE(name) static void name(Thread *t, Value *a, Value *ret)

void gfx_register(void);

/* ---------------- events ---------------- */
NATIVE(Sys_waitEvent) {
    Array *ev = (Array *)a[0].l;
    int timeout = a[1].i;
    int e[3];
    if (events_pop(e)) {
        ARR_I(ev)[0] = e[0];
        ARR_I(ev)[1] = e[1];
        ARR_I(ev)[2] = e[2];
        t->ev_waiting = false;
        ret->i = 1;
        return;
    }
    if (vm.event_wakeup) {
        vm.event_wakeup = false;
        t->ev_waiting = false;
        ret->i = 0;
        return;
    }
    int64_t now = vm_now();
    if (t->ev_waiting) {
        if (t->ev_deadline && now >= t->ev_deadline) {
            t->ev_waiting = false;
            ret->i = 0;
            return;
        }
    } else {
        if (timeout == 0) { ret->i = 0; return; }
        t->ev_waiting = true;
        t->ev_deadline = timeout > 0 ? now + timeout : 0;
    }
    t->state = TS_WAIT_EVENT;
    t->wake_time = t->ev_deadline;
    t->reexecute = true;
}

NATIVE(Sys_wakeEvents) { (void)t; (void)a; (void)ret; vm.event_wakeup = true; }
NATIVE(Sys_screenWidth) { (void)t; (void)a; ret->i = vm.cfg.screen_w; }
NATIVE(Sys_screenHeight) { (void)t; (void)a; ret->i = vm.cfg.screen_h; }

NATIVE(Sys_flush) {
    (void)t;
    (void)ret;
    Array *pix = (Array *)a[0].l;
    int w = a[1].i, h = a[2].i;
    if (!pix || pix->length < w * h) return;
    if (vm.cfg.host.present) vm.cfg.host.present(vm.cfg.host.user, (const uint32_t *)ARR_I(pix), w, h);
}

/* ---------------- storage ---------------- */
static void file_path(Object *name, char *out, size_t outlen) {
    char *s = string_to_utf8(name);
    char enc[700];
    size_t n = 0;
    for (const unsigned char *p = (const unsigned char *)s; *p && n + 4 < sizeof enc; p++) {
        if ((*p >= 'a' && *p <= 'z') || (*p >= 'A' && *p <= 'Z') || (*p >= '0' && *p <= '9') || *p == '.' || *p == '_' || *p == '-')
            enc[n++] = (char)*p;
        else n += snprintf(enc + n, sizeof enc - n, "%%%02X", *p);
    }
    enc[n] = 0;
    free(s);
    snprintf(out, outlen, "%s/%s", vm.cfg.data_dir ? vm.cfg.data_dir : ".", enc);
}

static void decode_name(const char *in, char *out, size_t outlen) {
    size_t n = 0;
    for (const char *p = in; *p && n + 1 < outlen; p++) {
        if (*p == '%' && p[1] && p[2]) {
            char hex[3] = {p[1], p[2], 0};
            out[n++] = (char)strtol(hex, NULL, 16);
            p += 2;
        } else out[n++] = *p;
    }
    out[n] = 0;
}

NATIVE(Sys_readFile) {
    char path[1024];
    file_path(a[0].l, path, sizeof path);
    size_t len;
    uint8_t *data = os_read_file(path, &len);
    if (!data) return;
    Array *b = alloc_prim_array(t, 'B', (int)len);
    if (b) memcpy(ARR_B(b), data, len);
    free(data);
    ret->l = (Object *)b;
}

NATIVE(Sys_writeFile) {
    (void)t;
    char path[1024];
    if (vm.cfg.data_dir) os_mkdirs(vm.cfg.data_dir);
    file_path(a[0].l, path, sizeof path);
    Array *b = (Array *)a[1].l;
    ret->i = os_write_file(path, b ? ARR_B(b) : NULL, b ? (size_t)b->length : 0) == 0;
}

NATIVE(Sys_deleteFile) {
    (void)t;
    char path[1024];
    file_path(a[0].l, path, sizeof path);
    ret->i = os_delete_file(path) == 0;
}

typedef struct { char **names; int n, cap; } NameList;
static void collect(void *ctx, const char *name) {
    NameList *l = (NameList *)ctx;
    size_t nl = strlen(name);
    if (nl > 4 && !strcmp(name + nl - 4, ".tmp")) return;
    if (l->n == l->cap) {
        l->cap = l->cap ? l->cap * 2 : 16;
        l->names = (char **)realloc(l->names, l->cap * sizeof(char *));
    }
    char buf[512];
    decode_name(name, buf, sizeof buf);
    l->names[l->n++] = strdup(buf);
}

NATIVE(Sys_listFiles) {
    (void)a;
    NameList l = {0};
    os_list_dir(vm.cfg.data_dir ? vm.cfg.data_dir : ".", collect, &l);
    Array *arr = alloc_array(t, vm.c_arr_String, l.n);
    for (int i = 0; arr && i < l.n; i++) ARR_L(arr)[i] = string_new_utf8(t, l.names[i]);
    for (int i = 0; i < l.n; i++) free(l.names[i]);
    free(l.names);
    ret->l = (Object *)arr;
}

/* ---------------- host services ---------------- */
NATIVE(Sys_vibrate) {
    (void)t;
    (void)ret;
    if (vm.cfg.host.vibrate) vm.cfg.host.vibrate(vm.cfg.host.user, a[0].i);
}

NATIVE(Sys_platformRequest) {
    (void)t;
    char *u = string_to_utf8(a[0].l);
    ret->i = vm.cfg.host.platform_request ? vm.cfg.host.platform_request(vm.cfg.host.user, u) : 0;
    free(u);
}

/* text input results arrive from the host thread */
typedef struct TextResult {
    int id;
    char *text;
    bool cancelled;
    struct TextResult *next;
} TextResult;
static TextResult *text_results;
static int text_req_counter;

void j4i_text_input_result(int req_id, const char *utf8) {
    TextResult *r = (TextResult *)calloc(1, sizeof(TextResult));
    r->id = req_id;
    r->text = utf8 ? strdup(utf8) : NULL;
    r->cancelled = utf8 == NULL;
    os_lock();
    r->next = text_results;
    text_results = r;
    os_unlock();
    j4i_post_event(J4I_EV_TEXT_INPUT, req_id, utf8 ? 1 : 0);
}

char *text_input_take(int id, bool *found) {
    char *res = NULL;
    *found = false;
    os_lock();
    for (TextResult **pp = &text_results; *pp; pp = &(*pp)->next) {
        if ((*pp)->id == id) {
            TextResult *r = *pp;
            *pp = r->next;
            res = r->text;
            *found = true;
            free(r);
            break;
        }
    }
    os_unlock();
    return res;
}

NATIVE(Sys_requestTextInput) {
    (void)t;
    int id = ++text_req_counter;
    char *title = a[0].l ? string_to_utf8(a[0].l) : strdup("");
    char *text = a[1].l ? string_to_utf8(a[1].l) : strdup("");
    if (vm.cfg.host.text_input) vm.cfg.host.text_input(vm.cfg.host.user, id, title, text, a[2].i, a[3].i);
    else j4i_text_input_result(id, text);
    free(title);
    free(text);
    ret->i = id;
}

NATIVE(Sys_takeTextInput) {
    bool found;
    char *s = text_input_take(a[0].i, &found);
    if (s) {
        ret->l = string_new_utf8(t, s);
        free(s);
    }
}

/* ---------------- audio ---------------- */
NATIVE(Sys_audioCreate) {
    (void)t;
    Array *b = (Array *)a[0].l;
    char *mime = a[1].l ? string_to_utf8(a[1].l) : strdup("");
    ret->i = (b && vm.cfg.host.audio_create) ? vm.cfg.host.audio_create(vm.cfg.host.user, (const uint8_t *)ARR_B(b), b->length, mime) : 0;
    free(mime);
}
NATIVE(Sys_audioPlay) { (void)t; (void)ret; if (vm.cfg.host.audio_play && a[0].i > 0) vm.cfg.host.audio_play(vm.cfg.host.user, a[0].i, a[1].i); }
NATIVE(Sys_audioStop) { (void)t; (void)ret; if (vm.cfg.host.audio_stop && a[0].i > 0) vm.cfg.host.audio_stop(vm.cfg.host.user, a[0].i); }
NATIVE(Sys_audioClose) { (void)t; (void)ret; if (vm.cfg.host.audio_close && a[0].i > 0) vm.cfg.host.audio_close(vm.cfg.host.user, a[0].i); }
NATIVE(Sys_audioVolume) { (void)t; (void)ret; if (vm.cfg.host.audio_volume && a[0].i > 0) vm.cfg.host.audio_volume(vm.cfg.host.user, a[0].i, a[1].i); }
NATIVE(Sys_audioIsPlaying) {
    (void)t;
    ret->i = (vm.cfg.host.audio_is_playing && a[0].i > 0) ? vm.cfg.host.audio_is_playing(vm.cfg.host.user, a[0].i) : 0;
}
NATIVE(Sys_tone) { (void)t; (void)ret; if (vm.cfg.host.tone) vm.cfg.host.tone(vm.cfg.host.user, a[0].i, a[1].i, a[2].i); }

void midp_free(void) {
    os_lock();
    while (text_results) {
        TextResult *r = text_results;
        text_results = r->next;
        free(r->text);
        free(r);
    }
    os_unlock();
}

void natives_midp_register(void) {
#define R(n, d, f) native_register("j4i/Sys", n, d, f)
    R("waitEvent", "([II)Z", Sys_waitEvent);
    R("wakeEvents", "()V", Sys_wakeEvents);
    R("screenWidth", "()I", Sys_screenWidth);
    R("screenHeight", "()I", Sys_screenHeight);
    R("flush", "([III)V", Sys_flush);
    R("readFile", "(Ljava/lang/String;)[B", Sys_readFile);
    R("writeFile", "(Ljava/lang/String;[B)Z", Sys_writeFile);
    R("deleteFile", "(Ljava/lang/String;)Z", Sys_deleteFile);
    R("listFiles", "()[Ljava/lang/String;", Sys_listFiles);
    R("vibrate", "(I)V", Sys_vibrate);
    R("platformRequest", "(Ljava/lang/String;)Z", Sys_platformRequest);
    R("requestTextInput", "(Ljava/lang/String;Ljava/lang/String;II)I", Sys_requestTextInput);
    R("takeTextInput", "(I)Ljava/lang/String;", Sys_takeTextInput);
    R("audioCreate", "([BLjava/lang/String;)I", Sys_audioCreate);
    R("audioPlay", "(II)V", Sys_audioPlay);
    R("audioStop", "(I)V", Sys_audioStop);
    R("audioClose", "(I)V", Sys_audioClose);
    R("audioVolume", "(II)V", Sys_audioVolume);
    R("audioIsPlaying", "(I)Z", Sys_audioIsPlaying);
    R("tone", "(III)V", Sys_tone);
#undef R
    gfx_register();
}
