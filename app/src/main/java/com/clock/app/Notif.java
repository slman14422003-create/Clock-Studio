package com.clock.app;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

/** Notification channels and the notifications that are not the full-screen ring: timers, reminders, missed. */
final class Notif {
    private Notif() {
    }

    static final String CH_RING = "ring", CH_TIMER = "timer", CH_TASK = "task", CH_MISSED = "missed", CH_BEDTIME = "bedtime";
    private static final String TAG_TIMER = "timer", TAG_TASK = "task", TAG_MISSED = "missed", TAG_BEDTIME = "bedtime";

    static void init(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        List<NotificationChannel> list = new ArrayList<>();
        NotificationChannel ring = new NotificationChannel(CH_RING, c.getString(R.string.ch_ring), NotificationManager.IMPORTANCE_HIGH);
        ring.setSound(null, null);
        ring.enableVibration(false);
        ring.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        list.add(ring);
        NotificationChannel timer = new NotificationChannel(CH_TIMER, c.getString(R.string.ch_timer), NotificationManager.IMPORTANCE_LOW);
        timer.setShowBadge(false);
        list.add(timer);
        list.add(new NotificationChannel(CH_TASK, c.getString(R.string.ch_task), NotificationManager.IMPORTANCE_HIGH));
        list.add(new NotificationChannel(CH_MISSED, c.getString(R.string.ch_missed), NotificationManager.IMPORTANCE_DEFAULT));
        list.add(new NotificationChannel(CH_BEDTIME, c.getString(R.string.ch_bedtime), NotificationManager.IMPORTANCE_DEFAULT));
        nm.createNotificationChannels(list);
    }

    static PendingIntent receiverAction(Context c, String action, int id) {
        Intent i = new Intent(c, ClockReceiver.class).setAction(action)
                .setData(Uri.parse("clock://act/" + action + "/" + id)).putExtra("id", id);
        return PendingIntent.getBroadcast(c, 0, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    static PendingIntent openTab(Context c, int tab) {
        Intent i = new Intent(c, MainActivity.class).setData(Uri.parse("clock://open/" + tab)).putExtra("tab", tab)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(c, 0, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @SuppressLint("MissingPermission")
    private static void post(Context c, String tag, int id, Notification n) {
        try {
            NotificationManagerCompat.from(c).notify(tag, id, n);
        } catch (SecurityException ignored) {
        }
    }

    static void cancelTag(Context c, String tag, int id) {
        try {
            NotificationManagerCompat.from(c).cancel(tag, id);
        } catch (Exception ignored) {
        }
    }

    /** Ongoing notification of a running or paused timer with a live countdown and pause / cancel buttons. */
    static void timerRunning(Context c, TimerItem t) {
        String title = t.label.isEmpty() ? c.getString(R.string.timer) : t.label;
        NotificationCompat.Builder b = new NotificationCompat.Builder(c, CH_TIMER)
                .setSmallIcon(R.drawable.ic_timer)
                .setContentTitle(title)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setContentIntent(openTab(c, NavBar.TIMERS));
        if (t.state == TimerItem.RUNNING) {
            b.setUsesChronometer(true).setChronometerCountDown(true).setShowWhen(true).setWhen(t.endAt);
            b.setContentText(c.getString(R.string.timer_running));
            b.addAction(0, c.getString(R.string.pause), receiverAction(c, ClockReceiver.A_TIMER_PAUSE, t.id));
        } else {
            b.setShowWhen(false);
            b.setContentText(c.getString(R.string.paused) + " \u00B7 " + Fmt.countdown(t.remaining));
            b.addAction(0, c.getString(R.string.resume), receiverAction(c, ClockReceiver.A_TIMER_RESUME, t.id));
        }
        b.addAction(0, c.getString(R.string.cancel), receiverAction(c, ClockReceiver.A_TIMER_CANCEL, t.id));
        post(c, TAG_TIMER, t.id, b.build());
    }

    static void cancelTimer(Context c, int id) {
        cancelTag(c, TAG_TIMER, id);
    }

    static void taskDue(Context c, Task t) {
        String text = t.notes.isEmpty() ? Fmt.taskDue(c, t) : t.notes;
        NotificationCompat.Builder b = new NotificationCompat.Builder(c, CH_TASK)
                .setSmallIcon(R.drawable.ic_check_circle)
                .setContentTitle(t.title)
                .setContentText(text)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(openTab(c, NavBar.TASKS))
                .addAction(0, c.getString(R.string.mark_done), receiverAction(c, ClockReceiver.A_TASK_DONE, t.id))
                .addAction(0, c.getString(R.string.later_10), receiverAction(c, ClockReceiver.A_TASK_LATER, t.id));
        post(c, TAG_TASK, t.id, b.build());
    }

    static void cancelTask(Context c, int id) {
        cancelTag(c, TAG_TASK, id);
    }

    /** Gentle "time to wind down" reminder before the Bedtime schedule's sleep time. */
    static void bedtime(Context c) {
        Bedtime b = Bedtime.load(c);
        String bed = Bedtime.label(c, b.bedHour(), b.bedMinute());
        String wake = Bedtime.label(c, b.wakeHour, b.wakeMinute);
        NotificationCompat.Builder nb = new NotificationCompat.Builder(c, CH_BEDTIME)
                .setSmallIcon(R.drawable.ic_moon)
                .setContentTitle(c.getString(R.string.bedtime_notif_title))
                .setContentText(c.getString(R.string.bedtime_notif_text, bed, wake))
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(openTab(c, NavBar.ALARMS));
        post(c, TAG_BEDTIME, 1, nb.build());
    }

    static void cancelBedtime(Context c) {
        cancelTag(c, TAG_BEDTIME, 1);
    }

    static void missedTimer(Context c, TimerItem t) {
        missed(c, c.getString(R.string.timer_done), t.label.isEmpty() ? c.getString(R.string.timer) : t.label, t.id);
    }

    static void missedRing(Context c, String kind, String label) {
        boolean alarm = Ring.KIND_ALARM.equals(kind);
        String title = c.getString(alarm ? R.string.missed_alarm : R.string.timer_done);
        String text = label == null || label.isEmpty()
                ? Fmt.time(c, System.currentTimeMillis(), TimeZone.getDefault(), false) : label;
        missed(c, title, text, (int) (System.currentTimeMillis() % 100000));
    }

    private static void missed(Context c, String title, String text, int id) {
        Notification n = new NotificationCompat.Builder(c, CH_MISSED)
                .setSmallIcon(R.drawable.ic_alarm)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true)
                .setContentIntent(openTab(c, NavBar.ALARMS))
                .build();
        post(c, TAG_MISSED, id, n);
    }
}
