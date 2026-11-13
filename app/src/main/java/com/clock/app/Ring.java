package com.clock.app;

import android.content.Context;
import android.content.Intent;

import androidx.core.content.ContextCompat;

final class Ring {
    private Ring() {
    }

    static final String KIND_ALARM = "alarm", KIND_TIMER = "timer";

    static void start(Context c, String kind, int id, String label, long chain, String chainLabel) {
        Intent i = new Intent(c, RingService.class).setAction(RingService.A_START)
                .putExtra("kind", kind).putExtra("id", id).putExtra("label", label == null ? "" : label)
                .putExtra("chain", chain).putExtra("chainLabel", chainLabel == null ? "" : chainLabel);
        ContextCompat.startForegroundService(c, i);
    }
}
