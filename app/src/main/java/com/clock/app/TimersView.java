package com.clock.app;

import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

/** Timers (several at once, ring progress, focus preset) and the stopwatch with laps, switched by a segmented control. */
final class TimersView extends TabView {
    private final TimerPane timer;
    private final StopwatchPane watch;
    private final LinearLayout seg;

    TimersView(Context c, Host h) {
        super(c, h);
        addView(new Header(c, c.getString(R.string.nav_timers)));
        seg = Ui.segmented(c, new CharSequence[]{c.getString(R.string.timer), c.getString(R.string.stopwatch)}, 0, this::show);
        LayoutParams sl = new LayoutParams(-1, -2);
        sl.setMargins(Ui.dp(c, 16), 0, Ui.dp(c, 16), Ui.dp(c, 8));
        addView(seg, sl);
        FrameLayout stage = new FrameLayout(c);
        timer = new TimerPane(c);
        watch = new StopwatchPane(c);
        stage.addView(timer, new FrameLayout.LayoutParams(-1, -1));
        stage.addView(watch, new FrameLayout.LayoutParams(-1, -1));
        addView(stage, new LayoutParams(-1, 0, 1f));
        show(0);
    }

    private void show(int i) {
        timer.setVisibility(i == 0 ? VISIBLE : GONE);
        watch.setVisibility(i == 1 ? VISIBLE : GONE);
        if (i == 1) watch.resume();
    }

    void showStopwatch(boolean on) {
        Ui.setSegment(seg, on ? 1 : 0);
        show(on ? 1 : 0);
    }

    @Override
    void refresh() {
        timer.refresh();
    }

    @Override
    void tick() {
        timer.tick();
    }

    @Override
    void scrollToTop() {
        timer.smoothScrollTo(0, 0);
    }

    // ================================================================== timer

    private static final class TimerPane extends ScrollView {
        private final WheelView wh, wm, ws;
        private final EditText label;
        private final LinearLayout running, recents;
        private String sig = "";
        private final List<Card> cards = new ArrayList<>();

        TimerPane(Context c) {
            super(c);
            setVerticalScrollBarEnabled(false);
            setOverScrollMode(OVER_SCROLL_NEVER);
            LinearLayout col = new LinearLayout(c);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setPadding(0, 0, 0, Ui.dp(c, 24));
            addView(col, new LayoutParams(-1, -2));

            wh = new WheelView(c);
            wm = new WheelView(c);
            ws = new WheelView(c);
            wh.setItems(Wheels.range(0, 23, false));
            wm.setItems(Wheels.range(0, 59, false));
            ws.setItems(Wheels.range(0, 59, false));
            wh.setUnit(c.getString(R.string.unit_h));
            wm.setUnit(c.getString(R.string.unit_m));
            ws.setUnit(c.getString(R.string.unit_s));
            wm.setIndex(5);
            col.addView(Wheels.group(c, wh, wm, ws), new LinearLayout.LayoutParams(-1, Ui.dp(c, 200)));

            EditText e = new EditText(c);
            e.setHint(R.string.timer_label_hint);
            e.setSingleLine(true);
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            e.setBackground(Ui.rect(Ui.color(c, R.color.field), Ui.dpf(c, 12)));
            e.setTextColor(Ui.color(c, R.color.text_primary));
            e.setHintTextColor(Ui.color(c, R.color.text_hint));
            e.setTextSize(16);
            e.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
            e.setPaddingRelative(Ui.dp(c, 16), Ui.dp(c, 12), Ui.dp(c, 16), Ui.dp(c, 12));
            label = e;
            LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(-1, -2);
            ll.setMargins(Ui.dp(c, 16), Ui.dp(c, 10), Ui.dp(c, 16), 0);
            col.addView(label, ll);

            TextView start = Ui.circleButton(c, c.getString(R.string.start), Ui.color(c, R.color.ok));
            start.setOnClickListener(v -> startFromWheels());
            LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(Ui.dp(c, 84), Ui.dp(c, 84));
            sl.gravity = Gravity.CENTER_HORIZONTAL;
            sl.topMargin = Ui.dp(c, 16);
            col.addView(start, sl);

            HorizontalScrollView hs = new HorizontalScrollView(c);
            hs.setHorizontalScrollBarEnabled(false);
            LinearLayout chips = new LinearLayout(c);
            chips.setPadding(Ui.dp(c, 16), 0, Ui.dp(c, 8), 0);
            int[] mins = {1, 5, 10, 15, 30, 60};
            for (final int m : mins) {
                chips.addView(Ui.chipView(c, Fmt.spoken(c, m * 60000L), v -> setWheels(m * 60000L)));
            }
            chips.addView(Ui.chipView(c, c.getString(R.string.focus_chip), v -> {
                Timers.start(getContext(), 25 * 60000L, getContext().getString(R.string.focus), 5 * 60000L, getContext().getString(R.string.break_label));
            }));
            hs.addView(chips);
            LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(-1, -2);
            hl.topMargin = Ui.dp(c, 16);
            col.addView(hs, hl);

            running = new LinearLayout(c);
            running.setOrientation(LinearLayout.VERTICAL);
            col.addView(running, new LinearLayout.LayoutParams(-1, -2));
            recents = new LinearLayout(c);
            recents.setOrientation(LinearLayout.VERTICAL);
            col.addView(recents, new LinearLayout.LayoutParams(-1, -2));
            refresh();
        }

