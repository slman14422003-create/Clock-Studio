package com.clock.app;

import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;

/** Tiny in-process change bus: repositories call changed(), open screens refresh themselves. */
final class Bus {
    private Bus() {
    }

    private static final List<Runnable> LISTENERS = new ArrayList<>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    static synchronized void listen(Runnable r) {
        if (!LISTENERS.contains(r)) LISTENERS.add(r);
    }

    static synchronized void unlisten(Runnable r) {
        LISTENERS.remove(r);
    }

    static void changed() {
        final List<Runnable> copy;
        synchronized (Bus.class) {
            copy = new ArrayList<>(LISTENERS);
        }
        MAIN.post(() -> {
            for (Runnable r : copy) r.run();
        });
    }
}
