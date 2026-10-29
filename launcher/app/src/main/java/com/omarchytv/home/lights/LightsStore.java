package com.omarchytv.home.lights;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Lights hub settings in app-private SharedPreferences (not in the shareable omarchytv.json), because
 * they hold credentials: the Hue username and the Home Assistant token. Nothing here is ever logged.
 */
public final class LightsStore {
    private static final String PREFS = "omarchy_lights";

    private LightsStore() {
    }

    static SharedPreferences p(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static String hub(Context c) {
        return p(c).getString("hub", "none");
    }

    public static void setHub(Context c, String id) {
        p(c).edit().putString("hub", id).apply();
    }

    public static String get(Context c, String k) {
        return p(c).getString(k, "");
    }

    public static void put(Context c, String k, String v) {
        p(c).edit().putString(k, v == null ? "" : v.trim()).apply();
    }

    /** "Forget hub": clears every credential (asked for confirmation in the UI first). */
    public static void forget(Context c) {
        p(c).edit().clear().apply();
    }

    public static boolean hasSecret(Context c, String k) {
        return !get(c, k).isEmpty();
    }
}
