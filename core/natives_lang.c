/* Natives for java.lang / java.util helpers and the core j4i.Sys bridge. */
#include "vm.h"
#include <math.h>
#include <errno.h>

#define NATIVE(name) static void name(Thread *t, Value *a, Value *ret)
#define UNUSED_ARGS (void)t; (void)a; (void)ret

/* ---------------- registry ---------------- */
typedef struct NativeEntry {
    const char *cls, *name, *desc;
    NativeFn fn;
} NativeEntry;

static J4I_TLS NativeEntry *natives;
static J4I_TLS int n_natives, cap_natives;

void native_register(const char *cls, const char *name, const char *desc, NativeFn fn) {
    if (n_natives == cap_natives) {
        cap_natives = cap_natives ? cap_natives * 2 : 256;
        natives = (NativeEntry *)realloc(natives, cap_natives * sizeof(NativeEntry));
    }
    natives[n_natives].cls = cls;
    natives[n_natives].name = name;
    natives[n_natives].desc = desc;
    natives[n_natives].fn = fn;
    n_natives++;
}

NativeFn native_lookup(Method *m) {
    for (int i = 0; i < n_natives; i++) {
        NativeEntry *e = &natives[i];
        if (!strcmp(e->name, m->name->str) && !strcmp(e->desc, m->desc->str) && !strcmp(e->cls, m->cls->name->str))
            return e->fn;
    }
    return NULL;
}

void natives_init(void) {
    if (n_natives) return;
    natives_lang_register();
    natives_midp_register();
}

/* ---------------- helpers ---------------- */
static Object *new_str(Thread *t, const char *s) { return string_new_utf8(t, s); }

static Array *new_bytes(Thread *t, const void *data, int len) {
    Array *a = alloc_prim_array(t, 'B', len);
    if (a && len) memcpy(ARR_B(a), data, len);
    return a;
}

/* ---------------- java.lang.Object ---------------- */
NATIVE(Object_getClass) { ret->l = class_mirror(t, a[0].l->cls); }
NATIVE(Object_hashCode) { ret->i = (int32_t)obj_identity_hash(a[0].l); }

NATIVE(Object_clone) {
    Object *o = a[0].l;
    if (o->cls->is_array) {
        Array *src = (Array *)o;
        Array *dst = alloc_array(t, o->cls, src->length);
        if (!dst) return;
        memcpy(ARR_DATA(dst), ARR_DATA(src), (size_t)src->length * o->cls->elem_size);
        ret->l = (Object *)dst;
        return;
    }
    Object *n = alloc_object(t, o->cls);
    if (!n) return;
    memcpy((char *)n + sizeof(Object), (char *)o + sizeof(Object), o->cls->inst_size - sizeof(Object));
    ret->l = n;
}

NATIVE(Object_notify) {
    if (!monitor_notify(t, a[0].l, false)) throw_new(t, "java/lang/IllegalMonitorStateException", NULL);
}
NATIVE(Object_notifyAll) {
    if (!monitor_notify(t, a[0].l, true)) throw_new(t, "java/lang/IllegalMonitorStateException", NULL);
}
NATIVE(Object_wait) {
    int64_t ms = a[1].j;
    if (ms < 0) { throw_new(t, "java/lang/IllegalArgumentException", "timeout value is negative"); return; }
    if (t->interrupted) {
        t->interrupted = false;
        throw_new(t, "java/lang/InterruptedException", NULL);
        return;
    }
    if (!monitor_wait(t, a[0].l, ms)) throw_new(t, "java/lang/IllegalMonitorStateException", NULL);
}

/* ---------------- java.lang.Class ---------------- */
NATIVE(Class_forName) {
    Object *name = a[0].l;
    if (!name) { throw_new(t, "java/lang/NullPointerException", NULL); return; }
    char *s = string_to_utf8(name);
    for (char *p = s; *p; p++)
        if (*p == '.') *p = '/';
    Class *c = class_find(t, sym(s));
    if (!c) {
        t->exception = NULL;
        for (char *p = s; *p; p++)
            if (*p == '/') *p = '.';
        throw_new(t, "java/lang/ClassNotFoundException", s);
        free(s);
        return;
    }
    free(s);
    int r = class_ensure_init(t, c);
    if (r < 0) return;
    if (r > 0) { t->reexecute = true; return; }
    ret->l = class_mirror(t, c);
}

