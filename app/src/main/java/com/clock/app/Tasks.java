package com.clock.app;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

final class Tasks {
    private Tasks() {
    }

    static final int TODAY = 0, SCHEDULED = 1, ALL = 2, DONE = 3;

    static List<Task> all(Context c) {
        JSONArray a = Store.array(c, Store.TASKS);
        List<Task> out = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null) out.add(Task.fromJson(o));
        }
        return out;
    }

    static Task get(Context c, int id) {
        for (Task t : all(c)) if (t.id == id) return t;
        return null;
    }

    private static void write(Context c, List<Task> list) {
        JSONArray a = new JSONArray();
        for (Task t : list) a.put(t.toJson());
        Store.putArray(c, Store.TASKS, a);
    }

    static void save(Context c, Task t) {
        if (t.id == 0) {
            t.id = Store.nextId(c);
            t.created = System.currentTimeMillis();
        }
        List<Task> list = all(c);
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
        reschedule(c, t);
        Bus.changed();
    }

    static void delete(Context c, int id) {
        List<Task> list = all(c);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id == id) {
                list.remove(i);
                break;
            }
        }
        write(c, list);
        Sched.cancel(c, Sched.KIND_TASK, id);
        Notif.cancelTask(c, id);
        Bus.changed();
    }

    /** Completes a task; a repeating one jumps to its next date instead of closing. */
    static void toggle(Context c, int id) {
        Task t = get(c, id);
        if (t == null) return;
        long now = System.currentTimeMillis();
        if (!t.done && t.repeat != Task.REPEAT_NONE && t.due > 0) {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(t.due);
            int guard = 0;
            do {
                if (t.repeat == Task.REPEAT_DAILY) cal.add(Calendar.DAY_OF_YEAR, 1);
                else if (t.repeat == Task.REPEAT_WEEKLY) cal.add(Calendar.WEEK_OF_YEAR, 1);
                else cal.add(Calendar.MONTH, 1);
            } while (cal.getTimeInMillis() <= now && ++guard < 800);
            t.due = cal.getTimeInMillis();
        } else {
            t.done = !t.done;
            t.doneAt = t.done ? now : 0;
        }
        Notif.cancelTask(c, id);
        save(c, t);
    }

    static void postpone(Context c, int id, long ms) {
        Task t = get(c, id);
        if (t == null) return;
        t.due = System.currentTimeMillis() + ms;
        t.hasTime = true;
        Notif.cancelTask(c, id);
        save(c, t);
    }

    static void reschedule(Context c, Task t) {
        Sched.cancel(c, Sched.KIND_TASK, t.id);
        if (!t.done && t.remind && t.hasTime && t.due > System.currentTimeMillis()) {
            Sched.exact(c, t.due, Sched.KIND_TASK, t.id);
        }
    }

    static void rescheduleAll(Context c) {
        for (Task t : all(c)) reschedule(c, t);
    }

    static List<Task> filter(Context c, int smart) {
        long now = System.currentTimeMillis();
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(Fmt.startOfDay(now));
        cal.add(Calendar.DAY_OF_YEAR, 1);
        long endOfToday = cal.getTimeInMillis();
        List<Task> out = new ArrayList<>();
        for (Task t : all(c)) {
            boolean keep;
            if (smart == DONE) keep = t.done;
            else if (t.done) keep = false;
            else if (smart == TODAY) keep = t.due > 0 && t.due < endOfToday;
            else if (smart == SCHEDULED) keep = t.due > 0;
            else keep = true;
            if (keep) out.add(t);
        }
        if (smart == DONE) {
            Collections.sort(out, (a, b) -> Long.compare(b.doneAt, a.doneAt));
        } else {
            Collections.sort(out, (a, b) -> {
                long da = a.due > 0 ? a.due : Long.MAX_VALUE;
                long db = b.due > 0 ? b.due : Long.MAX_VALUE;
                if (da != db) return Long.compare(da, db);
                if (a.priority != b.priority) return b.priority - a.priority;
                return Long.compare(a.created, b.created);
            });
        }
        return out;
    }

    static int count(Context c, int smart) {
        return filter(c, smart).size();
    }
}
