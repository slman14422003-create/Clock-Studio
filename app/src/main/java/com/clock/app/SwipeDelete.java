package com.clock.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.view.HapticFeedbackConstants;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Swipe a row either way to delete it (red background with a trash icon, like iOS). With a {@link Mover} a long press
 * also lifts the row so it can be dragged to a new place.
 */
final class SwipeDelete extends ItemTouchHelper.SimpleCallback {
    interface Listener {
        void onDelete(int position);
    }

    interface Filter {
        boolean canSwipe(int position);
    }

    interface Mover {
        boolean move(int from, int to);
    }

    private final Listener listener;
    private final Drawable icon;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int size;
    private Filter filter;
    private Mover mover;

    SwipeDelete(Context c, Listener l) {
        super(ItemTouchHelper.UP | ItemTouchHelper.DOWN, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT);
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

    SwipeDelete mover(Mover m) {
        mover = m;
        return this;
    }

    @Override
    public boolean isLongPressDragEnabled() {
        return mover != null;
    }

    @Override
    public int getDragDirs(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh) {
        int pos = vh.getBindingAdapterPosition();
        if (mover == null || pos < 0 || (filter != null && !filter.canSwipe(pos))) return 0;
        return super.getDragDirs(rv, vh);
    }

    @Override
    public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh, @NonNull RecyclerView.ViewHolder target) {
        if (mover == null) return false;
        int from = vh.getBindingAdapterPosition();
        int to = target.getBindingAdapterPosition();
        if (from < 0 || to < 0 || (filter != null && !filter.canSwipe(to))) return false;
        return mover.move(from, to);
    }

    @Override
    public int getSwipeDirs(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh) {
        int pos = vh.getBindingAdapterPosition();
        if (pos < 0 || (filter != null && !filter.canSwipe(pos))) return 0;
        return super.getSwipeDirs(rv, vh);
    }

    @Override
    public void onSelectedChanged(RecyclerView.ViewHolder vh, int state) {
        super.onSelectedChanged(vh, state);
        if (state == ItemTouchHelper.ACTION_STATE_DRAG && vh != null) {
            View v = vh.itemView;
            if (Store.haptics(v.getContext())) v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            v.animate().scaleX(1.03f).scaleY(1.03f).setDuration(420).setInterpolator(Anim.SPRING).start();
            v.setElevation(Ui.dpf(v.getContext(), 10));
        }
    }

    @Override
    public void clearView(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh) {
        super.clearView(rv, vh);
        vh.itemView.animate().scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(Anim.OUT).start();
        vh.itemView.setElevation(0f);
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
        if (state == ItemTouchHelper.ACTION_STATE_SWIPE && dX != 0 && icon != null) {
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
