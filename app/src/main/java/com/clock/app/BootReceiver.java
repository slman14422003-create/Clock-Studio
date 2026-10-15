package com.clock.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** After a reboot, an app update or a clock / time zone change every trigger is booked again. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        Alarms.rescheduleAll(c);
        Timers.rescheduleAll(c);
        Tasks.rescheduleAll(c);
    }
}
