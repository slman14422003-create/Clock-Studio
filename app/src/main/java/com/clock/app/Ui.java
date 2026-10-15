package com.clock.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;

/** Small builders for the iOS look shared by every screen (same language as the Gallery app). */
final class Ui {
    private Ui() {
    }

    interface IntCb {
        void on(int value);
    }

    static void press(Context c, View v) {
        if (v == null || v.getStateListAnimator() != null) return;
        v.setStateListAnimator(android.animation.AnimatorInflater.loadStateListAnimator(c, R.animator.press_scale));
    }

    static int dp(Context c, int v) {
        return (int) (v * c.getResources().getDisplayMetrics().density);
    }

    static float dpf(Context c, float v) {
        return v * c.getResources().getDisplayMetrics().density;
    }

    static int hairPx(Context c) {
        return Math.max(1, Math.round(c.getResources().getDisplayMetrics().density * 0.5f));
    }

    /** Colour by resource id; the theme attributes make day / night and the grouped backgrounds resolve correctly. */
    static int color(Context c, int res) {
        int attr = 0;
        if (res == R.color.accent) attr = R.attr.cAccent;
        else if (res == R.color.accent_text) attr = R.attr.cAccentText;
        else if (res == R.color.accent_soft) attr = R.attr.cAccentSoft;
        else if (res == R.color.bg) attr = R.attr.cBg;
        else if (res == R.color.bg_group) attr = R.attr.cGroupBg;
        else if (res == R.color.surface) attr = R.attr.cSurface;
        else if (res == R.color.surface_high) attr = R.attr.cSurfaceHigh;
        else if (res == R.color.field) attr = R.attr.cField;
        else if (res == R.color.stroke) attr = R.attr.cStroke;
        else if (res == R.color.stroke_soft) attr = R.attr.cStrokeSoft;
        else if (res == R.color.text_primary) attr = R.attr.cTextPrimary;
        else if (res == R.color.text_secondary) attr = R.attr.cTextSecondary;
        else if (res == R.color.text_hint) attr = R.attr.cTextHint;
        else if (res == R.color.neutral_soft) attr = R.attr.cNeutralSoft;
        if (attr != 0) {
            TypedValue tv = new TypedValue();
            if (c.getTheme().resolveAttribute(attr, tv, true)
                    && tv.type >= TypedValue.TYPE_FIRST_COLOR_INT && tv.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                return tv.data;
            }
        }
        return ContextCompat.getColor(c, res);
    }

    static int alpha(int color, int a) {
        return (color & 0x00FFFFFF) | (a << 24);
    }

