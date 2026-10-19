package com.clock.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.util.TypedValue;
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

    private int count, target = 33, phrase, total, rounds;
    private boolean hapticOn = true, volKeys, keepOn;
    private HorizontalScrollView optScroll;
    private LinearLayout optRow;
    private String day = "";
    private Rosary rosary;
    private HorizontalScrollView phraseScroll;
    private TextView phraseTv, totalText;
    private LinearLayout phraseRow, targetRow;
    private String[] phrases;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        useGroupedBackground();
        phrases = getResources().getStringArray(R.array.tasbih_phrases);
        load();
        hapticOn = !"0".equals(Store.raw(this, "tb_haptic"));
        volKeys = "1".equals(Store.raw(this, "tb_vol"));
        keepOn = "1".equals(Store.raw(this, "tb_keep"));
        applyKeepOn();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        Header hd = new Header(this, getString(R.string.tasbih));
        hd.addView(Ui.iconButton(this, R.drawable.ic_back, R.string.back, false, v -> finish()), 0,
                new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40)));
        ((LinearLayout.LayoutParams) hd.getChildAt(0).getLayoutParams()).setMarginEnd(Ui.dp(this, 10));
        hd.addButton(R.drawable.ic_refresh, R.string.tasbih_reset, false, v -> {
            count = 0;
            rounds = 0;
            save();
            sync(false, false);
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
        phraseScroll = hs;
        hs.setHorizontalScrollBarEnabled(false);
        phraseRow = new LinearLayout(this);
        phraseRow.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 8), Ui.dp(this, 8));
        hs.addView(phraseRow);
        col.addView(hs, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setBackground(Ui.rect(Ui.color(this, R.color.surface), Ui.dpf(this, 28)));
        card.setPadding(Ui.dp(this, 12), Ui.dp(this, 20), Ui.dp(this, 12), Ui.dp(this, 12));
        phraseTv = Ui.text(this, "", 28, R.color.text_primary);
        phraseTv.setTypeface(Ui.medium());
        phraseTv.setGravity(Gravity.CENTER);
        phraseTv.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        card.addView(phraseTv, new LinearLayout.LayoutParams(-1, -2));
        rosary = new Rosary(this);
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
        rl.topMargin = Ui.dp(this, 8);
        card.addView(rosary, rl);
        TextView hint = Ui.text(this, getString(R.string.tasbih_hint), 13, R.color.text_hint);
        hint.setGravity(Gravity.CENTER);
        hint.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        hint.setPadding(0, Ui.dp(this, 2), 0, Ui.dp(this, 4));
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
        totalText.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        totalText.setPadding(Ui.dp(this, 18), Ui.dp(this, 9), Ui.dp(this, 18), Ui.dp(this, 9));
        totalText.setBackground(Ui.rect(Ui.color(this, R.color.surface), Ui.dpf(this, 100)));
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
        tl.gravity = Gravity.CENTER_HORIZONTAL;
        tl.topMargin = Ui.dp(this, 16);
        col.addView(totalText, tl);

        optScroll = new HorizontalScrollView(this);
        optScroll.setHorizontalScrollBarEnabled(false);
        optRow = new LinearLayout(this);
        optRow.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), 0);
        optScroll.addView(optRow);
        LinearLayout.LayoutParams ol = new LinearLayout.LayoutParams(-1, -2);
        ol.topMargin = Ui.dp(this, 14);
        col.addView(optScroll, ol);
        buildOptions();

        sync(false, false);
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
                rounds = o.optInt("r", 0);
                day = o.optString("d", "");
            }
        } catch (Exception ignored) {
        }
        if (!today().equals(day)) {
            day = today();
            total = 0;
            rounds = 0;
        }
    }

    private void save() {
        try {
            JSONObject o = new JSONObject();
            o.put("c", count).put("t", target).put("p", phrase).put("n", total).put("r", rounds).put("d", day);
            Store.putRaw(this, KEY, o.toString());
        } catch (Exception ignored) {
        }
    }

    private void tap() {
        if (!today().equals(day)) {
            day = today();
            total = 0;
            rounds = 0;
        }
        count++;
        total++;
        boolean done = target > 0 && count % target == 0;
        if (done) rounds++;
        if (hapticOn) {
            if (done) phraseTv.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            else Ui.tick(phraseTv);
        }
        save();
        sync(true, done);
        if (done) Anim.bounce(phraseTv);
    }

    private void undo() {
        if (count <= 0) return;
        boolean wasRoundEnd = target > 0 && count % target == 0;
        count--;
        total = Math.max(0, total - 1);
        if (wasRoundEnd && rounds > 0) rounds--;
        save();
        sync(false, false);
        Anim.pulse(phraseTv);
    }

    private void applyKeepOn() {
        if (keepOn) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private TextView optChip(int label, boolean on, View.OnClickListener click) {
        TextView t = Ui.chipView(this, getString(label), click);
        if (on) {
            t.setBackground(Ui.rect(Ui.color(this, R.color.accent), Ui.dpf(this, 100)));
            t.setTextColor(0xFFFFFFFF);
        }
        return t;
    }

    private void buildOptions() {
        optRow.removeAllViews();
        optRow.addView(optChip(R.string.tasbih_undo, false, v -> undo()));
        optRow.addView(optChip(R.string.tasbih_opt_haptic, hapticOn, v -> {
            hapticOn = !hapticOn;
            Store.putRaw(this, "tb_haptic", hapticOn ? "1" : "0");
            buildOptions();
        }));
        optRow.addView(optChip(R.string.tasbih_opt_vol, volKeys, v -> {
            volKeys = !volKeys;
            Store.putRaw(this, "tb_vol", volKeys ? "1" : "0");
            buildOptions();
        }));
        optRow.addView(optChip(R.string.tasbih_opt_keep, keepOn, v -> {
            keepOn = !keepOn;
            Store.putRaw(this, "tb_keep", keepOn ? "1" : "0");
            applyKeepOn();
            buildOptions();
        }));
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent e) {
        if (volKeys && (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            if (e.getRepeatCount() == 0) tap();
            return true;
        }
        return super.onKeyDown(keyCode, e);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent e) {
        if (volKeys && (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) return true;
        return super.onKeyUp(keyCode, e);
    }

    private void sync(boolean animate, boolean done) {
        phraseTv.setText(phrases[phrase]);
        int eff = target > 0 ? target : 33;
        int inRound = count % eff;
        boolean full = count > 0 && inRound == 0;
        String sub = target > 0 ? "\u200E" + (full ? eff : inRound) + " / " + target + "\u200E" : getString(R.string.tasbih_free);
        rosary.update(count, sub, full ? 1f : inRound / (float) eff, animate, done);
        totalText.setText(getString(R.string.tasbih_total, total)
                + (target > 0 ? "  \u00B7  " + getString(R.string.tasbih_rounds, rounds) : ""));
        phraseRow.removeAllViews();
        for (int i = 0; i < phrases.length; i++) {
            final int idx = i;
            TextView chip = Ui.chipView(this, phrases[i], v -> {
                if (phrase == idx) return;
                phrase = idx;
                count = 0;
                save();
                sync(false, false);
                Anim.popIn(phraseTv);
            });
            if (i == phrase) {
                chip.setBackground(Ui.rect(Ui.color(this, R.color.accent), Ui.dpf(this, 100)));
                chip.setTextColor(0xFFFFFFFF);
            }
            phraseRow.addView(chip);
        }
        final int sel = phrase;
        phraseScroll.post(() -> {
            View ch = phraseRow.getChildAt(sel);
            if (ch == null) return;
            int x = ch.getLeft() - (phraseScroll.getWidth() - ch.getWidth()) / 2;
            phraseScroll.smoothScrollTo(Math.max(0, x), 0);
        });
        targetRow.removeAllViews();
        for (final int tg : TARGETS) {
            String label = tg == 0 ? getString(R.string.tasbih_free) : String.valueOf(tg);
            TextView chip = Ui.chipView(this, label, v -> {
                target = tg;
                save();
                sync(false, false);
            });
            if (tg == target) {
                chip.setBackground(Ui.rect(Ui.color(this, R.color.accent), Ui.dpf(this, 100)));
                chip.setTextColor(0xFFFFFFFF);
            }
            ((LinearLayout.LayoutParams) chip.getLayoutParams()).setMarginStart(Ui.dp(this, 4));
            targetRow.addView(chip);
        }
    }

    static final class Rosary extends View {
        private static final int N = 33;
        private static final int GOLD = 0xFFD4AF37;
        private int count, lit, popIdx = -1;
        private String sub = "";
        private float pop = 1f, flash = 1f, num = 1f;
        private float ang, vel, ang2, vel2, dir = 1f;
        private boolean running;
        private long last;
        private ValueAnimator popA, flashA, numA;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint chain = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint big = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint small = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private Shader shLit, shDim, glow, spacerSh, shieldSh, capSh;
        private float cx, cy, rad, rb, thick;
        private final float d;

        Rosary(Context c) {
            super(c);
            d = Ui.dpf(c, 1);
            line.setStyle(Paint.Style.STROKE);
            chain.setStyle(Paint.Style.STROKE);
            chain.setStrokeWidth(2.2f * d);
            chain.setColor(0xFFC5CBD1);
            chain.setPathEffect(new DashPathEffect(new float[]{3f * d, 1.6f * d}, 0));
            big.setTextAlign(Paint.Align.CENTER);
            big.setTypeface(Ui.light());
            big.setFontFeatureSettings("tnum");
            small.setTextAlign(Paint.Align.CENTER);
            small.setTypeface(Ui.medium());
            small.setFontFeatureSettings("tnum");
        }

        private float radius(int w) {
            return Math.min(w / 2f - 20f * d, 138f * d);
        }

        @Override
        protected void onMeasure(int wSpec, int hSpec) {
            int w = MeasureSpec.getSize(wSpec);
            float r = radius(w);
            setMeasuredDimension(w, (int) (2 * r + 12 * d + 134 * d));
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            Context c = getContext();
            rad = radius(w);
            cx = w / 2f;
            cy = 12f * d + rad;
            double step = Math.PI * 2 / 36;
            float half = (float) (rad * Math.sin(step / 2));
            rb = half * 0.93f;
            thick = half * 0.9f;
            int accent = Ui.color(c, R.color.accent);
            int base = ColorUtils.blendARGB(accent, 0xFF16B8B0, 0.3f);
            int dim = ColorUtils.blendARGB(base, Ui.color(c, R.color.surface), 0.8f);
            shLit = new RadialGradient(-rb * 0.35f, -rb * 0.4f, rb * 1.45f,
                    new int[]{ColorUtils.blendARGB(base, Color.WHITE, 0.65f), base, ColorUtils.blendARGB(base, Color.BLACK, 0.55f)},
                    new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
            shDim = new RadialGradient(-rb * 0.35f, -rb * 0.4f, rb * 1.45f,
                    new int[]{ColorUtils.blendARGB(dim, Color.WHITE, 0.3f), dim, ColorUtils.blendARGB(dim, Color.BLACK, 0.45f)},
                    new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
            glow = new RadialGradient(cx, cy, rad * 0.95f, new int[]{Ui.alpha(accent, 48), Ui.alpha(accent, 0)}, null, Shader.TileMode.CLAMP);
            spacerSh = new LinearGradient(0, -thick * 0.45f, 0, thick * 0.45f,
                    new int[]{0xFFF4F6F8, 0xFF9BA3AB, 0xFFE3E7EA}, null, Shader.TileMode.CLAMP);
            shieldSh = new LinearGradient(0, -7f * d, 0, 13f * d,
                    new int[]{0xFFF4F6F8, 0xFF8E979F, 0xFFD8DDE1}, null, Shader.TileMode.CLAMP);
            capSh = new LinearGradient(-6.5f * d, 0, 6.5f * d, 0,
                    new int[]{0xFF8E979F, 0xFFF5F7F9, 0xFF8E979F}, null, Shader.TileMode.CLAMP);
        }

        void update(int c, String s, float frac, boolean animate, boolean completed) {
            count = c;
            sub = s;
            int nl = frac >= 1f ? N : Math.min(N, (int) (frac * N));
            if (animate) {
                if (nl > lit && nl > 0) {
                    popIdx = nl - 1;
                    if (popA != null) popA.cancel();
                    popA = ValueAnimator.ofFloat(0f, 1f);
                    popA.setDuration(Anim.D_LONG);
                    popA.setInterpolator(Anim.SPRING);
                    popA.addUpdateListener(a -> {
                        pop = (float) a.getAnimatedValue();
                        invalidate();
                    });
                    popA.start();
                }
                if (numA != null) numA.cancel();
                numA = ValueAnimator.ofFloat(1.16f, 1f);
                numA.setDuration(Anim.D_MED);
                numA.setInterpolator(Anim.SPRING);
                numA.addUpdateListener(a -> {
                    num = (float) a.getAnimatedValue();
                    invalidate();
                });
                numA.start();
                dir = -dir;
                vel = Math.max(-140f, Math.min(140f, vel + 85f * dir));
                kick();
            } else {
                popIdx = -1;
                pop = 1f;
            }
            lit = nl;
            if (completed) {
                if (flashA != null) flashA.cancel();
                flashA = ValueAnimator.ofFloat(0f, 1f);
                flashA.setDuration(Anim.D_LONG * 2);
                flashA.setInterpolator(Anim.OUT);
                flashA.addUpdateListener(a -> {
                    flash = (float) a.getAnimatedValue();
                    invalidate();
                });
                flashA.start();
            }
            invalidate();
        }

        private void kick() {
            if (running) return;
            running = true;
            last = AnimationUtils.currentAnimationTimeMillis();
            postOnAnimation(stepper);
        }

        private final Runnable stepper = new Runnable() {
            @Override
            public void run() {
                long now = AnimationUtils.currentAnimationTimeMillis();
                float dt = Math.min(0.032f, Math.max(0.001f, (now - last) / 1000f));
                last = now;
                float acc = -62f * ang - 3.1f * vel;
                vel += acc * dt;
                ang = Math.max(-16f, Math.min(16f, ang + vel * dt));
                float acc2 = -95f * ang2 - 3.6f * vel2 - 0.55f * acc;
                vel2 += acc2 * dt;
                ang2 = Math.max(-22f, Math.min(22f, ang2 + vel2 * dt));
                invalidate();
                if (Math.abs(ang) + Math.abs(vel) + Math.abs(ang2) + Math.abs(vel2) < 0.15f) {
                    ang = vel = ang2 = vel2 = 0f;
                    running = false;
                } else {
                    postOnAnimation(this);
                }
            }
        };

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            removeCallbacks(stepper);
            running = false;
            if (popA != null) popA.cancel();
            if (numA != null) numA.cancel();
            if (flashA != null) flashA.cancel();
        }

        private void orb(Canvas cv, float x, float y, float radius, boolean on, float mix) {
            float sc = radius / rb;
            fill.setShader(null);
            fill.setColor(0x44000000);
            cv.drawCircle(x + radius * 0.1f, y + radius * 0.22f, radius, fill);
            cv.save();
            cv.translate(x, y);
            cv.scale(sc, sc);
            fill.setShader(shDim);
            fill.setAlpha(255);
            cv.drawCircle(0, 0, rb, fill);
            float a = on ? 1f : Math.max(0f, Math.min(1f, mix));
            if (a > 0f) {
                fill.setShader(shLit);
                fill.setAlpha((int) (255 * a));
                cv.drawCircle(0, 0, rb, fill);
            }
            fill.setShader(null);
            fill.setColor(Color.WHITE);
            fill.setAlpha(on ? 125 : 45);
            cv.drawCircle(-rb * 0.36f, -rb * 0.42f, rb * 0.17f, fill);
            cv.restore();
        }

        private void spacer(Canvas cv, float x, float y, float th) {
            cv.save();
            cv.translate(x, y);
            cv.rotate((float) Math.toDegrees(th));
            float len = rb * 1.9f;
            rect.set(-len / 2f, -thick * 0.45f, len / 2f, thick * 0.45f);
            fill.setShader(spacerSh);
            fill.setAlpha(255);
            cv.drawRoundRect(rect, 2f * d, 2f * d, fill);
            fill.setShader(null);
            fill.setColor(Color.WHITE);
            for (int i = -1; i <= 1; i++) cv.drawCircle(i * len / 3.4f, 0, 1.1f * d, fill);
            cv.restore();
        }

        private void tassel(Canvas cv) {
            float ay = cy + rad;
            cv.save();
            cv.rotate(ang, cx, ay);
            cv.translate(cx, ay);
            rect.set(-12f * d, -7f * d, 12f * d, 13f * d);
            fill.setShader(shieldSh);
            fill.setAlpha(255);
            cv.drawRoundRect(rect, 8f * d, 8f * d, fill);
            line.setStrokeWidth(1f * d);
            line.setColor(0x66000000);
            cv.drawRoundRect(rect, 8f * d, 8f * d, line);
            fill.setShader(null);
            fill.setColor(0x55000000);
            cv.drawCircle(0, 3f * d, 3.5f * d, fill);
            rect.set(-6.5f * d, 13f * d, 6.5f * d, 57f * d);
            fill.setShader(capSh);
            cv.drawRoundRect(rect, 5f * d, 5f * d, fill);
            line.setColor(0x55000000);
            for (int i = 0; i < 8; i++) {
                float y = 18f * d + i * 5f * d;
                cv.drawLine(-6.2f * d, y, 6.2f * d, y, line);
            }
            rect.set(-8f * d, 57f * d, 8f * d, 61f * d);
            fill.setShader(capSh);
            cv.drawRoundRect(rect, 2f * d, 2f * d, fill);
            fill.setShader(null);
            fill.setColor(0xFFC5CBD1);
            cv.drawRect(-1f * d, 61f * d, 1f * d, 66f * d, fill);
            orb(cv, 0, 76f * d, 9f * d, true, 1f);
            cv.save();
            cv.rotate(ang2, 0, 85f * d);
            cv.drawLine(0, 85f * d, -13f * d, 108f * d, chain);
            cv.drawLine(0, 85f * d, 13f * d, 108f * d, chain);
            fill.setShader(null);
            fill.setColor(0xFFC5CBD1);
            cv.drawCircle(-13f * d, 108f * d, 2.4f * d, fill);
            cv.drawCircle(13f * d, 108f * d, 2.4f * d, fill);
            orb(cv, -13f * d, 117f * d, 8f * d, true, 1f);
            orb(cv, 13f * d, 117f * d, 8f * d, true, 1f);
            cv.restore();
            cv.restore();
        }

        @Override
        protected void onDraw(Canvas cv) {
            if (shLit == null) return;
            Context c = getContext();
            int accent = Ui.color(c, R.color.accent);
            fill.setShader(glow);
            fill.setAlpha(255);
            cv.drawCircle(cx, cy, rad * 0.95f, fill);
            fill.setShader(null);

            line.setStrokeWidth(1.8f * d);
            line.setColor(Ui.alpha(Ui.color(c, R.color.text_hint), 170));
            cv.drawCircle(cx, cy, rad, line);

            tassel(cv);

            double step = Math.PI * 2 / 36;
            for (int j = 1; j <= 35; j++) {
                float th = (float) (Math.PI / 2 + j * step);
                float x = cx + rad * (float) Math.cos(th);
                float y = cy + rad * (float) Math.sin(th);
                if (j == 12 || j == 24) {
                    spacer(cv, x, y, th);
                    continue;
                }
                int k = j < 12 ? j - 1 : j < 24 ? j - 2 : j - 3;
                boolean on = k < lit;
                boolean popping = k == popIdx && pop < 1f;
                float s = popping ? 1f + 0.45f * (1f - pop) : 1f;
                orb(cv, x, y, rb * s, on && !popping, popping ? pop : 0f);
                if (flash < 1f) {
                    float g = Math.max(0f, 1f - Math.abs(k / (float) (N - 1) - (flash * 1.5f - 0.25f)) * 7f);
                    if (g > 0f) {
                        fill.setShader(null);
                        fill.setColor(GOLD);
                        fill.setAlpha((int) (200 * g));
                        cv.drawCircle(x, y, rb * s, fill);
                    }
                }
                if (popping) {
                    line.setStrokeWidth(2f * d);
                    line.setColor(Ui.alpha(accent, (int) (170 * (1f - pop))));
                    cv.drawCircle(x, y, rb * (1.1f + 0.9f * pop), line);
                }
                if (k == lit - 1 && lit > 0) {
                    line.setStrokeWidth(2f * d);
                    line.setColor(Ui.alpha(accent, 210));
                    cv.drawCircle(x, y, rb + 3.5f * d, line);
                }
            }

            if (flash < 1f) {
                line.setStrokeWidth(3f * d);
                line.setColor(Ui.alpha(GOLD, (int) (210 * (1f - flash))));
                cv.drawCircle(cx, cy, rad * (0.3f + 0.8f * flash), line);
                line.setColor(Ui.alpha(accent, (int) (120 * (1f - flash))));
                cv.drawCircle(cx, cy, rad * (0.2f + 0.65f * flash), line);
            }
            big.setColor(Ui.color(c, R.color.text_primary));
            big.setTextSize(rad * 0.5f);
            Paint.FontMetrics fm = big.getFontMetrics();
            float by = cy - rad * 0.06f - (fm.ascent + fm.descent) / 2f;
            cv.save();
            cv.scale(num, num, cx, cy - rad * 0.06f);
            cv.drawText(String.valueOf(count), cx, by, big);
            cv.restore();
            small.setColor(Ui.color(c, R.color.text_secondary));
            small.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 16, getResources().getDisplayMetrics()));
            cv.drawText(sub, cx, cy + rad * 0.42f, small);
        }
    }
}
