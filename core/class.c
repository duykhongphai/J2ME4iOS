/* Class file parsing, linking and constant pool resolution. */
#include "vm.h"

typedef struct Reader {
    const uint8_t *p, *end;
    bool err;
} Reader;

static uint8_t u1(Reader *r) {
    if (r->p + 1 > r->end) { r->err = true; return 0; }
    return *r->p++;
}
static uint16_t u2(Reader *r) {
    if (r->p + 2 > r->end) { r->err = true; r->p = r->end; return 0; }
    uint16_t v = (uint16_t)((r->p[0] << 8) | r->p[1]);
    r->p += 2;
    return v;
}
static uint32_t u4(Reader *r) {
    if (r->p + 4 > r->end) { r->err = true; r->p = r->end; return 0; }
    uint32_t v = ((uint32_t)r->p[0] << 24) | ((uint32_t)r->p[1] << 16) | ((uint32_t)r->p[2] << 8) | r->p[3];
    r->p += 4;
    return v;
}
static void skip(Reader *r, size_t n) {
    if ((size_t)(r->end - r->p) < n) { r->err = true; r->p = r->end; return; }
    r->p += n;
}

/* ---------------- class table ---------------- */
static void table_add(Class *c) {
    if (!vm.class_tab) {
        vm.class_tab_size = 1024;
        vm.class_tab = (Class **)calloc(vm.class_tab_size, sizeof(Class *));
    }
    if (vm.n_classes * 2 > vm.class_tab_size) {
        int ns = vm.class_tab_size * 2;
        Class **nt = (Class **)calloc(ns, sizeof(Class *));
        for (int i = 0; i < vm.class_tab_size; i++) {
            Class *e = vm.class_tab[i];
            while (e) {
                Class *nx = e->hash_next;
                int j = e->name->hash & (ns - 1);
                e->hash_next = nt[j];
                nt[j] = e;
                e = nx;
            }
        }
        free(vm.class_tab);
        vm.class_tab = nt;
        vm.class_tab_size = ns;
    }
    int idx = c->name->hash & (vm.class_tab_size - 1);
    c->hash_next = vm.class_tab[idx];
    vm.class_tab[idx] = c;
    c->next_all = vm.all_classes;
    vm.all_classes = c;
    vm.n_classes++;
}

Class *class_lookup_loaded(Symbol *name) {
    if (!vm.class_tab) return NULL;
    for (Class *e = vm.class_tab[name->hash & (vm.class_tab_size - 1)]; e; e = e->hash_next)
        if (e->name == name) return e;
    return NULL;
}

void classes_init(void) {}

void classes_free(void) {
    free(vm.class_tab);
    vm.class_tab = NULL;
    vm.class_tab_size = 0;
    vm.n_classes = 0;
    vm.all_classes = NULL;
}

/* ---------------- descriptors ---------------- */
static void parse_desc(Symbol *desc, bool is_static, uint16_t *nargs, char *ret) {
    const char *p = desc->str;
    int n = is_static ? 0 : 1;
    if (*p == '(') p++;
    while (*p && *p != ')') {
        if (*p == 'J' || *p == 'D') { n += 2; p++; }
        else {
            while (*p == '[') p++;
            if (*p == 'L') { while (*p && *p != ';') p++; }
            if (*p) p++;
            n++;
        }
    }
    if (*p == ')') p++;
    char r = *p;
    if (r == 'Z' || r == 'B' || r == 'C' || r == 'S') r = 'I';
    if (r == '[') r = 'L';
    *nargs = (uint16_t)n;
    *ret = r;
}

static int elem_size_of(char e) {
    switch (e) {
    case 'Z': case 'B': return 1;
    case 'C': case 'S': return 2;
    case 'I': case 'F': return 4;
    case 'J': case 'D': return 8;
    default: return (int)sizeof(Object *);
    }
}

