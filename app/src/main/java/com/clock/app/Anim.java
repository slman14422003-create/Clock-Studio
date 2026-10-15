package com.clock.app;

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

    /** Icon "pop" when a tab becomes selected. */
    static void bounce(View v) {
        if (v == null) return;
        v.animate().cancel();
        v.setScaleX(0.78f);
        v.setScaleY(0.78f);
        v.animate().scaleX(1f).scaleY(1f).setDuration(520).setInterpolator(SPRING).start();
    }

    /** A view appears with a small spring zoom. */
    static void popIn(View v) {
        if (v == null) return;
        v.animate().cancel();
        v.setAlpha(0f);
        v.setScaleX(0.9f);
        v.setScaleY(0.9f);
        v.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(480).setInterpolator(SPRING).start();
    }

    /** A view rises from below and settles. */
    static void riseIn(View v, float fromDp, long delay) {
        if (v == null) return;
        v.animate().cancel();
        v.setAlpha(0f);
        v.setTranslationY(Ui.dpf(v.getContext(), fromDp));
        v.animate().alpha(1f).translationY(0f).setStartDelay(delay).setDuration(620).setInterpolator(SPRING).start();
    }
}
