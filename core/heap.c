/* Non-moving mark & sweep heap with conservative stack scanning, plus Java strings. */
#include "vm.h"
#include <setjmp.h>

#define GRAN 8
#define BH_ALLOC 1u
#define BH_MARK 2u
#define SMALL_MAX 1024
#define NSMALL (SMALL_MAX / GRAN + 1)

typedef struct BH {
    uint32_t size; /* whole block incl. header */
    uint32_t tag;
} BH;

typedef struct FreeBlock {
    BH h;
    struct FreeBlock *next;
} FreeBlock;

static uint8_t *heap_base, *heap_end, *heap_top;
static uint8_t *start_bits;
static FreeBlock *small_lists[NSMALL];
static FreeBlock *large_list;
static size_t used_bytes, alloc_since_gc, gc_threshold, heap_size;
static void *stack_base;
static uint32_t hash_seed = 0x9E3779B9u;

static Object ***extra_roots;
static int n_extra_roots, cap_extra_roots;

static Object **mark_stack;
static size_t mark_sp, mark_cap;

static Object *oom_error;

static inline void bit_set(size_t i) { start_bits[i >> 3] |= (uint8_t)(1u << (i & 7)); }
static inline void bit_clr(size_t i) { start_bits[i >> 3] &= (uint8_t)~(1u << (i & 7)); }
static inline bool bit_get(size_t i) { return (start_bits[i >> 3] >> (i & 7)) & 1; }

void heap_init(size_t bytes) {
    heap_size = (bytes + 4095) & ~(size_t)4095;
    heap_base = (uint8_t *)malloc(heap_size);
    if (!heap_base) vm_fatal("cannot allocate heap of %zu bytes", heap_size);
    heap_end = heap_base + heap_size;
    heap_top = heap_base;
    start_bits = (uint8_t *)calloc(heap_size / GRAN / 8 + 1, 1);
    memset(small_lists, 0, sizeof small_lists);
    large_list = NULL;
    used_bytes = alloc_since_gc = 0;
    gc_threshold = heap_size / 4 > (8u << 20) ? (8u << 20) : heap_size / 4;
    mark_cap = 4096;
    mark_stack = (Object **)malloc(mark_cap * sizeof(Object *));
    oom_error = NULL;
}

void heap_destroy(void) {
    free(heap_base);
    free(start_bits);
    free(mark_stack);
    free(extra_roots);
    heap_base = heap_end = heap_top = NULL;
    start_bits = NULL;
    mark_stack = NULL;
    extra_roots = NULL;
    n_extra_roots = cap_extra_roots = 0;
    mark_sp = 0;
}

void gc_set_stack_base(void *base) { stack_base = base; }

void gc_add_root(Object **slot) {
    if (n_extra_roots == cap_extra_roots) {
        cap_extra_roots = cap_extra_roots ? cap_extra_roots * 2 : 64;
        extra_roots = (Object ***)realloc(extra_roots, cap_extra_roots * sizeof(Object **));
    }
    extra_roots[n_extra_roots++] = slot;
}

bool heap_is_object(const void *p) {
    const uint8_t *b = (const uint8_t *)p;
    if (b < heap_base + sizeof(BH) || b >= heap_top) return false;
    size_t off = (size_t)(b - heap_base);
    if (off & (GRAN - 1)) return false;
    return bit_get(off / GRAN);
}

int64_t heap_free_bytes(void) { return (int64_t)(heap_size - used_bytes); }
int64_t heap_total_bytes(void) { return (int64_t)heap_size; }

/* ---------------- free lists ---------------- */
static void free_list_add(uint8_t *p, size_t size) {
    FreeBlock *f = (FreeBlock *)p;
    f->h.size = (uint32_t)size;
    f->h.tag = 0;
    if (size <= SMALL_MAX) {
        f->next = small_lists[size / GRAN];
        small_lists[size / GRAN] = f;
    } else {
        f->next = large_list;
        large_list = f;
    }
}

static uint8_t *take_block(size_t size) {
    if (size <= SMALL_MAX && small_lists[size / GRAN]) {
        FreeBlock *f = small_lists[size / GRAN];
        small_lists[size / GRAN] = f->next;
        return (uint8_t *)f;
    }
    FreeBlock **pp = &large_list;
    while (*pp) {
        FreeBlock *f = *pp;
        if (f->h.size >= size) {
            *pp = f->next;
            size_t rest = f->h.size - size;
            if (rest >= sizeof(FreeBlock)) {
                free_list_add((uint8_t *)f + size, rest);
            } else {
                size = f->h.size;
            }
            ((BH *)f)->size = (uint32_t)size;
            return (uint8_t *)f;
        }
        pp = &f->next;
    }
    /* try a larger small block and split */
    if (size <= SMALL_MAX) {
        for (size_t s = size + sizeof(FreeBlock); s <= SMALL_MAX; s += GRAN) {
            if (small_lists[s / GRAN]) {
                FreeBlock *f = small_lists[s / GRAN];
                small_lists[s / GRAN] = f->next;
                free_list_add((uint8_t *)f + size, s - size);
                ((BH *)f)->size = (uint32_t)size;
                return (uint8_t *)f;
            }
        }
    }
    if (heap_top + size <= heap_end) {
        uint8_t *p = heap_top;
        heap_top += size;
        ((BH *)p)->size = (uint32_t)size;
        return p;
    }
    return NULL;
}