/* ---------------- parsing ---------------- */
static Class *parse_class(Thread *t, const uint8_t *data, int len) {
    Reader r = {data, data + len, false};
    if (u4(&r) != 0xCAFEBABE) return NULL;
    u2(&r);
    u2(&r);
    Class *c = (Class *)meta_alloc(sizeof(Class));
    c->cp_count = u2(&r);
    c->cp = (CPEntry *)meta_alloc(sizeof(CPEntry) * (c->cp_count ? c->cp_count : 1));
    for (int i = 1; i < c->cp_count && !r.err; i++) {
        CPEntry *e = &c->cp[i];
        e->tag = u1(&r);
        switch (e->tag) {
        case CT_UTF8: {
            uint16_t l = u2(&r);
            if ((size_t)(r.end - r.p) < l) { r.err = true; break; }
            e->v.sym = sym_intern((const char *)r.p, l);
            r.p += l;
            break;
        }
        case CT_INT: e->v.i = (int32_t)u4(&r); break;
        case CT_FLOAT: { uint32_t b = u4(&r); memcpy(&e->v.f, &b, 4); break; }
        case CT_LONG: {
            uint64_t hi = u4(&r), lo = u4(&r);
            e->v.j = (int64_t)((hi << 32) | lo);
            i++;
            break;
        }
        case CT_DOUBLE: {
            uint64_t hi = u4(&r), lo = u4(&r);
            uint64_t b = (hi << 32) | lo;
            memcpy(&e->v.d, &b, 8);
            i++;
            break;
        }
        case CT_CLASS: case CT_STRING: case CT_MTYPE: e->a = u2(&r); break;
        case CT_FIELD: case CT_METHOD: case CT_IMETHOD: case CT_NAT: case CT_INDY:
            e->a = u2(&r);
            e->b = u2(&r);
            break;
        case CT_MHANDLE: e->a = u1(&r); e->b = u2(&r); break;
        case 17: /* Dynamic */ e->a = u2(&r); e->b = u2(&r); break;
        case 19: case 20: /* Module, Package */ e->a = u2(&r); break;
        default: r.err = true; break;
        }
    }
    if (r.err) return NULL;
    c->access = u2(&r);
    uint16_t this_idx = u2(&r);
    uint16_t super_idx = u2(&r);
    if (this_idx >= c->cp_count || c->cp[this_idx].tag != CT_CLASS) return NULL;
    c->name = c->cp[c->cp[this_idx].a].v.sym;
    Symbol *super_name = (super_idx && super_idx < c->cp_count) ? c->cp[c->cp[super_idx].a].v.sym : NULL;

    c->n_ifaces = u2(&r);
    Symbol **iface_names = NULL;
    if (c->n_ifaces) {
        iface_names = (Symbol **)malloc(sizeof(Symbol *) * c->n_ifaces);
        for (int i = 0; i < c->n_ifaces; i++) {
            uint16_t ix = u2(&r);
            iface_names[i] = ix < c->cp_count ? c->cp[c->cp[ix].a].v.sym : NULL;
        }
    }

    c->n_fields = u2(&r);
    c->fields = (Field *)meta_alloc(sizeof(Field) * (c->n_fields ? c->n_fields : 1));
    for (int i = 0; i < c->n_fields && !r.err; i++) {
        Field *f = &c->fields[i];
        f->cls = c;
        f->access = u2(&r);
        f->name = c->cp[u2(&r)].v.sym;
        f->desc = c->cp[u2(&r)].v.sym;
        if (!f->name || !f->desc) { r.err = true; break; }
        f->type = f->desc->str[0];
        uint16_t ac = u2(&r);
        for (int k = 0; k < ac && !r.err; k++) {
            Symbol *an = c->cp[u2(&r)].v.sym;
            uint32_t al = u4(&r);
            if (an && !strcmp(an->str, "ConstantValue") && al == 2) {
                f->const_idx = u2(&r);
            } else skip(&r, al);
        }
    }

    c->n_methods = u2(&r);
    c->methods = (Method *)meta_alloc(sizeof(Method) * (c->n_methods ? c->n_methods : 1));
    for (int i = 0; i < c->n_methods && !r.err; i++) {
        Method *m = &c->methods[i];
        m->cls = c;
        m->access = u2(&r);
        m->name = c->cp[u2(&r)].v.sym;
        m->desc = c->cp[u2(&r)].v.sym;
        if (!m->name || !m->desc) { r.err = true; break; }
        m->vtable_index = -1;
        parse_desc(m->desc, (m->access & ACC_STATIC) != 0, &m->nargs, &m->ret);
        uint16_t ac = u2(&r);
        for (int k = 0; k < ac && !r.err; k++) {
            Symbol *an = c->cp[u2(&r)].v.sym;
            uint32_t al = u4(&r);
            const uint8_t *aend = r.p + al;
            if (an && !strcmp(an->str, "Code")) {
                m->max_stack = u2(&r);
                m->max_locals = u2(&r);
                m->code_len = u4(&r);
                if ((size_t)(r.end - r.p) < m->code_len) { r.err = true; break; }
                m->code = (uint8_t *)meta_alloc(m->code_len + 8);
                memcpy(m->code, r.p, m->code_len);
                r.p += m->code_len;
                m->n_exc = u2(&r);
                m->exc = (ExcEntry *)meta_alloc(sizeof(ExcEntry) * (m->n_exc ? m->n_exc : 1));
                for (int x = 0; x < m->n_exc; x++) {
                    m->exc[x].start = u2(&r);
                    m->exc[x].end = u2(&r);
                    m->exc[x].handler = u2(&r);
                    m->exc[x].catch_idx = u2(&r);
                }
                uint16_t cac = u2(&r);
                for (int y = 0; y < cac && !r.err; y++) {
                    Symbol *cn = c->cp[u2(&r)].v.sym;
                    uint32_t cl = u4(&r);
                    if (cn && !strcmp(cn->str, "LineNumberTable") && !m->lines) {
                        m->n_lines = u2(&r);
                        m->lines = (LineEntry *)meta_alloc(sizeof(LineEntry) * (m->n_lines ? m->n_lines : 1));
                        for (int z = 0; z < m->n_lines; z++) {
                            m->lines[z].pc = u2(&r);
                            m->lines[z].line = u2(&r);
                        }
                    } else skip(&r, cl);
                }
                if (m->max_locals < m->nargs) m->max_locals = m->nargs;
            }
            if (aend <= r.end) r.p = aend;
            else r.err = true;
        }
    }
    uint16_t cac = u2(&r);
    for (int k = 0; k < cac && !r.err; k++) {
        Symbol *an = c->cp[u2(&r)].v.sym;
        uint32_t al = u4(&r);
        if (an && !strcmp(an->str, "SourceFile") && al == 2) c->source = c->cp[u2(&r)].v.sym;
        else skip(&r, al);
    }
    /* Attribute errors at the very end are tolerated (some obfuscators append junk). */
    c->ifaces = (Class **)iface_names; /* resolved during link */
    c->super = (Class *)super_name;
    return c;
}

