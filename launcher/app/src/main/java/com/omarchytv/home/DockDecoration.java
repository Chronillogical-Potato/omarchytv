package com.omarchytv.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Draws the dock as a dark Hyprland-style window behind the first row, and a Walker-style launcher
 * header (search field with prompt and app count) above the app grid. Pure canvas drawing: no views,
 * nothing focusable, so D-pad navigation is unchanged.
 */
public class DockDecoration extends RecyclerView.ItemDecoration {

    private final int dockSize;
    /** Number of app items; positions after it belong to the "> tv" section. */
    int appCount = Integer.MAX_VALUE;
    /** Blinking cursor after the "> Apps" header while the dock or the app grid has focus. */
    boolean cursorOn;
    private final String header;
    private final float density;
    private final Paint band = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private final Paint walkerBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint walkerEdge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint prompt = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final String hintText;
    private final RectF walker = new RectF();
    static final int WALKER_DP = 58;

    DockDecoration(Context ctx, int dockSize, float density) {
        this.dockSize = dockSize;
        this.density = density;
        Typeface tf;
        try {
            tf = CastUi.font(ctx);
        } catch (Exception e) {
            tf = Typeface.MONOSPACE;
        }
        int t = LauncherConfig.get(ctx).transparency;
        walkerBg.setColor(Surfaces.surface(t));
        walkerEdge.setStyle(Paint.Style.STROKE);
        walkerEdge.setStrokeWidth(Math.max(1f, density));   // hairline; the accent is kept for the prompt and focus
        walkerEdge.setColor(Theme.m(0xFF292E42));
        prompt.setTypeface(tf);
        prompt.setTextSize(16 * density);
        prompt.setColor(LauncherConfig.get(ctx).accent);   // "> apps" prompt in the accent colour
        hint.setTypeface(tf);
        hint.setTextSize(14 * density);
        hint.setColor(Theme.m(0xFF7F88B3));   // text_dim (>= 4.5:1)
        // Subtle shadow: these prompts sit straight on the (scrimmed) wallpaper.
        prompt.setShadowLayer(3 * density, 0, density, 0x99000000);
        hint.setShadowLayer(3 * density, 0, density, 0x99000000);
        hintText = ctx.getString(R.string.walker_hint);
        header = ctx.getString(R.string.apps_prompt);
        band.setColor(0);                       // no big band behind the dock: just the hairline window frame
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(Math.max(1f, density));
        edge.setColor(Theme.cur().inactiveBorder);   // Hyprland col.inactive_border
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent,
                               @NonNull RecyclerView.State state) {
        int pos = parent.getChildAdapterPosition(view);
        if (pos >= appCount) return;
        if (pos >= 0 && pos < dockSize) {
            outRect.top = (int) (6 * density);
            outRect.bottom = (int) (12 * density);
        } else if (pos >= dockSize && pos < 2 * dockSize) {
            outRect.top = (int) (WALKER_DP * density);   // room for the Walker header
        }
    }

    @Override
    public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        drawWalker(c, parent);
        boolean any = false;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            int pos = parent.getChildAdapterPosition(child);
            if (pos < 0 || pos >= dockSize) continue;
            View card = child.findViewById(R.id.card);
            if (card == null) continue;
            float l = child.getLeft() + card.getLeft();
            float t = child.getTop() + card.getTop() + child.getTranslationY();
            float r = l + card.getWidth();
            float b = t + card.getHeight();
            if (!any) {
                rect.set(l, t, r, b);
                any = true;
            } else {
                rect.union(l, t, r, b);
            }
        }
        if (!any) return;
        float pad = 14 * density;
        rect.inset(-pad, -pad);
        float radius = 0f;
        c.drawRoundRect(rect, radius, radius, edge);
        // "> Apps" section header: always drawn just above the dock window, a divider like "> Omarchy" / "> TV".
        float hx = rect.left + 14 * density;
        float base = rect.top - 12 * density;
        c.drawText(header, hx, base, prompt);
        if (cursorOn) {
            float cx = hx + prompt.measureText(header) + 6 * density;
            c.drawRect(cx, base - 13 * density, cx + 8 * density, base + 3 * density, prompt);
        }
    }

    private void drawWalker(Canvas c, RecyclerView parent) {
        RecyclerView.Adapter<?> a = parent.getAdapter();
        int count = Math.min(appCount, a == null ? 0 : a.getItemCount());
        if (count <= dockSize) return;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (parent.getChildAdapterPosition(child) != dockSize) continue;
            float top = child.getTop() - WALKER_DP * density + 10 * density;
            float h = 38 * density;
            walker.set(parent.getPaddingLeft() + 14 * density, top, parent.getWidth() - parent.getPaddingRight() - 14 * density, top + h);
            c.drawRect(walker, walkerBg);
            c.drawRect(walker, walkerEdge);
            float base = walker.centerY() + 5 * density;
            float x = walker.left + 14 * density;
            c.drawText(">", x, base, prompt);
            String text = String.format(java.util.Locale.ROOT, hintText, count - dockSize);
            c.drawText(text, x + 22 * density, base, hint);
            return;
        }
    }
}