        private void setWheels(long ms) {
            long s = ms / 1000;
            wh.setIndex((int) Math.min(23, s / 3600));
            wm.setIndex((int) ((s % 3600) / 60));
            ws.setIndex((int) (s % 60));
        }

        private void startFromWheels() {
            Context c = getContext();
            long ms = (wh.getIndex() * 3600L + wm.getIndex() * 60L + ws.getIndex()) * 1000L;
            if (ms <= 0) {
                Ui.toast(c, c.getString(R.string.pick_duration));
                return;
            }
            Timers.addRecent(c, ms);
            Timers.start(c, ms, label.getText().toString().trim(), 0, "");
            label.setText("");
        }

        void refresh() {
            Context c = getContext();
            List<TimerItem> ts = Timers.all(c);
            List<Long> rec = Timers.recents(c);
            StringBuilder b = new StringBuilder();
            for (TimerItem t : ts) b.append(t.id).append(':').append(t.state).append(';');
            b.append('|');
            for (Long l : rec) b.append(l).append(',');
            String ns = b.toString();
            if (!ns.equals(sig)) {
                sig = ns;
                running.removeAllViews();
                cards.clear();
                if (!ts.isEmpty()) {
                    running.addView(Ui.sectionTitle(c, c.getString(R.string.running_timers)));
                    for (TimerItem t : ts) {
                        Card card = new Card(c, t.id);
                        cards.add(card);
                        running.addView(card);
                    }
                }
                recents.removeAllViews();
                if (!rec.isEmpty()) {
                    recents.addView(Ui.sectionTitle(c, c.getString(R.string.recents)));
                    LinearLayout g = Ui.group(c);
                    for (final Long ms : rec) {
                        LinearLayout row = Ui.row(c, R.drawable.ic_play_fill, R.color.ok, Fmt.spoken(c, ms), null, false, v -> {
                            Timers.start(getContext(), ms, "", 0, "");
                        });
                        Ui.addRow(g, row, true);
                    }
                    recents.addView(g);
                }
            }
            tick();
        }

        void tick() {
            long now = System.currentTimeMillis();
            for (Card c : cards) c.update(now);
        }
    }

    private static final class Card extends LinearLayout {
        final int id;
        final RingView ring;
        final TextView time, title, total;
        final ImageButton toggle;

