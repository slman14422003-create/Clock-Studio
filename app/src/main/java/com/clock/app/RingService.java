package com.clock.app;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;

import java.util.TimeZone;

/** Plays the alarm / timer sound, vibrates and shows the full-screen ring notification until it is stopped. */
public class RingService extends Service {
    static final String A_START = "app.clock.action.RING_START";
    static final String A_STOP = "app.clock.action.RING_STOP";
    static final String A_SNOOZE = "app.clock.action.RING_SNOOZE";
    static final String A_PLUS = "app.clock.action.RING_PLUS";
    static final String A_ENDED = "app.clock.action.RING_ENDED";
    private static final int NOTIF_ID = 4242;

    static volatile boolean active;

    private final Handler h = new Handler(Looper.getMainLooper());
    private MediaPlayer player;
    private Vibrator vibrator;
    private PowerManager.WakeLock wakeLock;
    private float vol = 1f;

    private String kind = Ring.KIND_ALARM;
    private int id;
    private String label = "";
    private long chain;
    private String chainLabel = "";

    private final Runnable rampStep = new Runnable() {
        @Override
        public void run() {
            vol = Math.min(1f, vol + 0.06f);
            applyVolume();
            if (vol < 1f) h.postDelayed(this, 1000);
        }
    };

    private final Runnable autoStop = () -> {
        Notif.missedRing(this, kind, label);
        end();
    };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent i, int flags, int startId) {
        String a = i == null ? null : i.getAction();
        if (a == null) {
            if (!active) stopSelf();
            return START_NOT_STICKY;
        }
        switch (a) {
            case A_START:
                begin(i);
                break;
            case A_STOP:
                onStop();
                break;
            case A_SNOOZE:
                if (active && Ring.KIND_ALARM.equals(kind)) Alarms.snooze(this, id, Store.snoozeMin(this));
                end();
                break;
            case A_PLUS:
                if (active) Timers.start(this, 60000L, label, 0, "");
                end();
                break;
            default:
                break;
        }
        return START_NOT_STICKY;
    }

    private void begin(Intent i) {
        if (active) stopEffects();
        kind = i.getStringExtra("kind") == null ? Ring.KIND_ALARM : i.getStringExtra("kind");
        id = i.getIntExtra("id", 0);
        label = i.getStringExtra("label") == null ? "" : i.getStringExtra("label");
        chain = i.getLongExtra("chain", 0);
        chainLabel = i.getStringExtra("chainLabel") == null ? "" : i.getStringExtra("chainLabel");
        active = true;

        Alarm al = Ring.KIND_ALARM.equals(kind) ? Alarms.get(this, id) : null;
        boolean vibrate = al != null ? al.vibrate : Store.defaultVibrate(this);
        boolean ramp = al != null && al.ramp;
        String sound = al != null ? al.sound : "";

        try {
            ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(al), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } catch (Throwable ignored) {
        }

        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "clock:ring");
            wakeLock.acquire(15 * 60 * 1000L);
        } catch (Exception ignored) {
        }

        vol = ramp ? 0.1f : 1f;
        startSound(sound);
        if (ramp) h.postDelayed(rampStep, 1000);
        if (vibrate) startVibration();
        h.postDelayed(autoStop, Store.silenceMin(this) * 60000L);
        try {
            startActivity(activityIntent());
        } catch (Exception ignored) {
        }
    }

    private Intent activityIntent() {
        return new Intent(this, RingActivity.class)
                .putExtra("kind", kind).putExtra("id", id).putExtra("label", label)
                .putExtra("chain", chain).putExtra("chainLabel", chainLabel)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    }

    private PendingIntent svc(String action, int req) {
        Intent i = new Intent(this, RingService.class).setAction(action);
        return PendingIntent.getService(this, req, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private Notification buildNotification(Alarm al) {
        boolean isAlarm = Ring.KIND_ALARM.equals(kind);
        String title = getString(isAlarm ? R.string.alarm : R.string.timer);
        String text = label.isEmpty()
                ? (isAlarm ? Fmt.time(this, System.currentTimeMillis(), TimeZone.getDefault(), false) : getString(R.string.timer_done))
                : label;
        PendingIntent full = PendingIntent.getActivity(this, 7, activityIntent(), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder b = new NotificationCompat.Builder(this, Notif.CH_RING)
                .setSmallIcon(isAlarm ? R.drawable.ic_alarm : R.drawable.ic_timer)
                .setContentTitle(title)
                .setContentText(text)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setAutoCancel(false)
                .setFullScreenIntent(full, true)
                .setContentIntent(full);
        boolean canSnooze = !isAlarm || al == null || al.snooze;
        if (canSnooze) {
            b.addAction(0, getString(isAlarm ? R.string.snooze : R.string.plus_one), svc(isAlarm ? A_SNOOZE : A_PLUS, 2));
        }
        boolean challenge = al != null && al.challenge;
        b.addAction(0, getString(R.string.stop), challenge ? full : svc(A_STOP, 3));
        return b.build();
    }

    private void startSound(String sound) {
        Uri uri = null;
        if (sound != null && !sound.isEmpty()) {
            try {
                uri = Uri.parse(sound);
            } catch (Exception ignored) {
            }
        }
        if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        if (uri == null) return;
        try {
            MediaPlayer mp = new MediaPlayer();
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            mp.setDataSource(this, uri);
            mp.setLooping(true);
            mp.prepare();
            mp.setVolume(vol, vol);
            mp.start();
            player = mp;
        } catch (Exception e) {
            if (sound != null && !sound.isEmpty()) startSound("");
        }
    }

    private void applyVolume() {
        if (player != null) {
            try {
                player.setVolume(vol, vol);
            } catch (Exception ignored) {
            }
        }
    }

    @SuppressWarnings("deprecation")
    private void startVibration() {
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                VibratorManager vm = (VibratorManager) getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                vibrator = vm == null ? null : vm.getDefaultVibrator();
            } else {
                vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            }
            if (vibrator == null || !vibrator.hasVibrator()) return;
            long[] pattern = {0, 700, 500, 700, 1000};
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0), attrs);
        } catch (Exception ignored) {
        }
    }

    private void onStop() {
        if (!active) {
            stopSelf();
            return;
        }
        if (Ring.KIND_TIMER.equals(kind) && chain > 0) Timers.start(this, chain, chainLabel, 0, "");
        end();
    }

    private void stopEffects() {
        h.removeCallbacks(rampStep);
        h.removeCallbacks(autoStop);
        if (player != null) {
            try {
                player.stop();
            } catch (Exception ignored) {
            }
            player.release();
            player = null;
        }
        if (vibrator != null) {
            try {
                vibrator.cancel();
            } catch (Exception ignored) {
            }
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            try {
                wakeLock.release();
            } catch (Exception ignored) {
            }
        }
    }

    private void end() {
        stopEffects();
        active = false;
        try {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } catch (Exception ignored) {
        }
        sendBroadcast(new Intent(A_ENDED).setPackage(getPackageName()));
        stopSelf();
    }

    @Override
    public void onDestroy() {
        stopEffects();
        active = false;
        super.onDestroy();
    }
}
