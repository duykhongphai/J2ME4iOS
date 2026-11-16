/* Bytecode interpreter. One call runs a green thread until it blocks, finishes or
 * exhausts its instruction budget. Java->Java calls never recurse on the C stack. */
#include "vm.h"
#include <math.h>
#include <stdarg.h>

#define U2(p) ((uint16_t)(((p)[0] << 8) | (p)[1]))
#define S2(p) ((int16_t)U2(p))
#define S4(p) ((int32_t)(((uint32_t)(p)[0] << 24) | ((uint32_t)(p)[1] << 16) | ((uint32_t)(p)[2] << 8) | (uint32_t)(p)[3]))

/* ---------------- exceptions ---------------- */
static int exc_depth;

Object *exception_new(Thread *t, const char *cls, const char *msg) {
    if (exc_depth > 2) vm_fatal("recursive exception creating %s (%s)", cls, msg ? msg : "");
    exc_depth++;
    Object *o = NULL;
    Class *c = class_find_c(t, cls);
    if (c) {
        o = alloc_object(t, c);
        if (o && msg) {
            Object *s = string_new_utf8(t, msg);
            OBJ_FIELD(o, vm.f_Throwable_message->offset)->l = s;
        }
        if (o) fill_stack_trace(t, o);
    }
    exc_depth--;
    return o;
}

void throw_new(Thread *t, const char *cls, const char *msg) {
    if (!t) {
        vm_log("exception without thread: %s: %s", cls, msg ? msg : "");
        return;
    }
    Object *e = exception_new(t, cls, msg);
    if (e) t->exception = e;
    TRACE(1, "throw %s: %s", cls, msg ? msg : "");
}

void throw_newf(Thread *t, const char *cls, const char *fmt, ...) {
    char buf[512];
    va_list ap;
    va_start(ap, fmt);
    vsnprintf(buf, sizeof buf, fmt, ap);
    va_end(ap);
    throw_new(t, cls, buf);
}

void fill_stack_trace(Thread *t, Object *thr) {
    if (!vm.f_Throwable_trace || t->fp < 0) return;
    int top = t->fp;
    /* skip the Throwable constructor frames */
    while (top > 0) {
        Method *m = t->frames[top].m;
        if ((m->name == vm.s_init || !strcmp(m->name->str, "fillInStackTrace")) && is_subclass(m->cls, vm.c_Throwable))
            top--;
        else break;
    }
    int n = top + 1;
    if (n > 64) n = 64;
    Array *a = alloc_prim_array(t, 'J', n * 2);
    if (!a) { t->exception = NULL; return; }
    for (int i = 0; i < n; i++) {
        Frame *f = &t->frames[top - i];
        ARR_J(a)[i * 2] = (int64_t)(intptr_t)f->m;
        ARR_J(a)[i * 2 + 1] = (int64_t)(f->pc - f->m->code);
    }
    OBJ_FIELD(thr, vm.f_Throwable_trace->offset)->l = (Object *)a;
}

void print_exception(Thread *t, Object *exc, const char *prefix) {
    if (!exc) return;
    char *cn = class_java_name(exc->cls);
    Object *msg = OBJ_FIELD(exc, vm.f_Throwable_message->offset)->l;
    char *ms = msg ? string_to_utf8(msg) : NULL;
    vm_log("%s%s%s%s", prefix ? prefix : "", cn, ms ? ": " : "", ms ? ms : "");
    free(cn);
    free(ms);
    Array *tr = (Array *)OBJ_FIELD(exc, vm.f_Throwable_trace->offset)->l;
    if (tr && tr->hdr.cls == vm.c_arr_J) {
        for (int i = 0; i + 1 < tr->length; i += 2) {
            Method *m = (Method *)(intptr_t)ARR_J(tr)[i];
            int pc = (int)ARR_J(tr)[i + 1];
            char *k = class_java_name(m->cls);
            int line = line_number(m, pc);
            if (line >= 0)
                vm_log("\tat %s.%s(%s:%d)", k, m->name->str, m->cls->source ? m->cls->source->str : "?", line);
            else
                vm_log("\tat %s.%s(pc %d)", k, m->name->str, pc);
            free(k);
        }
    }
}

/* ---------------- frames ---------------- */
int push_frame(Thread *t, Method *m, Value *args, int ret_adv) {
    Value *stack_base = args + m->max_locals;
    if (t->fp + 1 >= THREAD_MAX_FRAMES || stack_base + m->max_stack + 4 > t->stack_end) {
        throw_new(t, "java/lang/StackOverflowError", m->name->str);
        return -1;
    }
    Frame *nf = &t->frames[++t->fp];
    nf->m = m;
    nf->pc = m->code;
    nf->locals = args;
    nf->stack_base = stack_base;
    nf->sp = stack_base;
    nf->clinit_of = NULL;
    nf->sync_obj = NULL;
    nf->ret_adv = (uint8_t)ret_adv;
    return 0;
}

static inline int32_t f2i(double d) {
    if (d != d) return 0;
    if (d >= 2147483647.0) return INT32_MAX;
    if (d <= -2147483648.0) return INT32_MIN;
    return (int32_t)d;
}
static inline int64_t f2l(double d) {
    if (d != d) return 0;
    if (d >= 9223372036854775807.0) return INT64_MAX;
    if (d <= -9223372036854775808.0) return INT64_MIN;
    return (int64_t)d;
}