static Object *alloc_raw(Thread *t, size_t objsize) {
    size_t size = (objsize + sizeof(BH) + GRAN - 1) & ~(size_t)(GRAN - 1);
    if (size < sizeof(FreeBlock)) size = sizeof(FreeBlock);
    if (size > heap_size) goto oom;
    if (alloc_since_gc > gc_threshold) gc_collect();
    uint8_t *p = take_block(size);
    if (!p) {
        gc_collect();
        p = take_block(size);
        if (!p) goto oom;
    }
    BH *b = (BH *)p;
    b->tag = BH_ALLOC;
    used_bytes += b->size;
    alloc_since_gc += b->size;
    Object *o = (Object *)(p + sizeof(BH));
    memset(o, 0, b->size - sizeof(BH));
    bit_set((size_t)((uint8_t *)o - heap_base) / GRAN);
    return o;
oom:
    vm_log("OutOfMemoryError: requested %zu bytes, free %lld", objsize, (long long)heap_free_bytes());
    if (t) {
        if (!oom_error) {
            Class *c = class_find_c(t, "java/lang/OutOfMemoryError");
            if (c) {
                /* allocate directly from reserve: there may be no room, so just use bump-less path */
                uint8_t *q = take_block((c->inst_size + sizeof(BH) + GRAN - 1) & ~(size_t)(GRAN - 1));
                if (q) {
                    ((BH *)q)->tag = BH_ALLOC;
                    oom_error = (Object *)(q + sizeof(BH));
                    memset(oom_error, 0, ((BH *)q)->size - sizeof(BH));
                    bit_set((size_t)((uint8_t *)oom_error - heap_base) / GRAN);
                    oom_error->cls = c;
                }
            }
        }
        t->exception = oom_error;
        if (!oom_error) vm_fatal("out of memory");
    }
    return NULL;
}

Object *alloc_object(Thread *t, Class *c) {
    if (c->access & (ACC_INTERFACE | ACC_ABSTRACT)) {
        throw_new(t, "java/lang/InstantiationException", c->name->str);
        return NULL;
    }
    Object *o = alloc_raw(t, c->inst_size);
    if (o) o->cls = c;
    return o;
}

Array *alloc_array(Thread *t, Class *ac, int len) {
    if (len < 0) {
        throw_newf(t, "java/lang/NegativeArraySizeException", "%d", len);
        return NULL;
    }
    uint64_t sz = (uint64_t)sizeof(Array) + (uint64_t)len * (uint64_t)ac->elem_size;
    if (sz > heap_size) {
        alloc_raw(t, (size_t)heap_size + 1);
        return NULL;
    }
    Array *a = (Array *)alloc_raw(t, (size_t)sz);
    if (!a) return NULL;
    a->hdr.cls = ac;
    a->length = len;
    return a;
}

Array *alloc_prim_array(Thread *t, char elem, int len) {
    Class *c;
    switch (elem) {
    case 'C': c = vm.c_arr_C; break;
    case 'B': c = vm.c_arr_B; break;
    case 'I': c = vm.c_arr_I; break;
    case 'J': c = vm.c_arr_J; break;
    default: c = prim_array_class(elem); break;
    }
    if (!c) c = prim_array_class(elem);
    return alloc_array(t, c, len);
}

uint32_t obj_identity_hash(Object *o) {
    if (!o->hash) {
        hash_seed ^= hash_seed << 13;
        hash_seed ^= hash_seed >> 17;
        hash_seed ^= hash_seed << 5;
        o->hash = (hash_seed & 0x7FFFFFFF) | 1;
    }
    return o->hash;
}

/* ---------------- interned strings ---------------- */
static Object **intern_tab;
static int intern_size, intern_count;

/* ---------------- marking ---------------- */
static inline void mark_push(Object *o) {
    BH *b = (BH *)((uint8_t *)o - sizeof(BH));
    if (b->tag & BH_MARK) return;
    b->tag |= BH_MARK;
    if (mark_sp == mark_cap) {
        mark_cap *= 2;
        mark_stack = (Object **)realloc(mark_stack, mark_cap * sizeof(Object *));
    }
    mark_stack[mark_sp++] = o;
}

