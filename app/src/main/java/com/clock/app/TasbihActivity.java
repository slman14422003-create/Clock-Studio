package com.clock.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

/** Digital tasbih: tap to count, choose the dhikr and a target, the count and today's total are remembered. */
public class TasbihActivity extends BaseActivity {
    private static final String KEY = "tasbih";
    private static final int[] TARGETS = {33, 99, 100, 0};

    private int count, target = 33, phrase, total;
    private String day = "";
    private RingCounter ring;
    private TextView totalText;
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
            sync();
        });
        root.addView(hd);

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 30));
        sv.addView(col, new ScrollView.LayoutParams(-1, -2));
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        phraseRow = new LinearLayout(this);
        phraseRow.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 8), Ui.dp(this, 6));
        hs.addView(phraseRow);
        col.addView(hs, new LinearLayout.LayoutParams(-1, -2));

        ring = new RingCounter(this);
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
        rl.setMargins(Ui.dp(this, 24), Ui.dp(this, 10), Ui.dp(this, 24), Ui.dp(this, 10));
        col.addView(ring, rl);
        ring.setOnClickListener(v -> tap());

        targetRow = new LinearLayout(this);
        targetRow.setGravity(Gravity.CENTER);
        col.addView(targetRow, new LinearLayout.LayoutParams(-1, -2));

        totalText = Ui.text(this, "", 15, R.color.text_secondary);
        totalText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
        tl.topMargin = Ui.dp(this, 16);
        col.addView(totalText, tl);
        sync();
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
        if (done) {
            ring.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        } else {
            Ui.tick(ring);
        }
        save();
        sync();
    }

    private void sync() {
        ring.set(count, target, phrases[phrase]);
        totalText.setText(getString(R.string.tasbih_total, total));
        phraseRow.removeAllViews();
        for (int i = 0; i < phrases.length; i++) {
            final int idx = i;
            TextView chip = Ui.chipView(this, phrases[i], v -> {
                phrase = idx;
                count = 0;
                save();
                sync();
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
                sync();
            });
            if (tg == target) {
                chip.setBackground(Ui.rect(Ui.color(this, R.color.accent), Ui.dpf(this, 100)));
                chip.setTextColor(0xFFFFFFFF);
            }
            ((LinearLayout.LayoutParams) chip.getLayoutParams()).setMarginStart(Ui.dp(this, 4));
            targetRow.addView(chip);
        }
    }

    static final class RingCounter extends View {
        private int count, target;
        private String label = "";
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF box = new RectF();

        RingCounter(Context c) {
            super(c);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            t.setTextAlign(Paint.Align.CENTER);
            setClickable(true);
            Ui.press(c, this);
        }

        void set(int c, int tg, String l) {
            count = c;
            target = tg;
            label = l;
            invalidate();
        }

        @Override
        protected void onMeasure(int w, int h) {
            int s = Math.min(MeasureSpec.getSize(w), Ui.dp(getContext(), 300));
            setMeasuredDimension(MeasureSpec.getSize(w), s);
        }

        @Override
        protected void onDraw(Canvas cv) {
            Context c = getContext();
            float d = Ui.dpf(c, 1);
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            float r = Math.min(cx, cy) - 16 * d;
            Paint f = new Paint(Paint.ANTI_ALIAS_FLAG);
            f.setColor(Ui.color(c, R.color.surface));
            cv.drawCircle(cx, cy, r, f);
            p.setStrokeWidth(12 * d);
            p.setColor(Ui.color(c, R.color.neutral_soft));
            cv.drawCircle(cx, cy, r - 6 * d, p);
            box.set(cx - r + 6 * d, cy - r + 6 * d, cx + r - 6 * d, cy + r - 6 * d);
            if (target > 0) {
                int inRound = count % target;
                boolean full = count > 0 && inRound == 0;
                p.setColor(Ui.color(c, full ? R.color.ok : R.color.accent));
                cv.drawArc(box, -90f, full ? 360f : 360f * inRound / target, false, p);
            } else if (count > 0) {
                p.setColor(Ui.color(c, R.color.accent));
                cv.drawArc(box, -90f, 360f * (count % 100) / 100f, false, p);
            }
            t.setColor(Ui.color(c, R.color.text_primary));
            t.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
            t.setTextSize(78 * d);
            cv.drawText(String.valueOf(count), cx, cy + 14 * d, t);
            t.setColor(Ui.color(c, R.color.text_secondary));
            t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            t.setTextSize(16 * d);
            if (label.length() > 22) t.setTextSize(13 * d);
            cv.drawText(label, cx, cy + 58 * d, t);
            if (target > 0) {
                t.setTextSize(14 * d);
                cv.drawText((count % target == 0 && count > 0 ? target : count % target) + " / " + target, cx, cy - 56 * d, t);
            }
        }
    }
}
