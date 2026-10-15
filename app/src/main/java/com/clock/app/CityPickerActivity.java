package com.clock.app;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import java.util.List;
import java.util.TimeZone;

/** Search a city (or any tz database zone) and add it to the world clock. */
public class CityPickerActivity extends BaseActivity {
    private LinearLayout results;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        useGroupedBackground();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.addView(Ui.sheetBar(this, getString(R.string.add_city), getString(R.string.cancel), v -> finish(), "", null));
        EditText q = new EditText(this);
        q.setHint(R.string.search_city);
        q.setSingleLine(true);
        q.setInputType(InputType.TYPE_CLASS_TEXT);
        q.setBackground(Ui.rect(Ui.color(this, R.color.neutral_soft), Ui.dpf(this, 12)));
        q.setTextColor(Ui.color(this, R.color.text_primary));
        q.setHintTextColor(Ui.color(this, R.color.text_hint));
        q.setTextSize(16);
        q.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        q.setPaddingRelative(Ui.dp(this, 16), Ui.dp(this, 11), Ui.dp(this, 16), Ui.dp(this, 11));
        LinearLayout.LayoutParams ql = new LinearLayout.LayoutParams(-1, -2);
        ql.setMargins(Ui.dp(this, 16), Ui.dp(this, 6), Ui.dp(this, 16), Ui.dp(this, 10));
        root.addView(q, ql);
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        sv.addView(results, new ScrollView.LayoutParams(-1, -2));
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
        q.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b2, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b2, int c) { }

            @Override
            public void afterTextChanged(Editable s) {
                fill(s.toString());
            }
        });
        fill("");
    }

    private void fill(String query) {
        results.removeAllViews();
        List<Cities.City> list = Cities.search(query);
        LinearLayout g = Ui.group(this);
        long now = System.currentTimeMillis();
        int n = Math.min(list.size(), 80);
        for (int i = 0; i < n; i++) {
            final Cities.City c = list.get(i);
            TimeZone tz = TimeZone.getTimeZone(c.zone);
            LinearLayout row = Ui.row(this, 0, 0, c.name(), Fmt.time(this, now, tz, false), false, v -> {
                setResult(RESULT_OK, new Intent().putExtra("zone", c.zone));
                finish();
            });
            Ui.addRow(g, row, false);
        }
        if (n == 0) g.addView(Ui.text(this, getString(R.string.no_results), 16, R.color.text_secondary));
        results.addView(g);
    }
}