static Array *multi_new(Thread *t, Class *ac, const int32_t *counts, int ndims) {
    Array *a = alloc_array(t, ac, counts[0]);
    if (!a) return NULL;
    if (ndims > 1 && ac->component) {
        for (int i = 0; i < counts[0]; i++) {
            Array *sub = multi_new(t, ac->component, counts + 1, ndims - 1);
            if (!sub) return NULL;
            ARR_L(a)[i] = (Object *)sub;
        }
    }
    return a;
}

static Method *iface_lookup(Thread *t, Class *rc, Method *rm, CPEntry *e) {
    if (e->ic_cls == rc) return e->ic_m;
    Method *mm = find_virtual(rc, rm->name, rm->desc);
    if (!mm) {
        throw_newf(t, "java/lang/AbstractMethodError", "%s.%s%s", rc->name->str, rm->name->str, rm->desc->str);
        return NULL;
    }
    e->ic_cls = rc;
    e->ic_m = mm;
    return mm;
}

static const char *array_class_name_for_atype(int atype) {
    switch (atype) {
    case 4: return "[Z";
    case 5: return "[C";
    case 6: return "[F";
    case 7: return "[D";
    case 8: return "[B";
    case 9: return "[S";
    case 10: return "[I";
    case 11: return "[J";
    }
    return NULL;
}

