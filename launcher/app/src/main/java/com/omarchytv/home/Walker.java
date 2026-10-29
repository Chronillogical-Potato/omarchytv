package com.omarchytv.home;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * v2.9 Walker overlay (spec §4): 50 % black veil, 920 px window (opaque background, 2 px accent border, padding 28),
 * lens + 30 px field, 2 px muted separator, 64 px rows (name 26 px, type 20 px right), selected row on selection
 * with bright_foreground bold, 20 px footer. Searches installed apps, Android settings entries, PC shortcuts and
 * Wi-Fi; the mic key / voice runs the system recognizer and fills the field.
 */
final class Walker extends FrameLayout {

    static final class Item {
        final String name, type;
        final Runnable run;

        Item(String name, String type, Runnable run) {
            this.name = name;
            this.type = type;
            this.run = run;
        }
    }

    interface Host {
        void voice();
        void closed();
    }

    private static final int ROWS = 7;
    private final Activity act;
    private final Host host;
    private final float s;
    private final Typeface tf, tfBold;
    private final Theme t = Theme.cur();
    private final EditText field;
    private final LinearLayout list;
    private final List<Item> all;
    private final List<Item> shown = new ArrayList<>();
    private int sel;
    private boolean centerDown;

    Walker(Activity a, float s, List<Item> items, Host host) {
        super(a);
        act = a;
        this.s = s;
        this.host = host;
        all = items;
        tf = CastUi.font(a);
        tfBold = Typeface.create(tf, Typeface.BOLD);
        setBackgroundColor(0x80000000);
        setClickable(true);
        setElevation(900f);
        BoxView win = new BoxView(a, s, tf);
        win.setColors(Theme.withAlpha(t.bg, 1f), t.accent, t.accent, t.cyan);
        win.setLegend(null, px(28), px(28));
        addView(win, new LayoutParams(px(920), -2, Gravity.CENTER));
        LinearLayout top = new LinearLayout(a);
        top.setGravity(Gravity.CENTER_VERTICAL);
        ImageView lens = new ImageView(a);
        lens.setImageResource(R.drawable.ln_search);
        lens.setColorFilter(t.accent);
        top.addView(lens, new LinearLayout.LayoutParams(px(30), px(30)));
        field = new EditText(a);
        field.setTypeface(tf);
        field.setTextSize(TypedValue.COMPLEX_UNIT_PX, 30 * s);
        field.setTextColor(t.fgBright);
        field.setHintTextColor(t.fgDim);
        field.setHint(R.string.v29_search);
        field.setBackgroundColor(Color.TRANSPARENT);
        field.setSingleLine(true);
        field.setImeOptions(EditorInfo.IME_ACTION_GO);
        field.setPadding(px(16), px(4), 0, px(4));
        top.addView(field, new LinearLayout.LayoutParams(0, -2, 1f));
        win.addView(top, new LinearLayout.LayoutParams(-1, -2));
        View sep = new View(a);
        sep.setBackgroundColor(t.muted);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, Math.max(2, px(2)));
        sl.topMargin = px(16);
        sl.bottomMargin = px(8);
        win.addView(sep, sl);
        list = new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);
        win.addView(list, new LinearLayout.LayoutParams(-1, px(64) * ROWS));
        TextView foot = new TextView(a);
        foot.setTypeface(tf);
        foot.setTextSize(TypedValue.COMPLEX_UNIT_PX, 20 * s);
        foot.setTextColor(t.fg);
        foot.setText(R.string.v29_walker_hint);
        foot.setSingleLine(true);
        foot.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(-1, -2);
        fl.topMargin = px(16);
        win.addView(foot, fl);
        field.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence c, int a1, int b, int d) {
            }

            @Override
            public void onTextChanged(CharSequence c, int a1, int b, int d) {
                filter(c.toString());
            }

            @Override
            public void afterTextChanged(Editable e) {
            }
        });
        field.setOnEditorActionListener((v, id, e) -> {
            openSelected();
            return true;
        });
        filter("");
    }

    private int px(float v) {
        return Math.round(v * s);
    }

    void focus() {
        centerDown = false;
        field.requestFocus();
    }

    void setQuery(String q) {
        field.setText(q);
        field.setSelection(q.length());
    }

    private static String norm(String x) {
        return Normalizer.normalize(x, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    private void filter(String q) {
        shown.clear();
        String n = norm(q.trim());
        List<Item> prefix = new ArrayList<>(), rest = new ArrayList<>();
        for (Item i : all) {
            String nn = norm(i.name);
            if (n.isEmpty() || nn.startsWith(n)) prefix.add(i);
            else if (nn.contains(n) || fuzzy(nn, n)) rest.add(i);
        }
        shown.addAll(prefix);
        shown.addAll(rest);
        sel = 0;
        render();
    }

    private static boolean fuzzy(String hay, String needle) {
        int j = 0;
        for (int i = 0; i < hay.length() && j < needle.length(); i++) if (hay.charAt(i) == needle.charAt(j)) j++;
        return j == needle.length() && needle.length() >= 3;
    }

    private int first;

    private void render() {
        list.removeAllViews();
        if (sel < first) first = sel;
        if (sel >= first + ROWS) first = sel - ROWS + 1;
        if (first > Math.max(0, shown.size() - ROWS)) first = Math.max(0, shown.size() - ROWS);
        for (int i = first; i < Math.min(shown.size(), first + ROWS); i++) {
            Item it = shown.get(i);
            boolean on = i == sel;
            LinearLayout r = new LinearLayout(act);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(px(16), 0, px(16), 0);
            if (on) r.setBackgroundColor(t.selection);
            TextView name = new TextView(act);
            name.setTypeface(on ? tfBold : tf);
            name.setTextSize(TypedValue.COMPLEX_UNIT_PX, 26 * s);
            name.setTextColor(on ? t.fgBright : t.fg);
            name.setText(it.name);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            r.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
            TextView type = new TextView(act);
            type.setTypeface(tf);
            type.setTextSize(TypedValue.COMPLEX_UNIT_PX, 20 * s);
            type.setTextColor(on ? t.fgBright : t.fgDim);
            type.setText(it.type);
            type.setSingleLine(true);
            r.addView(type);
            final int idx = i;
            r.setOnClickListener(v -> {
                sel = idx;
                openSelected();
            });
            list.addView(r, new LinearLayout.LayoutParams(-1, px(64)));
        }
    }

    private void openSelected() {
        if (sel < 0 || sel >= shown.size()) return;
        Item it = shown.get(sel);
        close();
        it.run.run();
    }

    void close() {
        if (getParent() != null) ((android.view.ViewGroup) getParent()).removeView(this);
        android.view.inputmethod.InputMethodManager imm = act.getSystemService(android.view.inputmethod.InputMethodManager.class);
        if (imm != null) imm.hideSoftInputFromWindow(field.getWindowToken(), 0);
        host.closed();
    }

    /** Key handling while open; true = consumed. */
    boolean onKey(KeyEvent e) {
        int k = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        switch (k) {
            case KeyEvent.KEYCODE_DPAD_UP:
                if (down && sel > 0) {
                    sel--;
                    render();
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (down && sel < shown.size() - 1) {
                    sel++;
                    render();
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if (down) {
                    if (e.getRepeatCount() == 0) centerDown = true;
                } else if (centerDown) {
                    centerDown = false;
                    if (!e.isCanceled()) openSelected();
                }
                return true;
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE:
                if (!down) close();
                return true;
            case KeyEvent.KEYCODE_SEARCH:
            case KeyEvent.KEYCODE_VOICE_ASSIST:
                if (!down) host.voice();
                return true;
            default:
                return false;
        }
    }

    // ---- the catalogue ----

    static List<Item> settingsItems(Activity a, Runnable omarchySettings, Runnable inputs) {
        List<Item> out = new ArrayList<>();
        String type = a.getString(R.string.v29_type_settings);
        out.add(new Item(a.getString(R.string.v29_set_omarchy), type, omarchySettings));
        out.add(new Item(a.getString(R.string.v29_set_input), type, inputs));
        Object[][] e = {
                {R.string.v29_set_android, Settings.ACTION_SETTINGS}, {R.string.v29_set_network, Settings.ACTION_WIRELESS_SETTINGS},
                {R.string.v29_set_bt, Settings.ACTION_BLUETOOTH_SETTINGS}, {R.string.v29_set_display, Settings.ACTION_DISPLAY_SETTINGS},
                {R.string.v29_set_sound, Settings.ACTION_SOUND_SETTINGS}, {R.string.v29_set_apps, Settings.ACTION_APPLICATION_SETTINGS},
                {R.string.v29_set_storage, Settings.ACTION_INTERNAL_STORAGE_SETTINGS}, {R.string.v29_set_date, Settings.ACTION_DATE_SETTINGS},
                {R.string.v29_set_dream, Settings.ACTION_DREAM_SETTINGS}, {R.string.v29_set_dev, Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS},
                {R.string.v29_set_about, Settings.ACTION_DEVICE_INFO_SETTINGS}};
        for (Object[] x : e) {
            String action = (String) x[1];
            out.add(new Item(a.getString((Integer) x[0]), type, () -> {
                try {
                    a.startActivity(new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (Exception ex) {
                    try {
                        a.startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    } catch (Exception ignored) {
                    }
                }
            }));
        }
        return out;
    }
}
