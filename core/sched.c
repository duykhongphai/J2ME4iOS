/* Green threads, monitors and the host event queue. */
#include "vm.h"

#define QUANTUM 20000

int64_t vm_now(void) { return os_mono_ms(); }

/* ---------------- threads ---------------- */
Thread *thread_new(Object *jthread) {
    Thread *t = (Thread *)calloc(1, sizeof(Thread));
    t->id = ++vm.next_thread_id;
    t->state = TS_RUNNABLE;
    t->jthread = jthread;
    t->stack = (Value *)calloc(THREAD_STACK_SLOTS, sizeof(Value));
    t->stack_end = t->stack + THREAD_STACK_SLOTS;
    t->frames = (Frame *)calloc(THREAD_MAX_FRAMES, sizeof(Frame));
    t->fp = -1;
    /* append to keep round robin order stable */
    Thread **pp = &vm.threads;
    while (*pp) pp = &(*pp)->next;
    *pp = t;
    vm.n_live_threads++;
    return t;
}

void thread_free(Thread *t) {
    Thread **pp = &vm.threads;
    while (*pp && *pp != t) pp = &(*pp)->next;
    if (*pp) *pp = t->next;
    if (vm.last_run == t) vm.last_run = NULL;
    if (t->jthread && vm.f_Thread_vm) OBJ_FIELD(t->jthread, vm.f_Thread_vm->offset)->j = 0;
    free(t->stack);
    free(t->frames);
    free(t);
    vm.n_live_threads--;
}

/* ---------------- monitors ---------------- */
typedef struct Monitor {
    Object *obj;
    Thread *owner;
    int count;
    struct Monitor *next;
} Monitor;

#define MON_BUCKETS 1024
static Monitor *mon_tab[MON_BUCKETS];

static inline unsigned mon_hash(Object *o) { return (unsigned)(((uintptr_t)o >> 4) * 2654435761u) & (MON_BUCKETS - 1); }

static Monitor *mon_get(Object *o, bool create) {
    unsigned h = mon_hash(o);
    for (Monitor *m = mon_tab[h]; m; m = m->next)
        if (m->obj == o) return m;
    if (!create) return NULL;
    Monitor *m = (Monitor *)calloc(1, sizeof(Monitor));
    m->obj = o;
    m->next = mon_tab[h];
    mon_tab[h] = m;
    o->flags |= OF_HAS_MONITOR;
    return m;
}

void monitor_forget(Object *o) {
    unsigned h = mon_hash(o);
    Monitor **pp = &mon_tab[h];
    while (*pp) {
        if ((*pp)->obj == o) {
            Monitor *m = *pp;
            *pp = m->next;
            free(m);
            return;
        }
        pp = &(*pp)->next;
    }
}

void monitors_free(void) {
    for (int i = 0; i < MON_BUCKETS; i++) {
        Monitor *m = mon_tab[i];
        while (m) {
            Monitor *n = m->next;
            free(m);
            m = n;
        }
        mon_tab[i] = NULL;
    }
}

static bool owner_alive(Thread *o) {
    for (Thread *t = vm.threads; t; t = t->next)
        if (t == o) return t->state != TS_DEAD;
    return false;
}

bool monitor_enter(Thread *t, Object *o) {
    Monitor *m = mon_get(o, true);
    if (m->owner && m->owner != t && !owner_alive(m->owner)) { m->owner = NULL; m->count = 0; }
    if (!m->owner) { m->owner = t; m->count = 1; return true; }
    if (m->owner == t) { m->count++; return true; }
    t->state = TS_BLOCKED;
    t->blocked_on = o;
    return false;
}

bool monitor_exit(Thread *t, Object *o) {
    Monitor *m = mon_get(o, false);
    if (!m || m->owner != t) return false;
    if (--m->count == 0) m->owner = NULL;
    return true;
}

bool monitor_wait(Thread *t, Object *o, int64_t ms) {
    Monitor *m = mon_get(o, false);
    if (!m || m->owner != t) return false;
    t->wait_count = m->count;
    m->owner = NULL;
    m->count = 0;
    t->wait_obj = o;
    t->notified = false;
    t->state = TS_WAITING;
    t->wake_time = ms > 0 ? vm_now() + ms : 0;
    return true;
}

bool monitor_notify(Thread *t, Object *o, bool all) {
    Monitor *m = mon_get(o, false);
    if (!m || m->owner != t) return false;
    for (Thread *w = vm.threads; w; w = w->next) {
        if (w->state == TS_WAITING && w->wait_obj == o && !w->notified) {
            w->notified = true;
            if (!all) break;
        }
    }
    return true;
}

static bool try_reacquire(Thread *t) {
    Monitor *m = mon_get(t->wait_obj, true);
    if (m->owner && m->owner != t && !owner_alive(m->owner)) m->owner = NULL;
    if (m->owner && m->owner != t) return false;
    m->owner = t;
    m->count = t->wait_count;
    t->wait_obj = NULL;
    t->state = TS_RUNNABLE;
    t->wake_time = 0;
    return true;
}

