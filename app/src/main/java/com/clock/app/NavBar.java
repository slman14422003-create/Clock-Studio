package com.clock.app;

import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import eightbitlab.com.blurview.BlurView;

/**
 * iOS 26 floating tab bar: a blurred glass capsule over the content with five tabs. A liquid lens sits behind the
 * selected tab; it stretches and springs to the next one, and can be dragged across the bar like on the iPhone.
 */
final class NavBar {
    static final int CLOCK = 0, ALARMS = 1, TIMERS = 2, TASKS = 3, SETTINGS = 4;
    private static final int COUNT = 5;
    private static final long DURATION = Anim.D_MED;
    private static final ArgbEvaluator ARGB = new ArgbEvaluator();

    interface Callback {
        void onTab(int index);
    }

    interface Reselect {
        void onReselect(int index);
    }

    private final BaseActivity a;
    private final Callback cb;
    private final Bar bar;
    private final FrameLayout stack;
    private final LinearLayout row;
    private final View lens;
    private final LinearLayout[] items = new LinearLayout[COUNT];
    private final ImageView[] icons = new ImageView[COUNT];
    private final TextView[] names = new TextView[COUNT];
    private int selected = -1;
    private int visual = -1;
    private ValueAnimator anim;
    private Reselect reselect;
    private boolean dragging;

    View view() {
        return bar;
    }

    int height() {
        return bar.getHeight();
    }

    void setOnReselect(Reselect r) {
        reselect = r;
    }

    private final class Bar extends BlurView {
        private float downX;
        private final int slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();

