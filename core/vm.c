/* VM lifecycle: boot, run, shutdown. */
#include "vm.h"
#include <ctype.h>

J4I_TLS VM *vm_ptr;

/* ---------------- manifest / JAD parsing ---------------- */
int manifest_lookup(const char *text, const char *key, char *out, int outlen) {
    size_t klen = strlen(key);
    const char *p = text;
    while (*p) {
        const char *line = p;
        const char *eol = p;
        while (*eol && *eol != '\n' && *eol != '\r') eol++;
        const char *colon = memchr(line, ':', (size_t)(eol - line));
        if (colon && (size_t)(colon - line) == klen) {
            bool match = true;
            for (size_t i = 0; i < klen; i++)
                if (tolower((unsigned char)line[i]) != tolower((unsigned char)key[i])) { match = false; break; }
            if (match) {
                int n = 0;
                const char *v = colon + 1;
                while (v < eol && (*v == ' ' || *v == '\t')) v++;
                for (;;) {
                    while (v < eol && n < outlen - 1) out[n++] = *v++;
                    /* continuation lines start with a single space */
                    const char *nx = eol;
                    if (*nx == '\r') nx++;
                    if (*nx == '\n') nx++;
                    if (*nx == ' ') {
                        v = nx + 1;
                        eol = v;
                        while (*eol && *eol != '\n' && *eol != '\r') eol++;
                        continue;
                    }
                    break;
                }
                while (n > 0 && (out[n - 1] == ' ' || out[n - 1] == '\t')) n--;
                out[n] = 0;
                return n;
            }
        }
        p = eol;
        while (*p == '\r' || *p == '\n') p++;
    }
    return -1;
}

static void trim(char *s) {
    char *b = s;
    while (*b == ' ' || *b == '\t') b++;
    if (b != s) memmove(s, b, strlen(b) + 1);
    size_t n = strlen(s);
    while (n && (s[n - 1] == ' ' || s[n - 1] == '\t')) s[--n] = 0;
}

/* "Name, Icon, Class" -> field index */
static bool midlet_field(const char *spec, int field, char *out, int outlen) {
    const char *p = spec;
    for (int i = 0; i < field; i++) {
        p = strchr(p, ',');
        if (!p) return false;
        p++;
    }
    const char *e = strchr(p, ',');
    int n = e ? (int)(e - p) : (int)strlen(p);
    if (n >= outlen) n = outlen - 1;
    memcpy(out, p, n);
    out[n] = 0;
    trim(out);
    return true;
}

int j4i_manifest_value(const char *jar_path, const char *key, char *out, int out_len) {
    Zip *z = zip_open_file(jar_path);
    if (!z) return -1;
    int len;
    char *mf = (char *)zip_read(z, "META-INF/MANIFEST.MF", &len);
    int r = -1;
    if (mf) {
        r = manifest_lookup(mf, key, out, out_len);
        free(mf);
    }
    zip_close(z);
    return r;
}

uint8_t *j4i_read_icon(const char *jar_path, int *len) {
    Zip *z = zip_open_file(jar_path);
    if (!z) return NULL;
    int ml;
    char *mf = (char *)zip_read(z, "META-INF/MANIFEST.MF", &ml);
    uint8_t *data = NULL;
    if (mf) {
        char buf[512], icon[512];
        icon[0] = 0;
        if (manifest_lookup(mf, "MIDlet-Icon", buf, sizeof buf) > 0) snprintf(icon, sizeof icon, "%s", buf);
        else if (manifest_lookup(mf, "MIDlet-1", buf, sizeof buf) > 0) midlet_field(buf, 1, icon, sizeof icon);
        trim(icon);
        if (icon[0]) data = zip_read(z, icon[0] == '/' ? icon + 1 : icon, len);
        free(mf);
    }
    zip_close(z);
    return data;
}

/* ---------------- boot ---------------- */
static Class *must_class(const char *name) {
    Class *c = class_find(NULL, sym(name));
    if (!c) vm_fatal("missing runtime class %s", name);
    return c;
}

static Field *must_field(Class *c, const char *name) {
    Field *f = find_field_by_name(c, name);
    if (!f) vm_fatal("missing field %s.%s", c->name->str, name);
    return f;
}

