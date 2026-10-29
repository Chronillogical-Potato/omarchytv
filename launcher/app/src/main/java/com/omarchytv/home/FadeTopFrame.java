package com.omarchytv.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import androidx.recyclerview.widget.RecyclerView;

/**
 * The grid's own area below the title line. Leanback-style fixed focus line: the focused row always sits where
 * the dock sits ({@link #focusLine()}); once the grid is scrolled, everything above a short strip over that line
 * is clipped and the top {@link #FADE_DP} of the strip fades out, so scrolled rows never draw over the logo,
 * prompt or panes. Not scrolled (dock in view) = plain drawing, no layer, zero cost.
 */
public class FadeTopFrame extends FrameLayout {

    static final int FADE_DP = 48;
    /** Visible strip above the focus line: room for a section prompt ("> Omarchy", "> TV") or the row above. */
    static final int STRIP_DP = 88;
    private final Paint mask = new Paint();
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private final int fadePx;
    private int maskFor = -1;
    private float maskTop = Float.NaN;
    private int minTop;
    private String sticky;

    public FadeTopFrame(Context c, AttributeSet a) {
        super(c, a);
        density = getResources().getDisplayMetrics().density;
        fadePx = Math.round(FADE_DP * density);
        mask.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
        Typeface tf;
        try {
            tf = CastUi.font(c);
        } catch (Exception e) {
            tf = Typeface.MONOSPACE;
        }
        label.setTypeface(tf);
        label.setTextSize(16 * density);
        setClipChildren(true);
        setWillNotDraw(false);
    }

    private RecyclerView grid() {
        return getChildCount() > 0 && getChildAt(0) instanceof RecyclerView ? (RecyclerView) getChildAt(0) : null;
    }

    /** Top of the focused row in this view's (and the grid's) coordinates: the dock row's resting place. */
    int focusLine() {
        RecyclerView g = grid();
        return g == null ? 0 : g.getPaddingTop() + Math.round(6 * density);
    }

    /** Never let the strip reach above this y (the bottom of the panes / header, in this view's coordinates). */
    void setMinTop(int px) {
        if (px != minTop) {
            minTop = px;
            invalidate();
        }
    }

    /** Section prompt pinned at the top of the strip when the section's own prompt has scrolled away (null = none). */
    void setSticky(String text, int color) {
        label.setColor(color);
        if (text == null ? sticky != null : !text.equals(sticky)) {
            sticky = text;
            invalidate();
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        RecyclerView g = grid();
        int scrolled = g != null ? g.computeVerticalScrollOffset() : 0;
        if (scrolled <= 0 || getWidth() == 0) {
            super.dispatchDraw(canvas);
            return;
        }
        float top = Math.max(minTop, focusLine() - STRIP_DP * density);
        // The fade grows with the first 48dp of scroll, so it never pops in; the clip is there from the first pixel.
        float k = Math.min(1f, scrolled / (float) fadePx);
        int alpha = Math.round(255 * k);
        if (maskFor != alpha || maskTop != top) {
            mask.setShader(new LinearGradient(0, top, 0, top + fadePx, (alpha << 24), 0x00000000, Shader.TileMode.CLAMP));
            maskFor = alpha;
            maskTop = top;
        }
        int save = canvas.saveLayer(0, top, getWidth(), getHeight(), null);
        canvas.clipRect(0, top, getWidth(), getHeight());
        super.dispatchDraw(canvas);
        canvas.drawRect(0, top, getWidth(), top + fadePx, mask);
        canvas.restoreToCount(save);
        if (sticky != null) {
            canvas.drawText(sticky, getPaddingLeft() + 14 * density, top + 22 * density, label);
        }
    }
}