NATIVE(Class_newInstance) {
    Class *c = mirror_class(a[0].l);
    if (c->is_array || (c->access & (ACC_ABSTRACT | ACC_INTERFACE))) {
        throw_new(t, "java/lang/InstantiationException", c->name->str);
        return;
    }
    int r = class_ensure_init(t, c);
    if (r < 0) return;
    if (r > 0) { t->reexecute = true; return; }
    Method *init = find_method_local(c, vm.s_init, vm.s_V);
    if (!init) {
        throw_new(t, "java/lang/InstantiationException", c->name->str);
        return;
    }
    Object *o = alloc_object(t, c);
    if (!o) return;
    ret->l = o;
    t->pending = init;
    t->pending_args[0].l = o;
    t->pending_nargs = 1;
}

NATIVE(Class_isInstance) { ret->i = instance_of(a[1].l, mirror_class(a[0].l)); }
NATIVE(Class_isAssignableFrom) {
    if (!a[1].l) { throw_new(t, "java/lang/NullPointerException", NULL); return; }
    ret->i = is_assignable(mirror_class(a[1].l), mirror_class(a[0].l));
}
NATIVE(Class_isInterface) { ret->i = (mirror_class(a[0].l)->access & ACC_INTERFACE) != 0; }
NATIVE(Class_isArray) { ret->i = mirror_class(a[0].l)->is_array; }
NATIVE(Class_getName0) {
    char *n = class_java_name(mirror_class(a[0].l));
    ret->l = new_str(t, n);
    free(n);
}

/* ---------------- java.lang.String ---------------- */
NATIVE(String_intern) { ret->l = string_intern(t, a[0].l); }

/* ---------------- java.lang.System / Runtime ---------------- */
NATIVE(System_currentTimeMillis) { UNUSED_ARGS; ret->j = os_now_ms(); }
NATIVE(System_identityHashCode) { ret->i = a[0].l ? (int32_t)obj_identity_hash(a[0].l) : 0; }

NATIVE(System_arraycopy) {
    Object *src = a[0].l, *dst = a[2].l;
    int32_t sp = a[1].i, dp = a[3].i, len = a[4].i;
    if (!src || !dst) { throw_new(t, "java/lang/NullPointerException", NULL); return; }
    Class *sc = src->cls, *dc = dst->cls;
    if (!sc->is_array || !dc->is_array) { throw_new(t, "java/lang/ArrayStoreException", NULL); return; }
    bool sref = sc->elem == 'L' || sc->elem == '[', dref = dc->elem == 'L' || dc->elem == '[';
    if (sref != dref || (!sref && sc->elem != dc->elem)) { throw_new(t, "java/lang/ArrayStoreException", NULL); return; }
    Array *s = (Array *)src, *d = (Array *)dst;
    if (len < 0 || sp < 0 || dp < 0 || (int64_t)sp + len > s->length || (int64_t)dp + len > d->length) {
        throw_newf(t, "java/lang/ArrayIndexOutOfBoundsException", "arraycopy: src %d dst %d len %d", sp, dp, len);
        return;
    }
    if (len == 0) return;
    int es = sc->elem_size;
    if (sref && sc != dc && !is_assignable(sc, dc)) {
        Class *ec = dc->component;
        Object **so = ARR_L(s), **dob = ARR_L(d);
        if (src == dst) { memmove(dob + dp, so + sp, (size_t)len * es); return; }
        for (int i = 0; i < len; i++) {
            Object *o = so[sp + i];
            if (o && ec && !is_assignable(o->cls, ec)) { throw_new(t, "java/lang/ArrayStoreException", NULL); return; }
            dob[dp + i] = o;
        }
        return;
    }
    memmove((char *)ARR_DATA(d) + (size_t)dp * es, (char *)ARR_DATA(s) + (size_t)sp * es, (size_t)len * es);
}

