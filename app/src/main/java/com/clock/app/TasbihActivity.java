package com.clock.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import org.json.JSONObject;

/** Digital misbaha: a string of beads slides under your finger, with the dhikr, a target and today's total. */
public class TasbihActivity extends BaseActivity {
    private static final String KEY = "tasbih";
    private static final int[] TARGETS = {33, 99, 100, 0};

    private int count, target = 33, phrase, total;
    private String day = "";
    private Beads beads;
    private Bar bar;
    private TextView phraseTv, countTv, subTv, totalText;
    private LinearLayout phraseRow, targetRow;
    private String[] phrases;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        useGroupedBackground();
        phrases = getResources().getStringArray(R.array.tasbih_phrases);
        load();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        Header hd = new Header(this, getString(R.string.tasbih));
        hd.addView(Ui.iconButton(this, R.drawable.ic_back, R.string.back, false, v -> finish()), 0,
                new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40)));
        ((LinearLayout.LayoutParams) hd.getChildAt(0).getLayoutParams()).setMarginEnd(Ui.dp(this, 10));
        hd.addButton(R.drawable.ic_refresh, R.string.tasbih_reset, false, v -> {
            count = 0;
            save();
            beads.set(count, target);
            sync(false);
            Anim.pulse(countTv);
        });
        root.addView(hd);

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 30));
        sv.addView(col, new ScrollView.LayoutParams(-1, -2));
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        phraseRow = new LinearLayout(this);
        phraseRow.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 8), Ui.dp(this, 8));
        hs.addView(phraseRow);
        col.addView(hs, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setBackground(Ui.rect(Ui.color(this, R.color.surface), Ui.dpf(this, 28)));
        card.setPadding(Ui.dp(this, 18), Ui.dp(this, 22), Ui.dp(this, 18), Ui.dp(this, 10));
        phraseTv = Ui.text(this, "", 26, R.color.text_primary);
        phraseTv.setTypeface(Ui.medium());
        phraseTv.setGravity(Gravity.CENTER);
        card.addView(phraseTv, new LinearLayout.LayoutParams(-1, -2));
        countTv = Ui.tabular(Ui.text(this, "0", 76, R.color.text_primary));
        countTv.setTypeface(Ui.light());
        countTv.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-2, -2);
        cl.topMargin = Ui.dp(this, 6);
        card.addView(countTv, cl);
        subTv = Ui.tabular(Ui.text(this, "", 15, R.color.text_secondary));
        subTv.setGravity(Gravity.CENTER);
        card.addView(subTv);
        bar = new Bar(this);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, Ui.dp(this, 6));
        bl.setMargins(Ui.dp(this, 24), Ui.dp(this, 14), Ui.dp(this, 24), Ui.dp(this, 6));
        card.addView(bar, bl);
        beads = new Beads(this);
        card.addView(beads, new LinearLayout.LayoutParams(-1, Ui.dp(this, 190)));
        TextView hint = Ui.text(this, getString(R.string.tasbih_hint), 13, R.color.text_hint);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, 0, 0, Ui.dp(this, 6));
        card.addView(hint, new LinearLayout.LayoutParams(-1, -2));
        card.setOnClickListener(v -> tap());
        Ui.press(this, card);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, -2);
        cardLp.setMargins(Ui.dp(this, 16), Ui.dp(this, 4), Ui.dp(this, 16), Ui.dp(this, 14));
        col.addView(card, cardLp);

        targetRow = new LinearLayout(this);
        targetRow.setGravity(Gravity.CENTER);
        col.addView(targetRow, new LinearLayout.LayoutParams(-1, -2));

        totalText = Ui.text(this, "", 15, R.color.text_secondary);
        totalText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-1, -2);
        tl.topMargin = Ui.dp(this, 16);
        col.addView(totalText, tl);

        beads.set(count, target);
        sync(false);
        Anim.riseIn(card, 24, 0);
    }

    private String today() {
        return Fmt.date(this, System.currentTimeMillis(), "yyyyMMdd");
    }

    private void load() {
        try {
            String raw = Store.raw(this, KEY);
            if (raw != null && !raw.isEmpty()) {
                JSONObject o = new JSONObject(raw);
                count = o.optInt("c", 0);
                target = o.optInt("t", 33);
                phrase = Math.max(0, Math.min(phrases.length - 1, o.optInt("p", 0)));
                total = o.optInt("n", 0);
                day = o.optString("d", "");
            }
        } catch (Exception ignored) {
        }
        if (!today().equals(day)) {
            day = today();
            total = 0;
        }
    }

    private void save() {
        try {
            JSONObject o = new JSONObject();
            o.put("c", count).put("t", target).put("p", phrase).put("n", total).put("d", day);
            Store.putRaw(this, KEY, o.toString());
        } catch (Exception ignored) {
        }
    }

    private void tap() {
        if (!today().equals(day)) {
            day = today();
            total = 0;
        }
        count++;
        total++;
        boolean done = target > 0 && count % target == 0;
        if (done) countTv.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        else Ui.tick(countTv);
        save();
        beads.advance(count, done);
        sync(true);
        Anim.pulse(countTv);
        if (done) Anim.bounce(phraseTv);
    }

    private void sync(boolean animateBar) {
        phraseTv.setText(phrases[phrase]);
        countTv.setText(String.valueOf(count));
        if (target > 0) {
            int inRound = count % target;
            boolean full = count > 0 && inRound == 0;
            subTv.setText((full ? target : inRound) + " / " + target);
            bar.set(full ? 1f : inRound / (float) target, animateBar);
        } else {
            subTv.setText(getString(R.string.tasbih_free));
            bar.set((count % 100) / 100f, animateBar);
        }
        totalText.setText(getString(R.string.tasbih_total, total));
        phraseRow.removeAllViews();
        for (int i = 0; i < phrases.length; i++) {
            final int idx = i;
            TextView chip = Ui.chipView(this, phrases[i], v -> {
                if (phrase == idx) return;
                phrase = idx;
                count = 0;
                save();
                beads.set(count, target);
                sync(false);
                Anim.popIn(phraseTv);
            });
            if (i == phrase) {
                chip.setBackground(Ui.rect(Ui.color(this, R.color.accent), Ui.dpf(this, 100)));
                chip.setTextColor(0xFFFFFFFF);
            }
            phraseRow.addView(chip);
        }
        targetRow.removeAllViews();
        for (final int tg : TARGETS) {
            String label = tg == 0 ? getString(R.string.tasbih_free) : String.valueOf(tg);
            TextView chip = Ui.chipView(this, label, v -> {
                target = tg;
                save();
                beads.set(count, target);
                sync(false);
            });
            if (tg == target) {
                chip.setBackground(Ui.rect(Ui.color(this, R.color.accent), Ui.dpf(this, 100)));
                chip.setTextColor(0xFFFFFFFF);
            }
            ((LinearLayout.LayoutParams) chip.getLayoutParams()).setMarginStart(Ui.dp(this, 4));
            targetRow.addView(chip);
        }
    }

    /** Thin rounded progress bar that fills from the start side. */
    static final class Bar extends View {
        private float value, shown;
        private ValueAnimator va;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();

        Bar(Context c) {
            super(c);
        }

        void set(float v, boolean animate) {
            value = v;
            if (va != null) va.cancel();
            if (!animate || v < shown) {
                shown = v;
                invalidate();
                return;
            }
            va = ValueAnimator.ofFloat(shown, v);
            va.setDuration(Anim.D_MED);
            va.setInterpolator(Anim.OUT);
            va.addUpdateListener(a -> {
                shown = (float) a.getAnimatedValue();
                invalidate();
            });
            va.start();
        }

        @Override
        protected void onDraw(Canvas cv) {
            float h = getHeight(), w = getWidth();
            p.setColor(Ui.color(getContext(), R.color.neutral_soft));
            r.set(0, 0, w, h);
            cv.drawRoundRect(r, h / 2, h / 2, p);
            if (shown <= 0f) return;
            p.setColor(Ui.color(getContext(), shown >= 1f ? R.color.ok : R.color.accent));
            float fw = Math.max(h, w * Math.min(1f, shown));
            if (getLayoutDirection() == LAYOUT_DIRECTION_RTL) r.set(w - fw, 0, w, h);
            else r.set(0, 0, fw, h);
            cv.drawRoundRect(r, h / 2, h / 2, p);
        }
    }

    /** Beads strung on a curved cord; every tap slides the string one bead with a soft spring. */
    static final class Beads extends View {
        private int count, target;
        private float shift, flash;
        private ValueAnimator move, glow;
        private final Paint cord = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF oval = new RectF();
        private static final int GOLD = 0xFFD4AF37;

        Beads(Context c) {
            super(c);
            cord.setStyle(Paint.Style.STROKE);
            cord.setStrokeCap(Paint.Cap.ROUND);
            ring.setStyle(Paint.Style.STROKE);
        }

        void set(int c, int t) {
            count = c;
            target = t;
            invalidate();
        }

        void advance(int newCount, boolean completed) {
            count = newCount;
            if (move != null) move.cancel();
            move = ValueAnimator.ofFloat(1f, 0f);
            move.setDuration(Anim.D_LONG);
            move.setInterpolator(Anim.SPRING);
            move.addUpdateListener(a -> {
                shift = (float) a.getAnimatedValue();
                invalidate();
            });
            move.start();
            if (completed) {
                if (glow != null) glow.cancel();
                glow = ValueAnimator.ofFloat(0f, 1f);
                glow.setDuration(Anim.D_LONG);
                glow.setInterpolator(Anim.OUT);
                glow.addUpdateListener(a -> {
                    flash = (float) a.getAnimatedValue();
                    invalidate();
                });
                glow.start();
            }
        }

        private void bead(Canvas cv, float x, float y, float r, int color, int alpha) {
            fill.setShader(null);
            fill.setColor(0x33000000);
            fill.setAlpha(alpha / 6);
            cv.drawCircle(x + r * 0.12f, y + r * 0.2f, r, fill);
            fill.setShader(new RadialGradient(x - r * 0.35f, y - r * 0.4f, r * 1.45f,
                    new int[]{ColorUtils.blendARGB(color, Color.WHITE, 0.6f), color, ColorUtils.blendARGB(color, Color.BLACK, 0.5f)},
                    new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
            fill.setAlpha(alpha);
            cv.drawCircle(x, y, r, fill);
            fill.setShader(null);
            fill.setColor(Color.WHITE);
            fill.setAlpha(alpha * 110 / 255);
            cv.drawCircle(x - r * 0.36f, y - r * 0.42f, r * 0.17f, fill);
        }

        @Override
        protected void onDraw(Canvas cv) {
            Context c = getContext();
            float d = Ui.dpf(c, 1);
            float w = getWidth(), h = getHeight();
            float cx = w / 2f;
            float rb = 19 * d;
            float spacing = rb * 2.3f;
            float big = Math.max(w * 0.85f, 220 * d);
            float cy0 = h - 26 * d - big;
            float dTheta = spacing / big;
            float thMax = (float) Math.asin(Math.min(0.98, (w / 2f + rb) / big));
            float deg = (float) Math.toDegrees(thMax);

            cord.setStrokeWidth(2.2f * d);
            cord.setColor(Ui.alpha(Ui.color(c, R.color.text_hint), 200));
            oval.set(cx - big, cy0 - big, cx + big, cy0 + big);
            cv.drawArc(oval, 90 - deg, 2 * deg, false, cord);

            int base = ColorUtils.blendARGB(Ui.color(c, R.color.ok), Color.BLACK, 0.12f);
            for (int i = -9; i <= 9; i++) {
                float th = (i + shift) * dTheta;
                float at = Math.abs(th) / thMax;
                if (at > 1.08f) continue;
                float x = cx + big * (float) Math.sin(th);
                float y = cy0 + big * (float) Math.cos(th);
                float f = 1f - 0.4f * Math.min(1f, at);
                int id = count + i;
                boolean sep = target > 0 && id > 0 && id % target == 0;
                int alpha = (int) (255 * (1f - 0.7f * Math.min(1f, at)));
                bead(cv, x, y, rb * f * (sep ? 1.22f : 1f), sep ? GOLD : base, Math.max(0, alpha));
            }

            float ax = cx, ay = cy0 + big;
            ring.setStrokeWidth(2.4f * d);
            ring.setColor(Ui.color(c, R.color.accent));
            ring.setAlpha(150);
            cv.drawCircle(ax, ay, rb + 7 * d, ring);
            if (flash > 0f && flash < 1f) {
                ring.setColor(GOLD);
                ring.setAlpha((int) (255 * (1f - flash)));
                ring.setStrokeWidth(3f * d);
                cv.drawCircle(ax, ay, rb * (1.3f + 1.9f * flash), ring);
            }
        }
    }
}
