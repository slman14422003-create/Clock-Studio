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

    static void reschedule(Context c, Alarm a) {
        Sched.cancel(c, Sched.KIND_ALARM, a.id);
        Sched.cancel(c, Sched.KIND_SNOOZE, a.id);
        if (!a.enabled) return;
        long t = a.next(System.currentTimeMillis());
        if (t > 0) Sched.alarmClock(c, t, Sched.KIND_ALARM, a.id);
    }

    static void rescheduleAll(Context c) {
        for (Alarm a : all(c)) reschedule(c, a);
        ClockWidget.refresh(c);
    }

    /** Called when an alarm rings: a one-time alarm switches itself off, a repeating one books its next day. */
    static void afterFire(Context c, Alarm a) {
        if (a.days == 0) {
            a.enabled = false;
            upsert(c, a);
        } else {
            long t = a.next(System.currentTimeMillis());
            if (t > 0) Sched.alarmClock(c, t, Sched.KIND_ALARM, a.id);
        }
        changed(c);
    }

    static void snooze(Context c, int id, int minutes) {
        Sched.alarmClock(c, System.currentTimeMillis() + minutes * 60000L, Sched.KIND_SNOOZE, id);
        ClockWidget.refresh(c);
    }

    /** Earliest trigger of any enabled alarm, 0 if none. */
    static long nextTrigger(Context c) {
        long now = System.currentTimeMillis();
        long best = 0;
        for (Alarm a : all(c)) {
            if (!a.enabled) continue;
            long t = a.next(now);
            if (t > 0 && (best == 0 || t < best)) best = t;
        }
        return best;
    }

    private static void changed(Context c) {
        ClockWidget.refresh(c);
        Bus.changed();
    }
}
