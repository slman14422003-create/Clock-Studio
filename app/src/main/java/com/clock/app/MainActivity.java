package com.clock.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.splashscreen.SplashScreen;

/** Clock, Alarms, Timers and Tasks live here as four tabs; Settings is its own screen. */
public class MainActivity extends BaseActivity implements TabView.Host {
    static final String A_NEW_ALARM = "app.clock.action.NEW_ALARM";
    static final String A_NEW_TIMER = "app.clock.action.NEW_TIMER";
    static final String A_NEW_TASK = "app.clock.action.NEW_TASK";

    private final TabView[] tabs = new TabView[4];
    private NavBar nav;
    private LinearLayout rootView;
    private boolean revealed;
    private int tab = NavBar.CLOCK;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private OnBackPressedCallback backCb;
    private LinearLayout pill;
    private TextView pillMsg;
    private Runnable undoAction;
    private final Runnable hideUndo = this::hidePill;
    private final Runnable onBus = this::refreshAll;
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (tabs[tab] != null) tabs[tab].tick();
            ui.postDelayed(this, 1000 - System.currentTimeMillis() % 1000);
        }
    };

    private final ActivityResultLauncher<String> notifPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> { });

    private final ActivityResultLauncher<Intent> cityLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), r -> {
                Intent d = r.getData();
                if (r.getResultCode() == RESULT_OK && d != null && d.getStringExtra("zone") != null) {
                    WorldClockView.addZone(this, d.getStringExtra("zone"));
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen splash = SplashScreen.installSplashScreen(this);
        splash.setOnExitAnimationListener(provider -> {
            try {
                final View sv = provider.getView();
                View ic = provider.getIconView();
                long wait = 0;
                try {
                    long end = provider.getIconAnimationStartMillis() + provider.getIconAnimationDurationMillis();
                    wait = Math.max(0L, Math.min(650L, end - System.currentTimeMillis()));
                } catch (Throwable ignored) {
                }
                final long delay = wait;
                reveal(delay + 90);
                if (ic != null) {
                    ic.animate().setStartDelay(delay).scaleX(1.7f).scaleY(1.7f).alpha(0f).setDuration(440)
                            .setInterpolator(Anim.IN).start();
                }
                if (sv != null) {
                    sv.animate().setStartDelay(delay + 90).alpha(0f).setDuration(380).setInterpolator(Anim.OUT)
                            .withEndAction(provider::remove).start();
                } else {
                    provider.remove();
                }
            } catch (Throwable t) {
                reveal(0);
                try {
                    provider.remove();
                } catch (Throwable ignored) {
                }
            }
        });
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        rootView = root;
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.color(this, R.color.bg));
        FrameLayout stage = new FrameLayout(this);
        root.addView(stage, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
        // the app opens like iOS: the content settles from slightly zoomed-in once the splash lets go
        root.setAlpha(0f);
        root.setScaleX(1.05f);
        root.setScaleY(1.05f);
        ui.postDelayed(() -> reveal(0), 1600);

        tabs[0] = new WorldClockView(this, this);
        tabs[1] = new AlarmsView(this, this);
        tabs[2] = new TimersView(this, this);
        tabs[3] = new TasksView(this, this);
        for (TabView t : tabs) stage.addView(t, new FrameLayout.LayoutParams(-1, -1));

        pill = new LinearLayout(this);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setBackground(Ui.rect(0xF0262628, Ui.dpf(this, 100)));
        pill.setPadding(Ui.dp(this, 18), Ui.dp(this, 11), Ui.dp(this, 18), Ui.dp(this, 11));
        pill.setElevation(Ui.dp(this, 6));
        pillMsg = Ui.text(this, "", 15, R.color.text_primary);
        pillMsg.setTextColor(0xFFFFFFFF);
        pill.addView(pillMsg);
        TextView act = Ui.text(this, getString(R.string.undo), 15, R.color.text_primary);
        act.setTextColor(0xFF5CC0FF);
        act.setTypeface(Ui.medium());
        act.setPadding(Ui.dp(this, 16), 0, 0, 0);
        act.setOnClickListener(v -> {
            Runnable r = undoAction;
            hidePill();
            if (r != null) r.run();
        });
        pill.addView(act);
        pill.setVisibility(View.GONE);
        FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        pl.bottomMargin = Ui.dp(this, 10);
        stage.addView(pill, pl);

        nav = NavBar.attach(this, root, NavBar.CLOCK, idx -> {
            if (idx == NavBar.SETTINGS) startActivity(new Intent(this, SettingsActivity.class));
            else showTab(idx);
        });
        nav.setOnReselect(idx -> {
            if (idx < 4) tabs[idx].scrollToTop();
        });

        backCb = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                showTab(NavBar.CLOCK);
            }
        };
        getOnBackPressedDispatcher().addCallback(this, backCb);

        showTab(NavBar.CLOCK, false);
        if (savedInstanceState == null) handle(getIntent());

        if (Build.VERSION.SDK_INT >= 33 && !Store.askedNotifications(this)
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            Store.setAskedNotifications(this);
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    /** Fades the content in and lets the tab bar rise with a soft spring. Runs once. */
    private void reveal(long delay) {
        if (revealed || rootView == null) return;
        revealed = true;
        rootView.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(delay).setDuration(560)
                .setInterpolator(Anim.OUT).start();
        if (nav != null) {
            View bar = nav.view();
            bar.setTranslationY(Ui.dp(this, 90));
            bar.animate().translationY(0f).setStartDelay(delay + 120).setDuration(720).setInterpolator(Anim.SPRING).start();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handle(intent);
    }

    private void handle(Intent in) {
        if (in == null) return;
        String a = in.getAction();
        if (A_NEW_ALARM.equals(a)) {
            showTab(NavBar.ALARMS);
            editAlarm(0);
        } else if (A_NEW_TIMER.equals(a)) {
            showTab(NavBar.TIMERS);
        } else if (A_NEW_TASK.equals(a)) {
            showTab(NavBar.TASKS);
            editTask(0);
        } else if (in.hasExtra("tab")) {
            int t = in.getIntExtra("tab", NavBar.CLOCK);
            if (t >= 0 && t <= NavBar.TASKS) showTab(t);
        }
        in.setAction(null);
        in.removeExtra("tab");
    }

    private void showTab(int i) {
        showTab(i, true);
    }

    private void showTab(int i, boolean animate) {
        int prev = tab;
        tab = i;
        for (int k = 0; k < tabs.length; k++) tabs[k].setVisibility(k == i ? View.VISIBLE : View.GONE);
        tabs[i].refresh();
        tabs[i].animate().cancel();
        if (animate && prev != i) {
            float dir = i > prev ? 1f : -1f;
            tabs[i].setAlpha(0f);
            tabs[i].setTranslationX(dir * Ui.dp(this, 26));
            tabs[i].animate().alpha(1f).translationX(0f).setStartDelay(0).setDuration(400).setInterpolator(Anim.OUT).start();
        } else {
            tabs[i].setAlpha(1f);
            tabs[i].setTranslationX(0f);
        }
        nav.render(i);
        backCb.setEnabled(i != NavBar.CLOCK);
    }

    private void refreshAll() {
        for (TabView t : tabs) t.refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Bus.listen(onBus);
        refreshAll();
        PrayerTimes.autoRefresh(this, null);
        ui.removeCallbacks(ticker);
        ui.post(ticker);
    }

    @Override
    protected void onPause() {
        Bus.unlisten(onBus);
        ui.removeCallbacks(ticker);
        super.onPause();
    }

    // ---- TabView.Host

    @Override
    public void undo(CharSequence message, Runnable undo) {
        undoAction = undo;
        pillMsg.setText(message);
        pill.animate().cancel();
        pill.setVisibility(View.VISIBLE);
        pill.setAlpha(0f);
        pill.setTranslationY(Ui.dp(this, 36));
        pill.setScaleX(0.92f);
        pill.setScaleY(0.92f);
        pill.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f).setStartDelay(0).setDuration(560)
                .setInterpolator(Anim.SPRING).start();
        ui.removeCallbacks(hideUndo);
        ui.postDelayed(hideUndo, 4500);
    }

    private void hidePill() {
        undoAction = null;
        ui.removeCallbacks(hideUndo);
        if (pill.getVisibility() != View.VISIBLE) return;
        pill.animate().cancel();
        pill.animate().alpha(0f).translationY(Ui.dp(this, 20)).setStartDelay(0).setDuration(220)
                .setInterpolator(Anim.IN).withEndAction(() -> pill.setVisibility(View.GONE)).start();
    }

    @Override
    public void editAlarm(int id) {
        Ui.openSheet(this, new Intent(this, AlarmEditActivity.class).putExtra("id", id));
    }

    @Override
    public void editTask(int id) {
        Ui.openSheet(this, new Intent(this, TaskEditActivity.class).putExtra("id", id));
    }

    @Override
    public void pickCity() {
        cityLauncher.launch(new Intent(this, CityPickerActivity.class));
    }
}
