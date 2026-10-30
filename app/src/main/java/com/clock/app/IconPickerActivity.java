package com.clock.app;

import android.graphics.Outline;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Grid of launcher icon colours; the tapped one becomes the app icon. */
public class IconPickerActivity extends BaseActivity {
    private static final int COLS = 3;
    private LinearLayout grid;
    private int selected;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        useGroupedBackground();
        selected = IconSwitcher.current(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        Header hd = new Header(this, getString(R.string.app_icon));
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
        grid = Ui.group(this);
        grid.setPadding(Ui.dp(this, 8), Ui.dp(this, 14), Ui.dp(this, 8), Ui.dp(this, 8));
        col.addView(grid);
        col.addView(Ui.footnote(this, getString(R.string.app_icon_note)));
        setContentView(root);
        build();
        Anim.riseIn(grid, 14, 0);
    }

    private void build() {
        grid.removeAllViews();
        int n = IconSwitcher.IDS.length;
        LinearLayout row = null;
        for (int i = 0; i < n; i++) {
            if (i % COLS == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                grid.addView(row, new LinearLayout.LayoutParams(-1, -2));
            }
            row.addView(cell(i), new LinearLayout.LayoutParams(0, -2, 1f));
        }
    }

    private View cell(final int i) {
        final int size = Ui.dp(this, 68);
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        cell.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 12));
        ImageView img = new ImageView(this);
        img.setImageResource(IconSwitcher.PREVIEWS[i]);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        img.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), v.getWidth() * 0.225f);
            }
        });
        img.setClipToOutline(true);
        boolean on = i == selected;
        FrameHolder holder = new FrameHolder(this, on);
        holder.addView(img, new LinearLayout.LayoutParams(size, size));
        cell.addView(holder, new LinearLayout.LayoutParams(-2, -2));
        TextView t = Ui.text(this, getString(IconSwitcher.NAMES[i]), 13, on ? R.color.accent : R.color.text_secondary);
        t.setGravity(Gravity.CENTER);
        t.setTypeface(on ? Ui.medium() : android.graphics.Typeface.DEFAULT);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
        tl.topMargin = Ui.dp(this, 8);
        cell.addView(t, tl);
        Ui.press(this, cell);
        cell.setOnClickListener(v -> {
            if (i == selected) return;
            v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            selected = i;
            IconSwitcher.apply(this, i);
            build();
            Anim.pulse(grid.getChildAt(i / COLS) == null ? grid : ((LinearLayout) grid.getChildAt(i / COLS)).getChildAt(i % COLS));
        });
        return cell;
    }

    /** Rounded ring that marks the chosen icon. */
    private static final class FrameHolder extends LinearLayout {
        FrameHolder(android.content.Context c, boolean on) {
            super(c);
            int pad = Ui.dp(c, 4);
            setPadding(pad, pad, pad, pad);
            if (on) {
                android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
                g.setCornerRadius(Ui.dpf(c, 20));
                g.setColor(0);
                g.setStroke(Ui.dp(c, 3), Ui.color(c, R.color.accent));
                setBackground(g);
            }
        }
    }
}