static bool link_class(Thread *t, Class *c) {
    Symbol *super_name = (Symbol *)c->super;
    Symbol **iface_names = (Symbol **)c->ifaces;
    c->super = NULL;
    c->ifaces = NULL;
    if (super_name) {
        c->super = class_find(t, super_name);
        if (!c->super) { free(iface_names); return false; }
    }
    if (c->n_ifaces) {
        c->ifaces = (Class **)meta_alloc(sizeof(Class *) * c->n_ifaces);
        int n = 0;
        for (int i = 0; i < c->n_ifaces; i++) {
            if (!iface_names[i]) continue;
            Class *ic = class_find(t, iface_names[i]);
            if (!ic) { free(iface_names); return false; }
            c->ifaces[n++] = ic;
        }
        c->n_ifaces = (uint16_t)n;
    }
    free(iface_names);

    /* instance layout */
    uint32_t off = c->super ? c->super->inst_size : (uint32_t)sizeof(Object);
    int nrefs = c->super ? c->super->n_ref_offs : 0;
    int nstatic = 0;
    for (int i = 0; i < c->n_fields; i++) {
        Field *f = &c->fields[i];
        if (f->access & ACC_STATIC) { f->offset = nstatic++; continue; }
        f->offset = off;
        off += 8;
        if (f->type == 'L' || f->type == '[') nrefs++;
    }
    c->inst_size = off;
    c->n_ref_offs = nrefs;
    if (nrefs) {
        c->ref_offs = (uint32_t *)meta_alloc(sizeof(uint32_t) * nrefs);
        int k = 0;
        if (c->super)
            for (; k < c->super->n_ref_offs; k++) c->ref_offs[k] = c->super->ref_offs[k];
        for (int i = 0; i < c->n_fields; i++) {
            Field *f = &c->fields[i];
            if (!(f->access & ACC_STATIC) && (f->type == 'L' || f->type == '[')) c->ref_offs[k++] = f->offset;
        }
    }
    c->n_statics = nstatic;
    c->statics = (Value *)meta_alloc(sizeof(Value) * (nstatic ? nstatic : 1));

    /* vtable */
    if (!(c->access & ACC_INTERFACE)) {
        int cap = (c->super ? c->super->vtable_len : 0) + c->n_methods;
        c->vtable = (Method **)meta_alloc(sizeof(Method *) * (cap ? cap : 1));
        int n = 0;
        if (c->super) {
            memcpy(c->vtable, c->super->vtable, sizeof(Method *) * c->super->vtable_len);
            n = c->super->vtable_len;
        }
        for (int i = 0; i < c->n_methods; i++) {
            Method *m = &c->methods[i];
            if (m->access & (ACC_STATIC | ACC_PRIVATE)) continue;
            if (m->name->str[0] == '<') continue;
            int slot = -1;
            for (int k = 0; k < n; k++) {
                if (c->vtable[k]->name == m->name && c->vtable[k]->desc == m->desc) { slot = k; break; }
            }
            if (slot < 0) slot = n++;
            c->vtable[slot] = m;
            m->vtable_index = slot;
        }
        c->vtable_len = n;
    }
    for (int i = 0; i < c->n_methods; i++) {
        Method *m = &c->methods[i];
        if (m->name == vm.s_clinit && (m->access & ACC_STATIC)) c->clinit = m;
    }
    c->state = CS_LINKED;
    return true;
}