/* ---------------- host events ---------------- */
#define EVQ 512
static int evq[EVQ][3];
static int ev_head, ev_tail;

void j4i_post_event(int type, int a, int b) {
    os_lock();
    int next = (ev_tail + 1) % EVQ;
    /* coalesce pointer drags when the queue is backing up */
    if (type == J4I_EV_POINTER_DRAGGED && ev_tail != ev_head) {
        int last = (ev_tail + EVQ - 1) % EVQ;
        if (evq[last][0] == J4I_EV_POINTER_DRAGGED) {
            evq[last][1] = a;
            evq[last][2] = b;
            os_cond_signal();
            os_unlock();
            return;
        }
    }
    if (next != ev_head) {
        evq[ev_tail][0] = type;
        evq[ev_tail][1] = a;
        evq[ev_tail][2] = b;
        ev_tail = next;
    }
    os_cond_signal();
    os_unlock();
}

static volatile int64_t force_exit_at;

void j4i_request_exit(void) {
    force_exit_at = os_mono_ms() + 1500;
    j4i_post_event(J4I_EV_EXIT, 0, 0);
}

bool events_pop(int *ev) {
    bool r = false;
    os_lock();
    if (ev_head != ev_tail) {
        ev[0] = evq[ev_head][0];
        ev[1] = evq[ev_head][1];
        ev[2] = evq[ev_head][2];
        ev_head = (ev_head + 1) % EVQ;
        r = true;
    }
    os_unlock();
    return r;
}

bool events_pending(void) {
    os_lock();
    bool r = ev_head != ev_tail;
    os_unlock();
    return r;
}

void events_reset(void) {
    os_lock();
    ev_head = ev_tail = 0;
    os_unlock();
}

/* ---------------- scheduler ---------------- */
static void deliver_interrupt(Thread *t) {
    t->interrupted = false;
    t->exception = exception_new(t, "java/lang/InterruptedException", NULL);
}

void sched_loop(void) {
    force_exit_at = 0;
    while (!vm.exit_requested) {
        int64_t now = vm_now();
        int64_t next_wake = INT64_MAX;
        if (force_exit_at) {
            if (now >= force_exit_at) { vm.exit_requested = 1; break; }
            next_wake = force_exit_at;
        }
        bool ev_pending = events_pending();
        Thread *run = NULL;
        bool any_alive = false;

        Thread *start = vm.last_run && vm.last_run->next ? vm.last_run->next : vm.threads;
        Thread *t = start;
        if (t) do {
            switch (t->state) {
            case TS_RUNNABLE: break;
            case TS_SLEEPING:
                if (t->interrupted) { t->state = TS_RUNNABLE; deliver_interrupt(t); }
                else if (now >= t->wake_time) t->state = TS_RUNNABLE;
                else if (t->wake_time < next_wake) next_wake = t->wake_time;
                break;
            case TS_WAITING:
                if (t->notified || t->interrupted || (t->wake_time && now >= t->wake_time)) {
                    if (try_reacquire(t) && t->interrupted) deliver_interrupt(t);
                } else if (t->wake_time && t->wake_time < next_wake) next_wake = t->wake_time;
                break;
            case TS_BLOCKED: {
                Monitor *m = mon_get(t->blocked_on, true);
                if (!m->owner || !owner_alive(m->owner)) {
                    m->owner = NULL;
                    t->state = TS_RUNNABLE;
                    t->blocked_on = NULL;
                }
                break;
            }
            case TS_WAIT_EVENT:
                if (ev_pending || vm.event_wakeup || (t->wake_time && now >= t->wake_time)) t->state = TS_RUNNABLE;
                else if (t->wake_time && t->wake_time < next_wake) next_wake = t->wake_time;
                break;
            case TS_DEAD: break;
            }
            if (t->state != TS_DEAD) any_alive = true;
            if (!run && t->state == TS_RUNNABLE) run = t;
            t = t->next ? t->next : vm.threads;
        } while (t != start);

        if (run) {
            vm.last_run = run;
            interp_run(run, QUANTUM);
            if (run->state == TS_DEAD) thread_free(run);
            continue;
        }
        /* reap dead threads */
        for (Thread *d = vm.threads; d;) {
            Thread *n = d->next;
            if (d->state == TS_DEAD) thread_free(d);
            d = n;
        }
        if (!any_alive || !vm.threads) break;
        os_lock();
        bool pending = ev_head != ev_tail;
        if (!pending && !vm.exit_requested) {
            int timeout = -1;
            if (next_wake != INT64_MAX) {
                int64_t d = next_wake - vm_now();
                timeout = d < 0 ? 0 : (d > 1000000 ? 1000000 : (int)d);
            }
            if (timeout != 0) os_cond_wait(timeout);
        }
        os_unlock();
    }
}
