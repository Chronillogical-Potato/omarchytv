package com.omarchytv.home;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.widget.TextView;

/** Omarchy look for the cast screens: theme colours (Theme), JetBrains Mono, Hyprland window panes. */
final class CastUi {
    private CastUi() {
    }

    static Typeface font(Context c) {
        try {
            return android.os.Build.VERSION.SDK_INT >= 26 ? c.getResources().getFont(R.font.caskaydia) : Typeface.MONOSPACE;
        } catch (RuntimeException e) {
            return Typeface.MONOSPACE;
        }
    }

    /** A Hyprland window: theme background at the inactive opacity (0.96), 2 dp inactive border, square corners. */
    static GradientDrawable pane(Context c) {
        float d = c.getResources().getDisplayMetrics().density;
        GradientDrawable g = new GradientDrawable();
        g.setColor(Theme.withAlpha(Theme.cur().bg, 0.96f));
        g.setStroke(Math.round(Math.max(1, LauncherConfig.get(c).borderDp) * d), Theme.cur().inactiveBorder);
        return g;
    }

    static TextView label(Context c, CharSequence text, float sp, int color) {
        TextView t = new TextView(c);
        t.setTypeface(font(c));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setText(text);
        t.setIncludeFontPadding(false);
        return t;
    }

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static String clock(double s) {
        if (!(s >= 0)) return "--:--";
        long t = (long) s, h = t / 3600, m = (t / 60) % 60, x = t % 60;
        return h > 0 ? String.format(java.util.Locale.ROOT, "%d:%02d:%02d", h, m, x) : String.format(java.util.Locale.ROOT, "%02d:%02d", m, x);
    }
}