NATIVE(Runtime_freeMemory) { UNUSED_ARGS; ret->j = heap_free_bytes(); }
NATIVE(Runtime_totalMemory) { UNUSED_ARGS; ret->j = heap_total_bytes(); }
NATIVE(Runtime_gc) { UNUSED_ARGS; gc_collect(); }

/* ---------------- java.lang.Thread ---------------- */
NATIVE(Thread_start0) {
    Object *jt = a[0].l;
    Method *run = find_virtual(jt->cls, vm.s_run, vm.s_V);
    if (!run) { throw_new(t, "java/lang/AbstractMethodError", "run"); return; }
    Thread *nt = thread_new(jt);
    OBJ_FIELD(jt, vm.f_Thread_vm->offset)->j = nt->id;
    nt->stack[0].l = jt;
    push_frame(nt, run, nt->stack, 0);
}

static Object *ensure_jthread(Thread *t) {
    if (t->jthread) return t->jthread;
    Object *o = alloc_object(t, vm.c_Thread);
    if (!o) return NULL;
    char buf[32];
    snprintf(buf, sizeof buf, t->id == 1 ? "main" : "Thread-%d", t->id);
    Object *name = new_str(t, buf);
    OBJ_FIELD(o, vm.f_Thread_name->offset)->l = name;
    Field *pf = find_field_by_name(vm.c_Thread, "priority");
    if (pf) OBJ_FIELD(o, pf->offset)->i = 5;
    OBJ_FIELD(o, vm.f_Thread_vm->offset)->j = t->id;
    t->jthread = o;
    return o;
}

NATIVE(Thread_currentThread) { ret->l = ensure_jthread(t); }

NATIVE(Thread_sleep) {
    int64_t ms = a[0].j;
    if (ms < 0) { throw_new(t, "java/lang/IllegalArgumentException", "timeout value is negative"); return; }
    if (t->interrupted) {
        t->interrupted = false;
        throw_new(t, "java/lang/InterruptedException", NULL);
        return;
    }
    if (ms == 0) { t->yield = true; return; }
    t->state = TS_SLEEPING;
    t->wake_time = vm_now() + ms;
}

NATIVE(Thread_yield) { UNUSED_ARGS; t->yield = true; }

NATIVE(Thread_activeCount) {
    int n = 0;
    for (Thread *x = vm.threads; x; x = x->next)
        if (x->state != TS_DEAD) n++;
    ret->i = n;
}

NATIVE(Thread_isAlive) { ret->i = OBJ_FIELD(a[0].l, vm.f_Thread_vm->offset)->j != 0; }

NATIVE(Thread_interrupt0) {
    for (Thread *x = vm.threads; x; x = x->next)
        if (x->jthread == a[0].l) x->interrupted = true;
}

/* ---------------- java.lang.Throwable ---------------- */
NATIVE(Throwable_fillInStackTrace0) { fill_stack_trace(t, a[0].l); }
NATIVE(Throwable_printStackTrace0) { print_exception(t, a[0].l, ""); }

/* ---------------- java.lang.Math ---------------- */
#define MATH1(nm, fn) NATIVE(Math_##nm) { (void)t; ret->d = fn(a[0].d); }
MATH1(sin, sin)
MATH1(cos, cos)
MATH1(tan, tan)
MATH1(asin, asin)
MATH1(acos, acos)
MATH1(atan, atan)
MATH1(sqrt, sqrt)
MATH1(ceil, ceil)
MATH1(floor, floor)
MATH1(exp, exp)
MATH1(log, log)
NATIVE(Math_atan2) { (void)t; ret->d = atan2(a[0].d, a[2].d); }
NATIVE(Math_pow) { (void)t; ret->d = pow(a[0].d, a[2].d); }

