package com.omarchytv.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import java.util.Random;

/**
 * The home-screen and Cinemarchy wordmark: the pixel ASCII wordmark chosen by config "logo"
 * (default "omarchy-tv": Omarchy's logo.txt plus a matching "TV", or "cinemarchy": 35mm film-reel + "CINE"
 * in accent gradient + Omarchy's "MARCHY" in foreground), drawn as exact rectangles so it is sharp at 1080p and 4K.
 * {@link #reveal()} plays a short "decrypt": cells scramble in comment blue and lock into blocks left to right (~650 ms, then static).
 */
public class WordmarkView extends View {

    static final String TEXT = "OMARCHY TV";
    private static final long REVEAL_MS = 650;
    /** Target logo width in dp; cell size follows from the art's column count. */
    private static final float WIDTH_DP = 250f;

    private final String[] lines;
    private final int cols;
    private final int split;
    private final boolean prefixAccent;
    private final Paint word = new Paint();
    private final Paint accent = new Paint();
    private final Paint shadow = new Paint();
    private final Paint scramble = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float cellW;
    private final float[][] settle;
    private final Random random = new Random();
    private long revealStart = -1;
    private boolean continuousShimmer = false;
    private final Paint shimmerPaint = new Paint();

    public WordmarkView(Context context, AttributeSet attrs) {
        this(context, attrs, LauncherConfig.get(context).logo, WIDTH_DP);
    }

    public WordmarkView(Context context, String forcedLogo, float widthDp) {
        this(context, null, forcedLogo, widthDp);
    }

    private WordmarkView(Context context, AttributeSet attrs, String which, float widthDp) {
        super(context, attrs);
        float density = getResources().getDisplayMetrics().density;
        lines = Art.lines(Art.load(context, which));
        int c = 1;
        for (String l : lines) c = Math.max(c, l.length());
        cols = c;
        split = Art.split(which);
        prefixAccent = Art.isPrefixAccent(which);
        cellW = widthDp * density / cols;
        word.setColor(Theme.m(getResources().getColor(R.color.tn_fg, null)));
        shadow.setColor(Theme.m(0xFF0E0E14));
        scramble.setColor(Theme.m(getResources().getColor(R.color.tn_comment, null)));
        scramble.setTextSize(cellW * 1.7f);
        scramble.setTextAlign(Paint.Align.CENTER);
        try {
            scramble.setTypeface(CastUi.font(getContext()));
        } catch (Exception e) {
            scramble.setTypeface(Typeface.MONOSPACE);
        }
        settle = new float[lines.length][cols];
        for (int r = 0; r < lines.length; r++) {
            for (int k = 0; k < cols; k++) settle[r][k] = 0.55f * k / cols + 0.45f * random.nextFloat();
        }
    }

    /** Plays the decrypt reveal once. */
    void reveal() {
        revealStart = SystemClock.uptimeMillis();
        invalidate();
    }

    /** Enables or disables the live cinema projector shimmer wave across the wordmark. */
    void setContinuousShimmer(boolean enabled) {
        this.continuousShimmer = enabled;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension((int) Math.ceil(cols * cellW + cellW), (int) Math.ceil(lines.length * cellW * 2 + cellW));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        LauncherConfig cfg = LauncherConfig.get(getContext());
        if (prefixAccent && split > 0) {
            float x1 = Math.max(1f, split * cellW);
            accent.setShader(new LinearGradient(0, 0, x1, h, cfg.accent, cfg.accent2, Shader.TileMode.CLAMP));
        } else {
            float x0 = Math.max(0, split) * cellW;
            accent.setShader(new LinearGradient(x0, 0, w, h, cfg.accent, cfg.accent2, Shader.TileMode.CLAMP));
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float cellH = cellW * 2;
        float p = revealStart < 0 ? 1f : Math.min(1f, (SystemClock.uptimeMillis() - revealStart) / (float) REVEAL_MS);
        // A half-cell drop shadow like a terminal logo over a wallpaper, then the cells.
        for (int pass = 0; pass < 2; pass++) {
            float off = pass == 0 ? cellW * 0.5f : 0f;
            for (int r = 0; r < lines.length; r++) {
                String line = lines[r];
                for (int c = 0; c < line.length(); c++) {
                    char ch = line.charAt(c);
                    if (ch == ' ') continue;
                    float x = c * cellW + off, y = r * cellH + off;
                    if (p < settle[r][c]) {
                        if (pass == 1 && random.nextInt(3) > 0) {
                            char g = TextFx.GLYPHS.charAt(random.nextInt(TextFx.GLYPHS.length()));
                            canvas.drawText(String.valueOf(g), x + cellW / 2f, y + cellH * 0.8f, scramble);
                        }
                        continue;
                    }
                    boolean useAccent = split >= 0 && (prefixAccent ? (c < split) : (c >= split));
                    Paint paint = pass == 0 ? shadow : (useAccent ? accent : word);
                    if (pass == 1 && continuousShimmer && p >= 1f) {
                        float phase = ((SystemClock.uptimeMillis() % 3200L) / 3200f) * (cols + 24) - 12f;
                        float dist = Math.abs(c - phase);
                        if (dist < 6.5f) {
                            Theme t = Theme.cur();
                            shimmerPaint.setColor(dist < 2.2f ? t.fgBright : (useAccent ? t.cyan : t.accent));
                            paint = shimmerPaint;
                        }
                    }
                    blockRect(canvas, ch, x, y, cellW, cellH, paint);
                }
            }
        }
        if (p < 1f) postInvalidateDelayed(33);
        else {
            revealStart = -1;
            if (continuousShimmer) postInvalidateDelayed(48);
        }
    }

    /** Draws █ ▄ ▀ ▌ ▐ (and '#') as rectangles. */
    static void blockRect(Canvas canvas, char ch, float x, float y, float cw, float chh, Paint p) {
        switch (ch) {
            case '\u2584': canvas.drawRect(x, y + chh / 2f, x + cw + 0.5f, y + chh + 0.5f, p); break;
            case '\u2580': canvas.drawRect(x, y, x + cw + 0.5f, y + chh / 2f + 0.5f, p); break;
            case '\u258C': canvas.drawRect(x, y, x + cw / 2f + 0.5f, y + chh + 0.5f, p); break;
            case '\u2590': canvas.drawRect(x + cw / 2f, y, x + cw + 0.5f, y + chh + 0.5f, p); break;
            default: canvas.drawRect(x, y, x + cw + 0.5f, y + chh + 0.5f, p);
        }
    }
}