        Card(Context c, int timerId) {
            super(c);
            id = timerId;
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setBackground(Ui.rect(Ui.color(c, R.color.surface), Ui.dpf(c, 14)));
            setPaddingRelative(Ui.dp(c, 14), Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 12));
            LayoutParams lp = new LayoutParams(-1, -2);
            lp.setMargins(Ui.dp(c, 16), 0, Ui.dp(c, 16), Ui.dp(c, 8));
            setLayoutParams(lp);
            FrameLayout f = new FrameLayout(c);
            ring = new RingView(c);
            f.addView(ring, new FrameLayout.LayoutParams(-1, -1));
            time = Ui.tabular(Ui.text(c, "", 17, R.color.text_primary));
            time.setGravity(Gravity.CENTER);
            time.setTypeface(Ui.medium());
            f.addView(time, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
            addView(f, new LayoutParams(Ui.dp(c, 88), Ui.dp(c, 88)));
            LinearLayout col = new LinearLayout(c);
            col.setOrientation(VERTICAL);
            title = Ui.text(c, "", 17, R.color.text_primary);
            title.setSingleLine(true);
            col.addView(title);
            total = Ui.text(c, "", 13, R.color.text_secondary);
            col.addView(total);
            LayoutParams cl = new LayoutParams(0, -2, 1f);
            cl.setMarginStart(Ui.dp(c, 14));
            addView(col, cl);
            toggle = Ui.iconButton(c, R.drawable.ic_pause_fill, R.string.pause, true, v -> {
                TimerItem t = Timers.get(getContext(), id);
                if (t == null) return;
                if (t.state == TimerItem.RUNNING) Timers.pause(getContext(), id);
                else Timers.resume(getContext(), id);
            });
            addView(toggle, new LayoutParams(Ui.dp(c, 44), Ui.dp(c, 44)));
            ImageButton x = Ui.iconButton(c, R.drawable.ic_x, R.string.cancel, false, v -> Timers.cancel(getContext(), id));
            LayoutParams xl = new LayoutParams(Ui.dp(c, 44), Ui.dp(c, 44));
            xl.setMarginStart(Ui.dp(c, 8));
            addView(x, xl);
        }

        void update(long now) {
            Context c = getContext();
            TimerItem t = Timers.get(c, id);
            if (t == null) return;
            long left = t.left(now);
            time.setText(Fmt.countdown(left));
            ring.set(t.fraction(now), Ui.color(c, t.state == TimerItem.RUNNING ? R.color.accent : R.color.warn));
            title.setText(t.label.isEmpty() ? c.getString(R.string.timer) : t.label);
            total.setText(Fmt.spoken(c, t.total));
            toggle.setImageResource(t.state == TimerItem.RUNNING ? R.drawable.ic_pause_fill : R.drawable.ic_play_fill);
        }
    }

    // ================================================================== stopwatch

    private static final class StopwatchPane extends LinearLayout {
        private final Handler h = new Handler(Looper.getMainLooper());
        private final TextView big, lapBtn, startBtn;
        private final LinearLayout lapBox;
        private TextView liveTime;
        private boolean running;
        private long acc, startAt;
        private final List<Long> laps = new ArrayList<>();
        private final Runnable loop = new Runnable() {
            @Override
            public void run() {
                if (!running || !isAttachedToWindow()) return;
                paint();
                h.postDelayed(this, 40);
            }
        };

        StopwatchPane(Context c) {
            super(c);
            setOrientation(VERTICAL);
            big = Ui.tabular(Ui.text(c, "", 72, R.color.text_primary));
            big.setTypeface(Ui.thin());
            big.setGravity(Gravity.CENTER);
            LayoutParams bl = new LayoutParams(-1, -2);
            bl.topMargin = Ui.dp(c, 36);
            addView(big, bl);
            LinearLayout btns = new LinearLayout(c);
            btns.setGravity(Gravity.CENTER);
            btns.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
            lapBtn = Ui.circleButton(c, c.getString(R.string.lap), Ui.color(c, R.color.text_primary));
            startBtn = Ui.circleButton(c, c.getString(R.string.start), Ui.color(c, R.color.ok));
            btns.addView(lapBtn);
            View gap = new View(c);
            btns.addView(gap, new LayoutParams(Ui.dp(c, 90), 1));
            btns.addView(startBtn);
            LayoutParams btl = new LayoutParams(-1, -2);
            btl.topMargin = Ui.dp(c, 28);
            addView(btns, btl);
            ScrollView sv = new ScrollView(c);
            sv.setVerticalScrollBarEnabled(false);
            lapBox = new LinearLayout(c);
            lapBox.setOrientation(VERTICAL);
            sv.addView(lapBox, new ScrollView.LayoutParams(-1, -2));
            LayoutParams sl = new LayoutParams(-1, 0, 1f);
            sl.topMargin = Ui.dp(c, 18);
            addView(sv, sl);
            lapBtn.setOnClickListener(v -> {
                if (running) lap();
                else if (elapsed() > 0) reset();
            });
            startBtn.setOnClickListener(v -> {
                if (running) stop();
                else start();
            });
            load();
        }