/* ---------------- Float / Double ---------------- */
static void fmt_java_double(double d, bool is_float, char *out) {
    if (d != d) { strcpy(out, "NaN"); return; }
    if (isinf(d)) { strcpy(out, d > 0 ? "Infinity" : "-Infinity"); return; }
    if (d == 0) { strcpy(out, signbit(d) ? "-0.0" : "0.0"); return; }
    char buf[64];
    int maxp = is_float ? 9 : 17;
    for (int p = 1; p <= maxp; p++) {
        snprintf(buf, sizeof buf, "%.*e", p - 1, d);
        double back = strtod(buf, NULL);
        if (is_float ? ((float)back == (float)d) : (back == d)) break;
    }
    /* buf = [-]d[.ddd]e[+-]xx */
    const char *p = buf;
    bool neg = false;
    if (*p == '-') { neg = true; p++; }
    char digits[32];
    int nd = 0;
    while (*p && *p != 'e') {
        if (*p >= '0' && *p <= '9') digits[nd++] = *p;
        p++;
    }
    while (nd > 1 && digits[nd - 1] == '0') nd--;
    int exp = atoi(p + 1);
    char *o = out;
    if (neg) *o++ = '-';
    double ad = fabs(d);
    if (ad >= 1e-3 && ad < 1e7) {
        if (exp >= 0) {
            for (int i = 0; i <= exp; i++) *o++ = i < nd ? digits[i] : '0';
            *o++ = '.';
            if (nd > exp + 1) for (int i = exp + 1; i < nd; i++) *o++ = digits[i];
            else *o++ = '0';
        } else {
            *o++ = '0';
            *o++ = '.';
            for (int i = 0; i < -exp - 1; i++) *o++ = '0';
            for (int i = 0; i < nd; i++) *o++ = digits[i];
        }
        *o = 0;
    } else {
        *o++ = digits[0];
        *o++ = '.';
        if (nd > 1) for (int i = 1; i < nd; i++) *o++ = digits[i];
        else *o++ = '0';
        sprintf(o, "E%d", exp);
    }
}

NATIVE(Float_toString) {
    char buf[64];
    fmt_java_double(a[0].f, true, buf);
    ret->l = new_str(t, buf);
}
NATIVE(Double_toString) {
    char buf[64];
    fmt_java_double(a[0].d, false, buf);
    ret->l = new_str(t, buf);
}
NATIVE(Float_floatToIntBits) {
    (void)t;
    float f = a[0].f;
    int32_t i;
    if (f != f) i = 0x7fc00000;
    else memcpy(&i, &f, 4);
    ret->i = i;
}
NATIVE(Float_intBitsToFloat) { (void)t; memcpy(&ret->f, &a[0].i, 4); }
NATIVE(Double_doubleToLongBits) {
    (void)t;
    double d = a[0].d;
    int64_t j;
    if (d != d) j = 0x7ff8000000000000LL;
    else memcpy(&j, &d, 8);
    ret->j = j;
}
NATIVE(Double_longBitsToDouble) { (void)t; memcpy(&ret->d, &a[0].j, 8); }
NATIVE(Double_parse0) {
    char *s = string_to_utf8(a[0].l);
    char *end = NULL;
    bool ok = s[0] != 0;
    if (ok && (!strcmp(s, "NaN") || !strcmp(s, "+NaN") || !strcmp(s, "-NaN"))) { ret->d = NAN; free(s); return; }
    if (ok && (!strcmp(s, "Infinity") || !strcmp(s, "+Infinity"))) { ret->d = INFINITY; free(s); return; }
    if (ok && !strcmp(s, "-Infinity")) { ret->d = -INFINITY; free(s); return; }
    for (char *p = s; *p; p++)
        if (*p == 'n' || *p == 'N' || *p == 'i' || *p == 'I' || *p == 'x' || *p == 'X') ok = false;
    double d = ok ? strtod(s, &end) : 0;
    if (!ok || !end || *end) {
        throw_newf(t, "java/lang/NumberFormatException", "For input string: \"%s\"", s);
        free(s);
        return;
    }
    free(s);
    ret->d = d;
}