static void shutdown_vm(void) {
    while (vm.threads) thread_free(vm.threads);
    monitors_free();
    midp_free();
    font_cache_free();
    strings_free();
    heap_destroy();
    classes_free();
    sym_free_all();
    meta_free_all();
    for (int i = 0; i < vm.n_jars; i++) zip_close(vm.jars[i]);
    vm.n_jars = 0;
    vm.game = NULL;
    free(vm.manifest);
    vm.manifest = NULL;
    free(vm.jad_props);
    vm.jad_props = NULL;
    free(vm.sys_props);
    vm.sys_props = NULL;
    midi_cache_free();
    os_destroy();
}

static int run_vm(const J4IConfig *cfg) {
    volatile int base_marker = 0;
    memset(&vm, 0, sizeof vm);
    vm.cfg = *cfg;
    if (vm.cfg.screen_w <= 0 || vm.cfg.screen_h <= 0) {
        vm.cfg.screen_w = 240;
        vm.cfg.screen_h = 320;
    }
    if (vm.cfg.heap_mb <= 0) vm.cfg.heap_mb = 64;
    vm.trace = cfg->trace;
    gc_set_stack_base((void *)&base_marker);
    os_init();
    events_reset();
    heap_init((size_t)vm.cfg.heap_mb << 20);

    vm.s_init = sym("<init>");
    vm.s_clinit = sym("<clinit>");
    vm.s_run = sym("run");
    vm.s_V = sym("()V");
    vm.s_main = sym("main");
    vm.s_mainDesc = sym("([Ljava/lang/String;)V");

    Zip *rt = cfg->runtime_jar ? zip_open_file(cfg->runtime_jar) : NULL;
    if (!rt) {
        vm_log("cannot open runtime jar %s", cfg->runtime_jar ? cfg->runtime_jar : "(null)");
        shutdown_vm();
        return -1;
    }
    vm.jars[vm.n_jars++] = rt;
    if (cfg->game_jar) {
        Zip *g = zip_open_file(cfg->game_jar);
        if (!g) {
            vm_log("cannot open game jar %s", cfg->game_jar);
            shutdown_vm();
            return -1;
        }
        vm.jars[vm.n_jars++] = g;
        vm.game = g;
        int len;
        vm.manifest = (char *)zip_read(g, "META-INF/MANIFEST.MF", &len);
    }
    if (cfg->jad_path) {
        size_t jl;
        vm.jad_props = (char *)os_read_file(cfg->jad_path, &jl);
        if (!vm.jad_props) vm_log("cannot read JAD %s", cfg->jad_path);
    }
    if (cfg->sys_props) {
        /* accept "key=value" or "key: value" lines; store as "key: value" for manifest_lookup */
        size_t n = strlen(cfg->sys_props);
        vm.sys_props = (char *)malloc(n * 2 + 2);
        char *o = vm.sys_props;
        const char *s = cfg->sys_props;
        while (*s) {
            const char *e = s;
            while (*e && *e != '\n') e++;
            const char *eq = s;
            while (eq < e && *eq != '=' && *eq != ':') eq++;
            if (eq < e && eq > s) {
                memcpy(o, s, (size_t)(eq - s));
                o += eq - s;
                *o++ = ':';
                *o++ = ' ';
                const char *v = eq + 1;
                while (v < e && *v == ' ') v++;
                memcpy(o, v, (size_t)(e - v));
                o += e - v;
                *o++ = '\n';
            }
            s = *e ? e + 1 : e;
        }
        *o = 0;
    }

    natives_init();

    vm.c_Object = must_class("java/lang/Object");
    vm.c_Class = must_class("java/lang/Class");
    vm.c_String = must_class("java/lang/String");
    vm.c_Thread = must_class("java/lang/Thread");
    vm.c_Throwable = must_class("java/lang/Throwable");
    vm.c_arr_C = must_class("[C");
    vm.c_arr_B = must_class("[B");
    vm.c_arr_I = must_class("[I");
    vm.c_arr_J = must_class("[J");
    vm.c_arr_Object = must_class("[Ljava/lang/Object;");
    vm.c_arr_String = must_class("[Ljava/lang/String;");
    vm.f_String_value = must_field(vm.c_String, "value");
    vm.f_String_offset = must_field(vm.c_String, "offset");
    vm.f_String_count = must_field(vm.c_String, "count");
    vm.f_Class_vm = must_field(vm.c_Class, "vm");
    vm.f_Throwable_message = must_field(vm.c_Throwable, "detailMessage");
    vm.f_Throwable_trace = must_field(vm.c_Throwable, "trace");
    vm.f_Thread_vm = must_field(vm.c_Thread, "vm");
    vm.f_Thread_name = must_field(vm.c_Thread, "name");

    /* decide what to run */
    char cls[512];
    const char *mode = "midlet";
    cls[0] = 0;
    if (cfg->main_class && cfg->main_class[0]) {
        snprintf(cls, sizeof cls, "%s", cfg->main_class);
        char path[600];
        snprintf(path, sizeof path, "%s", cls);
        for (char *p = path; *p; p++)
            if (*p == '.') *p = '/';
        Class *c = class_find(NULL, sym(path));
        bool is_midlet = false;
        for (Class *k = c; k; k = k->super)
            if (!strcmp(k->name->str, "javax/microedition/midlet/MIDlet")) is_midlet = true;
        if (!is_midlet) mode = "main";
    } else if (vm.manifest) {
        char key[32], spec[1024];
        snprintf(key, sizeof key, "MIDlet-%d", cfg->midlet_index > 0 ? cfg->midlet_index : 1);
        if (manifest_lookup(vm.manifest, key, spec, sizeof spec) > 0) midlet_field(spec, 2, cls, sizeof cls);
    }
    if (!cls[0]) {
        vm_log("no MIDlet found in manifest");
        shutdown_vm();
        return -1;
    }
    vm_log("J2ME4iOS: starting %s %s (%dx%d, heap %d MB)", mode, cls, vm.cfg.screen_w, vm.cfg.screen_h, vm.cfg.heap_mb);

    Thread *t = thread_new(NULL);
    Object *jt = alloc_object(t, vm.c_Thread);
    t->jthread = jt;
    OBJ_FIELD(jt, vm.f_Thread_name->offset)->l = string_new_utf8(t, "main");
    OBJ_FIELD(jt, vm.f_Thread_vm->offset)->j = t->id;
    Field *pf = find_field_by_name(vm.c_Thread, "priority");
    if (pf) OBJ_FIELD(jt, pf->offset)->i = 5;

    /* build String[] args: mode, class, extra args */
    char *extra[64];
    int nextra = 0;
    char *argcopy = cfg->main_args ? strdup(cfg->main_args) : NULL;
    if (argcopy) {
        for (char *tok = strtok(argcopy, " "); tok && nextra < 64; tok = strtok(NULL, " ")) extra[nextra++] = tok;
    }
    Array *args = alloc_array(t, vm.c_arr_String, 2 + nextra);
    ARR_L(args)[0] = string_new_utf8(t, mode);
    ARR_L(args)[1] = string_new_utf8(t, cls);
    for (int i = 0; i < nextra; i++) ARR_L(args)[2 + i] = string_new_utf8(t, extra[i]);
    free(argcopy);

    Class *launcher = must_class("j4i/Launcher");
    Method *lm = find_method_local(launcher, vm.s_main, vm.s_mainDesc);
    if (!lm) vm_fatal("j4i.Launcher.main missing");
    t->stack[0].l = (Object *)args;
    push_frame(t, lm, t->stack, 0);
    class_ensure_init(t, launcher);

    sched_loop();

    int code = vm.exit_code;
    vm_log("J2ME4iOS: VM exited (%d)", code);
    J4IHost host = vm.cfg.host;
    shutdown_vm();
    if (host.exited) host.exited(host.user, code);
    (void)base_marker;
    return code;
}

/* ---------------- instances ---------------- */
static J4IInstance g_legacy; /* used by j4i_run / j4i_post_event (single VM hosts) */

J4IInstance *vm_default_instance(void) { return &g_legacy; }

J4IInstance *j4i_instance_new(void) { return (J4IInstance *)calloc(1, sizeof(J4IInstance)); }

void j4i_instance_free(J4IInstance *in) {
    if (!in || in == &g_legacy) return;
    os_lock();
    while (in->text_results) {
        TextResult *r = in->text_results;
        in->text_results = r->next;
        free(r->text);
        free(r);
    }
    os_unlock();
    free(in);
}

int j4i_instance_run(J4IInstance *in, const J4IConfig *cfg) {
    if (!in || !cfg) return -1;
    vm_ptr = &in->vm_state;
    in->running = 1;
    int r = run_vm(cfg);
    in->running = 0;
    vm_ptr = NULL;
    return r;
}

int j4i_run(const J4IConfig *cfg) {
    os_lock();
    g_legacy.ev_head = g_legacy.ev_tail = 0;
    g_legacy.force_exit_at = 0;
    os_unlock();
    return j4i_instance_run(&g_legacy, cfg);
}
