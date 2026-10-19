package com.clock.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Privacy policy: what the app stores, which services it contacts, why each permission exists, and how to delete everything. */
public class PrivacyActivity extends BaseActivity {
    static final String ISSUES = "https://github.com/" + Updater.REPO + "/issues";
    static final String SOURCE = "https://github.com/" + Updater.REPO;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        useGroupedBackground();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        Header hd = new Header(this, getString(R.string.privacy_title));
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

        col.addView(Ui.footnote(this, getString(R.string.privacy_updated)));
        String[] titles = getResources().getStringArray(R.array.privacy_titles);
        String[] bodies = getResources().getStringArray(R.array.privacy_bodies);
        for (int i = 0; i < titles.length && i < bodies.length; i++) {
            col.addView(Ui.sectionTitle(this, titles[i]));
            LinearLayout g = Ui.group(this);
            TextView t = Ui.text(this, bodies[i], 15, R.color.text_primary);
            t.setLineSpacing(0f, 1.25f);
            t.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 14));
            t.setTextIsSelectable(true);
            g.addView(t, new LinearLayout.LayoutParams(-1, -2));
            col.addView(g);
            Anim.riseIn(g, 10, Math.min(i, 6) * 40L);
        }

        col.addView(Ui.sectionTitle(this, getString(R.string.privacy_contact)));
        LinearLayout lg = Ui.group(this);
        Ui.addRow(lg, Ui.row(this, R.drawable.ic_shield, R.color.ok, getString(R.string.privacy_issues), null, true,
                v -> open(ISSUES)), true);
        Ui.addRow(lg, Ui.row(this, R.drawable.ic_lock, R.color.info, getString(R.string.privacy_source), null, true,
                v -> open(SOURCE)), true);
        col.addView(lg);
    }

    private void open(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception ignored) {
        }
    }
}