static Class *make_array_class(Thread *t, Symbol *name) {
    Class *comp = NULL;
    char e = name->str[1];
    if (e == 'L') {
        Symbol *cn = sym_intern(name->str + 2, name->len - 3);
        comp = class_find(t, cn);
        if (!comp) return NULL;
    } else if (e == '[') {
        comp = class_find(t, sym_intern(name->str + 1, name->len - 1));
        if (!comp) return NULL;
    } else if (!strchr("ZBCSIJFD", e) || name->len != 2) {
        return NULL;
    }
    Class *obj = vm.c_Object ? vm.c_Object : class_find(t, sym("java/lang/Object"));
    if (!obj) return NULL;
    Class *c = (Class *)meta_alloc(sizeof(Class));
    c->name = name;
    c->is_array = true;
    c->elem = e;
    c->elem_size = elem_size_of(e);
    c->component = comp;
    c->super = obj;
    c->access = ACC_PUBLIC | ACC_FINAL;
    c->vtable = obj->vtable;
    c->vtable_len = obj->vtable_len;
    c->inst_size = sizeof(Array);
    c->statics = NULL;
    c->state = CS_INITIALIZED;
    table_add(c);
    return c;
}

Class *class_find(Thread *t, Symbol *name) {
    Class *c = class_lookup_loaded(name);
    if (c) return c;
    if (name->str[0] == '[') {
        c = make_array_class(t, name);
        if (!c) {
            if (t) throw_new(t, "java/lang/NoClassDefFoundError", name->str);
            else vm_fatal("cannot create array class %s", name->str);
        }
        return c;
    }
    char path[1024];
    snprintf(path, sizeof path, "%s.class", name->str);
    uint8_t *data = NULL;
    int len = 0;
    for (int i = 0; i < vm.n_jars && !data; i++) data = zip_read(vm.jars[i], path, &len);
    if (!data) {
        if (t) throw_new(t, "java/lang/NoClassDefFoundError", name->str);
        else vm_fatal("class not found: %s", name->str);
        return NULL;
    }
    c = parse_class(t, data, len);
    free(data);
    if (!c) {
        vm_log("ClassFormatError: %s", name->str);
        if (t) throw_new(t, "java/lang/NoClassDefFoundError", name->str);
        else vm_fatal("bad class %s", name->str);
        return NULL;
    }
    if (c->name != name) {
        vm_log("class name mismatch: %s vs %s", c->name->str, name->str);
        c->name = name;
    }
    if (!link_class(t, c)) return NULL;
    table_add(c);
    TRACE(2, "loaded %s", name->str);
    return c;
}

Class *class_find_c(Thread *t, const char *name) { return class_find(t, sym(name)); }

Class *array_class_of(Thread *t, Class *elem) {
    if (elem->array_class) return elem->array_class;
    char buf[1024];
    if (elem->is_array) snprintf(buf, sizeof buf, "[%s", elem->name->str);
    else snprintf(buf, sizeof buf, "[L%s;", elem->name->str);
    Class *c = class_find(t, sym(buf));
    if (c) elem->array_class = c;
    return c;
}

