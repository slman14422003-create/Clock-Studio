package com.clock.app;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

public class WidgetConfigActivity extends BaseActivity {
    private int id;
    private WBase w;
    private WCfg cfg;
    private LinearLayout col;
    private Preview preview;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        useGroupedBackground();
        id = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        setResult(RESULT_CANCELED, new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id));
        try {
            String cn = AppWidgetManager.getInstance(this).getAppWidgetInfo(id).provider.getClassName();
            w = (WBase) Class.forName(cn).getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            finish();
            return;
        }
        cfg = WCfg.load(this, id, w.defaultBg());

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        Header hd = new Header(this, getString(R.string.widget_cfg_title));
        hd.addView(Ui.iconButton(this, R.drawable.ic_back, R.string.back, false, v -> finish()), 0,
                new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40)));
        ((LinearLayout.LayoutParams) hd.getChildAt(0).getLayoutParams()).setMarginEnd(Ui.dp(this, 10));
        root.addView(hd);

        preview = new Preview(this);
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(-1, Ui.dp(this, 170));
        pl.setMargins(Ui.dp(this, 16), Ui.dp(this, 4), Ui.dp(this, 16), Ui.dp(this, 6));
        root.addView(preview, pl);

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, 0, 0, Ui.dp(this, 30));
        sv.addView(col, new ScrollView.LayoutParams(-1, -2));
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
        render();
    }

    private LinearLayout card(int title) {
        col.addView(Ui.sectionTitle(this, getString(title)));
        LinearLayout g = Ui.group(this);
        g.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));
        col.addView(g);
        return g;
    }

    private void changed() {
        preview.invalidate();
        render();
    }

    private void swatches(LinearLayout g, int count, int selected, boolean autoFirst, boolean grads, SwatchPick pick) {
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        for (int i = 0; i < count; i++) {
            final int idx = i;
            int c1, c2;
            boolean auto = autoFirst && i == 0;
            if (grads) {
                c1 = WCfg.BG[i][0];
                c2 = WCfg.BG[i][1];
                auto = i == 0;
            } else {
                c1 = WCfg.PALETTE[i];
                c2 = c1;
            }
            Swatch s = new Swatch(this, c1, c2, auto, i == selected);
            s.setOnClickListener(v -> {
                Ui.tick(v);
                pick.pick(idx);
                changed();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40));
            lp.setMarginEnd(Ui.dp(this, 10));
            row.addView(s, lp);
        }
        hs.addView(row);
        g.addView(hs);
    }

    private void chips(LinearLayout g, int[] labels, int selected, SwatchPick pick) {
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            TextView t = Ui.chipView(this, getString(labels[i]), v -> {
                pick.pick(idx);
                changed();
            });
            if (i == selected) {
                t.setBackground(Ui.rect(Ui.color(this, R.color.accent), Ui.dpf(this, 100)));
                t.setTextColor(0xFFFFFFFF);
            }
            row.addView(t);
        }
        hs.addView(row);
        g.addView(hs);
    }

    private void render() {
        col.removeAllViews();
        int caps = w.caps();

        LinearLayout g = card(R.string.cfg_bg);
        swatches(g, WCfg.BG.length, cfg.bg, true, true, i -> cfg.bg = i);

        g = card(R.string.cfg_opacity);
        final TextView pct = Ui.text(this, cfg.alpha + "%", 15, R.color.text_secondary);
        SeekBar sb = new SeekBar(this);
        sb.setMax(95);
        sb.setProgress(Math.max(0, cfg.alpha - 5));
        sb.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.color(this, R.color.accent)));
        sb.setThumbTintList(android.content.res.ColorStateList.valueOf(Ui.color(this, R.color.accent)));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean user) {
                cfg.alpha = p + 5;
                pct.setText(cfg.alpha + "%");
                preview.invalidate();
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        LinearLayout line = new LinearLayout(this);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.addView(sb, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(Ui.dp(this, 52), -2);
        pct.setGravity(Gravity.CENTER);
        line.addView(pct, pp);
        g.addView(line);

        g = card(R.string.cfg_radius);
        chips(g, new int[]{R.string.cfg_r0, R.string.cfg_r1, R.string.cfg_r2, R.string.cfg_r3}, cfg.radius, i -> cfg.radius = i);

        if ((caps & WBase.TEXT) != 0) {
            g = card(R.string.cfg_text);
            swatches(g, WCfg.PALETTE.length, cfg.text, true, false, i -> cfg.text = i);
        }
        if ((caps & WBase.ACCENT) != 0) {
            g = card(R.string.cfg_accent);
            swatches(g, WCfg.PALETTE.length, cfg.accent, true, false, i -> cfg.accent = i);
        }
        if ((caps & WBase.SIZE) != 0) {
            g = card(R.string.cfg_size);
            chips(g, new int[]{R.string.cfg_s0, R.string.cfg_s1, R.string.cfg_s2}, cfg.size, i -> cfg.size = i);
        }
        if ((caps & WBase.FMT) != 0) {
            g = card(R.string.cfg_fmt);
            chips(g, new int[]{R.string.cfg_f0, R.string.cfg_f1, R.string.cfg_f2}, cfg.fmt, i -> cfg.fmt = i);
        }
        if ((caps & WBase.FACE) != 0) {
            g = card(R.string.cfg_face);
            chips(g, new int[]{R.string.cfg_fc0, R.string.cfg_fc1, R.string.cfg_fc2}, cfg.face, i -> cfg.face = i);
        }
        if ((caps & WBase.DATE) != 0) {
            col.addView(Ui.sectionTitle(this, ""));
            LinearLayout sg = Ui.group(this);
            sg.addView(Ui.switchRow(this, 0, 0, getString(R.string.cfg_date), null, cfg.date, (v, on) -> {
                cfg.date = on;
                preview.invalidate();
            }));
            col.addView(sg);
        }

        LinearLayout actions = new LinearLayout(this);
        actions.setPadding(Ui.dp(this, 16), Ui.dp(this, 22), Ui.dp(this, 16), 0);
        TextView reset = button(R.string.cfg_reset, R.drawable.btn_secondary, R.color.text_primary);
        reset.setOnClickListener(v -> {
            cfg = new WCfg();
            cfg.bg = w.defaultBg();
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
        col.addView(actions);
    }

    private TextView button(int label, int bg, int color) {
        TextView t = Ui.text(this, getString(label), 16, color);
        t.setTypeface(Ui.medium());
        t.setGravity(Gravity.CENTER);
        t.setBackgroundResource(bg);
        Ui.press(this, t);
        return t;
    }

    private interface SwatchPick {
        void pick(int i);
    }

    private static final class Swatch extends View {
        private final int c1, c2;
        private final boolean auto, sel;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();

        Swatch(Context c, int c1, int c2, boolean auto, boolean sel) {
            super(c);
            this.c1 = c1;
            this.c2 = c2;
            this.auto = auto;
            this.sel = sel;
        }

        @Override
        protected void onDraw(Canvas cv) {
            float d = Ui.dpf(getContext(), 1);
            float w = getWidth(), h = getHeight();
            float cx = w / 2, cy = h / 2, rad = w / 2 - 5 * d;
            r.set(cx - rad, cy - rad, cx + rad, cy + rad);
            if (auto) {
                p.setShader(null);
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFFFFFFFF);
                cv.drawArc(r, 90, 180, true, p);
                p.setColor(0xFF1C1C1E);
                cv.drawArc(r, 270, 180, true, p);
            } else {
                p.setStyle(Paint.Style.FILL);
                p.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom, c1, c2, Shader.TileMode.CLAMP));
                cv.drawCircle(cx, cy, rad, p);
                p.setShader(null);
            }
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1f * d);
            p.setColor(0x55808080);
            cv.drawCircle(cx, cy, rad, p);
            if (sel) {
                p.setStrokeWidth(2.5f * d);
                p.setColor(Ui.color(getContext(), R.color.accent));
                cv.drawCircle(cx, cy, rad + 3.5f * d, p);
            }
        }
    }

    private final class Preview extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();

        Preview(Context c) {
            super(c);
        }

        @Override
        protected void onDraw(Canvas cv) {
            Context c = getContext();
            float d = Ui.dpf(c, 1);
            float W = getWidth(), H = getHeight();
            p.setStyle(Paint.Style.FILL);
            p.setShader(new LinearGradient(0, 0, W, H, 0xFF4F7CAC, 0xFFB08AD6, Shader.TileMode.CLAMP));
            r.set(0, 0, W, H);
            cv.drawRoundRect(r, 18 * d, 18 * d, p);
            p.setShader(null);

            float ww = Math.min(W - 40 * d, 300 * d), hh = 110 * d;
            r.set((W - ww) / 2, (H - hh) / 2, (W + ww) / 2, (H + hh) / 2);
            float rad = WCfg.RADII[cfg.radius] * d;
            int a = cfg.alpha * 255 / 100;
            if (cfg.adaptive()) {
                p.setColor(Ui.color(c, R.color.surface));
            } else if (cfg.bg <= WCfg.GRAD_LAST) {
                p.setShader(new LinearGradient(r.left, r.bottom, r.right, r.top, WCfg.BG[cfg.bg][0], WCfg.BG[cfg.bg][1], Shader.TileMode.CLAMP));
            } else {
                p.setColor(cfg.bgColor());
            }
            p.setAlpha(a);
            cv.drawRoundRect(r, rad, rad, p);
            p.setShader(null);

            int main = cfg.text != 0 ? WCfg.PALETTE[cfg.text] : cfg.autoText() != 0 ? cfg.autoText() : Ui.color(c, R.color.text_primary);
            int acd = w.defaultAccent(cfg);
            int ac = cfg.accent != 0 ? WCfg.PALETTE[cfg.accent] : acd != 0 ? acd : Ui.color(c, R.color.accent);
            float cx = r.centerX(), cy = r.centerY();

            if ((w.caps() & WBase.FACE) != 0) {
                float rr = hh / 2 - 12 * d;
                int fc = cfg.face == 1 ? Color.WHITE : cfg.face == 2 ? Color.BLACK : main;
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeCap(Paint.Cap.ROUND);
                p.setColor(fc);
                p.setStrokeWidth(2.5f * d);
                for (int i = 0; i < 12; i++) {
                    double an = Math.toRadians(i * 30);
                    float r1 = rr * (i % 3 == 0 ? 0.78f : 0.88f);
                    cv.drawLine(cx + r1 * (float) Math.sin(an), cy - r1 * (float) Math.cos(an),
                            cx + rr * (float) Math.sin(an), cy - rr * (float) Math.cos(an), p);
                }
                p.setStrokeWidth(4 * d);
                cv.drawLine(cx, cy, cx + rr * 0.45f, cy - rr * 0.35f, p);
                p.setStrokeWidth(2.6f * d);
                cv.drawLine(cx, cy, cx - rr * 0.1f, cy - rr * 0.8f, p);
                p.setStyle(Paint.Style.FILL);
                p.setColor(ac);
                cv.drawCircle(cx, cy, 3.5f * d, p);
                return;
            }
            p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.CENTER);
            float k = cfg.scale();
            p.setColor(main);
            p.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
            p.setTextSize(46 * d * k);
            String time = cfg.fmt == 2 || (cfg.fmt == 0 && Fmt.is24(c)) ? "10:45" : "10:45";
            cv.drawText(time, cx, cy + 4 * d, p);
            if (cfg.date || (w.caps() & WBase.DATE) == 0) {
                p.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
                p.setTextSize(14 * d * k);
                p.setColor(ac);
                cv.drawText(Fmt.date(c, System.currentTimeMillis(), "EEEE d MMMM"), cx, cy + 30 * d, p);
            }
            if ((w.caps() & WBase.FMT) != 0 && !(cfg.fmt == 2 || (cfg.fmt == 0 && Fmt.is24(c)))) {
                p.setTextSize(13 * d * k);
                p.setColor(ac);
                cv.drawText(getString(R.string.pm), cx + 62 * d * k, cy - 14 * d, p);
            }
        }
    }
}