        Bar(Context c) {
            super(c);
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getX();
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (!dragging && Math.abs(e.getX() - downX) > slop) {
                        // Explicit qualifier to call NavBar's own startDrag, not View.startDrag(ClipData, ...)
                        NavBar.this.startDrag(e.getX());
                        return true;
                    }
                    break;
                default:
                    break;
            }
            return dragging;
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (!dragging) return false;
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_MOVE:
                    NavBar.this.drag(e.getX());
                    return true;
                case MotionEvent.ACTION_UP:
                    NavBar.this.endDrag(e.getX(), true);
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    NavBar.this.endDrag(e.getX(), false);
                    return true;
                default:
                    return true;
            }
        }
    }

    private NavBar(BaseActivity a, Callback cb) {
        this.a = a;
        this.cb = cb;
        bar = new Bar(a);
        bar.setBackground(new GlassDrawable(a, Ui.dpf(a, 100), Glass.panelFill(a), 1f));
        bar.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
        bar.setClipToOutline(true);
        bar.setElevation(0f);
        bar.setLayoutDirection(View.LAYOUT_DIRECTION_LOCALE);
        bar.setPadding(Ui.dp(a, 5), Ui.dp(a, 5), Ui.dp(a, 5), Ui.dp(a, 5));
        Glass.setup(a, bar);
        stack = new FrameLayout(a);
        bar.addView(stack, new FrameLayout.LayoutParams(-1, -2));

        lens = new View(a);
        int lensFill = Appearance.isNight(a) ? 0x33FFFFFF : 0x1F000000;
        lens.setBackground(new GlassDrawable(a, Ui.dpf(a, 100), lensFill, 0f));
        lens.setVisibility(View.INVISIBLE);
        stack.addView(lens, new FrameLayout.LayoutParams(Ui.dp(a, 60), Ui.dp(a, 54), Gravity.TOP | Gravity.LEFT));

        row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_LOCALE);
        stack.addView(row, new FrameLayout.LayoutParams(-1, -2));
    }

    static NavBar attach(BaseActivity a, int selected, Callback cb) {
        NavBar n = new NavBar(a, cb);
        FrameLayout ov = a.glassOverlay();
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        lp.setMargins(Ui.dp(a, 18), 0, Ui.dp(a, 18), Ui.dp(a, 10));
        ov.addView(n.bar, lp);
        n.build();
        n.bar.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> n.placeLens(n.selected, false));
        a.setTiltView(n.bar);
        n.apply(selected, false);
        return n;
    }

    /** Lifts the bar above the system navigation bar (Android 15+ draws under it). */
    void setBottomInset(int px) {
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) bar.getLayoutParams();
        int want = Ui.dp(a, 10) + px;
        if (lp.bottomMargin != want) {
            lp.bottomMargin = want;
            bar.setLayoutParams(lp);
        }
    }

    private void build() {
        int[] iconRes = {R.drawable.ic_clock, R.drawable.ic_alarm, R.drawable.ic_timer, R.drawable.ic_check_circle, R.drawable.ic_settings};
        int[] labelRes = {R.string.nav_clock, R.string.nav_alarms, R.string.nav_timers, R.string.nav_tasks, R.string.nav_settings};
        for (int i = 0; i < COUNT; i++) {
            final int idx = i;
            LinearLayout item = new LinearLayout(a);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setContentDescription(a.getString(labelRes[i]));

            ImageView icon = new ImageView(a);
            icon.setImageResource(iconRes[i]);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            item.addView(icon, new LinearLayout.LayoutParams(Ui.dp(a, 24), Ui.dp(a, 24)));

            TextView t = new TextView(a);
            t.setText(labelRes[i]);
            t.setTextSize(10.5f);
            t.setSingleLine(true);
            t.setGravity(Gravity.CENTER);
            t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            t.setTextColor(Ui.color(a, R.color.text_secondary));
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tp.topMargin = Ui.dp(a, 2);
            item.addView(t, tp);

            item.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                if (idx != selected) cb.onTab(idx);
                else if (reselect != null) reselect.onReselect(idx);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(a, 54), 1f);
            row.addView(item, lp);
            items[i] = item;
            icons[i] = icon;
            names[i] = t;
        }
    }

    void render(int sel) {
        apply(sel, selected >= 0);
    }

    // ------------------------------------------------------------------ selection

    private void apply(final int sel, boolean animate) {
        if (sel == selected) return;
        final int from = visual >= 0 ? visual : selected;
        selected = sel;
        for (int i = 0; i < COUNT; i++) items[i].setSelected(i == sel);
        animate = animate && bar.isLaidOut();
        colorTo(from, sel, animate);
        placeLens(sel, animate);
        if (animate) Anim.bounce(icons[sel]);
    }

    private void colorTo(final int from, final int to, boolean animate) {
        if (anim != null) anim.cancel();
        visual = to;
        if (!animate || from < 0 || from == to) {
            paint(from, to, 1f);
            return;
        }
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(DURATION);
        anim.setInterpolator(Anim.OUT);
        anim.addUpdateListener(v -> paint(from, to, (float) v.getAnimatedValue()));
        anim.start();
    }

    private void paint(int from, int to, float t) {
        int on = Ui.color(a, R.color.accent);
        int off = Ui.color(a, R.color.text_secondary);
        for (int i = 0; i < COUNT; i++) {
            float k = i == to ? t : i == from ? 1f - t : 0f;
            int col = (Integer) ARGB.evaluate(k, off, on);
            icons[i].setImageTintList(ColorStateList.valueOf(col));
            names[i].setTextColor(col);
        }
    }

    // ------------------------------------------------------------------ the liquid lens

    private int slotLeft(int i) {
        return row.getLeft() + items[i].getLeft();
    }

    private void placeLens(int i, boolean animate) {
        if (i < 0 || dragging || items[i].getWidth() == 0) return;
        ViewGroup.LayoutParams lp = lens.getLayoutParams();
        int w = items[i].getWidth();
        if (lp.width != w || lp.height != items[i].getHeight()) {
            lp.width = w;
            lp.height = items[i].getHeight();
            lens.setLayoutParams(lp);
        }
        float x = slotLeft(i);
        lens.animate().cancel();
        if (lens.getVisibility() != View.VISIBLE) {
            lens.setTranslationX(x);
            lens.setVisibility(View.VISIBLE);
            return;
        }
        if (!animate) {
            lens.setTranslationX(x);
            lens.setScaleX(1f);
            lens.setScaleY(1f);
            return;
        }
        lens.animate().translationX(x).setDuration(Anim.D_LONG).setInterpolator(Anim.SPRING).start();
        ObjectAnimator.ofPropertyValuesHolder(lens,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1.16f, 1f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.1f, 1f))
                .setDuration(Anim.D_LONG).start();
    }

    private int nearest(float x) {
        int best = 0;
        float bd = Float.MAX_VALUE;
        for (int i = 0; i < COUNT; i++) {
            float c = slotLeft(i) + items[i].getWidth() / 2f;
            float d = Math.abs(c - x);
            if (d < bd) {
                bd = d;
                best = i;
            }
        }
        return best;
    }

    private void startDrag(float x) {
        dragging = true;
        bar.getParent().requestDisallowInterceptTouchEvent(true);
        lens.animate().cancel();
        lens.animate().scaleX(1.18f).scaleY(1.14f).setDuration(Anim.D_MED).setInterpolator(Anim.SPRING).start();
        bar.animate().scaleX(1.03f).scaleY(1.03f).setDuration(Anim.D_MED).setInterpolator(Anim.SPRING).start();
        bar.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        drag(x);
    }

    private void drag(float x) {
        float w = lens.getLayoutParams().width;
        float min = slotLeft(0), max = slotLeft(0);
        for (int i = 1; i < COUNT; i++) {
            min = Math.min(min, slotLeft(i));
            max = Math.max(max, slotLeft(i));
        }
        float tx = Math.max(min, Math.min(max, x - w / 2f - stack.getLeft()));
        lens.setTranslationX(tx);
        int n = nearest(tx + w / 2f);
        if (n != visual) {
            Ui.tick(bar);
            colorTo(visual, n, true);
        }
    }

    private void endDrag(float x, boolean commit) {
        dragging = false;
        bar.animate().scaleX(1f).scaleY(1f).setDuration(Anim.D_MED).setInterpolator(Anim.SPRING).start();
        float w = lens.getLayoutParams().width;
        int n = commit ? nearest(lens.getTranslationX() + w / 2f) : selected;
        if (n != selected) cb.onTab(n);
        // opening a screen (Settings) leaves the selection where it was: lens and colours go back
        if (n != selected) {
            colorTo(visual, selected, true);
        }
        placeLens(selected, true);
    }
}