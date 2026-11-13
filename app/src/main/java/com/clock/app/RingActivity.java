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
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.TimeZone;

/** Full-screen ring screen over the lock screen: big time, pulsing bell, Snooze / Stop and the optional math challenge. */
public class RingActivity extends BaseActivity {
    @Override
    protected boolean glassScreen() {
        return false;
    }

    @Override
    protected int themeOverride() {
        return 0;
    }

    private String kind = Ring.KIND_ALARM;
    private int id;
    private String label = "";
    private Alarm alarm;
    private TextView clock, ampm, sub, ringing;
    private LinearLayout actions;
    private RingFx.Slide slide;
    private final long ringStart = System.currentTimeMillis();
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Random rnd = new Random();
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (clock != null && Ring.KIND_ALARM.equals(kind)) {
                Calendar k = Calendar.getInstance();
                clock.setText(Fmt.hm(RingActivity.this, k.get(Calendar.HOUR_OF_DAY), k.get(Calendar.MINUTE)));
                if (ampm != null) ampm.setText(Fmt.ampm(RingActivity.this, k.get(Calendar.HOUR_OF_DAY)));
            }
            if (ringing != null) {
                long sec = Math.max(0, (System.currentTimeMillis() - ringStart) / 1000);
                ringing.setText(getString(R.string.ring_for, (sec / 60) + ":" + (sec % 60 < 10 ? "0" : "") + (sec % 60)));
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
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                | WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (Build.VERSION.SDK_INT >= 28) {
            getWindow().getAttributes().layoutInDisplayCutoutMode = Build.VERSION.SDK_INT >= 30
                    ? WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                    : WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        read(getIntent());
        if (!RingService.active) {
            finish();
            return;
        }
        build();
        hideBars();
        ContextCompat.registerReceiver(this, ended, new IntentFilter(RingService.A_ENDED), ContextCompat.RECEIVER_NOT_EXPORTED);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // the ring must be answered with Stop or Snooze, never dismissed by accident
            }
        });
    }

    /** Immersive: no status bar and no navigation bar (a swipe from the edge shows them for a moment only). */
    private void hideBars() {
        try {
            WindowInsetsControllerCompat c = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
            c.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            c.hide(WindowInsetsCompat.Type.systemBars());
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideBars();
    }

    /** The volume buttons do nothing while it rings, so the alarm cannot be turned down by accident. */
    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        if (k == KeyEvent.KEYCODE_VOLUME_UP || k == KeyEvent.KEYCODE_VOLUME_DOWN || k == KeyEvent.KEYCODE_VOLUME_MUTE) return true;
        return super.dispatchKeyEvent(e);
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

    private int tint() {
        if (!Ring.KIND_ALARM.equals(kind)) return 0xFFFF9500;
        return Alarm.COLORS[alarm != null ? alarm.color : 0];
    }

    private android.graphics.drawable.GradientDrawable glass(float radiusDp) {
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setColor(0x24FFFFFF);
        g.setCornerRadius(Ui.dpf(this, radiusDp));
        g.setStroke(Math.max(1, Ui.dp(this, 1)), 0x33FFFFFF);
        return g;
    }

    private void build() {
        final boolean isAlarm = Ring.KIND_ALARM.equals(kind);
        final int tint = tint();
        FrameLayout root = new FrameLayout(this);
        root.addView(new RingFx.Backdrop(this, tint), new FrameLayout.LayoutParams(-1, -1));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(Ui.dp(this, 24), Ui.dp(this, 64), Ui.dp(this, 24), Ui.dp(this, 34));
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout chip = new LinearLayout(this);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setBackground(glass(100));
        chip.setPaddingRelative(Ui.dp(this, 12), Ui.dp(this, 7), Ui.dp(this, 16), Ui.dp(this, 7));
        ImageView ci = new ImageView(this);
        ci.setImageResource(isAlarm ? R.drawable.ic_alarm : R.drawable.ic_timer);
        ci.setImageTintList(ColorStateList.valueOf(androidx.core.graphics.ColorUtils.blendARGB(tint, Color.WHITE, 0.35f)));
        LinearLayout.LayoutParams cil = new LinearLayout.LayoutParams(Ui.dp(this, 20), Ui.dp(this, 20));
        cil.setMarginEnd(Ui.dp(this, 8));
        chip.addView(ci, cil);
        TextView kindText = new TextView(this);
        kindText.setText(isAlarm ? R.string.alarm : R.string.timer);
        kindText.setTextColor(androidx.core.graphics.ColorUtils.blendARGB(tint, Color.WHITE, 0.35f));
        kindText.setTextSize(16);
        kindText.setTypeface(Ui.medium());
        chip.addView(kindText);
        col.addView(chip, new LinearLayout.LayoutParams(-2, -2));

        if (isAlarm) {
            TextView date = new TextView(this);
            date.setText(Fmt.date(this, System.currentTimeMillis(), "EEEE d MMMM"));
            date.setTextColor(0xB3FFFFFF);
            date.setTextSize(17);
            date.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-2, -2);
            dl.topMargin = Ui.dp(this, 22);
            col.addView(date, dl);
        }

        LinearLayout timeRow = new LinearLayout(this);
        timeRow.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        timeRow.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        clock = new TextView(this);
        clock.setTextColor(Color.WHITE);
        clock.setTypeface(Ui.thin());
        clock.setFontFeatureSettings("tnum");
        clock.setIncludeFontPadding(false);
        clock.setTextSize(isAlarm ? 108 : 46);
        clock.setShadowLayer(Ui.dpf(this, 24), 0, 0, (tint & 0x00FFFFFF) | 0x66000000);
        Calendar now = Calendar.getInstance();
        clock.setText(isAlarm ? Fmt.hm(this, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE)) : getString(R.string.timer_done));
        timeRow.addView(clock);
        ampm = null;
        if (isAlarm) {
            ampm = new TextView(this);
            ampm.setTextColor(0xCCFFFFFF);
            ampm.setTextSize(24);
            ampm.setTypeface(Ui.light());
            ampm.setText(Fmt.ampm(this, now.get(Calendar.HOUR_OF_DAY)));
            LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(-2, -2);
            al.setMarginStart(Ui.dp(this, 8));
            al.bottomMargin = Ui.dp(this, 20);
            timeRow.addView(ampm, al);
        }
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
        tl.topMargin = Ui.dp(this, isAlarm ? 2 : 24);
        col.addView(timeRow, tl);

        sub = new TextView(this);
        sub.setTextColor(Color.WHITE);
        sub.setTextSize(26);
        sub.setTypeface(Ui.medium());
        sub.setGravity(Gravity.CENTER);
        sub.setText(label);
        sub.setSingleLine(true);
        sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
        sub.setVisibility(label.isEmpty() ? View.GONE : View.VISIBLE);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-2, -2);
        sl.topMargin = Ui.dp(this, 8);
        col.addView(sub, sl);

        RingFx.Bell bell = new RingFx.Bell(this, tint, isAlarm ? R.drawable.ic_alarm : R.drawable.ic_timer, isAlarm);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, 0, 1f);
        bl.topMargin = Ui.dp(this, 8);
        bl.bottomMargin = Ui.dp(this, 8);
        col.addView(bell, bl);

        ringing = new TextView(this);
        ringing.setTextColor(0x99FFFFFF);
        ringing.setTextSize(14);
        ringing.setGravity(Gravity.CENTER);
        ringing.setFontFeatureSettings("tnum");
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-2, -2);
        rl.bottomMargin = Ui.dp(this, 16);
        col.addView(ringing, rl);

        actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        col.addView(actions, new LinearLayout.LayoutParams(-1, -2));
        showButtons(isAlarm);
        setContentView(root);
        h.removeCallbacks(tick);
        h.post(tick);
    }

    private TextView glassButton(CharSequence text, float sp, float radiusDp) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(sp);
        t.setTypeface(Ui.medium());
        t.setGravity(Gravity.CENTER);
        t.setTextColor(Color.WHITE);
        t.setBackground(glass(radiusDp));
        Ui.press(this, t);
        return t;
    }

    private void showButtons(boolean isAlarm) {
        actions.removeAllViews();
        boolean canSnooze = !isAlarm || alarm == null || alarm.snooze;
        if (canSnooze) {
            int m = alarm != null && alarm.snoozeMin > 0 ? alarm.snoozeMin : Store.snoozeMin(this);
            TextView s = glassButton(isAlarm ? getString(R.string.ring_snooze_fmt, m) : getString(R.string.plus_one), 18, 100);
            s.setOnClickListener(v -> sendService(isAlarm ? RingService.A_SNOOZE : RingService.A_PLUS));
            LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, Ui.dp(this, 60));
            sl.bottomMargin = Ui.dp(this, 14);
            actions.addView(s, sl);
        }
        slide = new RingFx.Slide(this, tint(), getString(R.string.ring_slide));
        slide.done = () -> {
            if (alarm != null && alarm.challenge) showChallenge();
            else sendService(RingService.A_STOP);
        };
        actions.addView(slide, new LinearLayout.LayoutParams(-1, -2));
    }

    private void showChallenge() {
        actions.removeAllViews();
        final int a = 12 + rnd.nextInt(38), c = 3 + rnd.nextInt(7);
        final int answer = a + c;
        TextView hint = new TextView(this);
        hint.setText(R.string.ring_solve);
        hint.setTextColor(0xB3FFFFFF);
        hint.setTextSize(15);
        hint.setGravity(Gravity.CENTER);
        actions.addView(hint, new LinearLayout.LayoutParams(-1, -2));
        TextView q = new TextView(this);
        q.setText(a + " + " + c + " = ?");
        q.setTextColor(Color.WHITE);
        q.setTextSize(46);
        q.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        q.setGravity(Gravity.CENTER);
        q.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        LinearLayout.LayoutParams ql = new LinearLayout.LayoutParams(-1, -2);
        ql.topMargin = Ui.dp(this, 6);
        ql.bottomMargin = Ui.dp(this, 14);
        actions.addView(q, ql);
        List<Integer> opts = new ArrayList<>();
        opts.add(answer);
        while (opts.size() < 4) {
            int d = answer + (rnd.nextInt(11) - 5);
            if (d != answer && d > 0 && !opts.contains(d)) opts.add(d);
        }
        Collections.shuffle(opts, rnd);
        for (int r = 0; r < 2; r++) {
            LinearLayout row = new LinearLayout(this);
            row.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
            for (int k = 0; k < 2; k++) {
                final int o = opts.get(r * 2 + k);
                TextView t = glassButton(String.valueOf(o), 26, 22);
                t.setOnClickListener(v -> {
                    if (o == answer) {
                        sendService(RingService.A_STOP);
                    } else {
                        v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                        v.animate().translationX(18f).setDuration(60).withEndAction(() -> v.animate().translationX(0f).setDuration(120).start()).start();
                        showChallenge();
                    }
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(this, 64), 1f);
                lp.setMargins(Ui.dp(this, 5), Ui.dp(this, 5), Ui.dp(this, 5), Ui.dp(this, 5));
                row.addView(t, lp);
            }
            actions.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
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
