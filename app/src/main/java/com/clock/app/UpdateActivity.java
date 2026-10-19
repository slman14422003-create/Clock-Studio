package com.clock.app;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.Locale;

/** Checks the GitHub releases of the project and downloads / installs a newer version without leaving the app. */
public class UpdateActivity extends BaseActivity {
    private static final int M_CHECK = 0, M_DOWNLOAD = 1, M_CANCEL = 2, M_INSTALL = 3;

    private TextView status, sub, btn, notes;
    private ProgressBar bar;
    private LinearLayout notesBox;
    private Updater.Info info;
    private Updater.Job job;
    private File ready;
    private int mode = M_CHECK;
    private boolean checking;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        useGroupedBackground();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        Header hd = new Header(this, getString(R.string.upd_title));
        hd.addView(Ui.iconButton(this, R.drawable.ic_back, R.string.back, false, v -> finish()), 0,
                new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40)));
        ((LinearLayout.LayoutParams) hd.getChildAt(0).getLayoutParams()).setMarginEnd(Ui.dp(this, 10));
        root.addView(hd);

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        hd.bindScroll(sv);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 30));
        sv.addView(col, new ScrollView.LayoutParams(-1, -2));
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);

        String ver = "";
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            ver = pi.versionName == null ? "" : pi.versionName;
        } catch (Exception ignored) {
        }
        LinearLayout g = Ui.group(this);
        LinearLayout about = Ui.row(this, 0, 0, getString(R.string.app_name), getString(R.string.upd_current, ver), false, null);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_launcher_foreground);
        logo.setBackground(Ui.rect(Ui.color(this, R.color.accent_soft), Ui.dpf(this, 9)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40));
        lp.setMarginEnd(Ui.dp(this, 12));
        about.addView(logo, 0, lp);
        Ui.addRow(g, about, true);
        col.addView(g);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(Ui.dp(this, 20), Ui.dp(this, 22), Ui.dp(this, 20), Ui.dp(this, 20));
        box.setBackground(Ui.rect(Ui.color(this, R.color.surface), Ui.dpf(this, 22)));
        status = Ui.text(this, "", 19, R.color.text_primary);
        status.setTypeface(Ui.medium());
        status.setGravity(Gravity.CENTER);
        status.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        box.addView(status, new LinearLayout.LayoutParams(-1, -2));
        sub = Ui.text(this, "", 14, R.color.text_secondary);
        sub.setGravity(Gravity.CENTER);
        sub.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, -2);
        sl.topMargin = Ui.dp(this, 6);
        box.addView(sub, sl);
        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(1000);
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.color(this, R.color.accent)));
        bar.setVisibility(View.GONE);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, Ui.dp(this, 6));
        bl.topMargin = Ui.dp(this, 16);
        box.addView(bar, bl);
        btn = new TextView(this);
        btn.setGravity(Gravity.CENTER);
        btn.setTextSize(17);
        btn.setTypeface(Ui.medium());
        btn.setTextColor(0xFFFFFFFF);
        btn.setBackground(Ui.rect(Ui.color(this, R.color.accent), Ui.dpf(this, 100)));
        btn.setMinHeight(Ui.dp(this, 50));
        btn.setPadding(Ui.dp(this, 22), 0, Ui.dp(this, 22), 0);
        btn.setOnClickListener(v -> onButton());
        Ui.press(this, btn);
        LinearLayout.LayoutParams btl = new LinearLayout.LayoutParams(-1, Ui.dp(this, 50));
        btl.topMargin = Ui.dp(this, 18);
        box.addView(btn, btl);
        LinearLayout.LayoutParams boxLp = new LinearLayout.LayoutParams(-1, -2);
        boxLp.setMargins(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), 0);
        col.addView(box, boxLp);

        notesBox = new LinearLayout(this);
        notesBox.setOrientation(LinearLayout.VERTICAL);
        notesBox.setVisibility(View.GONE);
        notesBox.addView(Ui.sectionTitle(this, getString(R.string.upd_notes)));
        LinearLayout ng = Ui.group(this);
        notes = Ui.text(this, "", 15, R.color.text_primary);
        notes.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 14));
        notes.setLineSpacing(0f, 1.2f);
        ng.addView(notes, new LinearLayout.LayoutParams(-1, -2));
        notesBox.addView(ng);
        col.addView(notesBox);

        col.addView(Ui.sectionTitle(this, getString(R.string.upd_source)));
        LinearLayout lg = Ui.group(this);
        Ui.addRow(lg, Ui.row(this, R.drawable.ic_shield, R.color.accent, getString(R.string.upd_open_page), null, true,
                v -> openLink(Updater.PAGE)), true);
        col.addView(lg);
        col.addView(Ui.footnote(this, getString(R.string.upd_footnote)));

        Anim.riseIn(box, 18, 0);
        doCheck();
    }

    private void openLink(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception ignored) {
        }
    }

    private void setMode(int m, int labelRes, Object... args) {
        mode = m;
        btn.setText(getString(labelRes, args));
        btn.setAlpha(1f);
        btn.setEnabled(true);
    }

    private void doCheck() {
        if (checking) return;
        checking = true;
        bar.setVisibility(View.GONE);
        status.setText(R.string.upd_checking);
        sub.setText("");
        btn.setEnabled(false);
        btn.setAlpha(0.5f);
        btn.setText(R.string.upd_checking);
        Updater.check(this, (i, err) -> {
            checking = false;
            if (isFinishing() || isDestroyed()) return;
            if (i == null) {
                info = null;
                status.setText(err == 2 ? R.string.upd_err_none : R.string.upd_err_net);
                sub.setText("");
                setMode(M_CHECK, R.string.upd_check);
                return;
            }
            info = i;
            if (!i.newer) {
                Updater.clean(this);
                status.setText(R.string.upd_latest);
                sub.setText(getString(R.string.upd_current, i.version));
                setMode(M_CHECK, R.string.upd_check);
                return;
            }
            status.setText(getString(R.string.upd_available, i.version));
            if (!i.notes.isEmpty()) {
                notes.setText(i.notes);
                notesBox.setVisibility(View.VISIBLE);
                Anim.riseIn(notesBox, 12, 80);
            }
            if (i.apkUrl == null) {
                sub.setText(R.string.upd_no_apk);
                setMode(M_CHECK, R.string.upd_check);
                return;
            }
            sub.setText(i.size > 0 ? getString(R.string.upd_size, String.format(Locale.US, "%.1f MB", i.size / 1048576.0)) : "");
            setMode(M_DOWNLOAD, R.string.upd_download);
        });
    }

    private void onButton() {
        if (mode == M_CHECK) {
            doCheck();
        } else if (mode == M_DOWNLOAD) {
            startDownload();
        } else if (mode == M_CANCEL) {
            if (job != null) job.cancel();
        } else if (mode == M_INSTALL) {
            install();
        }
    }

    private void startDownload() {
        if (info == null || info.apkUrl == null) return;
        bar.setProgress(0);
        bar.setVisibility(View.VISIBLE);
        status.setText(R.string.upd_downloading);
        setMode(M_CANCEL, R.string.upd_cancel);
        job = Updater.download(this, info, (done, total) -> {
            if (isFinishing() || isDestroyed()) return;
            int per = total > 0 ? (int) (done * 1000L / total) : 0;
            bar.setProgress(per);
            sub.setText(total > 0 ? getString(R.string.upd_progress, per / 10) : "");
        }, (f, err) -> {
            job = null;
            if (isFinishing() || isDestroyed()) return;
            bar.setVisibility(View.GONE);
            if (f != null) {
                ready = f;
                status.setText(R.string.upd_ready);
                sub.setText(R.string.upd_ready_sub);
                setMode(M_INSTALL, R.string.upd_install);
                Anim.popIn(status);
                install();
            } else {
                status.setText(err == 3 ? R.string.upd_cancelled : err == 2 ? R.string.upd_err_verify : R.string.upd_err_net);
                sub.setText("");
                setMode(M_DOWNLOAD, R.string.upd_download);
            }
        });
    }

    private void install() {
        if (ready == null || !ready.exists()) {
            setMode(M_DOWNLOAD, R.string.upd_download);
            return;
        }
        if (!Updater.canInstall(this)) {
            new Dlg(this).setTitle(R.string.upd_perm_title).setMessage(R.string.upd_perm_msg)
                    .setPositiveButton(R.string.upd_perm_open, (d, w) -> Updater.openInstallPermission(this))
                    .setNegativeButton(R.string.cancel, null).show();
            return;
        }
        Updater.install(this, ready);
    }

    @Override
    protected void onDestroy() {
        if (job != null) job.cancel();
        super.onDestroy();
    }
}