        private long elapsed() {
            return running ? acc + (SystemClock.elapsedRealtime() - startAt) : acc;
        }

        private void load() {
            Context c = getContext();
            running = Store.swRunning(c);
            acc = Store.swAcc(c);
            startAt = Store.swStart(c);
            if (running && startAt > SystemClock.elapsedRealtime()) {
                running = false;
                acc = 0;
            }
            laps.clear();
            JSONArray a = Store.array(c, Store.LAPS);
            for (int i = 0; i < a.length(); i++) laps.add(a.optLong(i));
            buttons();
            rebuildLaps();
            paint();
        }

        private void save() {
            Context c = getContext();
            Store.setStopwatch(c, acc, startAt, running);
            JSONArray a = new JSONArray();
            for (Long l : laps) a.put(l);
            Store.putArray(c, Store.LAPS, a);
        }

        void resume() {
            if (running) {
                h.removeCallbacks(loop);
                h.post(loop);
            }
            paint();
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            resume();
        }

        @Override
        protected void onDetachedFromWindow() {
            h.removeCallbacks(loop);
            super.onDetachedFromWindow();
        }

        private void start() {
            startAt = SystemClock.elapsedRealtime();
            running = true;
            save();
            buttons();
            rebuildLaps();
            h.removeCallbacks(loop);
            h.post(loop);
        }

        private void stop() {
            acc = elapsed();
            running = false;
            save();
            buttons();
            paint();
        }

        private void lap() {
            laps.add(elapsed());
            save();
            rebuildLaps();
        }

        private void reset() {
            acc = 0;
            running = false;
            laps.clear();
            save();
            buttons();
            rebuildLaps();
            paint();
        }

        private void buttons() {
            Context c = getContext();
            startBtn.setText(running ? R.string.stop : R.string.start);
            Ui.setCircleColor(startBtn, Ui.color(c, running ? R.color.bad : R.color.ok));
            boolean canLap = running || elapsed() > 0;
            lapBtn.setText(running || elapsed() == 0 ? R.string.lap : R.string.reset);
            lapBtn.setEnabled(canLap);
            Ui.setCircleColor(lapBtn, Ui.color(c, canLap ? R.color.text_primary : R.color.text_hint));
        }

        private void paint() {
            long e = elapsed();
            big.setText(Fmt.stopwatch(e));
            if (liveTime != null) liveTime.setText(Fmt.stopwatch(e - (laps.isEmpty() ? 0 : laps.get(laps.size() - 1))));
        }

        private void rebuildLaps() {
            Context c = getContext();
            lapBox.removeAllViews();
            liveTime = null;
            int n = laps.size();
            long best = Long.MAX_VALUE, worst = 0;
            for (int i = 0; i < n; i++) {
                long d = laps.get(i) - (i > 0 ? laps.get(i - 1) : 0);
                best = Math.min(best, d);
                worst = Math.max(worst, d);
            }
            if (running || elapsed() > 0) {
                LinearLayout live = lapRow(c, c.getString(R.string.lap_n, n + 1), "", R.color.text_primary);
                liveTime = (TextView) live.getChildAt(1);
                lapBox.addView(live);
                lapBox.addView(Ui.hairline(c, 20));
            }
            for (int i = n - 1; i >= 0; i--) {
                long d = laps.get(i) - (i > 0 ? laps.get(i - 1) : 0);
                int col = R.color.text_primary;
                if (n >= 2 && d == best) col = R.color.ok;
                else if (n >= 2 && d == worst) col = R.color.bad;
                lapBox.addView(lapRow(c, c.getString(R.string.lap_n, i + 1), Fmt.stopwatch(d), col));
                lapBox.addView(Ui.hairline(c, 20));
            }
            paint();
        }

        private LinearLayout lapRow(Context c, String name, String time, int colorRes) {
            LinearLayout r = new LinearLayout(c);
            r.setPaddingRelative(Ui.dp(c, 20), Ui.dp(c, 12), Ui.dp(c, 20), Ui.dp(c, 12));
            TextView a = Ui.text(c, name, 17, colorRes);
            r.addView(a, new LayoutParams(0, -2, 1f));
            TextView b = Ui.tabular(Ui.text(c, time, 17, colorRes));
            r.addView(b);
            return r;
        }
    }
}
