package com.clock.app;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;

import java.util.ArrayList;
import java.util.List;

import eightbitlab.com.blurview.BlurView;

/**
 * The iOS 26 context menu: a long press lifts the pressed card and a glass panel springs out of it, blurring the
 * screen behind. Tap an action, tap outside or press back to close.
 */
final class GlassMenu {
    private GlassMenu() {
    }

    static final class Item {
        final CharSequence label;
        final int icon;
        final boolean destructive;
        final Runnable action;

        Item(CharSequence label, int icon, boolean destructive, Runnable action) {
            this.label = label;
            this.icon = icon;
            this.destructive = destructive;
            this.action = action;
        }
    }

    static final class Builder {
        private final List<Item> items = new ArrayList<>();

        Builder add(CharSequence label, int icon, Runnable r) {
            items.add(new Item(label, icon, false, r));
            return this;
        }

        Builder destructive(CharSequence label, int icon, Runnable r) {
            items.add(new Item(label, icon, true, r));
            return this;
        }

        void show(View anchor) {
            GlassMenu.show(anchor, items);
        }
    }

    static Builder builder() {
        return new Builder();
    }

    static void show(final View anchor, final List<Item> items) {
        final Activity act = Glass.activity(anchor.getContext());
        final FrameLayout ov = act instanceof BaseActivity ? ((BaseActivity) act).glassOverlay() : null;
        if (ov == null || items.isEmpty() || !anchor.isAttachedToWindow()) {
            fallback(anchor, items);
            return;
        }
        final BaseActivity b = (BaseActivity) act;
        final boolean night = Appearance.isNight(act);

        final FrameLayout layer = new FrameLayout(act);
        final View scrim = new View(act);
        scrim.setBackgroundColor(night ? 0x99000000 : 0x40000000);
        scrim.setAlpha(0f);
        layer.addView(scrim, new FrameLayout.LayoutParams(-1, -1));

        final BlurView menu = Glass.panel(act, 24, night ? 0x992C2C2E : 0xB3FFFFFF);
        try {
            menu.setBlurRadius(20f);
        } catch (Throwable ignored) {
        }
        menu.setElevation(Ui.dpf(act, 12));
        LinearLayout col = new LinearLayout(act);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, Ui.dp(act, 6), 0, Ui.dp(act, 6));
        menu.addView(col, new FrameLayout.LayoutParams(-1, -2));
        layer.addView(menu, new FrameLayout.LayoutParams(-2, -2));
        ov.addView(layer, new FrameLayout.LayoutParams(-1, -1));

        final boolean[] closing = {false};
        final OnBackPressedCallback[] back = new OnBackPressedCallback[1];
        final Runnable[] closer = new Runnable[1];

        for (int i = 0; i < items.size(); i++) {
            final Item it = items.get(i);
            LinearLayout row = new LinearLayout(act);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(Ui.dp(act, 48));
            row.setPaddingRelative(Ui.dp(act, 18), 0, Ui.dp(act, 16), 0);
            int colorRes = it.destructive ? R.color.bad : R.color.text_primary;
            TextView t = Ui.text(act, it.label, 17, colorRes);
            t.setSingleLine(true);
            t.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
            row.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
            if (it.icon != 0) {
                ImageView iv = new ImageView(act);
                iv.setImageResource(it.icon);
                iv.setImageTintList(ColorStateList.valueOf(Ui.color(act, colorRes)));
                LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(Ui.dp(act, 22), Ui.dp(act, 22));
                ip.setMarginStart(Ui.dp(act, 14));
                row.addView(iv, ip);
            }
            Ui.ripple(act, row);
            row.setOnClickListener(v -> {
                Ui.tick(v);
                if (closer[0] != null) closer[0].run();
                // the action runs once the menu has started to leave, like iOS
                menu.postDelayed(it.action == null ? () -> { } : it.action, 120);
            });
            if (i > 0) col.addView(Ui.hairline(act, 0));
            col.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }

        // size and place the menu next to the pressed view
        int screenW = act.getResources().getDisplayMetrics().widthPixels;
        int screenH = act.getResources().getDisplayMetrics().heightPixels;
        int mw = Math.min(Ui.dp(act, 260), screenW - Ui.dp(act, 32));
        menu.measure(View.MeasureSpec.makeMeasureSpec(mw, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(screenH, View.MeasureSpec.AT_MOST));
        int mh = menu.getMeasuredHeight();
        int[] ao = new int[2], oo = new int[2];
        anchor.getLocationInWindow(ao);
        ov.getLocationInWindow(oo);
        Rect ar = new Rect(ao[0] - oo[0], ao[1] - oo[1], ao[0] - oo[0] + anchor.getWidth(), ao[1] - oo[1] + anchor.getHeight());
        int margin = Ui.dp(act, 14);
        int left = Math.max(margin, Math.min(screenW - margin - mw, ar.centerX() - mw / 2));
        int gap = Ui.dp(act, 10);
        int top = ar.bottom + gap;
        boolean above = false;
        int floor = ov.getHeight() > 0 ? ov.getHeight() - b.glassBottom() : screenH;
        if (top + mh > floor) {
            above = true;
            top = ar.top - gap - mh;
        }
        top = Math.max(b.glassInsetTop() + margin, Math.min(top, floor - mh));
        FrameLayout.LayoutParams mp = new FrameLayout.LayoutParams(mw, mh, Gravity.TOP | Gravity.LEFT);
        mp.leftMargin = left;
        mp.topMargin = top;
        menu.setLayoutParams(mp);
        menu.setPivotX(Math.max(0, Math.min(mw, ar.centerX() - left)));
        menu.setPivotY(above ? mh : 0f);
        menu.setAlpha(0f);
        menu.setScaleX(0.35f);
        menu.setScaleY(0.35f);

        anchor.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        final float lift = 1.035f;
        anchor.animate().cancel();
        anchor.animate().scaleX(lift).scaleY(lift).setDuration(Anim.D_MED).setInterpolator(Anim.SPRING).start();
        scrim.animate().alpha(1f).setDuration(Anim.D_MED).setInterpolator(Anim.OUT).start();
        menu.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(Anim.D_LONG).setInterpolator(Anim.SPRING).start();

        closer[0] = () -> {
            if (closing[0]) return;
            closing[0] = true;
            if (back[0] != null) back[0].remove();
            anchor.animate().cancel();
            anchor.animate().scaleX(1f).scaleY(1f).setDuration(Anim.D_MED).setInterpolator(Anim.SPRING).start();
            scrim.animate().alpha(0f).setDuration(Anim.D_SHORT).setInterpolator(Anim.IN).start();
            menu.animate().alpha(0f).scaleX(0.6f).scaleY(0.6f).setDuration(Anim.D_SHORT).setInterpolator(Anim.IN)
                    .withEndAction(() -> ov.removeView(layer)).start();
        };
        scrim.setOnClickListener(v -> closer[0].run());
        back[0] = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                closer[0].run();
            }
        };
        b.getOnBackPressedDispatcher().addCallback(b, back[0]);
    }

    private static void fallback(View anchor, List<Item> items) {
        String[] labels = new String[items.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = String.valueOf(items.get(i).label);
        Dlg.choose(anchor.getContext(), R.string.alarm, labels, -1, i -> {
            Runnable r = items.get(i).action;
            if (r != null) r.run();
        });
    }
}
