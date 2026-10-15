package com.clock.app;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class Timers {
    private Timers() {
    }

    static List<TimerItem> all(Context c) {
        JSONArray a = Store.array(c, Store.TIMERS);
        List<TimerItem> out = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null) out.add(TimerItem.fromJson(o));
        }
        return out;
    }

    static TimerItem get(Context c, int id) {
        for (TimerItem t : all(c)) if (t.id == id) return t;
        return null;
    }

    private static void write(Context c, List<TimerItem> list) {
        JSONArray a = new JSONArray();
        for (TimerItem t : list) a.put(t.toJson());
        Store.putArray(c, Store.TIMERS, a);
    }

    private static void upsert(Context c, TimerItem t) {
        List<TimerItem> list = all(c);
        boolean found = false;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id == t.id) {
                list.set(i, t);
                found = true;
                break;
            }
        }
        if (!found) list.add(t);
        write(c, list);
    }

    static TimerItem start(Context c, long ms, String label, long chain, String chainLabel) {
        TimerItem t = new TimerItem();
        t.id = Store.nextId(c);
        t.label = label == null ? "" : label;
        t.total = ms;
        t.state = TimerItem.RUNNING;
        t.endAt = System.currentTimeMillis() + ms;
        t.chain = chain;
        t.chainLabel = chainLabel == null ? "" : chainLabel;
        upsert(c, t);
        Sched.exact(c, t.endAt, Sched.KIND_TIMER, t.id);
        Notif.timerRunning(c, t);
        Bus.changed();
        return t;
    }

    static void pause(Context c, int id) {
        TimerItem t = get(c, id);
        if (t == null || t.state != TimerItem.RUNNING) return;
        t.remaining = Math.max(0, t.endAt - System.currentTimeMillis());
        t.state = TimerItem.PAUSED;
        upsert(c, t);
        Sched.cancel(c, Sched.KIND_TIMER, id);
        Notif.timerRunning(c, t);
        Bus.changed();
    }

    static void resume(Context c, int id) {
        TimerItem t = get(c, id);
        if (t == null || t.state != TimerItem.PAUSED) return;
        t.endAt = System.currentTimeMillis() + t.remaining;
        t.state = TimerItem.RUNNING;
        upsert(c, t);
        Sched.exact(c, t.endAt, Sched.KIND_TIMER, id);
        Notif.timerRunning(c, t);
        Bus.changed();
    }

    /** Removes a timer (cancelled by the user, or finished and handed over to the ring screen). */
    static void cancel(Context c, int id) {
        List<TimerItem> list = all(c);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id == id) {
                list.remove(i);
                break;
            }
        }
        write(c, list);
        Sched.cancel(c, Sched.KIND_TIMER, id);
        Notif.cancelTimer(c, id);
        Bus.changed();
    }

    static void rescheduleAll(Context c) {
        long now = System.currentTimeMillis();
        for (TimerItem t : all(c)) {
            if (t.state == TimerItem.RUNNING) {
                if (t.endAt <= now) {
                    cancel(c, t.id);
                    Notif.missedTimer(c, t);
                } else {
                    Sched.exact(c, t.endAt, Sched.KIND_TIMER, t.id);
                    Notif.timerRunning(c, t);
                }
            } else {
                Notif.timerRunning(c, t);
            }
        }
    }

    // ---- recents (last durations started from the picker)

    static List<Long> recents(Context c) {
        JSONArray a = Store.array(c, Store.RECENTS);
        List<Long> out = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) out.add(a.optLong(i));
        return out;
    }

    static void addRecent(Context c, long ms) {
        List<Long> list = recents(c);
        list.remove(Long.valueOf(ms));
        list.add(0, ms);
        JSONArray a = new JSONArray();
        for (int i = 0; i < list.size() && i < 6; i++) a.put(list.get(i));
        Store.putArray(c, Store.RECENTS, a);
    }
}
