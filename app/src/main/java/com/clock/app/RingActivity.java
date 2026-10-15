package com.clock.app;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.TimeZone;

/** Full-screen ring screen over the lock screen: big time, pulsing bell, Snooze / Stop and the optional math challenge. */
public class RingActivity extends BaseActivity {
    private String kind = Ring.KIND_ALARM;
    private int id;
    private String label = "";
    private Alarm alarm;
    private TextView clock, sub;
    private LinearLayout actions;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Random rnd = new Random();
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (clock != null && Ring.KIND_ALARM.equals(kind)) {
                clock.setText(Fmt.time(RingActivity.this, System.currentTimeMillis(), TimeZone.getDefault(), false));
            }
            h.postDelayed(this, 1000);
        }
    };
    private final BroadcastReceiver ended = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            finish();
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        read(getIntent());
        if (!RingService.active) {
            finish();
            return;
        }
        build();
        ContextCompat.registerReceiver(this, ended, new IntentFilter(RingService.A_ENDED), ContextCompat.RECEIVER_NOT_EXPORTED);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // the ring must be answered with Stop or Snooze, never dismissed by accident
            }
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        read(intent);
        build();
    }

    private void read(Intent i) {
        kind = i.getStringExtra("kind") == null ? Ring.KIND_ALARM : i.getStringExtra("kind");
        id = i.getIntExtra("id", 0);
        label = i.getStringExtra("label") == null ? "" : i.getStringExtra("label");
        alarm = Ring.KIND_ALARM.equals(kind) ? Alarms.get(this, id) : null;
    }

    private void build() {
        final boolean isAlarm = Ring.KIND_ALARM.equals(kind);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(Ui.dp(this, 24), Ui.dp(this, 56), Ui.dp(this, 24), Ui.dp(this, 36));
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));

        TextView kindText = new TextView(this);
        kindText.setText(isAlarm ? R.string.alarm : R.string.timer);
        kindText.setTextColor(0xFF0A84FF);
        kindText.setTextSize(18);
        kindText.setTypeface(Ui.medium());
        col.addView(kindText);

        clock = new TextView(this);
        clock.setTextColor(Color.WHITE);
        clock.setTypeface(Ui.thin());
        clock.setFontFeatureSettings("tnum");
        clock.setIncludeFontPadding(false);
        clock.setTextSize(isAlarm ? 92 : 44);
        clock.setText(isAlarm ? Fmt.time(this, System.currentTimeMillis(), TimeZone.getDefault(), false) : getString(R.string.timer_done));
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-2, -2);
        cl.topMargin = Ui.dp(this, 18);
        col.addView(clock, cl);

        sub = new TextView(this);
        sub.setTextColor(0xFFAEAEB2);
        sub.setTextSize(22);
        sub.setGravity(Gravity.CENTER);
        sub.setText(label);
        col.addView(sub, new LinearLayout.LayoutParams(-2, -2));

        FrameLayout bell = new FrameLayout(this);
        View pulse1 = pulse(), pulse2 = pulse();
        bell.addView(pulse1, new FrameLayout.LayoutParams(Ui.dp(this, 96), Ui.dp(this, 96), Gravity.CENTER));
        bell.addView(pulse2, new FrameLayout.LayoutParams(Ui.dp(this, 96), Ui.dp(this, 96), Gravity.CENTER));
        ImageView ic = new ImageView(this);
        ic.setImageResource(isAlarm ? R.drawable.ic_alarm : R.drawable.ic_timer);
        ic.setImageTintList(ColorStateList.valueOf(Color.WHITE));
        ic.setBackground(Ui.oval(0xFF0A84FF));
        int p = Ui.dp(this, 22);
        ic.setPadding(p, p, p, p);
        bell.addView(ic, new FrameLayout.LayoutParams(Ui.dp(this, 96), Ui.dp(this, 96), Gravity.CENTER));
        col.addView(bell, new LinearLayout.LayoutParams(0, 0, 1f));
        animate(pulse1, 0);
        animate(pulse2, 900);
        ObjectAnimator shake = ObjectAnimator.ofFloat(ic, "rotation", -12f, 12f);
        shake.setDuration(110);
        shake.setRepeatCount(ValueAnimator.INFINITE);
        shake.setRepeatMode(ValueAnimator.REVERSE);
        shake.start();

        actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        col.addView(actions, new LinearLayout.LayoutParams(-1, -2));
        showButtons(isAlarm);
        setContentView(root);
    }

    private View pulse() {
        View v = new View(this);
        v.setBackground(Ui.oval(0x550A84FF));
        v.setAlpha(0f);
        return v;
    }

    private void animate(final View v, long delay) {
        ObjectAnimator sx = ObjectAnimator.ofFloat(v, "scaleX", 1f, 2.6f);
        ObjectAnimator sy = ObjectAnimator.ofFloat(v, "scaleY", 1f, 2.6f);
        ObjectAnimator al = ObjectAnimator.ofFloat(v, "alpha", 0.8f, 0f);
        for (ObjectAnimator a : new ObjectAnimator[]{sx, sy, al}) {
            a.setDuration(1800);
            a.setStartDelay(delay);
            a.setRepeatCount(ValueAnimator.INFINITE);
            a.setInterpolator(new LinearInterpolator());
            a.start();
        }
    }

    private TextView pill(CharSequence text, int fill, int textColor) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(19);
        t.setTypeface(Ui.medium());
        t.setGravity(Gravity.CENTER);
        t.setTextColor(textColor);
        t.setBackground(Ui.rect(fill, Ui.dpf(this, 100)));
        Ui.press(this, t);
        return t;
    }

    private void showButtons(boolean isAlarm) {
        actions.removeAllViews();
        boolean canSnooze = !isAlarm || alarm == null || alarm.snooze;
        if (canSnooze) {
            TextView s = pill(getString(isAlarm ? R.string.snooze : R.string.plus_one), 0xFF2C2C2E, Color.WHITE);
            s.setOnClickListener(v -> sendService(isAlarm ? RingService.A_SNOOZE : RingService.A_PLUS));
            LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, Ui.dp(this, 62));
            sl.bottomMargin = Ui.dp(this, 14);
            actions.addView(s, sl);
        }
        TextView stop = pill(getString(R.string.stop), 0xFF0A84FF, Color.WHITE);
        stop.setOnClickListener(v -> {
            if (alarm != null && alarm.challenge) showChallenge();
            else sendService(RingService.A_STOP);
        });
        actions.addView(stop, new LinearLayout.LayoutParams(-1, Ui.dp(this, 62)));
    }

    private void showChallenge() {
        actions.removeAllViews();
        final int a = 12 + rnd.nextInt(38), c = 3 + rnd.nextInt(7);
        final int answer = a + c;
        TextView q = new TextView(this);
        q.setText(a + " + " + c + " = ?");
        q.setTextColor(Color.WHITE);
        q.setTextSize(34);
        q.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        q.setGravity(Gravity.CENTER);
        actions.addView(q, new LinearLayout.LayoutParams(-1, -2));
        TextView hint = new TextView(this);
        hint.setText(R.string.challenge_hint);
        hint.setTextColor(0xFFAEAEB2);
        hint.setTextSize(14);
        hint.setGravity(Gravity.CENTER);
        actions.addView(hint, new LinearLayout.LayoutParams(-1, -2));
        List<Integer> opts = new ArrayList<>();
        opts.add(answer);
        while (opts.size() < 4) {
            int d = answer + (rnd.nextInt(11) - 5);
            if (d != answer && d > 0 && !opts.contains(d)) opts.add(d);
        }
        Collections.shuffle(opts, rnd);
        LinearLayout row = new LinearLayout(this);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        for (final int o : opts) {
            TextView t = pill(String.valueOf(o), 0xFF2C2C2E, Color.WHITE);
            t.setOnClickListener(v -> {
                if (o == answer) {
                    sendService(RingService.A_STOP);
                } else {
                    v.animate().translationX(18f).setDuration(60).withEndAction(() -> v.animate().translationX(0f).setDuration(120).start()).start();
                    showChallenge();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(this, 62), 1f);
            lp.setMargins(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0);
            row.addView(t, lp);
        }
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
        rl.topMargin = Ui.dp(this, 20);
        actions.addView(row, rl);
    }

    private void sendService(String action) {
        try {
            startService(new Intent(this, RingService.class).setAction(action));
        } catch (Exception ignored) {
        }
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        h.removeCallbacks(tick);
        h.post(tick);
    }

    @Override
    protected void onPause() {
        h.removeCallbacks(tick);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        try {
            unregisterReceiver(ended);
        } catch (Exception ignored) {
        }
        super.onDestroy();
    }
}
