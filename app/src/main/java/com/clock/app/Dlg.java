package com.clock.app;

import android.content.Context;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

/** Dialog builder: option lists are drawn as an iOS list (17sp rows, hairlines, blue check on the current choice). */
public class Dlg extends AlertDialog.Builder {
    private AlertDialog dlg;

    public Dlg(Context context) {
        super(context);
    }

    @Override
    public AlertDialog create() {
        final AlertDialog d = super.create();
        dlg = d;
        return d;
    }

    /** Labels starting with the check mark character mark the current choice. */
    @Override
    public AlertDialog.Builder setItems(CharSequence[] items, final DialogInterface.OnClickListener listener) {
        final Context c = getContext();
        ScrollView sv = new ScrollView(c);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, Ui.dp(c, 4), 0, Ui.dp(c, 10));
        sv.addView(list, new ViewGroup.LayoutParams(-1, -2));
        for (int i = 0; i < items.length; i++) {
            final int which = i;
            String label = String.valueOf(items[i]);
            boolean on = false;
            if (label.startsWith("\u2713")) {
                on = true;
                label = label.substring(1);
            }
            label = label.trim();
            LinearLayout row = new LinearLayout(c);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(Ui.dp(c, 54));
            row.setPaddingRelative(Ui.dp(c, 22), Ui.dp(c, 8), Ui.dp(c, 22), Ui.dp(c, 8));
            TextView t = new TextView(c);
            t.setText(label);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
            t.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
            t.setTextColor(Ui.color(c, R.color.text_primary));
            row.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
            if (on) {
                ImageView ck = new ImageView(c);
                ck.setImageResource(R.drawable.ic_check);
                ck.setImageTintList(ColorStateList.valueOf(Ui.color(c, R.color.accent)));
                row.addView(ck, new LinearLayout.LayoutParams(Ui.dp(c, 22), Ui.dp(c, 22)));
            }
            if (i < items.length - 1) {
                GradientDrawable line = new GradientDrawable();
                line.setColor(Ui.color(c, R.color.stroke_soft));
                LayerDrawable ld = new LayerDrawable(new android.graphics.drawable.Drawable[]{line});
                ld.setLayerGravity(0, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL);
                ld.setLayerHeight(0, Ui.hairPx(c));
                ld.setLayerInsetStart(0, Ui.dp(c, 22));
                row.setBackground(new RippleDrawable(ColorStateList.valueOf(Ui.color(c, R.color.ripple)), ld, null));
            } else {
                row.setBackground(new RippleDrawable(ColorStateList.valueOf(Ui.color(c, R.color.ripple)), null, new ColorDrawable(Color.WHITE)));
            }
            row.setOnClickListener(v -> {
                if (dlg != null) dlg.dismiss();
                if (listener != null) listener.onClick(dlg, which);
            });
            list.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
        return setView(sv);
    }

    /** Convenience: pick one of {@code labels}; the current one is ticked. */
    static void choose(Context c, int titleRes, String[] labels, int current, Ui.IntCb cb) {
        CharSequence[] items = new CharSequence[labels.length];
        for (int i = 0; i < labels.length; i++) items[i] = (i == current ? "\u2713 " : "") + labels[i];
        new Dlg(c).setTitle(titleRes).setItems(items, (d, w) -> cb.on(w)).setNegativeButton(R.string.cancel, null).show();
    }
}