/* ---------------- j4i.Utf8 ---------------- */
NATIVE(Utf8_decode) {
    Array *b = (Array *)a[0].l;
    int off = a[1].i, len = a[2].i;
    if (!b) { throw_new(t, "java/lang/NullPointerException", NULL); return; }
    if (off < 0 || len < 0 || off + len > b->length) { throw_new(t, "java/lang/IndexOutOfBoundsException", NULL); return; }
    int n = utf8_to_utf16((const uint8_t *)ARR_B(b) + off, len, NULL);
    Array *c = alloc_prim_array(t, 'C', n);
    if (!c) return;
    b = (Array *)a[0].l;
    utf8_to_utf16((const uint8_t *)ARR_B(b) + off, len, ARR_C(c));
    ret->l = (Object *)c;
}
NATIVE(Utf8_encode) {
    Array *c = (Array *)a[0].l;
    int off = a[1].i, len = a[2].i;
    if (!c) { throw_new(t, "java/lang/NullPointerException", NULL); return; }
    int n = utf16_to_utf8(ARR_C(c) + off, len, NULL);
    Array *b = alloc_prim_array(t, 'B', n);
    if (!b) return;
    utf16_to_utf8(ARR_C(c) + off, len, (char *)ARR_B(b));
    ret->l = (Object *)b;
}

/* ---------------- j4i.Sys (core part) ---------------- */
NATIVE(Sys_log) {
    char *s = string_to_utf8(a[0].l);
    vm_log("%s", s);
    free(s);
}
NATIVE(Sys_logBytes) {
    Array *b = (Array *)a[0].l;
    int n = a[1].i;
    char *s = (char *)malloc((size_t)n + 1);
    memcpy(s, ARR_B(b), n);
    s[n] = 0;
    vm_log("%s%s", a[2].i ? "[err] " : "", s);
    free(s);
}

static void normalize_path(char *p) {
    /* resolve "." and ".." components in place */
    char *parts[128];
    int np = 0;
    char *save = p, *tok;
    char tmp[1024];
    snprintf(tmp, sizeof tmp, "%s", p);
    for (tok = strtok(tmp, "/"); tok && np < 128; tok = strtok(NULL, "/")) {
        if (!strcmp(tok, ".") || !*tok) continue;
        if (!strcmp(tok, "..")) { if (np) np--; continue; }
        parts[np++] = tok;
    }
    char out[1024];
    out[0] = 0;
    for (int i = 0; i < np; i++) {
        if (i) strncat(out, "/", sizeof out - strlen(out) - 1);
        strncat(out, parts[i], sizeof out - strlen(out) - 1);
    }
    strcpy(save, out);
}

NATIVE(Sys_getResource) {
    if (!a[0].l) return;
    char *s = string_to_utf8(a[0].l);
    normalize_path(s);
    int len = 0;
    uint8_t *data = zip_read(vm.game, s, &len);
    if (!data) {
        for (int i = 0; i < vm.n_jars && !data; i++) data = zip_read(vm.jars[i], s, &len);
    }
    if (!data) TRACE(1, "resource not found: %s", s);
    free(s);
    if (!data) return;
    ret->l = (Object *)new_bytes(t, data, len);
    free(data);
}

int manifest_lookup(const char *text, const char *key, char *out, int outlen);