Class *prim_array_class(char elem) {
    char buf[3] = {'[', elem, 0};
    return class_find(NULL, sym(buf));
}

/* ---------------- type checks ---------------- */
bool is_subclass(Class *c, Class *sup) {
    for (; c; c = c->super)
        if (c == sup) return true;
    return false;
}

static bool implements(Class *c, Class *iface) {
    for (; c; c = c->super) {
        for (int i = 0; i < c->n_ifaces; i++) {
            Class *ic = c->ifaces[i];
            if (ic == iface || implements(ic, iface)) return true;
        }
    }
    return false;
}

bool is_assignable(Class *from, Class *to) {
    if (from == to || to == vm.c_Object) return true;
    if (from->is_array) {
        if (!to->is_array) return false; /* Cloneable/Serializable not modelled */
        if (from->component && to->component) return is_assignable(from->component, to->component);
        return from->elem == to->elem;
    }
    if (to->is_array) return false;
    if (to->access & ACC_INTERFACE) return implements(from, to);
    return is_subclass(from, to);
}

/* ---------------- member lookup ---------------- */
Method *find_method_local(Class *c, Symbol *name, Symbol *desc) {
    for (int i = 0; i < c->n_methods; i++)
        if (c->methods[i].name == name && c->methods[i].desc == desc) return &c->methods[i];
    return NULL;
}

static Method *find_in_ifaces(Class *c, Symbol *name, Symbol *desc) {
    for (Class *k = c; k; k = k->super) {
        for (int i = 0; i < k->n_ifaces; i++) {
            Method *m = find_method_local(k->ifaces[i], name, desc);
            if (m) return m;
            m = find_in_ifaces(k->ifaces[i], name, desc);
            if (m) return m;
        }
    }
    return NULL;
}

Method *find_method(Class *c, Symbol *name, Symbol *desc) {
    for (Class *k = c; k; k = k->super) {
        Method *m = find_method_local(k, name, desc);
        if (m) return m;
    }
    return find_in_ifaces(c, name, desc);
}

Method *find_virtual(Class *c, Symbol *name, Symbol *desc) {
    for (int i = c->vtable_len - 1; i >= 0; i--) {
        Method *m = c->vtable[i];
        if (m->name == name && m->desc == desc) return m;
    }
    /* private / static fallbacks */
    for (Class *k = c; k; k = k->super) {
        Method *m = find_method_local(k, name, desc);
        if (m && !(m->access & ACC_STATIC)) return m;
    }
    return NULL;
}

static Field *find_field_rec(Class *c, Symbol *name, Symbol *desc) {
    for (int i = 0; i < c->n_fields; i++)
        if (c->fields[i].name == name && (!desc || c->fields[i].desc == desc)) return &c->fields[i];
    for (int i = 0; i < c->n_ifaces; i++) {
        Field *f = find_field_rec(c->ifaces[i], name, desc);
        if (f) return f;
    }
    if (c->super) return find_field_rec(c->super, name, desc);
    return NULL;
}

Field *find_field(Class *c, Symbol *name, Symbol *desc) { return find_field_rec(c, name, desc); }
Field *find_field_by_name(Class *c, const char *name) { return find_field_rec(c, sym(name), NULL); }

/* ---------------- constant pool resolution ---------------- */
Class *resolve_class(Thread *t, Class *cur, int idx) {
    CPEntry *e = &cur->cp[idx];
    if (e->resolved) return e->v.cls;
    Symbol *name = cur->cp[e->a].v.sym;
    Class *c = class_find(t, name);
    if (!c) return NULL;
    e->v.cls = c;
    e->resolved = 1;
    return c;
}

Field *resolve_field(Thread *t, Class *cur, int idx) {
    CPEntry *e = &cur->cp[idx];
    if (e->resolved) return e->v.field;
    Class *c = resolve_class(t, cur, e->a);
    if (!c) return NULL;
    CPEntry *nat = &cur->cp[e->b];
    Symbol *name = cur->cp[nat->a].v.sym, *desc = cur->cp[nat->b].v.sym;
    Field *f = find_field(c, name, desc);
    if (!f) {
        throw_newf(t, "java/lang/NoSuchFieldError", "%s.%s", c->name->str, name->str);
        return NULL;
    }
    e->v.field = f;
    e->resolved = 1;
    return f;
}

