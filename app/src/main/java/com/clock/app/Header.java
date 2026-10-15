package com.clock.app;

import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

/** iOS large-title header: big bold title and subtitle on the start side, round buttons on the end side. */
final class Header extends LinearLayout {
    final TextView title;
    final TextView subtitle;
    private final LinearLayout actions;

    Header(Context c, CharSequence t) {
        super(c);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setMinimumHeight(Ui.dp(c, 64));
        setPaddingRelative(Ui.dp(c, 16), Ui.dp(c, 8), Ui.dp(c, 16), Ui.dp(c, 6));
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(VERTICAL);
        title = Ui.text(c, t, 32, R.color.text_primary);
        title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(title);
        subtitle = Ui.text(c, "", 13, R.color.text_secondary);
        subtitle.setSingleLine(true);
        subtitle.setVisibility(GONE);
        col.addView(subtitle);
        addView(col, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        actions = new LinearLayout(c);
        actions.setOrientation(HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        addView(actions, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
    }

    ImageButton addButton(int icon, int desc, boolean accent, View.OnClickListener l) {
        Context c = getContext();
        ImageButton b = Ui.iconButton(c, icon, desc, accent, l);
        LayoutParams lp = new LayoutParams(Ui.dp(c, 40), Ui.dp(c, 40));
        lp.setMarginStart(Ui.dp(c, 8));
        actions.addView(b, lp);
        return b;
    }

    void setSubtitle(CharSequence s) {
        subtitle.setText(s);
        subtitle.setVisibility(s == null || s.length() == 0 ? GONE : VISIBLE);
    }
}
