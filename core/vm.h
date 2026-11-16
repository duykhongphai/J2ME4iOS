/* J2ME4iOS - internal VM definitions. */
#ifndef J4I_VM_H
#define J4I_VM_H

#include "j4i.h"
#include <stdint.h>
#include <stddef.h>
#include <stdbool.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>

#ifdef _MSC_VER
#pragma warning(disable : 4200 4244 4267 4018 4146 4996 4100 4127 4389)
#endif

/* ---- access flags ---- */
#define ACC_PUBLIC       0x0001
#define ACC_PRIVATE      0x0002
#define ACC_PROTECTED    0x0004
#define ACC_STATIC       0x0008
#define ACC_FINAL        0x0010
#define ACC_SYNCHRONIZED 0x0020
#define ACC_VOLATILE     0x0040
#define ACC_NATIVE       0x0100
#define ACC_INTERFACE    0x0200
#define ACC_ABSTRACT     0x0400

typedef struct Class Class;
typedef struct Object Object;
typedef struct Method Method;
typedef struct Field Field;
typedef struct Thread Thread;
typedef struct Frame Frame;
typedef struct Zip Zip;

typedef union Value {
    int32_t i;
    int64_t j;
    float f;
    double d;
    Object *l;
    uintptr_t raw;
} Value;

typedef struct Symbol {
    struct Symbol *next;
    uint32_t hash;
    uint32_t len;
    char str[];
} Symbol;

/* ---- heap objects ---- */
struct Object {
    Class *cls;
    uint32_t hash;
    uint32_t flags;
};

typedef struct Array {
    Object hdr;
    int32_t length;
    int32_t pad_;
} Array;

#define ARR_DATA(a) ((void *)((Array *)(a) + 1))
#define ARR_B(a) ((int8_t *)ARR_DATA(a))
#define ARR_C(a) ((uint16_t *)ARR_DATA(a))
#define ARR_S(a) ((int16_t *)ARR_DATA(a))
#define ARR_I(a) ((int32_t *)ARR_DATA(a))
#define ARR_J(a) ((int64_t *)ARR_DATA(a))
#define ARR_F(a) ((float *)ARR_DATA(a))
#define ARR_D(a) ((double *)ARR_DATA(a))
#define ARR_L(a) ((Object **)ARR_DATA(a))

#define OBJ_FIELD(o, off) ((Value *)((char *)(o) + (off)))

#define OF_HAS_MONITOR 1u

/* ---- constant pool ---- */
enum {
    CT_UTF8 = 1, CT_INT = 3, CT_FLOAT = 4, CT_LONG = 5, CT_DOUBLE = 6, CT_CLASS = 7,
    CT_STRING = 8, CT_FIELD = 9, CT_METHOD = 10, CT_IMETHOD = 11, CT_NAT = 12,
    CT_MHANDLE = 15, CT_MTYPE = 16, CT_INDY = 18
};

typedef struct CPEntry {
    uint8_t tag;
    uint8_t resolved;
    uint16_t a, b;
    union {
        Symbol *sym;
        int32_t i;
        float f;
        int64_t j;
        double d;
        Class *cls;
        Field *field;
        Method *method;
        Object *str;
    } v;
    Class *ic_cls; /* inline cache: receiver class / instanceof hit */
    Method *ic_m;
} CPEntry;

typedef struct ExcEntry { uint16_t start, end, handler, catch_idx; } ExcEntry;
typedef struct LineEntry { uint16_t pc, line; } LineEntry;

typedef void (*NativeFn)(Thread *t, Value *args, Value *ret);

struct Method {
    Class *cls;
    Symbol *name;
    Symbol *desc;
    uint16_t access;
    uint16_t max_stack;
    uint16_t max_locals;
    uint16_t nargs; /* argument slots including 'this' */
    uint8_t *code;
    uint32_t code_len;
    ExcEntry *exc;
    LineEntry *lines;
    uint16_t n_exc;
    uint16_t n_lines;
    int vtable_index;
    char ret; /* V I J F D L (Z/B/C/S folded to I) */
    NativeFn native_fn;
};

struct Field {
    Class *cls;
    Symbol *name;
    Symbol *desc;
    uint16_t access;
    uint16_t const_idx;
    uint32_t offset; /* instance: byte offset in object; static: slot index */
    char type;       /* first char of descriptor */
};

enum { CS_LOADED, CS_LINKED, CS_INITIALIZING, CS_INITIALIZED, CS_ERROR };

