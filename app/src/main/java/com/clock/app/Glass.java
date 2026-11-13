package com.clock.app;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.os.Build;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import eightbitlab.com.blurview.BlurTarget;
import eightbitlab.com.blurview.BlurView;

/** Liquid Glass (iOS 26): live blur, translucent surfaces with a specular rim, and the helpers every screen shares. */
final class Glass {
    private Glass() {
    }

    /** Phone tilt, -1..1, smoothed. The rim light of every glass surface follows it. */
    static volatile float tiltX, tiltY;

    static boolean enabled(Context c) {
        return Store.glass(c);
    }

    /** Blur radius by the strength chosen in Settings (the library multiplies it by its scale factor). */
    static float blurRadius(Context c) {
        int l = Store.glassLevel(c);
        return l <= 0 ? 7f : l == 1 ? 11f : 15f;
    }

    static Activity activity(Context c) {
        while (c instanceof ContextWrapper) {
            if (c instanceof Activity) return (Activity) c;
            c = ((ContextWrapper) c).getBaseContext();
        }
        return null;
    }

    /** Fill of floating glass (tab bar, menus, pills): light when there is a real blur behind it, denser when not. */
    static int panelFill(Context c) {
        boolean night = Appearance.isNight(c);
        if (enabled(c)) return night ? 0xB81C1C1E : 0xB8F9F9F9;
        return night ? 0xF01C1C1E : 0xF2FFFFFF;
    }

    /** A card on the backdrop (no blur of its own: cards live inside the blurred content). */
    static Drawable card(Context c, float radiusDp) {
        return card(c, radiusDp, Ui.color(c, R.color.surface));
    }

    static Drawable card(Context c, float radiusDp, int fill) {
        if (!enabled(c)) return Ui.rect(fill, Ui.dpf(c, radiusDp));
        return new GlassDrawable(c, Ui.dpf(c, radiusDp), fill, 0.55f);
    }

    /** Round glass button background (header buttons). */
    static Drawable circle(Context c) {
        int fill = Ui.color(c, R.color.field);
        Drawable d = enabled(c) ? new GlassDrawable(c, Ui.dpf(c, 100), fill, 0.8f) : Ui.oval(fill);
        return new InsetDrawable(d, Ui.dp(c, 4));
    }

    /** A floating blurred glass panel. Without a BlurTarget (or with Glass off) it is just a dense tinted panel. */
    static BlurView panel(Context c, float radiusDp, int fill) {
        BlurView bv = new BlurView(c);
        bv.setBackground(new GlassDrawable(c, Ui.dpf(c, radiusDp), fill, 1f));
        bv.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        bv.setClipToOutline(true);
        bv.setElevation(Ui.dpf(c, 6));
        setup(c, bv);
        return bv;
    }

    static void setup(Context c, BlurView bv) {
        if (!enabled(c)) return;
        Activity a = activity(c);
        if (!(a instanceof BaseActivity)) return;
        BlurTarget t = ((BaseActivity) a).glassTarget();
        if (t == null) return;
        try {
            bv.setupWith(t).setBlurRadius(blurRadius(c));
        } catch (Throwable ignored) {
        }
    }

    // ------------------------------------------------------------------ lists that scroll under the floating bar

    private static View findScroller(View v) {
        if (v instanceof RecyclerView || v instanceof ScrollView || v instanceof androidx.core.widget.NestedScrollView) return v;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                View f = findScroller(g.getChildAt(i));
                if (f != null) return f;
            }
        }
        return null;
    }

    /** Adds bottom room to the first scrolling list inside {@code root} so its last row can rise above the bar. */
    static void padBottom(View root, int px) {
        View s = findScroller(root);
        if (s == null) s = root;
        Object o = s.getTag(R.id.tag_pad_base);
        int base;
        if (o instanceof Integer) {
            base = (Integer) o;
        } else {
            base = s.getPaddingBottom();
            s.setTag(R.id.tag_pad_base, base);
        }
        s.setPadding(s.getPaddingLeft(), s.getPaddingTop(), s.getPaddingRight(), base + px);
        if (s instanceof ViewGroup) ((ViewGroup) s).setClipToPadding(false);
    }

    // ------------------------------------------------------------------ glass toast

    private static View lastToast;

    /** Capsule message in the iOS 26 style; falls back to the system toast outside a glass screen. */
    static void toast(Context c, CharSequence text) {
        Activity a = activity(c);
        FrameLayout ov = a instanceof BaseActivity ? ((BaseActivity) a).glassOverlay() : null;
        if (ov == null || Looper.myLooper() != Looper.getMainLooper() || a.isFinishing()) {
            android.widget.Toast.makeText(c, text, android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        final BaseActivity b = (BaseActivity) a;
        if (lastToast != null && lastToast.getParent() instanceof ViewGroup) {
            ((ViewGroup) lastToast.getParent()).removeView(lastToast);
        }
        final BlurView pill = panel(a, 100, panelFill(a));
        TextView t = Ui.text(a, text, 15, R.color.text_primary);
        t.setTypeface(Ui.medium());
        t.setGravity(Gravity.CENTER);
        t.setMaxLines(3);
        pill.addView(t, new FrameLayout.LayoutParams(-2, -2));
        pill.setPadding(Ui.dp(a, 20), Ui.dp(a, 12), Ui.dp(a, 20), Ui.dp(a, 12));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        lp.bottomMargin = b.glassBottom() + Ui.dp(a, 16);
        lp.leftMargin = lp.rightMargin = Ui.dp(a, 24);
        ov.addView(pill, lp);
        lastToast = pill;
        pill.setAlpha(0f);
        pill.setScaleX(0.85f);
        pill.setScaleY(0.85f);
        pill.setTranslationY(Ui.dp(a, 20));
        pill.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f).setDuration(Anim.D_LONG)
                .setInterpolator(Anim.SPRING).start();
        pill.postDelayed(() -> {
            if (pill.getParent() == null) return;
            pill.animate().alpha(0f).scaleX(0.92f).scaleY(0.92f).setDuration(Anim.D_SHORT).setInterpolator(Anim.IN)
                    .withEndAction(() -> {
                        if (pill.getParent() instanceof ViewGroup) ((ViewGroup) pill.getParent()).removeView(pill);
                        if (lastToast == pill) lastToast = null;
                    }).start();
        }, 2300);
    }

    // ------------------------------------------------------------------ dialogs

    /** Glass dialog: the whole app behind it is blurred (Android 12+) and the panel is a translucent glass card. */
    static void styleDialog(Context c, Window w) {
        if (w == null) return;
        boolean night = Appearance.isNight(c);
        int fill = night ? 0xFF1C1C1E : 0xFFF2F2F7;
        w.setDimAmount(night ? 0.6f : 0.35f);
        w.setBackgroundDrawable(new InsetDrawable(new GlassDrawable(c, Ui.dpf(c, 28), fill, 0.6f), Ui.dp(c, 4)));
        w.setWindowAnimations(R.style.DialogAnim);
    }

    // ------------------------------------------------------------------ tilt light

    static int dim(int color, float k) {
        return Color.argb(Math.round(Color.alpha(color) * k), Color.red(color), Color.green(color), Color.blue(color));
    }
}