static inline void mark_ref(Object *o) {
    if (o && heap_is_object(o)) mark_push(o);
}

static void mark_range(const void *lo, const void *hi) {
    uintptr_t a = ((uintptr_t)lo + sizeof(void *) - 1) & ~(uintptr_t)(sizeof(void *) - 1);
    for (const uintptr_t *p = (const uintptr_t *)a; (const void *)(p + 1) <= hi; p++) {
        uintptr_t v = *p;
        if (heap_is_object((void *)v)) mark_push((Object *)v);
    }
}

static void drain(void) {
    while (mark_sp) {
        Object *o = mark_stack[--mark_sp];
        Class *c = o->cls;
        if (!c) continue;
        if (c->is_array) {
            if (c->elem == 'L' || c->elem == '[') {
                Array *a = (Array *)o;
                Object **d = ARR_L(a);
                for (int i = 0; i < a->length; i++) mark_ref(d[i]);
            }
        } else {
            for (int i = 0; i < c->n_ref_offs; i++) mark_ref(OBJ_FIELD(o, c->ref_offs[i])->l);
        }
    }
}

#if defined(_MSC_VER)
__declspec(noinline)
#else
__attribute__((noinline))
#endif
static void scan_c_stack(void) {
    jmp_buf jb;
    setjmp(jb);
    volatile char here;
    const void *lo = (const void *)&here;
    if ((const void *)&jb < lo) lo = (const void *)&jb;
    if (stack_base && lo < stack_base) mark_range(lo, stack_base);
    mark_range(&jb, (const char *)&jb + sizeof jb);
}

void gc_collect(void) {
    int64_t t0 = os_nanos();
    mark_sp = 0;
    /* threads */
    for (Thread *t = vm.threads; t; t = t->next) {
        mark_ref(t->jthread);
        mark_ref(t->exception);
        mark_ref(t->wait_obj);
        mark_ref(t->blocked_on);
        for (int i = 0; i < t->pending_nargs; i++) mark_ref(t->pending_args[i].l);
        if (t->fp >= 0) {
            Frame *top = &t->frames[t->fp];
            Value *hi = top->stack_base + top->m->max_stack + 1;
            if (top->sp > hi) hi = top->sp;
            if (hi > t->stack_end) hi = t->stack_end;
            mark_range(t->stack, hi);
            for (int i = 0; i <= t->fp; i++) mark_ref(t->frames[i].sync_obj);
        }
    }
    /* classes */
    for (Class *c = vm.all_classes; c; c = c->next_all) {
        mark_ref(c->mirror);
        if (c->statics && c->n_statics) mark_range(c->statics, c->statics + c->n_statics);
    }
    for (int i = 0; i < intern_size; i++) mark_ref(intern_tab[i]);
    for (int i = 0; i < n_extra_roots; i++) mark_ref(*extra_roots[i]);
    mark_ref(oom_error);
    scan_c_stack();
    drain();

    /* sweep */
    memset(small_lists, 0, sizeof small_lists);
    large_list = NULL;
    size_t live = 0;
    uint8_t *p = heap_base, *run = NULL;
    while (p < heap_top) {
        BH *b = (BH *)p;
        uint32_t sz = b->size;
        if (sz == 0) vm_fatal("heap corrupted at %p", (void *)p);
        if ((b->tag & BH_ALLOC) && (b->tag & BH_MARK)) {
            b->tag = BH_ALLOC;
            live += sz;
            if (run) { free_list_add(run, (size_t)(p - run)); run = NULL; }
        } else {
            if (b->tag & BH_ALLOC) {
                Object *o = (Object *)(p + sizeof(BH));
                if (o->flags & OF_HAS_MONITOR) monitor_forget(o);
                bit_clr((size_t)((uint8_t *)o - heap_base) / GRAN);
            }
            if (!run) run = p;
        }
        p += sz;
    }
    if (run) heap_top = run;
    used_bytes = live;
    alloc_since_gc = 0;
    gc_threshold = live * 2;
    if (gc_threshold < (8u << 20)) gc_threshold = 8u << 20;
    if (gc_threshold > heap_size / 2) gc_threshold = heap_size / 2;
    TRACE(1, "GC: live %zu KB, top %zu KB, %.2f ms", live / 1024, (size_t)(heap_top - heap_base) / 1024,
          (os_nanos() - t0) / 1e6);
}

/* ---------------- strings ---------------- */
static int str_hash(const uint16_t *s, int n) {
    uint32_t h = 0;
    for (int i = 0; i < n; i++) h = 31 * h + s[i];
    return (int)h;
}

