package com.clock.app;

import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.view.View;
import android.view.animation.PathInterpolator;

/** The motion language of the app: iOS ease curves and a soft damped spring (about 7% overshoot, settles quickly). */
final class Anim {
    private Anim() {
    }

    /** Navigation / sheet curve: fast start, long soft landing. */
    static final PathInterpolator OUT = new PathInterpolator(0.32f, 0.72f, 0f, 1f);
    /** Leaving curve. */
    static final PathInterpolator IN = new PathInterpolator(0.42f, 0f, 1f, 1f);
    /** Damped spring: 1 - e^(-7.5t) * cos(9t). */
    static final TimeInterpolator SPRING = t -> t >= 1f ? 1f : (float) (1.0 - Math.exp(-7.5 * t) * Math.cos(9.0 * t));

    /** One duration scale for the whole app. */
    static final long D_TAP = 120, D_SHORT = 220, D_MED = 420, D_LONG = 600;

    /** Tap effect of an icon, chosen by what the icon shows: refresh spins, bells wiggle, bookmarks pop, share lifts... */
    static void icon(View v, int res) {
        if (v == null || res == 0) return;
        String n;
        try {
            n = v.getResources().getResourceEntryName(res);
        } catch (Exception e) {
            n = "";
        }
        final float d = Ui.dpf(v.getContext(), 1);
        v.animate().cancel();
        v.setRotation(0f);
        v.setTranslationX(0f);
        v.setTranslationY(0f);
        if (n.contains("refresh") || n.contains("repeat") || n.contains("sync")) {
            ObjectAnimator a = ObjectAnimator.ofFloat(v, View.ROTATION, 0f, 360f);
            a.setDuration(D_LONG + 140);
            a.setInterpolator(OUT);
            a.start();
        } else if (n.contains("bell") || n.contains("alarm") || n.contains("timer") || n.contains("clock") || n.contains("bedtime") || n.contains("moon")) {
            ObjectAnimator a = ObjectAnimator.ofFloat(v, View.ROTATION, 0f, 16f, -14f, 10f, -6f, 0f);
            a.setDuration(D_LONG + 100);
            a.start();
        } else if (n.contains("sliders") || n.contains("settings") || n.contains("gear") || n.contains("palette") || n.contains("more")) {
            ObjectAnimator a = ObjectAnimator.ofFloat(v, View.ROTATION, 0f, 90f, 0f);
            a.setDuration(D_LONG);
            a.setInterpolator(OUT);
            a.start();
        } else if (n.contains("plus") || n.equals("ic_x") || n.contains("close") || n.contains("add")) {
            ObjectAnimator a = ObjectAnimator.ofFloat(v, View.ROTATION, 0f, 90f);
            a.setDuration(D_MED);
            a.setInterpolator(OUT);
            a.start();
        } else if (n.contains("share") || n.contains("upload") || n.contains("download")) {
            ObjectAnimator a = ObjectAnimator.ofFloat(v, View.TRANSLATION_Y, 0f, -5f * d, 0f);
            a.setDuration(D_MED);
            a.setInterpolator(SPRING);
            a.start();
        } else if (n.contains("play") || n.contains("sound") || n.contains("volume") || n.contains("mic") || n.contains("vibrate")) {
            v.setScaleX(1f);
            v.setScaleY(1f);
            v.animate().scaleX(1.22f).scaleY(1.22f).setDuration(D_TAP).withEndAction(
                    () -> v.animate().scaleX(1f).scaleY(1f).setDuration(D_MED).setInterpolator(SPRING).start()).start();
        } else {
            bounce(v);
        }
    }

    /** Quick scale pulse for a value that just changed (counters). */
    static void pulse(View v) {
        if (v == null) return;
        v.animate().cancel();
        v.setScaleX(1.08f);
        v.setScaleY(1.08f);
        v.animate().scaleX(1f).scaleY(1f).setDuration(D_MED).setInterpolator(SPRING).start();
    }

    /** Icon "pop" when a tab becomes selected. */
    static void bounce(View v) {
        if (v == null) return;
        v.animate().cancel();
        v.setScaleX(0.78f);
        v.setScaleY(0.78f);
        v.animate().scaleX(1f).scaleY(1f).setDuration(D_LONG).setInterpolator(SPRING).start();
    }

    /** A view appears with a small spring zoom. */
    static void popIn(View v) {
        if (v == null) return;
        v.animate().cancel();
        v.setAlpha(0f);
        v.setScaleX(0.9f);
        v.setScaleY(0.9f);
        v.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(D_MED).setInterpolator(SPRING).start();
    }

    /** A view rises from below and settles. */
    static void riseIn(View v, float fromDp, long delay) {
        if (v == null) return;
        v.animate().cancel();
        v.setAlpha(0f);
        v.setTranslationY(Ui.dpf(v.getContext(), fromDp));
        v.animate().alpha(1f).translationY(0f).setStartDelay(delay).setDuration(D_LONG).setInterpolator(SPRING).start();
    }
}