NATIVE(Sys_getProperty) {
    char *k = string_to_utf8(a[0].l);
    const char *v = NULL;
    char buf[64];
    if (vm.sys_props) { /* per-game overrides */
        char *ov = (char *)malloc(1024);
        if (manifest_lookup(vm.sys_props, k, ov, 1024) >= 0) {
            ret->l = new_str(t, ov);
            free(ov);
            free(k);
            return;
        }
        free(ov);
    }
    if (!strcmp(k, "j4i.font.small") || !strcmp(k, "j4i.font.medium") || !strcmp(k, "j4i.font.large")) {
        int px = k[9] == 's' ? vm.cfg.font_small : k[9] == 'm' ? vm.cfg.font_medium : vm.cfg.font_large;
        if (px > 0) {
            snprintf(buf, sizeof buf, "%d", px);
            v = buf;
        }
    } else if (!strcmp(k, "microedition.platform")) v = "Nokia6300/07.21 (J2ME4iOS)";
    else if (!strcmp(k, "microedition.configuration")) v = "CLDC-1.1";
    else if (!strcmp(k, "microedition.profiles")) v = "MIDP-2.0";
    else if (!strcmp(k, "microedition.locale")) v = "en-US";
    else if (!strcmp(k, "microedition.encoding")) v = "UTF-8";
    else if (!strcmp(k, "microedition.media.version")) v = "1.1";
    else if (!strcmp(k, "microedition.jtwi.version")) v = "1.0";
    else if (!strcmp(k, "supports.mixing")) v = "true";
    else if (!strcmp(k, "supports.audio.capture") || !strcmp(k, "supports.video.capture") || !strcmp(k, "supports.recording")) v = "false";
    else if (!strcmp(k, "audio.encodings")) v = "encoding=pcm";
    else if (!strcmp(k, "com.nokia.mid.ui.version")) v = "1.4";
    else if (!strcmp(k, "com.nokia.keyboard.type")) v = "PhoneKeypad";
    else if (!strcmp(k, "java.vendor")) v = "J2ME4iOS";
    else if (!strcmp(k, "microedition.commports") || !strcmp(k, "microedition.hostname")) v = NULL;
    else if (!strcmp(k, "j4i.screen")) {
        snprintf(buf, sizeof buf, "%dx%d", vm.cfg.screen_w, vm.cfg.screen_h);
        v = buf;
    }
    free(k);
    if (v) ret->l = new_str(t, v);
}

NATIVE(Sys_getAppProperty) {
    char *k = string_to_utf8(a[0].l);
    char *buf = (char *)malloc(4096);
    int n = -1;
    if (vm.jad_props) n = manifest_lookup(vm.jad_props, k, buf, 4096);
    if (n < 0 && vm.manifest) n = manifest_lookup(vm.manifest, k, buf, 4096);
    free(k);
    if (n >= 0) ret->l = new_str(t, buf);
    free(buf);
}

NATIVE(Sys_exit) {
    vm.exit_code = a[0].i;
    vm.exit_requested = 1;
    t->yield = true;
}

NATIVE(Sys_tzOffset) { UNUSED_ARGS; ret->i = os_tz_offset_ms(); }

NATIVE(Sys_invokeMain) {
    Class *c = mirror_class(a[0].l);
    int r = class_ensure_init(t, c);
    if (r < 0) return;
    if (r > 0) { t->reexecute = true; return; }
    Method *m = find_method_local(c, vm.s_main, vm.s_mainDesc);
    if (!m || !(m->access & ACC_STATIC)) {
        throw_newf(t, "java/lang/NoSuchMethodError", "%s.main", c->name->str);
        return;
    }
    t->pending = m;
    t->pending_args[0].l = a[1].l;
    t->pending_nargs = 1;
}

