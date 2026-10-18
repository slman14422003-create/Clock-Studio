package com.clock.app;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.RemoteViews;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.widget.SwitchCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * Widget settings. The preview is the real widget (the same RemoteViews the home screen gets), so what you see is
 * exactly what you save. Controls update in place, so nothing jumps while you drag a slider.
 */
public class WidgetConfigActivity extends BaseActivity {
    private interface IntGet {
        int get();
    }

    private interface IntSet {
        void set(int v);
    }

    private int id;
    private WBase w;
    private WCfg cfg;
    private FrameLayout stage, holder;
    private LinearLayout col;
    private int backdrop;
    private int wDp = 150, hDp = 150;
    private final List<Runnable> syncers = new ArrayList<>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        useGroupedBackground();
        id = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        setResult(RESULT_CANCELED, new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id));
        AppWidgetProviderInfo info = null;
        try {
            AppWidgetManager am = AppWidgetManager.getInstance(this);
            info = am.getAppWidgetInfo(id);
            if (info == null) {
                for (Class<?> k : WBase.ALL) {
                    int[] ids = am.getAppWidgetIds(new ComponentName(this, k));
                    if (ids != null) for (int x : ids) if (x == id) w = (WBase) k.getDeclaredConstructor().newInstance();
                }
            } else {
                w = (WBase) Class.forName(info.provider.getClassName()).getDeclaredConstructor().newInstance();
            }
        } catch (Throwable e) {
            w = null;
        }
        if (w == null) {
            finish();
            return;
        }
        cfg = WCfg.load(this, id, w.defaultBg());
        measureStage(info);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        Header hd = new Header(this, getString(R.string.widget_cfg_title));
        hd.addView(Ui.iconButton(this, R.drawable.ic_back, R.string.back, false, v -> finish()), 0,
                new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40)));
        ((LinearLayout.LayoutParams) hd.getChildAt(0).getLayoutParams()).setMarginEnd(Ui.dp(this, 10));
        try {
            if (info != null) hd.setSubtitle(info.loadLabel(getPackageManager()));
        } catch (Throwable ignored) {
        }
        root.addView(hd);

        buildStage();
        root.addView(stage);

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, 0, 0, Ui.dp(this, 16));
        sv.addView(col, new ScrollView.LayoutParams(-1, -2));
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        buildControls();
        root.addView(actionBar());
        setContentView(root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            androidx.core.graphics.Insets s = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(s.left, s.top, s.right, s.bottom);
            return insets;
        });
        refreshPreview();
    }

    // ------------------------------------------------------------------ preview

    private void measureStage(AppWidgetProviderInfo info) {
        int cw = 0, ch = 0;
        try {
            if (info != null && android.os.Build.VERSION.SDK_INT >= 31) {
                cw = info.targetCellWidth;
                ch = info.targetCellHeight;
            }
            if (cw <= 0 && info != null) {
                float d = getResources().getDisplayMetrics().density;
                cw = Math.max(1, Math.round((info.minWidth / d + 30) / 70f));
                ch = Math.max(1, Math.round((info.minHeight / d + 30) / 70f));
            }
        } catch (Throwable ignored) {
        }
        if (cw <= 0) cw = 2;
        if (ch <= 0) ch = 2;
        wDp = cw * 74 - 8;
        hDp = ch * 76 - 8;
        float screen = getResources().getDisplayMetrics().widthPixels / getResources().getDisplayMetrics().density - 72;
        if (wDp > screen) {
            hDp = Math.round(hDp * (screen / wDp));
            wDp = Math.round(screen);
        }
    }

    private void buildStage() {
        stage = new FrameLayout(this);
        holder = new FrameLayout(this);
        FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(Ui.dp(this, wDp), Ui.dp(this, hDp), Gravity.CENTER);
        stage.addView(holder, hp);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, Ui.dp(this, hDp + 44));
        sp.setMargins(Ui.dp(this, 16), Ui.dp(this, 2), Ui.dp(this, 16), Ui.dp(this, 4));
        stage.setLayoutParams(sp);
        stage.setClipToOutline(true);
        applyBackdrop();
        stage.setOnClickListener(v -> {
            Ui.tick(v);
            backdrop = (backdrop + 1) % 3;
            applyBackdrop();
        });
    }

    private void applyBackdrop() {
        int[][] c = {{0xFF4F7CAC, 0xFFB08AD6}, {0xFFEDEFF2, 0xFFC4CEDA}, {0xFF1B1B22, 0xFF050507}};
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, c[backdrop]);
        g.setCornerRadius(Ui.dpf(this, 22));
        stage.setBackground(g);
    }

    private void refreshPreview() {
        holder.removeAllViews();
        try {
            RemoteViews rv = w.build(getApplicationContext(), cfg);
            View v = rv.apply(this, holder);
            holder.addView(v, new FrameLayout.LayoutParams(-1, -1));
        } catch (Throwable e) {
            TextView t = Ui.text(this, getString(R.string.cfg_preview_failed), 14, R.color.text_secondary);
            t.setGravity(Gravity.CENTER);
            holder.addView(t, new FrameLayout.LayoutParams(-1, -1));
        }
    }

    private void changed() {
        refreshPreview();
        for (Runnable r : syncers) r.run();
    }

    // ------------------------------------------------------------------ controls

    private void buildControls() {
        int caps = w.caps();

        LinearLayout g = card(R.string.cfg_bg);
        caption(g, R.string.cfg_grad);
        swatchRow(g, 0, WCfg.GRAD_LAST, false, () -> cfg.bg, i -> cfg.bg = i, () -> cfg.customBg);
        caption(g, R.string.cfg_solid);
        swatchRow(g, WCfg.SOLID_FIRST, WCfg.CUSTOM, false, () -> cfg.bg, i -> cfg.bg = i, () -> cfg.customBg);
        Panel bgPanel = new Panel(() -> cfg.customBg, v -> cfg.customBg = v);
        g.addView(bgPanel.view);
        syncers.add(() -> bgPanel.show(cfg.bg == WCfg.CUSTOM));

        g = card(R.string.cfg_opacity);
        slider(g, 5, 100, () -> cfg.alpha, v -> cfg.alpha = v, "%");

        g = card(R.string.cfg_radius);
        chips(g, new int[]{R.string.cfg_r0, R.string.cfg_r1, R.string.cfg_r2, R.string.cfg_r3, R.string.cfg_r4, R.string.cfg_r5},
                () -> cfg.radius, i -> cfg.radius = i);
        LinearLayout bd = Ui.group(this);
        addSwitch(bd, R.string.cfg_border, () -> cfg.border, on -> cfg.border = on);
        col.addView(bd);

        if ((caps & WBase.TEXT) != 0) {
            g = card(R.string.cfg_text);
            swatchRow(g, 0, WCfg.PALETTE.length, true, () -> cfg.text, i -> cfg.text = i, () -> cfg.customText);
            Panel p = new Panel(() -> cfg.customText, v -> cfg.customText = v);
            g.addView(p.view);
            syncers.add(() -> p.show(cfg.text >= WCfg.PALETTE.length));
        }
        if ((caps & WBase.ACCENT) != 0) {
            g = card(R.string.cfg_accent);
            swatchRow(g, 0, WCfg.PALETTE.length, true, () -> cfg.accent, i -> cfg.accent = i, () -> cfg.customAccent);
            Panel p = new Panel(() -> cfg.customAccent, v -> cfg.customAccent = v);
            g.addView(p.view);
            syncers.add(() -> p.show(cfg.accent >= WCfg.PALETTE.length));
        }
        if ((caps & WBase.SIZE) != 0) {
            g = card(R.string.cfg_size);
            slider(g, 70, 150, () -> cfg.sizePct, v -> cfg.sizePct = v, "%");
        }
        if ((caps & WBase.FMT) != 0) {
            g = card(R.string.cfg_fmt);
            chips(g, new int[]{R.string.cfg_f0, R.string.cfg_f1, R.string.cfg_f2}, () -> cfg.fmt, i -> cfg.fmt = i);
        }
        if ((caps & WBase.FACE) != 0) {
            g = card(R.string.cfg_face);
            chips(g, new int[]{R.string.cfg_fc0, R.string.cfg_fc1, R.string.cfg_fc2}, () -> cfg.face, i -> cfg.face = i);
        }
        if ((caps & (WBase.DATE | WBase.HIJRI)) != 0) {
            col.addView(Ui.sectionTitle(this, getString(R.string.cfg_content)));
            LinearLayout sg = Ui.group(this);
            if ((caps & WBase.DATE) != 0) addSwitch(sg, R.string.cfg_date, () -> cfg.date, on -> cfg.date = on);
            if ((caps & WBase.HIJRI) != 0) addSwitch(sg, R.string.cfg_hijri, () -> cfg.hijri, on -> cfg.hijri = on);
            col.addView(sg);
        }
        col.addView(Ui.footnote(this, getString(R.string.cfg_preview_hint)));
        changed();
    }

    private LinearLayout card(int title) {
        col.addView(Ui.sectionTitle(this, getString(title)));
        LinearLayout g = Ui.group(this);
        g.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));
        col.addView(g);
        return g;
    }

    private void caption(LinearLayout g, int res) {
        TextView t = Ui.text(this, getString(res), 12, R.color.text_hint);
        t.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 6));
        g.addView(t);
    }

    private void swatchRow(LinearLayout g, int from, int to, boolean palette, IntGet sel, IntSet pick, IntGet customColor) {
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        final List<Swatch> items = new ArrayList<>();
        for (int i = from; i <= to; i++) {
            final int idx = i;
            Swatch s;
            if (palette) {
                boolean custom = i == WCfg.PALETTE.length;
                int c = custom ? 0 : WCfg.PALETTE[i];
                s = new Swatch(this, c, c, i == 0 ? Swatch.AUTO : custom ? Swatch.CUSTOM : Swatch.PLAIN);
            } else if (i == WCfg.CUSTOM) {
                s = new Swatch(this, 0, 0, Swatch.CUSTOM);
            } else if (i == 0) {
                s = new Swatch(this, 0, 0, Swatch.AUTO);
            } else {
                s = new Swatch(this, WCfg.BG[i][0], WCfg.BG[i][1], Swatch.PLAIN);
            }
            s.idx = idx;
            s.setOnClickListener(v -> {
                Ui.tick(v);
                pick.set(idx);
                changed();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 42), Ui.dp(this, 42));
            lp.setMarginEnd(Ui.dp(this, 8));
            row.addView(s, lp);
            items.add(s);
        }
        hs.addView(row);
        g.addView(hs);
        syncers.add(() -> {
            int cur = sel.get();
            for (Swatch s : items) {
                s.chosen = s.idx == cur;
                if (s.kind == Swatch.CUSTOM) s.custom = customColor.get();
                s.invalidate();
            }
        });
    }

    private void chips(LinearLayout g, int[] labels, IntGet sel, IntSet pick) {
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout row = new LinearLayout(this);
        final List<TextView> items = new ArrayList<>();
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            TextView t = Ui.chipView(this, getString(labels[i]), v -> {
                pick.set(idx);
                changed();
            });
            row.addView(t);
            items.add(t);
        }
        hs.addView(row);
        g.addView(hs);
        syncers.add(() -> {
            int cur = sel.get();
            for (int i = 0; i < items.size(); i++) {
                TextView t = items.get(i);
                boolean on = i == cur;
                t.setBackground(Ui.rect(on ? Ui.color(this, R.color.accent) : Ui.color(this, R.color.neutral_soft), Ui.dpf(this, 100)));
                t.setTextColor(on ? Color.WHITE : Ui.color(this, R.color.text_primary));
            }
        });
    }

    private void slider(LinearLayout g, int min, int max, IntGet get, IntSet set, String unit) {
        LinearLayout line = new LinearLayout(this);
        line.setGravity(Gravity.CENTER_VERTICAL);
        final TextView val = Ui.text(this, "", 15, R.color.text_secondary);
        val.setGravity(Gravity.CENTER);
        final SeekBar sb = new SeekBar(this);
        sb.setMax(max - min);
        sb.setProgressTintList(ColorStateList.valueOf(Ui.color(this, R.color.accent)));
        sb.setThumbTintList(ColorStateList.valueOf(Ui.color(this, R.color.accent)));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean user) {
                if (!user) return;
                set.set(p + min);
                val.setText((p + min) + unit);
                refreshPreview();
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        line.addView(sb, new LinearLayout.LayoutParams(0, -2, 1f));
        line.addView(val, new LinearLayout.LayoutParams(Ui.dp(this, 56), -2));
        g.addView(line);
        syncers.add(() -> {
            sb.setProgress(Math.max(0, Math.min(max - min, get.get() - min)));
            val.setText(get.get() + unit);
        });
    }

    private interface BoolGet {
        boolean get();
    }

    private interface BoolSet {
        void set(boolean v);
    }

    private void addSwitch(LinearLayout group, int title, BoolGet get, BoolSet set) {
        LinearLayout row = Ui.switchRow(this, 0, 0, getString(title), null, get.get(), (v, on) -> {
            if (get.get() == on) return;
            set.set(on);
            refreshPreview();
        });
        Ui.addRow(group, row, false);
        final SwitchCompat sw = row.findViewById(R.id.row_switch);
        syncers.add(() -> {
            if (sw != null && sw.isChecked() != get.get()) sw.setChecked(get.get());
        });
    }

    private View actionBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setBackgroundColor(Ui.color(this, R.color.bg_group));
        bar.addView(Ui.hairline(this, 0));
        LinearLayout actions = new LinearLayout(this);
        actions.setPadding(Ui.dp(this, 16), Ui.dp(this, 10), Ui.dp(this, 16), Ui.dp(this, 10));
        TextView reset = button(R.string.cfg_reset, R.drawable.btn_secondary, R.color.text_primary);
        reset.setOnClickListener(v -> {
            int keepBg = w.defaultBg();
            cfg = new WCfg();
            cfg.bg = keepBg;
            changed();
        });
        TextView save = button(R.string.cfg_save, R.drawable.btn_primary, R.color.on_accent);
        save.setOnClickListener(v -> {
            cfg.save(this, id);
            WBase.refreshOne(this, id);
            WBase.scheduleTicks(this);
            setResult(RESULT_OK, new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id));
            finish();
        });
        LinearLayout.LayoutParams a = new LinearLayout.LayoutParams(0, Ui.dp(this, 50), 1f);
        a.setMarginEnd(Ui.dp(this, 10));
        actions.addView(reset, a);
        actions.addView(save, new LinearLayout.LayoutParams(0, Ui.dp(this, 50), 1.6f));
        bar.addView(actions);
        return bar;
    }

    private TextView button(int label, int bg, int color) {
        TextView t = Ui.text(this, getString(label), 16, color);
        t.setTypeface(Ui.medium());
        t.setGravity(Gravity.CENTER);
        t.setBackgroundResource(bg);
        Ui.press(this, t);
        return t;
    }

    // ------------------------------------------------------------------ colour picker

    /** Hue / saturation / brightness sliders for a custom colour; hidden until the custom swatch is chosen. */
    private final class Panel {
        final LinearLayout view;
        private final IntGet get;
        private final IntSet set;
        private final float[] hsv = new float[3];
        private final List<Bar> bars = new ArrayList<>();
        private final TextView hex;

        Panel(IntGet get, IntSet set) {
            this.get = get;
            this.set = set;
            view = new LinearLayout(WidgetConfigActivity.this);
            view.setOrientation(LinearLayout.VERTICAL);
            view.setVisibility(View.GONE);
            view.setPadding(0, Ui.dp(WidgetConfigActivity.this, 12), 0, 0);
            int[] labels = {R.string.cfg_hue, R.string.cfg_sat, R.string.cfg_val};
            for (int m = 0; m < 3; m++) {
                LinearLayout line = new LinearLayout(WidgetConfigActivity.this);
                line.setGravity(Gravity.CENTER_VERTICAL);
                TextView l = Ui.text(WidgetConfigActivity.this, getString(labels[m]), 13, R.color.text_secondary);
                line.addView(l, new LinearLayout.LayoutParams(Ui.dp(WidgetConfigActivity.this, 64), -2));
                Bar bar = new Bar(WidgetConfigActivity.this, m, hsv, this::pushColor);
                bars.add(bar);
                line.addView(bar, new LinearLayout.LayoutParams(0, Ui.dp(WidgetConfigActivity.this, 36), 1f));
                view.addView(line, new LinearLayout.LayoutParams(-1, -2));
            }
            hex = Ui.text(WidgetConfigActivity.this, "", 13, R.color.text_secondary);
            hex.setTypeface(android.graphics.Typeface.MONOSPACE);
            hex.setGravity(Gravity.CENTER);
            hex.setPadding(0, Ui.dp(WidgetConfigActivity.this, 6), 0, 0);
            view.addView(hex);
            load();
        }

        private void load() {
            Color.colorToHSV(get.get(), hsv);
            hex.setText(String.format("#%06X", get.get() & 0xFFFFFF));
            for (Bar b : bars) b.invalidate();
        }

        private void pushColor() {
            int c = Color.HSVToColor(hsv);
            set.set(c);
            hex.setText(String.format("#%06X", c & 0xFFFFFF));
            for (Bar b : bars) b.invalidate();
            refreshPreview();
        }

        void show(boolean on) {
            if (on && view.getVisibility() != View.VISIBLE) {
                load();
                view.setVisibility(View.VISIBLE);
                view.setAlpha(0f);
                view.animate().alpha(1f).setDuration(Anim.D_SHORT).start();
            } else if (!on) {
                view.setVisibility(View.GONE);
            }
        }
    }

    private static final class Bar extends View {
        private final int mode;
        private final float[] hsv;
        private final Runnable changed;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();

        Bar(Context c, int mode, float[] hsv, Runnable changed) {
            super(c);
            this.mode = mode;
            this.hsv = hsv;
            this.changed = changed;
            setLayerType(LAYER_TYPE_SOFTWARE, null);
        }

        private float value() {
            return mode == 0 ? hsv[0] / 360f : mode == 1 ? hsv[1] : hsv[2];
        }

        @Override
        protected void onDraw(Canvas cv) {
            float d = Ui.dpf(getContext(), 1);
            float h = 14 * d, cy = getHeight() / 2f, pad = 14 * d;
            r.set(pad, cy - h / 2, getWidth() - pad, cy + h / 2);
            int[] cols;
            if (mode == 0) {
                cols = new int[7];
                for (int i = 0; i < 7; i++) cols[i] = Color.HSVToColor(new float[]{i * 60f, 1f, 1f});
            } else if (mode == 1) {
                cols = new int[]{Color.HSVToColor(new float[]{hsv[0], 0f, hsv[2]}), Color.HSVToColor(new float[]{hsv[0], 1f, hsv[2]})};
            } else {
                cols = new int[]{0xFF000000, Color.HSVToColor(new float[]{hsv[0], hsv[1], 1f})};
            }
            p.setStyle(Paint.Style.FILL);
            p.setShader(new LinearGradient(r.left, 0, r.right, 0, cols, null, Shader.TileMode.CLAMP));
            cv.drawRoundRect(r, h / 2, h / 2, p);
            p.setShader(null);
            float x = r.left + value() * r.width();
            p.setColor(0xFFFFFFFF);
            p.setShadowLayer(3 * d, 0, 1 * d, 0x55000000);
            cv.drawCircle(x, cy, 12 * d, p);
            p.clearShadowLayer();
            p.setColor(Color.HSVToColor(hsv));
            cv.drawCircle(x, cy, 8 * d, p);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            getParent().requestDisallowInterceptTouchEvent(true);
            float d = Ui.dpf(getContext(), 1);
            float left = 14 * d, wid = Math.max(1f, getWidth() - 28 * d);
            float t = Math.max(0f, Math.min(1f, (e.getX() - left) / wid));
            if (mode == 0) hsv[0] = Math.min(359.9f, t * 360f);
            else if (mode == 1) hsv[1] = t;
            else hsv[2] = t;
            changed.run();
            if (e.getActionMasked() == MotionEvent.ACTION_UP) performClick();
            return true;
        }

        @Override
        public boolean performClick() {
            return super.performClick();
        }
    }

    // ------------------------------------------------------------------ swatch

    private static final class Swatch extends View {
        static final int PLAIN = 0, AUTO = 1, CUSTOM = 2;
        final int c1, c2, kind;
        int idx;
        boolean chosen;
        int custom;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();

        Swatch(Context c, int c1, int c2, int kind) {
            super(c);
            this.c1 = c1;
            this.c2 = c2;
            this.kind = kind;
        }

        @Override
        protected void onDraw(Canvas cv) {
            float d = Ui.dpf(getContext(), 1);
            float w = getWidth(), h = getHeight();
            float cx = w / 2, cy = h / 2, rad = w / 2 - 5 * d;
            r.set(cx - rad, cy - rad, cx + rad, cy + rad);
            p.setStyle(Paint.Style.FILL);
            if (kind == AUTO) {
                p.setShader(null);
                p.setColor(0xFFFFFFFF);
                cv.drawArc(r, 90, 180, true, p);
                p.setColor(0xFF1C1C1E);
                cv.drawArc(r, 270, 180, true, p);
            } else if (kind == CUSTOM) {
                p.setShader(new SweepGradient(cx, cy, new int[]{0xFFFF3B30, 0xFFFFCC00, 0xFF34C759, 0xFF32ADE6, 0xFF5E5CE6, 0xFFFF2D55, 0xFFFF3B30}, null));
                cv.drawCircle(cx, cy, rad, p);
                p.setShader(null);
                p.setColor(chosen ? custom : 0xFFFFFFFF);
                cv.drawCircle(cx, cy, rad * 0.42f, p);
            } else {
                p.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom, c1, c2, Shader.TileMode.CLAMP));
                cv.drawCircle(cx, cy, rad, p);
                p.setShader(null);
            }
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1f * d);
            p.setColor(0x55808080);
            cv.drawCircle(cx, cy, rad, p);
            if (chosen) {
                p.setStrokeWidth(2.5f * d);
                p.setColor(Ui.color(getContext(), R.color.accent));
                cv.drawCircle(cx, cy, rad + 3.5f * d, p);
            }
        }
    }
}