    static GradientDrawable rect(int color, float radiusPx) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radiusPx);
        return g;
    }

    static GradientDrawable oval(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        return g;
    }

    static Typeface medium() {
        return Typeface.create("sans-serif-medium", Typeface.NORMAL);
    }

    static Typeface thin() {
        return Typeface.create("sans-serif-thin", Typeface.NORMAL);
    }

    static Typeface light() {
        return Typeface.create("sans-serif-light", Typeface.NORMAL);
    }

    static TextView text(Context c, CharSequence t, float sp, int colorRes) {
        TextView v = new TextView(c);
        v.setText(t);
        v.setTextSize(sp);
        v.setTextColor(color(c, colorRes));
        v.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        return v;
    }

    static TextView tabular(TextView v) {
        v.setFontFeatureSettings("tnum");
        v.setIncludeFontPadding(false);
        return v;
    }

    static View hairline(Context c, int insetStartDp) {
        View v = new View(c);
        v.setBackgroundColor(color(c, R.color.stroke));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, hairPx(c));
        lp.setMarginStart(dp(c, insetStartDp));
        v.setLayoutParams(lp);
        return v;
    }

    static void ripple(Context c, View v) {
        v.setForeground(new RippleDrawable(ColorStateList.valueOf(color(c, R.color.ripple)), null, new ColorDrawable(Color.WHITE)));
    }

    static ImageButton iconButton(Context c, int icon, int desc, boolean accent, View.OnClickListener click) {
        ImageButton b = new ImageButton(c);
        b.setImageResource(icon);
        b.setImageTintList(ColorStateList.valueOf(color(c, accent ? R.color.accent : R.color.text_primary)));
        b.setScaleType(ImageView.ScaleType.CENTER);
        b.setBackgroundResource(R.drawable.bg_icon_btn);
        b.setContentDescription(c.getString(desc));
        b.setOnClickListener(click);
        press(c, b);
        return b;
    }

    /** Blue text button of an iOS navigation bar. */
    static TextView navText(Context c, CharSequence text, boolean semibold) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(17);
        t.setTextColor(color(c, R.color.accent));
        t.setTypeface(semibold ? Typeface.create("sans-serif-medium", Typeface.BOLD) : Typeface.DEFAULT);
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(dp(c, 44));
        t.setSingleLine(true);
        t.setPadding(dp(c, 14), 0, dp(c, 14), 0);
        press(c, t);
        return t;
    }

    /** iOS sheet header: Cancel on one side, title in the middle, Done on the other. */
    static LinearLayout sheetBar(Context c, CharSequence title, CharSequence left, View.OnClickListener onLeft,
                                 CharSequence right, View.OnClickListener onRight) {
        LinearLayout bar = new LinearLayout(c);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(c, 2), dp(c, 6), dp(c, 2), dp(c, 2));
        TextView l = navText(c, left, false);
        l.setOnClickListener(onLeft);
        TextView t = text(c, title, 17, R.color.text_primary);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        TextView r = navText(c, right, true);
        r.setOnClickListener(onRight);
        bar.addView(l, new LinearLayout.LayoutParams(-2, -2));
        bar.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        bar.addView(r, new LinearLayout.LayoutParams(-2, -2));
        return bar;
    }

    // ------------------------------------------------------------------ segmented control

    static LinearLayout segmented(Context c, CharSequence[] labels, int selected, IntCb cb) {
        final LinearLayout track = new LinearLayout(c);
        track.setOrientation(LinearLayout.HORIZONTAL);
        track.setLayoutDirection(View.LAYOUT_DIRECTION_LOCALE);
        track.setBackground(rect(color(c, R.color.neutral_soft), dpf(c, 9)));
        track.setPadding(dp(c, 2), dp(c, 2), dp(c, 2), dp(c, 2));
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            TextView t = new TextView(c);
            t.setText(labels[i]);
            t.setTextSize(13);
            t.setSingleLine(true);
            t.setGravity(Gravity.CENTER);
            t.setTypeface(medium());
            t.setOnClickListener(v -> {
                tick(v);
                setSegment(track, idx);
                if (cb != null) cb.on(idx);
            });
            track.addView(t, new LinearLayout.LayoutParams(0, dp(c, 32), 1f));
        }
        setSegment(track, selected);
        return track;
    }

    static void setSegment(LinearLayout track, int sel) {
        Context c = track.getContext();
        int thumb = Appearance.isNight(c) ? 0xFF636366 : 0xFFFFFFFF;
        for (int i = 0; i < track.getChildCount(); i++) {
            TextView t = (TextView) track.getChildAt(i);
            boolean on = i == sel;
            t.setBackground(on ? rect(thumb, dpf(c, 7)) : null);
            t.setTextColor(color(c, on ? R.color.text_primary : R.color.text_secondary));
        }
        track.setTag(sel);
    }

    static int segment(LinearLayout track) {
        Object o = track.getTag();
        return o instanceof Integer ? (Integer) o : 0;
    }

    // ------------------------------------------------------------------ iOS round buttons (timer / stopwatch)

    static TextView circleButton(Context c, CharSequence label, int base) {
        TextView t = new TextView(c);
        t.setText(label);
        t.setGravity(Gravity.CENTER);
        t.setTextSize(17);
        t.setTypeface(medium());
        t.setSingleLine(true);
        t.setLayoutParams(new LinearLayout.LayoutParams(dp(c, 84), dp(c, 84)));
        press(c, t);
        setCircleColor(t, base);
        return t;
    }

    static void setCircleColor(TextView t, int base) {
        Context c = t.getContext();
        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setColor(0);
        ring.setStroke(dp(c, 2), alpha(base, 0x70));
        GradientDrawable fill = oval(alpha(base, 0x33));
        LayerDrawable ld = new LayerDrawable(new Drawable[]{ring, fill});
        ld.setLayerInset(1, dp(c, 5), dp(c, 5), dp(c, 5), dp(c, 5));
        t.setBackground(ld);
        t.setTextColor(base);
    }

    // ------------------------------------------------------------------ inset grouped rows

    static LinearLayout group(Context c) {
        LinearLayout g = new LinearLayout(c);
        g.setOrientation(LinearLayout.VERTICAL);
        g.setBackground(rect(color(c, R.color.surface), dpf(c, 12)));
        g.setClipToOutline(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(c, 16), 0, dp(c, 16), dp(c, 6));
        g.setLayoutParams(lp);
        return g;
    }

    static void addRow(LinearLayout group, View row, boolean hasIcon) {
        if (group.getChildCount() > 0) group.addView(hairline(group.getContext(), hasIcon ? 58 : 16));
        group.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    static ImageView tile(Context c, int icon, int colorRes) {
        ImageView iv = new ImageView(c);
        iv.setImageResource(icon);
        iv.setImageTintList(ColorStateList.valueOf(Color.WHITE));
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int p = dp(c, 6);
        iv.setPadding(p, p, p, p);
        iv.setBackground(rect(color(c, colorRes), dpf(c, 7)));
        return iv;
    }

    static LinearLayout row(Context c, int icon, int tileColor, CharSequence title, CharSequence value,
                            boolean chevron, View.OnClickListener click) {
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setMinimumHeight(dp(c, 52));
        r.setPaddingRelative(dp(c, 16), dp(c, 8), dp(c, 14), dp(c, 8));
        if (icon != 0) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(c, 30), dp(c, 30));
            lp.setMarginEnd(dp(c, 12));
            r.addView(tile(c, icon, tileColor), lp);
        }
        TextView t = text(c, title, 17, R.color.text_primary);
        t.setId(R.id.row_title);
        r.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (value != null) {
            TextView v = text(c, value, 17, R.color.text_secondary);
            v.setId(R.id.row_value);
            v.setSingleLine(true);
            v.setEllipsize(android.text.TextUtils.TruncateAt.END);
            v.setMaxWidth(dp(c, 170));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMarginStart(dp(c, 8));
            r.addView(v, lp);
        }
        if (chevron) {
            ImageView ch = new ImageView(c);
            ch.setImageResource(R.drawable.ic_chevron);
            ch.setImageTintList(ColorStateList.valueOf(color(c, R.color.text_hint)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(c, 18), dp(c, 18));
            lp.setMarginStart(dp(c, 6));
            r.addView(ch, lp);
        }
        if (click != null) {
            r.setOnClickListener(click);
            ripple(c, r);
        }
        return r;
    }

    static void setValue(View row, CharSequence value) {
        TextView v = row.findViewById(R.id.row_value);
        if (v != null) v.setText(value);
    }

    static SwitchCompat styledSwitch(Context c, boolean checked) {
        SwitchCompat s = new SwitchCompat(c);
        s.setBackground(null);
        s.setThumbDrawable(ContextCompat.getDrawable(c, R.drawable.switch_thumb));
        s.setTrackDrawable(ContextCompat.getDrawable(c, R.drawable.switch_track));
        s.setShowText(false);
        s.setSplitTrack(false);
        s.setChecked(checked);
        return s;
    }

    static LinearLayout switchRow(Context c, int icon, int tileColor, CharSequence title, CharSequence sub,
                                  boolean checked, CompoundButton.OnCheckedChangeListener l) {
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setMinimumHeight(dp(c, 52));
        r.setPaddingRelative(dp(c, 16), dp(c, 8), dp(c, 16), dp(c, 8));
        if (icon != 0) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(c, 30), dp(c, 30));
            lp.setMarginEnd(dp(c, 12));
            r.addView(tile(c, icon, tileColor), lp);
        }
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView t = text(c, title, 17, R.color.text_primary);
        t.setId(R.id.row_title);
        col.addView(t);
        if (sub != null) {
            TextView s = text(c, sub, 13, R.color.text_secondary);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(c, 2);
            col.addView(s, lp);
        }
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        clp.setMarginEnd(dp(c, 12));
        r.addView(col, clp);
        final SwitchCompat sw = styledSwitch(c, checked);
        sw.setId(R.id.row_switch);
        sw.setOnCheckedChangeListener((btn, on) -> {
            tick(btn);
            if (l != null) l.onCheckedChanged(btn, on);
        });
        r.addView(sw, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        r.setOnClickListener(v -> sw.toggle());
        ripple(c, r);
        return r;
    }

    static TextView sectionTitle(Context c, CharSequence text) {
        TextView t = text(c, text, 13, R.color.text_secondary);
        t.setPaddingRelative(dp(c, 32), dp(c, 22), dp(c, 32), dp(c, 7));
        return t;
    }

    static TextView footnote(Context c, CharSequence text) {
        TextView t = text(c, text, 13, R.color.text_secondary);
        t.setPaddingRelative(dp(c, 32), dp(c, 2), dp(c, 32), dp(c, 8));
        return t;
    }

    static LinearLayout empty(Context c, int icon, CharSequence title, CharSequence sub) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER);
        l.setPadding(dp(c, 40), 0, dp(c, 40), dp(c, 60));
        ImageView iv = new ImageView(c);
        iv.setImageResource(icon);
        iv.setImageTintList(ColorStateList.valueOf(color(c, R.color.text_hint)));
        l.addView(iv, new LinearLayout.LayoutParams(dp(c, 64), dp(c, 64)));
        TextView t = text(c, title, 20, R.color.text_primary);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        t.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tl.topMargin = dp(c, 14);
        l.addView(t, tl);
        TextView s = text(c, sub, 15, R.color.text_secondary);
        s.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sl.topMargin = dp(c, 6);
        l.addView(s, sl);
        return l;
    }

    /** Fades and slides a freshly shown item into place; index staggers neighbours. */
    static void enter(View v, int index) {
        if (v == null) return;
        v.animate().cancel();
        v.setAlpha(0f);
        v.setTranslationY(dp(v.getContext(), 22));
        v.animate().alpha(1f).translationY(0f)
                .setStartDelay(Math.min(Math.max(index, 0), 9) * 34L)
                .setDuration(480)
                .setInterpolator(Anim.OUT)
                .start();
    }

    /** Soft haptic tick for switches, segments and pick-ups (can be turned off in Settings). */
    static void tick(View v) {
        if (v == null || !Store.haptics(v.getContext())) return;
        v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
    }

    /** Opens a screen as an iOS bottom sheet. */
    static void openSheet(Activity a, Intent i) {
        a.startActivity(i);
        a.overridePendingTransition(R.anim.sheet_open_enter, R.anim.sheet_open_exit);
    }

    /** Opens a screen with the soft iOS zoom (full-screen viewers such as StandBy). */
    static void openZoom(Activity a, Intent i) {
        a.startActivity(i);
        a.overridePendingTransition(R.anim.viewer_open_enter, R.anim.hold);
    }

    static void toast(Context c, CharSequence t) {
        Toast.makeText(c, t, Toast.LENGTH_SHORT).show();
    }

    static GradientDrawable groupShape(Context c, boolean first, boolean last, int fillRes) {
        float big = dpf(c, 12);
        float top = first ? big : 0;
        float bottom = last ? big : 0;
        GradientDrawable g = new GradientDrawable();
        g.setColor(color(c, fillRes));
        g.setCornerRadii(new float[]{top, top, top, top, bottom, bottom, bottom, bottom});
        return g;
    }

    /** Grey capsule chip with a tap action (quick timer presets). */
    static TextView chipView(Context c, CharSequence text, View.OnClickListener click) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(14);
        t.setSingleLine(true);
        t.setTypeface(medium());
        t.setTextColor(color(c, R.color.text_primary));
        t.setBackground(rect(color(c, R.color.neutral_soft), dpf(c, 100)));
        t.setPadding(dp(c, 16), dp(c, 8), dp(c, 16), dp(c, 8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(dp(c, 8));
        t.setLayoutParams(lp);
        t.setOnClickListener(click);
        press(c, t);
        return t;
    }
}