struct Class {
    Symbol *name;
    Class *super;
    Class **ifaces;
    uint16_t n_ifaces;
    uint16_t access;
    CPEntry *cp;
    uint16_t cp_count;
    uint16_t n_fields;
    uint16_t n_methods;
    Field *fields;
    Method *methods;
    Method **vtable;
    int vtable_len;
    uint32_t inst_size;
    uint32_t *ref_offs;
    int n_ref_offs;
    Value *statics;
    int n_statics;
    int state;
    Thread *init_thread;
    Object *mirror;
    bool is_array;
    char elem;      /* arrays: element descriptor char */
    int elem_size;  /* arrays: bytes per element */
    Class *component;
    Class *array_class; /* cache of "[" + this */
    Symbol *source;
    Method *clinit;
    Class *next_all;
    Class *hash_next;
};

/* ---- threads ---- */
enum { TS_RUNNABLE, TS_SLEEPING, TS_WAITING, TS_BLOCKED, TS_WAIT_EVENT, TS_DEAD };

struct Frame {
    Method *m;
    uint8_t *pc;
    Value *locals;
    Value *sp;
    Value *stack_base;
    Class *clinit_of;
    Object *sync_obj;
    uint8_t ret_adv;
};

#define THREAD_STACK_SLOTS (48 * 1024)
#define THREAD_MAX_FRAMES 2048

struct Thread {
    int id;
    int state;
    Object *jthread;
    Value *stack;
    Value *stack_end;
    Frame *frames;
    int fp;
    Object *exception;
    int64_t wake_time; /* monotonic ms, 0 = none */
    Object *wait_obj;
    int wait_count;
    bool notified;
    Object *blocked_on;
    bool interrupted;
    bool reexecute;
    bool yield;
    bool ev_waiting;
    int64_t ev_deadline;
    Method *pending;
    int pending_nargs;
    Value pending_args[8];
    Thread *next;
};

/* ---- well known symbols/classes ---- */
typedef struct VM {
    J4IConfig cfg;
    Zip *jars[4];
    int n_jars;
    Zip *game;

    Class **class_tab;
    int class_tab_size;
    int n_classes;
    Class *all_classes;

    Class *c_Object, *c_Class, *c_String, *c_Thread, *c_Throwable;
    Class *c_arr_C, *c_arr_B, *c_arr_I, *c_arr_J, *c_arr_Object, *c_arr_String;
    Field *f_String_value, *f_String_offset, *f_String_count;
    Field *f_Class_vm, *f_Throwable_message, *f_Throwable_trace;
    Field *f_Thread_vm, *f_Thread_name;

    Symbol *s_init, *s_clinit, *s_run, *s_V, *s_main, *s_mainDesc;

    Thread *threads;
    Thread *last_run;
    int next_thread_id;
    int n_live_threads;

    volatile int exit_requested;
    int exit_code;
    bool event_wakeup;

    char *manifest; /* raw manifest text */
    char *jad_props;
    int trace;
} VM;

extern VM vm;

/* ---- util.c ---- */
void *meta_alloc(size_t n);
char *meta_strdup(const char *s);
void meta_free_all(void);
Symbol *sym_intern(const char *s, size_t len);
Symbol *sym(const char *s);
void sym_free_all(void);
void vm_log(const char *fmt, ...);
void vm_fatal(const char *fmt, ...);
int mutf8_to_utf16(const uint8_t *s, int len, uint16_t *out); /* out may be NULL: count */
int utf8_to_utf16(const uint8_t *s, int len, uint16_t *out);  /* lenient: invalid -> latin1 */
int utf16_to_utf8(const uint16_t *s, int len, char *out);      /* out may be NULL */

/* ---- osdep.c ---- */
void os_init(void);
void os_destroy(void);
void os_lock(void);
void os_unlock(void);
void os_cond_wait(int timeout_ms); /* lock held; -1 = forever */
void os_cond_signal(void);
int64_t os_now_ms(void);
int64_t os_mono_ms(void);
int64_t os_nanos(void);
uint8_t *os_read_file(const char *path, size_t *len);
int os_write_file(const char *path, const void *data, size_t len);
int os_delete_file(const char *path);
int os_mkdirs(const char *path);
typedef void (*os_dir_cb)(void *ctx, const char *name);
int os_list_dir(const char *path, os_dir_cb cb, void *ctx);
int os_tz_offset_ms(void);
/* ---- osfs.c ---- */
int os_stat(const char *path, int *is_dir, int64_t *size, int64_t *mtime_ms);
int os_rename(const char *from, const char *to);
int os_rmdir(const char *path);
typedef void (*os_dir_ex_cb)(void *ctx, const char *name, bool is_dir);
int os_list_dir_ex(const char *path, os_dir_ex_cb cb, void *ctx);
int os_write_at(const char *path, const void *data, size_t len, bool append);
int os_truncate(const char *path, int64_t len);
int64_t os_free_space(const char *path);
int os_thread_start(void (*fn)(void *), void *arg);
int net_connect(const char *host, int port);
int net_state(int id);
int net_read(int id, uint8_t *dst, int len);
int net_available(int id);
int net_write(int id, const uint8_t *src, int len);
void net_close(int id);
int net_address(int id, bool local, char *out, int outlen);
void net_shutdown_all(void);