void natives_lang_register(void) {
#define R(c, n, d, f) native_register(c, n, d, f)
    R("java/lang/Object", "getClass", "()Ljava/lang/Class;", Object_getClass);
    R("java/lang/Object", "hashCode", "()I", Object_hashCode);
    R("java/lang/Object", "clone", "()Ljava/lang/Object;", Object_clone);
    R("java/lang/Object", "notify", "()V", Object_notify);
    R("java/lang/Object", "notifyAll", "()V", Object_notifyAll);
    R("java/lang/Object", "wait", "(J)V", Object_wait);
    R("java/lang/Class", "forName", "(Ljava/lang/String;)Ljava/lang/Class;", Class_forName);
    R("java/lang/Class", "newInstance", "()Ljava/lang/Object;", Class_newInstance);
    R("java/lang/Class", "isInstance", "(Ljava/lang/Object;)Z", Class_isInstance);
    R("java/lang/Class", "isAssignableFrom", "(Ljava/lang/Class;)Z", Class_isAssignableFrom);
    R("java/lang/Class", "isInterface", "()Z", Class_isInterface);
    R("java/lang/Class", "isArray", "()Z", Class_isArray);
    R("java/lang/Class", "getName0", "()Ljava/lang/String;", Class_getName0);
    R("java/lang/String", "intern", "()Ljava/lang/String;", String_intern);
    R("java/lang/System", "currentTimeMillis", "()J", System_currentTimeMillis);
    R("java/lang/System", "arraycopy", "(Ljava/lang/Object;ILjava/lang/Object;II)V", System_arraycopy);
    R("java/lang/System", "identityHashCode", "(Ljava/lang/Object;)I", System_identityHashCode);
    R("java/lang/Runtime", "freeMemory", "()J", Runtime_freeMemory);
    R("java/lang/Runtime", "totalMemory", "()J", Runtime_totalMemory);
    R("java/lang/Runtime", "gc", "()V", Runtime_gc);
    R("java/lang/Thread", "start0", "()V", Thread_start0);
    R("java/lang/Thread", "interrupt0", "()V", Thread_interrupt0);
    R("java/lang/Thread", "currentThread", "()Ljava/lang/Thread;", Thread_currentThread);
    R("java/lang/Thread", "sleep", "(J)V", Thread_sleep);
    R("java/lang/Thread", "yield", "()V", Thread_yield);
    R("java/lang/Thread", "activeCount", "()I", Thread_activeCount);
    R("java/lang/Thread", "isAlive", "()Z", Thread_isAlive);
    R("java/lang/Throwable", "fillInStackTrace0", "()V", Throwable_fillInStackTrace0);
    R("java/lang/Throwable", "printStackTrace0", "()V", Throwable_printStackTrace0);
    R("java/lang/Math", "sin", "(D)D", Math_sin);
    R("java/lang/Math", "cos", "(D)D", Math_cos);
    R("java/lang/Math", "tan", "(D)D", Math_tan);
    R("java/lang/Math", "asin", "(D)D", Math_asin);
    R("java/lang/Math", "acos", "(D)D", Math_acos);
    R("java/lang/Math", "atan", "(D)D", Math_atan);
    R("java/lang/Math", "sqrt", "(D)D", Math_sqrt);
    R("java/lang/Math", "ceil", "(D)D", Math_ceil);
    R("java/lang/Math", "floor", "(D)D", Math_floor);
    R("java/lang/Math", "exp", "(D)D", Math_exp);
    R("java/lang/Math", "log", "(D)D", Math_log);
    R("java/lang/Math", "atan2", "(DD)D", Math_atan2);
    R("java/lang/Math", "pow", "(DD)D", Math_pow);
    R("java/lang/Float", "toString", "(F)Ljava/lang/String;", Float_toString);
    R("java/lang/Float", "floatToIntBits", "(F)I", Float_floatToIntBits);
    R("java/lang/Float", "intBitsToFloat", "(I)F", Float_intBitsToFloat);
    R("java/lang/Double", "toString", "(D)Ljava/lang/String;", Double_toString);
    R("java/lang/Double", "doubleToLongBits", "(D)J", Double_doubleToLongBits);
    R("java/lang/Double", "longBitsToDouble", "(J)D", Double_longBitsToDouble);
    R("java/lang/Double", "parse0", "(Ljava/lang/String;)D", Double_parse0);
    R("j4i/Utf8", "decode", "([BII)[C", Utf8_decode);
    R("j4i/Utf8", "encode", "([CII)[B", Utf8_encode);
    R("j4i/Sys", "log", "(Ljava/lang/String;)V", Sys_log);
    R("j4i/Sys", "logBytes", "([BIZ)V", Sys_logBytes);
    R("j4i/Sys", "getResource", "(Ljava/lang/String;)[B", Sys_getResource);
    R("j4i/Sys", "getProperty", "(Ljava/lang/String;)Ljava/lang/String;", Sys_getProperty);
    R("j4i/Sys", "getAppProperty", "(Ljava/lang/String;)Ljava/lang/String;", Sys_getAppProperty);
    R("j4i/Sys", "exit", "(I)V", Sys_exit);
    R("j4i/Sys", "tzOffset", "()I", Sys_tzOffset);
    R("j4i/Sys", "invokeMain", "(Ljava/lang/Class;[Ljava/lang/String;)V", Sys_invokeMain);
#undef R
}