uint16_t *string_chars(Object *str, int *len) {
    Array *v = (Array *)OBJ_FIELD(str, vm.f_String_value->offset)->l;
    int off = OBJ_FIELD(str, vm.f_String_offset->offset)->i;
    *len = OBJ_FIELD(str, vm.f_String_count->offset)->i;
    if (!v) { *len = 0; return NULL; }
    return ARR_C(v) + off;
}

Object *string_new_utf16(Thread *t, const uint16_t *s, int len) {
    Array *a = alloc_prim_array(t, 'C', len);
    if (!a) return NULL;
    if (len) memcpy(ARR_C(a), s, (size_t)len * 2);
    Object *str = alloc_object(t, vm.c_String);
    if (!str) return NULL;
    OBJ_FIELD(str, vm.f_String_value->offset)->l = (Object *)a;
    OBJ_FIELD(str, vm.f_String_offset->offset)->i = 0;
    OBJ_FIELD(str, vm.f_String_count->offset)->i = len;
    return str;
}

Object *string_new_utf8(Thread *t, const char *s) {
    int n = (int)strlen(s);
    int m = utf8_to_utf16((const uint8_t *)s, n, NULL);
    uint16_t sbuf[256];
    uint16_t *buf = m <= 256 ? sbuf : (uint16_t *)malloc((size_t)m * 2);
    utf8_to_utf16((const uint8_t *)s, n, buf);
    Object *r = string_new_utf16(t, buf, m);
    if (buf != sbuf) free(buf);
    return r;
}

Object *string_new_mutf8(Thread *t, const uint8_t *s, int len) {
    int m = mutf8_to_utf16(s, len, NULL);
    uint16_t sbuf[256];
    uint16_t *buf = m <= 256 ? sbuf : (uint16_t *)malloc((size_t)m * 2);
    mutf8_to_utf16(s, len, buf);
    Object *r = string_new_utf16(t, buf, m);
    if (buf != sbuf) free(buf);
    return r;
}

static Object *intern_find(const uint16_t *s, int n, int h, int *slot) {
    if (!intern_tab) {
        intern_size = 1024;
        intern_tab = (Object **)calloc(intern_size, sizeof(Object *));
    }
    int i = h & (intern_size - 1);
    while (intern_tab[i]) {
        int l;
        uint16_t *c = string_chars(intern_tab[i], &l);
        if (l == n && (n == 0 || memcmp(c, s, (size_t)n * 2) == 0)) return intern_tab[i];
        i = (i + 1) & (intern_size - 1);
    }
    *slot = i;
    return NULL;
}

static void intern_insert(Object *str) {
    if ((intern_count + 1) * 2 > intern_size) {
        int os = intern_size;
        Object **ot = intern_tab;
        intern_size *= 2;
        intern_tab = (Object **)calloc(intern_size, sizeof(Object *));
        for (int i = 0; i < os; i++) {
            if (!ot[i]) continue;
            int l;
            uint16_t *c = string_chars(ot[i], &l);
            int j = str_hash(c, l) & (intern_size - 1);
            while (intern_tab[j]) j = (j + 1) & (intern_size - 1);
            intern_tab[j] = ot[i];
        }
        free(ot);
    }
    int l;
    uint16_t *c = string_chars(str, &l);
    int j = str_hash(c, l) & (intern_size - 1);
    while (intern_tab[j]) j = (j + 1) & (intern_size - 1);
    intern_tab[j] = str;
    intern_count++;
}

Object *string_intern(Thread *t, Object *str) {
    int n, slot;
    uint16_t *s = string_chars(str, &n);
    Object *e = intern_find(s, n, str_hash(s, n), &slot);
    if (e) return e;
    intern_insert(str);
    return str;
}

Object *string_intern_sym(Thread *t, Symbol *sy) {
    int m = mutf8_to_utf16((const uint8_t *)sy->str, (int)sy->len, NULL);
    uint16_t sbuf[256];
    uint16_t *buf = m <= 256 ? sbuf : (uint16_t *)malloc((size_t)m * 2);
    mutf8_to_utf16((const uint8_t *)sy->str, (int)sy->len, buf);
    int slot;
    Object *e = intern_find(buf, m, str_hash(buf, m), &slot);
    if (!e) {
        e = string_new_utf16(t, buf, m);
        if (e) intern_insert(e);
    }
    if (buf != sbuf) free(buf);
    return e;
}

char *string_to_utf8(Object *str) {
    if (!str) {
        char *r = (char *)malloc(5);
        memcpy(r, "null", 5);
        return r;
    }
    int n;
    uint16_t *s = string_chars(str, &n);
    int m = utf16_to_utf8(s, n, NULL);
    char *r = (char *)malloc((size_t)m + 1);
    utf16_to_utf8(s, n, r);
    r[m] = 0;
    return r;
}

void strings_free(void) {
    free(intern_tab);
    intern_tab = NULL;
    intern_size = intern_count = 0;
}
