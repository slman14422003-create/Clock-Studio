package com.clock.app;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class Alarms {
    private Alarms() {
    }

    static List<Alarm> all(Context c) {
        JSONArray a = Store.array(c, Store.ALARMS);
        List<Alarm> out = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null) out.add(Alarm.fromJson(o));
        }
        Collections.sort(out, (x, y) -> (x.hour * 60 + x.minute) - (y.hour * 60 + y.minute));
        return out;
    }

    static Alarm get(Context c, int id) {
        for (Alarm a : all(c)) if (a.id == id) return a;
        return null;
    }

    private static void write(Context c, List<Alarm> list) {
        JSONArray a = new JSONArray();
        for (Alarm x : list) a.put(x.toJson());
        Store.putArray(c, Store.ALARMS, a);
    }

    private static void upsert(Context c, Alarm a) {
        List<Alarm> list = all(c);
        boolean found = false;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id == a.id) {
                list.set(i, a);
                found = true;
                break;
            }
        }
        if (!found) list.add(a);
        write(c, list);
    }

    static void save(Context c, Alarm a) {
        if (a.id == 0) a.id = Store.nextId(c);
        upsert(c, a);
        reschedule(c, a);
        changed(c);
    }

    static void delete(Context c, int id) {
        List<Alarm> list = all(c);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id == id) {
                list.remove(i);
                break;
            }
        }
        write(c, list);
        Sched.cancel(c, Sched.KIND_ALARM, id);
        Sched.cancel(c, Sched.KIND_SNOOZE, id);
        changed(c);
    }

    static void setEnabled(Context c, int id, boolean on) {
        Alarm a = get(c, id);
        if (a == null) return;
        a.enabled = on;
        save(c, a);
    }

    /** Skips the next ring of a repeating alarm; the one after it rings as usual. */
    static void skipNext(Context c, int id) {
        Alarm a = get(c, id);
        if (a == null || !a.enabled || a.days == 0) return;
        a.skipUntil = 0;
        long t = a.next(System.currentTimeMillis());
        if (t <= 0) return;
        a.skipUntil = t;
        save(c, a);
    }

    static void clearSkip(Context c, int id) {
        Alarm a = get(c, id);
        if (a == null) return;
        a.skipUntil = 0;
        save(c, a);
    }

    static void reschedule(Context c, Alarm a) {
        Sched.cancel(c, Sched.KIND_ALARM, a.id);
        Sched.cancel(c, Sched.KIND_SNOOZE, a.id);
        if (!a.enabled) return;
        long t = nextOf(c, a, System.currentTimeMillis());
        if (t > 0) Sched.alarmClock(c, t, Sched.KIND_ALARM, a.id);
    }

    /** Next ring of an alarm, honouring the global pause. */
    static long nextOf(Context c, Alarm a, long now) {
        return a.next(now, Store.pauseUntil(c));
    }

    static boolean paused(Context c) {
        return Store.pauseUntil(c) > System.currentTimeMillis();
    }

    /** Pauses every alarm until {@code until}; 0 resumes them. */
    static void setPause(Context c, long until) {
        Store.setPauseUntil(c, until);
        for (Alarm a : all(c)) reschedule(c, a);
        changed(c);
    }

    static void setAllEnabled(Context c, boolean on) {
        List<Alarm> list = all(c);
        for (Alarm a : list) {
            a.enabled = on;
            if (on) a.skipUntil = 0;
        }
        write(c, list);
        for (Alarm a : list) reschedule(c, a);
        changed(c);
    }

    static Alarm duplicate(Context c, int id) {
        Alarm a = get(c, id);
        if (a == null) return null;
        Alarm d = a.copy();
        d.id = 0;
        d.enabled = true;
        d.skipUntil = 0;
        save(c, d);
        return d;
    }

    /** One-time alarms flagged "delete after ringing" disappear once they have rung (and are not snoozed). */
    static void cleanupAfterRing(Context c, int id) {
        Alarm a = get(c, id);
        if (a != null && a.days == 0 && a.deleteAfter && !a.enabled) delete(c, id);
    }

    static void rescheduleAll(Context c) {
        for (Alarm a : all(c)) reschedule(c, a);
        WBase.refreshAll(c);
    }

    /** Called when an alarm rings: a one-time alarm switches itself off, a repeating one books its next day. */
    static void afterFire(Context c, Alarm a) {
        if (a.days == 0) {
            a.enabled = false;
            upsert(c, a);
        } else {
            long t = nextOf(c, a, System.currentTimeMillis());
            if (t > 0) Sched.alarmClock(c, t, Sched.KIND_ALARM, a.id);
        }
        changed(c);
    }

    static void snooze(Context c, int id, int minutes) {
        Sched.alarmClock(c, System.currentTimeMillis() + minutes * 60000L, Sched.KIND_SNOOZE, id);
        WBase.refreshAll(c);
    }

    /** Earliest trigger of any enabled alarm, 0 if none. */
    static long nextTrigger(Context c) {
        long now = System.currentTimeMillis();
        long best = 0;
        for (Alarm a : all(c)) {
            if (!a.enabled) continue;
            long t = nextOf(c, a, now);
            if (t > 0 && (best == 0 || t < best)) best = t;
        }
        return best;
    }

    private static void changed(Context c) {
        WBase.refreshAll(c);
        Bedtime.reschedule(c);
        Bus.changed();
    }
}
