package com.clock.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

/** Swipe a row either way to delete it: red background with a trash icon, like iOS. */
final class SwipeDelete extends ItemTouchHelper.SimpleCallback {
    interface Listener {
        void onDelete(int position);
    }

    interface Filter {
        boolean canSwipe(int position);
    }

    private final Listener listener;
    private final Drawable icon;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int size;
    private Filter filter;

    SwipeDelete(Context c, Listener l) {
        super(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT);
        listener = l;
        paint.setColor(Ui.color(c, R.color.bad));
        Drawable d = ContextCompat.getDrawable(c, R.drawable.ic_delete);
        icon = d == null ? null : d.mutate();
        if (icon != null) icon.setTint(Color.WHITE);
        size = Ui.dp(c, 24);
    }

    SwipeDelete filter(Filter f) {
        filter = f;
        return this;
    }

    @Override
    public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh, @NonNull RecyclerView.ViewHolder target) {
        return false;
    }

    @Override
    public int getSwipeDirs(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh) {
        int pos = vh.getBindingAdapterPosition();
        if (pos < 0 || (filter != null && !filter.canSwipe(pos))) return 0;
        return super.getSwipeDirs(rv, vh);
    }

    @Override
    public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int direction) {
        int pos = vh.getBindingAdapterPosition();
        if (pos >= 0) listener.onDelete(pos);
    }

    @Override
    public void onChildDraw(@NonNull Canvas cv, @NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh,
                            float dX, float dY, int state, boolean active) {
        View v = vh.itemView;
        if (dX != 0 && icon != null) {
            float top = v.getTop();
            float bottom = v.getBottom();
            if (dX > 0) {
                cv.drawRect(v.getLeft(), top, v.getLeft() + dX, bottom, paint);
                int cx = v.getLeft() + size;
                int cy = (int) ((top + bottom) / 2);
                icon.setBounds(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2);
                if (dX > size * 2) icon.draw(cv);
            } else {
                cv.drawRect(v.getRight() + dX, top, v.getRight(), bottom, paint);
                int cx = v.getRight() - size;
                int cy = (int) ((top + bottom) / 2);
                icon.setBounds(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2);
                if (-dX > size * 2) icon.draw(cv);
            }
        }
        super.onChildDraw(cv, rv, vh, dX, dY, state, active);
    }
}
