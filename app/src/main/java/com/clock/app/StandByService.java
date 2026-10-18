package com.clock.app;

import android.app.KeyguardManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.OrientationEventListener;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

/**
 * Keeps StandBy ready: when the charger is connected, the screen is on and the phone is locked (and, if chosen, lying on its side)
 * the desk clock is raised over the lock screen, like the iPhone. Android only lets a background app open a screen through a
 * full-screen-intent notification (or a granted overlay permission), so both are used.
 */
public class StandByService extends Service {
    private static final int ID_FG = 4101, ID_LAUNCH = 4102;
    static final String EXTRA_AUTO = "auto";

    private final Handler h = new Handler(Looper.getMainLooper());
    private OrientationEventListener orient;
    private boolean landscape;
    private boolean registered;

    private final BroadcastReceiver rx = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            String a = i.getAction();
            if (a == null) return;
            switch (a) {
                case Intent.ACTION_POWER_CONNECTED:
                    setOrientation(true);
                    h.postDelayed(StandByService.this::check, 1200);
                    break;
                case Intent.ACTION_POWER_DISCONNECTED:
                    setOrientation(false);
                    StandByActivity.closeAuto();
                    cancelLaunch();
                    break;
                case Intent.ACTION_SCREEN_ON:
                    h.postDelayed(StandByService.this::check, 600);
                    break;
                default:
                    break;
            }
        }
    };

    static void sync(Context c) {
        Context app = c.getApplicationContext();
        try {
            if (Store.standbyAuto(app)) {
                ContextCompat.startForegroundService(app, new Intent(app, StandByService.class));
            } else {
                app.stopService(new Intent(app, StandByService.class));
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!Store.standbyAuto(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        Notif.init(this);
        Intent open = new Intent(this, StandByActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new NotificationCompat.Builder(this, Notif.CH_STANDBY)
                .setSmallIcon(R.drawable.ic_standby)
                .setContentTitle(getString(R.string.standby_svc_title))
                .setContentText(getString(R.string.standby_svc_text))
                .setContentIntent(pi)
                .setOngoing(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                .build();
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(ID_FG, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(ID_FG, n);
            }
        } catch (Throwable e) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!registered) {
            registered = true;
            IntentFilter f = new IntentFilter();
            f.addAction(Intent.ACTION_POWER_CONNECTED);
            f.addAction(Intent.ACTION_POWER_DISCONNECTED);
            f.addAction(Intent.ACTION_SCREEN_ON);
            ContextCompat.registerReceiver(this, rx, f, ContextCompat.RECEIVER_NOT_EXPORTED);
            setOrientation(charging());
        }
        return START_STICKY;
    }

    private void setOrientation(boolean on) {
        if (!on) {
            if (orient != null) orient.disable();
            orient = null;
            return;
        }
        if (orient != null) return;
        orient = new OrientationEventListener(this) {
            @Override
            public void onOrientationChanged(int deg) {
                if (deg == ORIENTATION_UNKNOWN) return;
                boolean side = (deg > 60 && deg < 120) || (deg > 240 && deg < 300);
                boolean upright = deg < 30 || deg > 330 || (deg > 150 && deg < 210);
                boolean old = landscape;
                if (side) landscape = true;
                else if (upright) landscape = false;
                if (landscape && !old) h.postDelayed(StandByService.this::check, 400);
            }
        };
        if (orient.canDetectOrientation()) orient.enable();
    }

    private boolean charging() {
        Intent b = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (b == null) return false;
        return b.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
    }

    private void check() {
        if (!Store.standbyAuto(this) || !charging() || StandByActivity.visible()) return;
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        if (pm == null || !pm.isInteractive()) return;
        if (km != null && !km.isKeyguardLocked()) return;
        if (Store.standbyLandscape(this) && !landscape) return;
        launch();
    }

    private void launch() {
        Intent i = new Intent(this, StandByActivity.class).putExtra(EXTRA_AUTO, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (Settings.canDrawOverlays(this)) {
            try {
                startActivity(i);
            } catch (Throwable ignored) {
            }
        }
        if (StandByActivity.visible()) return;
        PendingIntent pi = PendingIntent.getActivity(this, 1, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new NotificationCompat.Builder(this, Notif.CH_STANDBY_LAUNCH)
                .setSmallIcon(R.drawable.ic_standby)
                .setContentTitle(getString(R.string.standby_svc_title))
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                .setSilent(true)
                .setAutoCancel(true)
                .setTimeoutAfter(15000)
                .setFullScreenIntent(pi, true)
                .setContentIntent(pi)
                .build();
        try {
            NotificationManagerCompat.from(this).notify(ID_LAUNCH, n);
        } catch (SecurityException ignored) {
        }
    }

    private void cancelLaunch() {
        try {
            NotificationManagerCompat.from(this).cancel(ID_LAUNCH);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onDestroy() {
        h.removeCallbacksAndMessages(null);
        if (orient != null) orient.disable();
        if (registered) {
            try {
                unregisterReceiver(rx);
            } catch (Throwable ignored) {
            }
        }
        cancelLaunch();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
