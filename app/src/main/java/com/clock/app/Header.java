package com.clock.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

/**
 * iOS large-title header: big bold title and subtitle on the start side, round buttons on the end side.
 * When a list is bound the large title shrinks while it scrolls and a hairline appears underneath, like iOS.
 */
final class Header extends LinearLayout {
    final TextView title;
    final TextView subtitle;
    private final LinearLayout actions;
    private final Paint linePaint = new Paint();
    private float collapse;
    private boolean hasSubtitle;

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
        hasSubtitle = s != null && s.length() > 0;
        subtitle.setText(s);
        subtitle.setVisibility(hasSubtitle ? VISIBLE : GONE);
        subtitle.setAlpha(1f - collapse);
    }

    void bindScroll(RecyclerView rv) {
        rv.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView r, int dx, int dy) {
                setCollapse(r.computeVerticalScrollOffset() / Ui.dpf(getContext(), 56));
            }
        });
    }

    void bindScroll(ScrollView sv) {
        sv.setOnScrollChangeListener((v, x, y, ox, oy) -> setCollapse(y / Ui.dpf(getContext(), 56)));
    }

    private void setCollapse(float f) {
        f = Math.max(0f, Math.min(1f, f));
        if (f == collapse) return;
        collapse = f;
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        title.setPivotX(rtl ? title.getWidth() : 0f);
        title.setPivotY(title.getHeight() / 2f);
        float s = 1f - 0.28f * f;
        title.setScaleX(s);
        title.setScaleY(s);
        subtitle.setAlpha(1f - f);
        invalidate();
    }

    @Override
    protected void dispatchDraw(Canvas cv) {
        super.dispatchDraw(cv);
        if (collapse > 0.05f) {
            linePaint.setColor(Ui.alpha(Ui.color(getContext(), R.color.stroke), Math.round(255 * collapse)));
            cv.drawRect(0, getHeight() - Ui.hairPx(getContext()), getWidth(), getHeight(), linePaint);
        }
    }
}
