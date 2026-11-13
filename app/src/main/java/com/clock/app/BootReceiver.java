package com.clock.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** After a reboot, an app update or a clock / time zone change every trigger is booked again. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        try {
            Alarms.rescheduleAll(c);
        } catch (Throwable ignored) {
        }
        try {
            Timers.rescheduleAll(c);
        } catch (Throwable ignored) {
        }
        try {
            Tasks.rescheduleAll(c);
        } catch (Throwable ignored) {
        }
        try {
            Bedtime.reschedule(c);
        } catch (Throwable ignored) {
        }
        try {
            PrayerTimes.scheduleNext(c);
        } catch (Throwable ignored) {
        }
        String a = i == null ? null : i.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(a) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)) {
            try {
                StandByService.sync(c);
            } catch (Throwable ignored) {
            }
        }
    }
}