/* ---- zip.c ---- */
Zip *zip_open_file(const char *path);
void zip_close(Zip *z);
uint8_t *zip_read(Zip *z, const char *name, int *len);
bool zip_has(Zip *z, const char *name);

/* ---- inflate.c ---- */
int inflate_raw(const uint8_t *src, size_t srclen, uint8_t *dst, size_t dstcap, size_t *outlen);
int inflate_raw_alloc(const uint8_t *src, size_t srclen, uint8_t **out, size_t *outlen);

/* ---- png.c ---- */
uint32_t *png_decode(const uint8_t *data, int len, int *w, int *h);

/* ---- class.c ---- */
void classes_init(void);
void classes_free(void);
Class *class_find(Thread *t, Symbol *name);       /* throws NoClassDefFoundError */
Class *class_find_c(Thread *t, const char *name);
Class *class_lookup_loaded(Symbol *name);
Class *array_class_of(Thread *t, Class *elem);
Class *prim_array_class(char elem);
bool is_assignable(Class *from, Class *to);
static inline bool instance_of(Object *o, Class *c) { return o && is_assignable(o->cls, c); }
bool is_subclass(Class *c, Class *sup);
Method *find_method(Class *c, Symbol *name, Symbol *desc);
Method *find_method_local(Class *c, Symbol *name, Symbol *desc);
Method *find_virtual(Class *c, Symbol *name, Symbol *desc);
Field *find_field(Class *c, Symbol *name, Symbol *desc);
Field *find_field_by_name(Class *c, const char *name);
Class *resolve_class(Thread *t, Class *cur, int idx);
Field *resolve_field(Thread *t, Class *cur, int idx);
Method *resolve_method(Thread *t, Class *cur, int idx);
Object *resolve_string(Thread *t, Class *cur, int idx);
int class_ensure_init(Thread *t, Class *c); /* 0 ready, 1 retry (frame pushed / yield), -1 exception */
Object *class_mirror(Thread *t, Class *c);
Class *mirror_class(Object *mirror);
int line_number(Method *m, int pc);
char *class_java_name(Class *c); /* malloc'd "java.lang.String" style */

/* ---- heap.c ---- */
void heap_init(size_t bytes);
void heap_destroy(void);
Object *alloc_object(Thread *t, Class *c);
Array *alloc_array(Thread *t, Class *arr_cls, int len);
Array *alloc_prim_array(Thread *t, char elem, int len);
void gc_collect(void);
bool heap_is_object(const void *p);
int64_t heap_free_bytes(void);
int64_t heap_total_bytes(void);
void gc_set_stack_base(void *base);
void gc_add_root(Object **slot);
uint32_t obj_identity_hash(Object *o);

/* strings */
Object *string_new_utf16(Thread *t, const uint16_t *s, int len);
Object *string_new_utf8(Thread *t, const char *s);
Object *string_new_mutf8(Thread *t, const uint8_t *s, int len);
Object *string_intern(Thread *t, Object *str);
Object *string_intern_sym(Thread *t, Symbol *s);
uint16_t *string_chars(Object *str, int *len);
char *string_to_utf8(Object *str); /* malloc'd, NULL -> "null" */
void strings_free(void);

/* ---- interp.c ---- */
void interp_run(Thread *t, int budget);
void throw_new(Thread *t, const char *cls, const char *msg);
void throw_newf(Thread *t, const char *cls, const char *fmt, ...);
Object *exception_new(Thread *t, const char *cls, const char *msg);
void fill_stack_trace(Thread *t, Object *throwable);
int push_frame(Thread *t, Method *m, Value *args, int ret_adv);
void print_exception(Thread *t, Object *exc, const char *prefix);

/* ---- sched.c ---- */
Thread *thread_new(Object *jthread);
void thread_free(Thread *t);
bool monitor_enter(Thread *t, Object *o);
bool monitor_exit(Thread *t, Object *o);
bool monitor_wait(Thread *t, Object *o, int64_t ms);
bool monitor_notify(Thread *t, Object *o, bool all);
void monitor_forget(Object *o);
void monitors_free(void);
void sched_loop(void);
bool events_pop(int *ev);
bool events_pending(void);
void events_reset(void);
void events_wakeup(void);
int64_t vm_now(void);

/* ---- natives ---- */
void natives_init(void);
NativeFn native_lookup(Method *m);
void natives_lang_register(void);
void natives_midp_register(void);
void natives_io_register(void);
void m3g_register(void);
void native_register(const char *cls, const char *name, const char *desc, NativeFn fn);
void midp_free(void);
char *text_input_take(int id, bool *found);

/* ---- font.c / gfx ---- */
void font_cache_free(void);

#define TRACE(lvl, ...) do { if (vm.trace >= (lvl)) vm_log(__VA_ARGS__); } while (0)

#endif