/* ---------------- interpreter ---------------- */
void interp_run(Thread *t, int budget) {
    Frame *f;
    Method *m;
    uint8_t *pc;
    Value *sp;
    Value *locals;
    CPEntry *cp;
    Method *callee = NULL;
    int inv_len = 3;

#define LOAD()                    \
    do {                          \
        f = &t->frames[t->fp];    \
        m = f->m;                 \
        pc = f->pc;               \
        sp = f->sp;               \
        locals = f->locals;       \
        cp = m->cls->cp;          \
    } while (0)
#define SAVE() (f->pc = pc, f->sp = sp)
#define THROW(cls, msg)          \
    do {                         \
        SAVE();                  \
        throw_new(t, cls, msg);  \
        goto exception;          \
    } while (0)
#define NULLCHECK(o) \
    if (!(o)) THROW("java/lang/NullPointerException", NULL)
#define BRANCH(off)                              \
    do {                                         \
        int32_t o_ = (off);                      \
        pc += o_;                                \
        if (o_ <= 0 && --budget <= 0) {          \
            SAVE();                              \
            return;                              \
        }                                        \
    } while (0)
#define ENSURE_INIT(c)                                   \
    if ((c)->state != CS_INITIALIZED) {                  \
        SAVE();                                          \
        int r_ = class_ensure_init(t, (c));              \
        if (r_ < 0) goto exception;                      \
        if (r_ > 0) {                                    \
            if (t->yield) { t->yield = false; return; }  \
            LOAD();                                      \
            continue;                                    \
        }                                                \
    }
#define RESOLVE_CLASS(var, idx)                                                       \
    Class *var = cp[idx].resolved ? cp[idx].v.cls : (SAVE(), resolve_class(t, m->cls, idx)); \
    if (!var) goto exception;
#define RESOLVE_FIELD(var, idx)                                                       \
    Field *var = cp[idx].resolved ? cp[idx].v.field : (SAVE(), resolve_field(t, m->cls, idx)); \
    if (!var) goto exception;
#define RESOLVE_METHOD(var, idx)                                                      \
    Method *var = cp[idx].resolved ? cp[idx].v.method : (SAVE(), resolve_method(t, m->cls, idx)); \
    if (!var) goto exception;
#define AIDX(a, idx)                                                         \
    NULLCHECK(a);                                                            \
    if ((uint32_t)(idx) >= (uint32_t)((Array *)(a))->length) {               \
        char b_[64];                                                         \
        snprintf(b_, sizeof b_, "Index %d out of bounds for length %d",      \
                 (int)(idx), (int)((Array *)(a))->length);                   \
        THROW("java/lang/ArrayIndexOutOfBoundsException", b_);               \
    }

    if (t->fp < 0) return;
    LOAD();
    if (t->exception) goto exception;

    for (;;) {
        switch (*pc) {
        case 0x00: pc++; break;
        case 0x01: (sp++)->l = NULL; pc++; break;
        case 0x02: case 0x03: case 0x04: case 0x05: case 0x06: case 0x07: case 0x08:
            (sp++)->i = (int)*pc - 0x03;
            pc++;
            break;
        case 0x09: case 0x0a: sp->j = *pc - 0x09; sp += 2; pc++; break;
        case 0x0b: case 0x0c: case 0x0d: (sp++)->f = (float)(*pc - 0x0b); pc++; break;
        case 0x0e: case 0x0f: sp->d = (double)(*pc - 0x0e); sp += 2; pc++; break;
        case 0x10: (sp++)->i = (int8_t)pc[1]; pc += 2; break;
        case 0x11: (sp++)->i = S2(pc + 1); pc += 3; break;
        case 0x12: case 0x13: {
            int idx = *pc == 0x12 ? pc[1] : U2(pc + 1);
            int len = *pc == 0x12 ? 2 : 3;
            CPEntry *e = &cp[idx];
            switch (e->tag) {
            case CT_INT: (sp++)->i = e->v.i; break;
            case CT_FLOAT: (sp++)->f = e->v.f; break;
            case CT_STRING: {
                Object *s = e->resolved ? e->v.str : (SAVE(), resolve_string(t, m->cls, idx));
                if (!s) goto exception;
                (sp++)->l = s;
                break;
            }
            case CT_CLASS: {
                RESOLVE_CLASS(c, idx);
                SAVE();
                Object *mi = class_mirror(t, c);
                if (!mi) goto exception;
                (sp++)->l = mi;
                break;
            }
            default: THROW("java/lang/VirtualMachineError", "bad ldc");
            }
            pc += len;
            break;
        }
        case 0x14: {
            CPEntry *e = &cp[U2(pc + 1)];
            if (e->tag == CT_LONG) sp->j = e->v.j;
            else sp->d = e->v.d;
            sp += 2;
            pc += 3;
            break;
        }
        case 0x15: case 0x17: case 0x19: *sp++ = locals[pc[1]]; pc += 2; break;
        case 0x16: case 0x18: sp[0] = locals[pc[1]]; sp += 2; pc += 2; break;
        case 0x1a: case 0x1b: case 0x1c: case 0x1d: *sp++ = locals[*pc - 0x1a]; pc++; break;
        case 0x1e: case 0x1f: case 0x20: case 0x21: sp[0] = locals[*pc - 0x1e]; sp += 2; pc++; break;
        case 0x22: case 0x23: case 0x24: case 0x25: *sp++ = locals[*pc - 0x22]; pc++; break;
        case 0x26: case 0x27: case 0x28: case 0x29: sp[0] = locals[*pc - 0x26]; sp += 2; pc++; break;
        case 0x2a: case 0x2b: case 0x2c: case 0x2d: *sp++ = locals[*pc - 0x2a]; pc++; break;

        /* array loads */
        case 0x2e: { int32_t i = sp[-1].i; Object *a = sp[-2].l; AIDX(a, i); sp--; sp[-1].i = ARR_I(a)[i]; pc++; break; }
        case 0x2f: { int32_t i = sp[-1].i; Object *a = sp[-2].l; AIDX(a, i); sp[-2].j = ARR_J(a)[i]; pc++; break; }
        case 0x30: { int32_t i = sp[-1].i; Object *a = sp[-2].l; AIDX(a, i); sp--; sp[-1].f = ARR_F(a)[i]; pc++; break; }
        case 0x31: { int32_t i = sp[-1].i; Object *a = sp[-2].l; AIDX(a, i); sp[-2].d = ARR_D(a)[i]; pc++; break; }
        case 0x32: { int32_t i = sp[-1].i; Object *a = sp[-2].l; AIDX(a, i); sp--; sp[-1].l = ARR_L(a)[i]; pc++; break; }
        case 0x33: { int32_t i = sp[-1].i; Object *a = sp[-2].l; AIDX(a, i); sp--; sp[-1].i = ARR_B(a)[i]; pc++; break; }
        case 0x34: { int32_t i = sp[-1].i; Object *a = sp[-2].l; AIDX(a, i); sp--; sp[-1].i = ARR_C(a)[i]; pc++; break; }
        case 0x35: { int32_t i = sp[-1].i; Object *a = sp[-2].l; AIDX(a, i); sp--; sp[-1].i = ARR_S(a)[i]; pc++; break; }

        case 0x36: case 0x38: case 0x3a: locals[pc[1]] = *--sp; pc += 2; break;
        case 0x37: case 0x39: sp -= 2; locals[pc[1]] = sp[0]; pc += 2; break;
        case 0x3b: case 0x3c: case 0x3d: case 0x3e: locals[*pc - 0x3b] = *--sp; pc++; break;
        case 0x3f: case 0x40: case 0x41: case 0x42: sp -= 2; locals[*pc - 0x3f] = sp[0]; pc++; break;
        case 0x43: case 0x44: case 0x45: case 0x46: locals[*pc - 0x43] = *--sp; pc++; break;
        case 0x47: case 0x48: case 0x49: case 0x4a: sp -= 2; locals[*pc - 0x47] = sp[0]; pc++; break;
        case 0x4b: case 0x4c: case 0x4d: case 0x4e: locals[*pc - 0x4b] = *--sp; pc++; break;

        /* array stores */
        case 0x4f: { int32_t v = sp[-1].i, i = sp[-2].i; Object *a = sp[-3].l; AIDX(a, i); ARR_I(a)[i] = v; sp -= 3; pc++; break; }
        case 0x50: { int64_t v = sp[-2].j; int32_t i = sp[-3].i; Object *a = sp[-4].l; AIDX(a, i); ARR_J(a)[i] = v; sp -= 4; pc++; break; }
        case 0x51: { float v = sp[-1].f; int32_t i = sp[-2].i; Object *a = sp[-3].l; AIDX(a, i); ARR_F(a)[i] = v; sp -= 3; pc++; break; }
        case 0x52: { double v = sp[-2].d; int32_t i = sp[-3].i; Object *a = sp[-4].l; AIDX(a, i); ARR_D(a)[i] = v; sp -= 4; pc++; break; }
        case 0x53: {
            Object *v = sp[-1].l;
            int32_t i = sp[-2].i;
            Object *a = sp[-3].l;
            AIDX(a, i);
            if (v) {
                Class *ec = a->cls->component;
                if (ec && ec != vm.c_Object && !is_assignable(v->cls, ec)) THROW("java/lang/ArrayStoreException", v->cls->name->str);
            }
            ARR_L(a)[i] = v;
            sp -= 3;
            pc++;
            break;
        }
        case 0x54: {
            int32_t v = sp[-1].i, i = sp[-2].i;
            Object *a = sp[-3].l;
            AIDX(a, i);
            ARR_B(a)[i] = (int8_t)(a->cls->elem == 'Z' ? (v & 1) : v);
            sp -= 3;
            pc++;
            break;
        }
        case 0x55: case 0x56: { int32_t v = sp[-1].i, i = sp[-2].i; Object *a = sp[-3].l; AIDX(a, i); ARR_C(a)[i] = (uint16_t)v; sp -= 3; pc++; break; }

        /* stack ops */
        case 0x57: sp--; pc++; break;
        case 0x58: sp -= 2; pc++; break;
        case 0x59: sp[0] = sp[-1]; sp++; pc++; break;
        case 0x5a: { Value v1 = sp[-1], v2 = sp[-2]; sp[-2] = v1; sp[-1] = v2; sp[0] = v1; sp++; pc++; break; }
        case 0x5b: { Value v1 = sp[-1], v2 = sp[-2], v3 = sp[-3]; sp[-3] = v1; sp[-2] = v3; sp[-1] = v2; sp[0] = v1; sp++; pc++; break; }
        case 0x5c: sp[0] = sp[-2]; sp[1] = sp[-1]; sp += 2; pc++; break;
        case 0x5d: {
            Value v1 = sp[-1], v2 = sp[-2], v3 = sp[-3];
            sp[-3] = v2; sp[-2] = v1; sp[-1] = v3; sp[0] = v2; sp[1] = v1;
            sp += 2;
            pc++;
            break;
        }
        case 0x5e: {
            Value v1 = sp[-1], v2 = sp[-2], v3 = sp[-3], v4 = sp[-4];
            sp[-4] = v2; sp[-3] = v1; sp[-2] = v4; sp[-1] = v3; sp[0] = v2; sp[1] = v1;
            sp += 2;
            pc++;
            break;
        }
        case 0x5f: { Value v = sp[-1]; sp[-1] = sp[-2]; sp[-2] = v; pc++; break; }

        /* arithmetic */
#define IBIN(expr) { int32_t b = sp[-1].i, a = sp[-2].i; (void)a; (void)b; sp--; sp[-1].i = (expr); pc++; break; }
#define LBIN(expr) { int64_t b = sp[-2].j, a = sp[-4].j; (void)a; (void)b; sp -= 2; sp[-2].j = (expr); pc++; break; }
#define FBIN(expr) { float b = sp[-1].f, a = sp[-2].f; sp--; sp[-1].f = (expr); pc++; break; }
#define DBIN(expr) { double b = sp[-2].d, a = sp[-4].d; sp -= 2; sp[-2].d = (expr); pc++; break; }
        case 0x60: IBIN((int32_t)((uint32_t)a + (uint32_t)b))
        case 0x61: LBIN((int64_t)((uint64_t)a + (uint64_t)b))
        case 0x62: FBIN(a + b)
        case 0x63: DBIN(a + b)
        case 0x64: IBIN((int32_t)((uint32_t)a - (uint32_t)b))
        case 0x65: LBIN((int64_t)((uint64_t)a - (uint64_t)b))
        case 0x66: FBIN(a - b)
        case 0x67: DBIN(a - b)
        case 0x68: IBIN((int32_t)((uint32_t)a * (uint32_t)b))
        case 0x69: LBIN((int64_t)((uint64_t)a * (uint64_t)b))
        case 0x6a: FBIN(a * b)
        case 0x6b: DBIN(a * b)
        case 0x6c: {
            int32_t b = sp[-1].i, a = sp[-2].i;
            if (b == 0) THROW("java/lang/ArithmeticException", "/ by zero");
            sp--;
            sp[-1].i = (b == -1) ? (int32_t)(0u - (uint32_t)a) : a / b;
            pc++;
            break;
        }
        case 0x6d: {
            int64_t b = sp[-2].j, a = sp[-4].j;
            if (b == 0) THROW("java/lang/ArithmeticException", "/ by zero");
            sp -= 2;
            sp[-2].j = (b == -1) ? (int64_t)(0ull - (uint64_t)a) : a / b;
            pc++;
            break;
        }
        case 0x6e: FBIN(a / b)
        case 0x6f: DBIN(a / b)
        case 0x70: {
            int32_t b = sp[-1].i, a = sp[-2].i;
            if (b == 0) THROW("java/lang/ArithmeticException", "/ by zero");
            sp--;
            sp[-1].i = (b == -1) ? 0 : a % b;
            pc++;
            break;
        }
        case 0x71: {
            int64_t b = sp[-2].j, a = sp[-4].j;
            if (b == 0) THROW("java/lang/ArithmeticException", "/ by zero");
            sp -= 2;
            sp[-2].j = (b == -1) ? 0 : a % b;
            pc++;
            break;
        }
        case 0x72: FBIN(fmodf(a, b))
        case 0x73: DBIN(fmod(a, b))
        case 0x74: sp[-1].i = (int32_t)(0u - (uint32_t)sp[-1].i); pc++; break;
        case 0x75: sp[-2].j = (int64_t)(0ull - (uint64_t)sp[-2].j); pc++; break;
        case 0x76: sp[-1].f = -sp[-1].f; pc++; break;
        case 0x77: sp[-2].d = -sp[-2].d; pc++; break;
        case 0x78: IBIN((int32_t)((uint32_t)a << (b & 31)))
        case 0x79: { int32_t s = sp[-1].i; sp--; sp[-2].j = (int64_t)((uint64_t)sp[-2].j << (s & 63)); pc++; break; }
        case 0x7a: IBIN(a >> (b & 31))
        case 0x7b: { int32_t s = sp[-1].i; sp--; sp[-2].j = sp[-2].j >> (s & 63); pc++; break; }
        case 0x7c: IBIN((int32_t)((uint32_t)a >> (b & 31)))
        case 0x7d: { int32_t s = sp[-1].i; sp--; sp[-2].j = (int64_t)((uint64_t)sp[-2].j >> (s & 63)); pc++; break; }
        case 0x7e: IBIN(a & b)
        case 0x7f: LBIN(a & b)
        case 0x80: IBIN(a | b)
        case 0x81: LBIN(a | b)
        case 0x82: IBIN(a ^ b)
        case 0x83: LBIN(a ^ b)
        case 0x84: locals[pc[1]].i = (int32_t)((uint32_t)locals[pc[1]].i + (uint32_t)(int8_t)pc[2]); pc += 3; break;

        /* conversions */
        case 0x85: { int32_t v = sp[-1].i; sp[-1].j = v; sp++; pc++; break; }
        case 0x86: sp[-1].f = (float)sp[-1].i; pc++; break;
        case 0x87: { int32_t v = sp[-1].i; sp[-1].d = v; sp++; pc++; break; }
        case 0x88: { int64_t v = sp[-2].j; sp--; sp[-1].i = (int32_t)v; pc++; break; }
        case 0x89: { int64_t v = sp[-2].j; sp--; sp[-1].f = (float)v; pc++; break; }
        case 0x8a: sp[-2].d = (double)sp[-2].j; pc++; break;
        case 0x8b: sp[-1].i = f2i(sp[-1].f); pc++; break;
        case 0x8c: { float v = sp[-1].f; sp[-1].j = f2l(v); sp++; pc++; break; }
        case 0x8d: { float v = sp[-1].f; sp[-1].d = v; sp++; pc++; break; }
        case 0x8e: { double v = sp[-2].d; sp--; sp[-1].i = f2i(v); pc++; break; }
        case 0x8f: sp[-2].j = f2l(sp[-2].d); pc++; break;
        case 0x90: { double v = sp[-2].d; sp--; sp[-1].f = (float)v; pc++; break; }
        case 0x91: sp[-1].i = (int8_t)sp[-1].i; pc++; break;
        case 0x92: sp[-1].i = (uint16_t)sp[-1].i; pc++; break;
        case 0x93: sp[-1].i = (int16_t)sp[-1].i; pc++; break;

        /* comparisons */
        case 0x94: { int64_t b = sp[-2].j, a = sp[-4].j; sp -= 3; sp[-1].i = (a > b) - (a < b); pc++; break; }
        case 0x95: case 0x96: {
            float b = sp[-1].f, a = sp[-2].f;
            sp--;
            sp[-1].i = a > b ? 1 : a == b ? 0 : a < b ? -1 : (*pc == 0x95 ? -1 : 1);
            pc++;
            break;
        }
        case 0x97: case 0x98: {
            double b = sp[-2].d, a = sp[-4].d;
            sp -= 3;
            sp[-1].i = a > b ? 1 : a == b ? 0 : a < b ? -1 : (*pc == 0x97 ? -1 : 1);
            pc++;
            break;
        }
#define IF1(cond) { int32_t v = (--sp)->i; if (cond) BRANCH(S2(pc + 1)); else pc += 3; break; }
#define IF2(cond) { int32_t b = sp[-1].i, a = sp[-2].i; sp -= 2; if (cond) BRANCH(S2(pc + 1)); else pc += 3; break; }
        case 0x99: IF1(v == 0)
        case 0x9a: IF1(v != 0)
        case 0x9b: IF1(v < 0)
        case 0x9c: IF1(v >= 0)
        case 0x9d: IF1(v > 0)
        case 0x9e: IF1(v <= 0)
        case 0x9f: IF2(a == b)
        case 0xa0: IF2(a != b)
        case 0xa1: IF2(a < b)
        case 0xa2: IF2(a >= b)
        case 0xa3: IF2(a > b)
        case 0xa4: IF2(a <= b)
        case 0xa5: { Object *b = sp[-1].l, *a = sp[-2].l; sp -= 2; if (a == b) BRANCH(S2(pc + 1)); else pc += 3; break; }
        case 0xa6: { Object *b = sp[-1].l, *a = sp[-2].l; sp -= 2; if (a != b) BRANCH(S2(pc + 1)); else pc += 3; break; }
        case 0xa7: BRANCH(S2(pc + 1)); break;
        case 0xa8: (sp++)->i = (int32_t)(pc + 3 - m->code); BRANCH(S2(pc + 1)); break;
        case 0xa9: pc = m->code + locals[pc[1]].i; break;
        case 0xaa: {
            uint8_t *base = pc;
            uint8_t *p = pc + 1;
            p += (4 - ((p - m->code) & 3)) & 3;
            int32_t def = S4(p), lo = S4(p + 4), hi = S4(p + 8);
            int32_t key = (--sp)->i;
            int32_t off = (key < lo || key > hi) ? def : S4(p + 12 + (int64_t)(key - (int64_t)lo) * 4);
            pc = base;
            BRANCH(off);
            break;
        }
        case 0xab: {
            uint8_t *base = pc;
            uint8_t *p = pc + 1;
            p += (4 - ((p - m->code) & 3)) & 3;
            int32_t def = S4(p), n = S4(p + 4);
            int32_t key = (--sp)->i;
            int32_t off = def;
            int lo = 0, hi = n - 1;
            while (lo <= hi) {
                int mid = (lo + hi) >> 1;
                int32_t k = S4(p + 8 + mid * 8);
                if (k == key) { off = S4(p + 12 + mid * 8); break; }
                if (k < key) lo = mid + 1;
                else hi = mid - 1;
            }
            pc = base;
            BRANCH(off);
            break;
        }

        /* returns */
        case 0xac: case 0xae: case 0xb0: case 0xad: case 0xaf: case 0xb1: {
            uint8_t op = *pc;
            Value rv = {0}, rv2 = {0};
            int nret = 0;
            if (op == 0xac || op == 0xae || op == 0xb0) { rv = sp[-1]; nret = 1; }
            else if (op == 0xad || op == 0xaf) { rv = sp[-2]; rv2 = sp[-1]; nret = 2; }
            if (f->sync_obj) monitor_exit(t, f->sync_obj);
            if (f->clinit_of) {
                f->clinit_of->state = CS_INITIALIZED;
                f->clinit_of->init_thread = NULL;
            }
            Value *base = f->locals;
            int adv = f->ret_adv;
            t->fp--;
            if (t->fp < 0) {
                t->state = TS_DEAD;
                return;
            }
            LOAD();
            sp = base;
            if (nret >= 1) *sp++ = rv;
            if (nret == 2) *sp++ = rv2;
            pc += adv;
            break;
        }

        /* fields */
        case 0xb2: {
            int idx = U2(pc + 1);
            RESOLVE_FIELD(fl, idx);
            ENSURE_INIT(fl->cls);
            Value *v = &fl->cls->statics[fl->offset];
            if (fl->type == 'J' || fl->type == 'D') { sp[0] = *v; sp += 2; }
            else *sp++ = *v;
            pc += 3;
            break;
        }
        case 0xb3: {
            int idx = U2(pc + 1);
            RESOLVE_FIELD(fl, idx);
            ENSURE_INIT(fl->cls);
            Value *v = &fl->cls->statics[fl->offset];
            if (fl->type == 'J' || fl->type == 'D') { sp -= 2; *v = sp[0]; }
            else *v = *--sp;
            pc += 3;
            break;
        }
        case 0xb4: {
            int idx = U2(pc + 1);
            RESOLVE_FIELD(fl, idx);
            Object *o = sp[-1].l;
            NULLCHECK(o);
            Value *v = OBJ_FIELD(o, fl->offset);
            sp[-1] = *v;
            if (fl->type == 'J' || fl->type == 'D') sp++;
            pc += 3;
            break;
        }
        case 0xb5: {
            int idx = U2(pc + 1);
            RESOLVE_FIELD(fl, idx);
            bool wide = fl->type == 'J' || fl->type == 'D';
            Object *o = wide ? sp[-3].l : sp[-2].l;
            NULLCHECK(o);
            Value *v = OBJ_FIELD(o, fl->offset);
            if (wide) { *v = sp[-2]; sp -= 3; }
            else {
                switch (fl->type) {
                case 'Z': v->j = sp[-1].i & 1; break;
                case 'B': v->j = (int8_t)sp[-1].i; break;
                case 'C': v->j = (uint16_t)sp[-1].i; break;
                case 'S': v->j = (int16_t)sp[-1].i; break;
                default: *v = sp[-1]; break;
                }
                sp -= 2;
            }
            pc += 3;
            break;
        }

        /* invocation */
        case 0xb6: {
            int idx = U2(pc + 1);
            RESOLVE_METHOD(rm, idx);
            Object *recv = sp[-rm->nargs].l;
            NULLCHECK(recv);
            if (rm->vtable_index >= 0 && !(rm->cls->access & ACC_INTERFACE)) {
                callee = rm->vtable_index < recv->cls->vtable_len ? recv->cls->vtable[rm->vtable_index] : rm;
            } else if (rm->access & (ACC_PRIVATE | ACC_STATIC)) {
                callee = rm;
            } else {
                SAVE();
                callee = iface_lookup(t, recv->cls, rm, &cp[idx]);
                if (!callee) goto exception;
            }
            inv_len = 3;
            goto invoke;
        }
        case 0xb7: {
            int idx = U2(pc + 1);
            RESOLVE_METHOD(rm, idx);
            Object *recv = sp[-rm->nargs].l;
            NULLCHECK(recv);
            callee = rm;
            if (rm->name != vm.s_init && !(rm->access & ACC_PRIVATE) && rm->vtable_index >= 0 && m->cls->super &&
                rm->cls != m->cls && is_subclass(m->cls->super, rm->cls) &&
                rm->vtable_index < m->cls->super->vtable_len) {
                callee = m->cls->super->vtable[rm->vtable_index];
            }
            inv_len = 3;
            goto invoke;
        }
        case 0xb8: {
            int idx = U2(pc + 1);
            RESOLVE_METHOD(rm, idx);
            ENSURE_INIT(rm->cls);
            callee = rm;
            inv_len = 3;
            goto invoke;
        }
        case 0xb9: {
            int idx = U2(pc + 1);
            RESOLVE_METHOD(rm, idx);
            Object *recv = sp[-rm->nargs].l;
            NULLCHECK(recv);
            SAVE();
            callee = iface_lookup(t, recv->cls, rm, &cp[idx]);
            if (!callee) goto exception;
            inv_len = 5;
            goto invoke;
        }
        case 0xba: THROW("java/lang/UnsupportedOperationException", "invokedynamic");

        case 0xbb: {
            int idx = U2(pc + 1);
            RESOLVE_CLASS(c, idx);
            ENSURE_INIT(c);
            SAVE();
            Object *o = alloc_object(t, c);
            if (!o) goto exception;
            (sp++)->l = o;
            pc += 3;
            break;
        }
        case 0xbc: {
            const char *an = array_class_name_for_atype(pc[1]);
            if (!an) THROW("java/lang/VirtualMachineError", "bad newarray");
            SAVE();
            Array *a = alloc_prim_array(t, an[1], sp[-1].i);
            if (!a) goto exception;
            sp[-1].l = (Object *)a;
            pc += 2;
            break;
        }
        case 0xbd: {
            int idx = U2(pc + 1);
            RESOLVE_CLASS(ec, idx);
            SAVE();
            Class *ac = array_class_of(t, ec);
            if (!ac) goto exception;
            Array *a = alloc_array(t, ac, sp[-1].i);
            if (!a) goto exception;
            sp[-1].l = (Object *)a;
            pc += 3;
            break;
        }
        case 0xbe: { Object *a = sp[-1].l; NULLCHECK(a); sp[-1].i = ((Array *)a)->length; pc++; break; }
        case 0xbf: {
            Object *e = sp[-1].l;
            NULLCHECK(e);
            SAVE();
            t->exception = e;
            goto exception;
        }
        case 0xc0: {
            int idx = U2(pc + 1);
            Object *o = sp[-1].l;
            if (o && o->cls != cp[idx].ic_cls) {
                RESOLVE_CLASS(c, idx);
                if (!is_assignable(o->cls, c)) {
                    char buf[600];
                    char *a = class_java_name(o->cls), *b = class_java_name(c);
                    snprintf(buf, sizeof buf, "%s cannot be cast to %s", a, b);
                    free(a);
                    free(b);
                    THROW("java/lang/ClassCastException", buf);
                }
                cp[idx].ic_cls = o->cls;
            }
            pc += 3;
            break;
        }
        case 0xc1: {
            int idx = U2(pc + 1);
            Object *o = sp[-1].l;
            if (!o) sp[-1].i = 0;
            else if (o->cls == cp[idx].ic_cls) sp[-1].i = 1;
            else {
                RESOLVE_CLASS(c, idx);
                bool r = is_assignable(o->cls, c);
                if (r) cp[idx].ic_cls = o->cls;
                sp[-1].i = r;
            }
            pc += 3;
            break;
        }
        case 0xc2: {
            Object *o = sp[-1].l;
            NULLCHECK(o);
            if (!monitor_enter(t, o)) { SAVE(); return; }
            sp--;
            pc++;
            break;
        }
        case 0xc3: {
            Object *o = sp[-1].l;
            NULLCHECK(o);
            if (!monitor_exit(t, o)) THROW("java/lang/IllegalMonitorStateException", NULL);
            sp--;
            pc++;
            break;
        }
        case 0xc4: {
            uint8_t op = pc[1];
            int idx = U2(pc + 2);
            switch (op) {
            case 0x15: case 0x17: case 0x19: *sp++ = locals[idx]; break;
            case 0x16: case 0x18: sp[0] = locals[idx]; sp += 2; break;
            case 0x36: case 0x38: case 0x3a: locals[idx] = *--sp; break;
            case 0x37: case 0x39: sp -= 2; locals[idx] = sp[0]; break;
            case 0xa9: pc = m->code + locals[idx].i; continue;
            case 0x84:
                locals[idx].i = (int32_t)((uint32_t)locals[idx].i + (uint32_t)(int32_t)S2(pc + 4));
                pc += 6;
                continue;
            default: THROW("java/lang/VirtualMachineError", "bad wide");
            }
            pc += 4;
            break;
        }
        case 0xc5: {
            int idx = U2(pc + 1);
            int dims = pc[3];
            RESOLVE_CLASS(ac, idx);
            int32_t counts[255];
            for (int i = 0; i < dims; i++) {
                counts[i] = sp[-dims + i].i;
                if (counts[i] < 0) THROW("java/lang/NegativeArraySizeException", NULL);
            }
            SAVE();
            Array *a = multi_new(t, ac, counts, dims);
            if (!a) goto exception;
            sp -= dims;
            (sp++)->l = (Object *)a;
            pc += 4;
            break;
        }
        case 0xc6: { Object *o = (--sp)->l; if (!o) BRANCH(S2(pc + 1)); else pc += 3; break; }
        case 0xc7: { Object *o = (--sp)->l; if (o) BRANCH(S2(pc + 1)); else pc += 3; break; }
        case 0xc8: BRANCH(S4(pc + 1)); break;
        case 0xc9: (sp++)->i = (int32_t)(pc + 5 - m->code); BRANCH(S4(pc + 1)); break;
        default: {
            char buf[64];
            snprintf(buf, sizeof buf, "bad opcode 0x%02x", *pc);
            THROW("java/lang/VirtualMachineError", buf);
        }
        }
        continue;

    invoke: {
        Value *args = sp - callee->nargs;
        if (callee->access & ACC_NATIVE) {
            NativeFn fn = callee->native_fn;
            if (!fn) {
                fn = native_lookup(callee);
                if (!fn) {
                    char buf[512];
                    snprintf(buf, sizeof buf, "%s.%s%s", callee->cls->name->str, callee->name->str, callee->desc->str);
                    vm_log("missing native %s", buf);
                    THROW("java/lang/UnsatisfiedLinkError", buf);
                }
                callee->native_fn = fn;
            }
            SAVE();
            Value ret;
            ret.j = 0;
            t->reexecute = false;
            t->pending = NULL;
            fn(t, args, &ret);
            if (t->exception) goto exception;
            if (t->reexecute) {
                t->reexecute = false;
                if (t->yield || t->state != TS_RUNNABLE) { t->yield = false; return; }
                LOAD();
                continue;
            }
            sp = args;
            switch (callee->ret) {
            case 'V': break;
            case 'J': case 'D': sp[0] = ret; sp += 2; break;
            default: *sp++ = ret; break;
            }
            if (t->pending) {
                Method *pm = t->pending;
                t->pending = NULL;
                Value *pargs = sp;
                for (int i = 0; i < t->pending_nargs; i++) *sp++ = t->pending_args[i];
                t->pending_nargs = 0;
                SAVE();
                if (push_frame(t, pm, pargs, inv_len) < 0) goto exception;
                LOAD();
                continue;
            }
            pc += inv_len;
            if (t->yield || t->state != TS_RUNNABLE) {
                t->yield = false;
                SAVE();
                return;
            }
            continue;
        }
        if (!callee->code) {
            char buf[512];
            snprintf(buf, sizeof buf, "%s.%s%s", callee->cls->name->str, callee->name->str, callee->desc->str);
            THROW("java/lang/AbstractMethodError", buf);
        }
        Object *syncobj = NULL;
        if (callee->access & ACC_SYNCHRONIZED) {
            if (callee->access & ACC_STATIC) {
                SAVE();
                syncobj = class_mirror(t, callee->cls);
                if (!syncobj) goto exception;
            } else syncobj = args[0].l;
            if (!monitor_enter(t, syncobj)) { SAVE(); return; }
        }
        SAVE();
        if (push_frame(t, callee, args, inv_len) < 0) {
            if (syncobj) monitor_exit(t, syncobj);
            goto exception;
        }
        t->frames[t->fp].sync_obj = syncobj;
        LOAD();
        if (--budget <= 0) { SAVE(); return; }
        continue;
    }

    exception: {
        Object *exc = t->exception;
        for (;;) {
            f = &t->frames[t->fp];
            m = f->m;
            int off = (int)(f->pc - m->code);
            int handler = -1;
            for (int i = 0; i < m->n_exc; i++) {
                ExcEntry *e = &m->exc[i];
                if (off < e->start || off >= e->end) continue;
                if (e->catch_idx == 0) { handler = e->handler; break; }
                Class *cc = resolve_class(t, m->cls, e->catch_idx);
                t->exception = exc;
                if (cc && is_assignable(exc->cls, cc)) { handler = e->handler; break; }
            }
            if (handler >= 0) {
                t->exception = NULL;
                f->sp = f->stack_base;
                f->sp->l = exc;
                f->sp++;
                f->pc = m->code + handler;
                LOAD();
                break;
            }
            if (f->sync_obj) monitor_exit(t, f->sync_obj);
            if (f->clinit_of) {
                vm_log("exception in static initializer of %s", f->clinit_of->name->str);
                f->clinit_of->state = CS_ERROR;
                f->clinit_of->init_thread = NULL;
            }
            t->fp--;
            if (t->fp < 0) {
                print_exception(t, exc, "Uncaught exception: ");
                t->exception = NULL;
                t->state = TS_DEAD;
                return;
            }
        }
        continue;
    }
    }
}
