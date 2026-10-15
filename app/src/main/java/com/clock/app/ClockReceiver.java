package com.clock.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Receives every scheduled trigger (alarm, snooze, timer, task) and the buttons of the notifications. */
public class ClockReceiver extends BroadcastReceiver {
    static final String A_TIMER_PAUSE = "app.clock.action.TIMER_PAUSE";
    static final String A_TIMER_RESUME = "app.clock.action.TIMER_RESUME";
    static final String A_TIMER_CANCEL = "app.clock.action.TIMER_CANCEL";
    static final String A_TASK_DONE = "app.clock.action.TASK_DONE";
    static final String A_TASK_LATER = "app.clock.action.TASK_LATER";

    @Override
    public void onReceive(Context c, Intent i) {
        String a = i.getAction();
        if (a == null) return;
        int id = i.getIntExtra("id", 0);
        if (Sched.ACTION_FIRE.equals(a)) {
            fire(c, i.getStringExtra("kind"), id);
        } else if (A_TIMER_PAUSE.equals(a)) {
            Timers.pause(c, id);
        } else if (A_TIMER_RESUME.equals(a)) {
            Timers.resume(c, id);
        } else if (A_TIMER_CANCEL.equals(a)) {
            Timers.cancel(c, id);
        } else if (A_TASK_DONE.equals(a)) {
            Tasks.toggle(c, id);
        } else if (A_TASK_LATER.equals(a)) {
            Tasks.postpone(c, id, 10 * 60000L);
        }
        Bus.changed();
    }

    private static void fire(Context c, String kind, int id) {
        if (kind == null) return;
        if (Sched.KIND_ALARM.equals(kind)) {
            Alarm a = Alarms.get(c, id);
            if (a == null || !a.enabled) return;
            Alarms.afterFire(c, a);
            Ring.start(c, Ring.KIND_ALARM, id, a.label, 0, "");
        } else if (Sched.KIND_SNOOZE.equals(kind)) {
            Alarm a = Alarms.get(c, id);
            if (a == null) return;
            Ring.start(c, Ring.KIND_ALARM, id, a.label, 0, "");
        } else if (Sched.KIND_TIMER.equals(kind)) {
            TimerItem t = Timers.get(c, id);
            if (t == null || t.state != TimerItem.RUNNING) return;
            Timers.cancel(c, id);
            Ring.start(c, Ring.KIND_TIMER, id, t.label, t.chain, t.chainLabel);
        } else if (Sched.KIND_TASK.equals(kind)) {
            Task t = Tasks.get(c, id);
            if (t == null || t.done) return;
            Notif.taskDue(c, t);
        }
    }
}