Method *resolve_method(Thread *t, Class *cur, int idx) {
    CPEntry *e = &cur->cp[idx];
    if (e->resolved) return e->v.method;
    Class *c = resolve_class(t, cur, e->a);
    if (!c) return NULL;
    CPEntry *nat = &cur->cp[e->b];
    Symbol *name = cur->cp[nat->a].v.sym, *desc = cur->cp[nat->b].v.sym;
    Method *m = c->is_array ? find_method(vm.c_Object, name, desc) : find_method(c, name, desc);
    if (!m) {
        throw_newf(t, "java/lang/NoSuchMethodError", "%s.%s%s", c->name->str, name->str, desc->str);
        return NULL;
    }
    e->v.method = m;
    e->resolved = 1;
    return m;
}

Object *resolve_string(Thread *t, Class *cur, int idx) {
    CPEntry *e = &cur->cp[idx];
    if (e->resolved) return e->v.str;
    Object *s = string_intern_sym(t, cur->cp[e->a].v.sym);
    if (!s) return NULL;
    e->v.str = s;
    e->resolved = 1;
    return s;
}

/* ---------------- initialization ---------------- */
static void init_constants(Thread *t, Class *c) {
    for (int i = 0; i < c->n_fields; i++) {
        Field *f = &c->fields[i];
        if (!(f->access & ACC_STATIC) || !f->const_idx) continue;
        CPEntry *e = &c->cp[f->const_idx];
        Value *v = &c->statics[f->offset];
        switch (e->tag) {
        case CT_INT: v->i = e->v.i; break;
        case CT_FLOAT: v->f = e->v.f; break;
        case CT_LONG: v->j = e->v.j; break;
        case CT_DOUBLE: v->d = e->v.d; break;
        case CT_STRING: v->l = resolve_string(t, c, f->const_idx); break;
        }
    }
}

int class_ensure_init(Thread *t, Class *c) {
    for (;;) {
        if (c->state == CS_INITIALIZED) return 0;
        if (c->state == CS_ERROR) {
            throw_new(t, "java/lang/NoClassDefFoundError", c->name->str);
            return -1;
        }
        if (c->state == CS_INITIALIZING) {
            if (c->init_thread == t) return 0;
            t->yield = true;
            return 1;
        }
        if (c->super && c->super->state != CS_INITIALIZED) {
            int r = class_ensure_init(t, c->super);
            if (r) return r;
        }
        c->state = CS_INITIALIZING;
        c->init_thread = t;
        init_constants(t, c);
        if (t->exception) { c->state = CS_ERROR; return -1; }
        if (c->clinit) {
            Value *base = t->fp >= 0 ? t->frames[t->fp].sp : t->stack;
            if (push_frame(t, c->clinit, base, 0) < 0) { c->state = CS_ERROR; return -1; }
            t->frames[t->fp].clinit_of = c;
            TRACE(2, "clinit %s", c->name->str);
            return 1;
        }
        c->state = CS_INITIALIZED;
        c->init_thread = NULL;
        return 0;
    }
}

/* ---------------- mirrors ---------------- */
Object *class_mirror(Thread *t, Class *c) {
    if (c->mirror) return c->mirror;
    Object *o = alloc_object(t, vm.c_Class);
    if (!o) return NULL;
    OBJ_FIELD(o, vm.f_Class_vm->offset)->j = (int64_t)(intptr_t)c;
    c->mirror = o;
    return o;
}

Class *mirror_class(Object *mirror) {
    if (!mirror) return NULL;
    return (Class *)(intptr_t)OBJ_FIELD(mirror, vm.f_Class_vm->offset)->j;
}

int line_number(Method *m, int pc) {
    int best = -1, bestpc = -1;
    for (int i = 0; i < m->n_lines; i++) {
        if (m->lines[i].pc <= pc && m->lines[i].pc > bestpc) {
            bestpc = m->lines[i].pc;
            best = m->lines[i].line;
        }
    }
    return best;
}

char *class_java_name(Class *c) {
    char *s = (char *)malloc(c->name->len + 1);
    for (uint32_t i = 0; i <= c->name->len; i++) s[i] = c->name->str[i] == '/' ? '.' : c->name->str[i];
    return s;
}
