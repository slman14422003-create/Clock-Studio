package com.clock.app;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;

import java.io.File;

/** Plays a recitation ayah after ayah from the first requested one to the end of the Quran, local file first, else streamed. */
final class QuranPlayer {
    interface Listener {
        void onAyah(int ayah);

        void onState(boolean playing);

        void onError();
    }

    private static QuranPlayer inst;

    static QuranPlayer get() {
        if (inst == null) inst = new QuranPlayer();
        return inst;
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private MediaPlayer mp;
    private Context app;
    private QuranData d;
    private QuranAudio.Reciter reciter;
    private int idx = -1;
    private boolean playing, prepared;
    Listener listener;

    boolean isPlaying() {
        return playing;
    }

    /** Paused with a loaded ayah that can continue. */
    boolean canResume() {
        return mp != null && prepared && !playing;
    }

    void play(Context c, QuranData data, QuranAudio.Reciter r, int from) {
        app = c.getApplicationContext();
        d = data;
        reciter = r;
        try {
            AudioManager am = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) am.requestAudioFocus(f -> {
                if (f == AudioManager.AUDIOFOCUS_LOSS || f == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) pause();
            }, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        } catch (Throwable ignored) {
        }
        start(from);
    }

    private void start(int i) {
        release();
        if (i < 0 || i >= QuranData.AYAHS) {
            idx = -1;
            playing = false;
            fireState();
            return;
        }
        idx = i;
        final int mine = i;
        try {
            MediaPlayer m = new MediaPlayer();
            mp = m;
            m.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            m.setWakeMode(app, PowerManager.PARTIAL_WAKE_LOCK);
            File f = QuranAudio.file(app, reciter, d.surahOf[i], d.num[i]);
            if (f.length() > 0) m.setDataSource(f.getAbsolutePath());
            else m.setDataSource(QuranAudio.url(reciter, d.surahOf[i], d.num[i]));
            m.setOnPreparedListener(p -> {
                if (mp != p) return;
                prepared = true;
                p.start();
                playing = true;
                if (listener != null) listener.onAyah(mine);
                fireState();
            });
            m.setOnCompletionListener(p -> {
                if (mp != p) return;
                main.post(() -> start(mine + 1));
            });
            m.setOnErrorListener((p, what, extra) -> {
                if (mp == p) fail();
                return true;
            });
            m.prepareAsync();
        } catch (Throwable e) {
            fail();
        }
    }

    private void fail() {
        stop();
        if (listener != null) listener.onError();
    }

    void pause() {
        if (mp != null && playing) {
            try {
                mp.pause();
            } catch (Throwable ignored) {
            }
            playing = false;
            fireState();
        }
    }

    void resume() {
        if (canResume()) {
            try {
                mp.start();
                playing = true;
            } catch (Throwable ignored) {
            }
            fireState();
        }
    }

    void stop() {
        release();
        playing = false;
        idx = -1;
        fireState();
    }

    private void release() {
        prepared = false;
        if (mp != null) {
            try {
                mp.release();
            } catch (Throwable ignored) {
            }
            mp = null;
        }
    }

    private void fireState() {
        if (listener != null) listener.onState(playing);
    }
}
