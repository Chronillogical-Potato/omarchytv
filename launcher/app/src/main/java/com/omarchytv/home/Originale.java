package com.omarchytv.home;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.provider.Settings;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * "Originale": one press back to the TV's own home screen and screensaver, one press back to OmarchyTV.
 * Idea from Free My TV (github.com/simbasjungles/free-my-tv, MIT): the switch runs through the TV's own ADB
 * (LocalAdb, 127.0.0.1:5555) with fixed commands only, so no PC and no root are needed. Opt-in: it works only while
 * the user keeps "Network debugging" on and accepted "Allow debugging?" once on the TV.
 *
 * Safety: only pm enable / pm disable-user --user 0 of the stock launcher (and of the stock screensaver package when
 * it was this switch that enabled it), set-home-activity, and screensaver_components. Never DNS, never uninstall.
 * Every switch to OmarchyTV is verified with resolve-activity and reverted to the stock home if Home is not ours.
 * Without debugging: the fallback opens the system home picker and explains tools/restore.sh.
 */
final class Originale {
    private Originale() {}

    static final String STOCK = "com.google.android.tvlauncher";
    private static final String PREFS = "originale", ON = "on", ENABLED = "enabledPkgs", OUR_DREAM = "ourDream";
    private static final String HOME_Q = "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME";

    static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** True while the TV is in "Originale" mode (stock home). */
    static boolean isOriginal(Context c) {
        return prefs(c).getBoolean(ON, false);
    }

    private static LocalAdb adb(Context c) throws IOException {
        return LocalAdb.connect(c, 60_000, null);
    }

    private static String last(String out) {
        String[] l = out.trim().split("\n");
        return l[l.length - 1].trim();
    }

    private static String pkgOf(String component) {
        int i = component.indexOf('/');
        return i > 0 ? component.substring(0, i) : component;
    }

    /** Stock home + stock screensaver. Throws if the TV's ADB is not reachable (caller shows the fallback). */
    static void toOriginal(Context c) throws IOException {
        HomeOverride.pass();   // the stock home is about to come up on purpose
        SharedPreferences p = prefs(c);
        Set<String> enabled = new LinkedHashSet<>();
        try (LocalAdb a = adb(c)) {
            if (a.shell("pm list packages -d " + STOCK).contains("package:" + STOCK + "\n")
                    || a.shell("pm list packages -d").contains("package:" + STOCK)) {
                a.shell("pm enable --user 0 " + STOCK);
                enabled.add(STOCK);
            }
            String home = last(a.shell(HOME_Q + " " + STOCK));
            if (home.startsWith(STOCK + "/")) a.shell("cmd package set-home-activity --user 0 " + home);
            // Screensaver: remember ours, put the system default back (enable its package only if it is off).
            String cur = last(a.shell("settings get secure screensaver_components"));
            String def = last(a.shell("settings get secure screensaver_default_component"));
            if (!def.isEmpty() && !"null".equals(def) && def.contains("/")) {
                String dp = pkgOf(def);
                if (a.shell("pm list packages -d").contains("package:" + dp + "\n")) {
                    a.shell("pm enable --user 0 " + dp);
                    enabled.add(dp);
                }
                a.shell("settings put secure screensaver_components " + def);
            }
            p.edit().putBoolean(ON, true).putStringSet(ENABLED, enabled).putString(OUR_DREAM, cur).commit();
            a.shell("am start -a android.intent.action.MAIN -c android.intent.category.HOME");
        }
    }

    /** Back to OmarchyTV: start ours, disable what toOriginal enabled, set home last, verify, revert if not ours. */
    static boolean toOmarchy(Context c) throws IOException {
        SharedPreferences p = prefs(c);
        String me = c.getPackageName() + "/.MainActivity";
        try (LocalAdb a = adb(c)) {
            Set<String> enabled = p.getStringSet(ENABLED, new LinkedHashSet<>());
            for (String pkg : enabled) a.shell("pm disable-user --user 0 " + pkg);
            a.shell("cmd package set-home-activity --user 0 " + me);
            String dream = p.getString(OUR_DREAM, null);
            if (dream != null && !dream.isEmpty() && !"null".equals(dream))
                a.shell("settings put secure screensaver_components " + dream);
            String home = last(a.shell(HOME_Q));
            if (!home.startsWith(c.getPackageName() + "/")) {
                for (String pkg : enabled) a.shell("pm enable --user 0 " + pkg);   // never leave the TV without a home
                return false;
            }
            p.edit().putBoolean(ON, false).remove(ENABLED).commit();
            Watchdog.clear(c);
            return true;
        }
    }

    /** No ADB: the best a normal app can do is open the system home picker. */
    static void fallback(Context c) {
        Intent i = new Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (i.resolveActivity(c.getPackageManager()) == null)
            i = Intent.createChooser(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), "Home")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            c.startActivity(i);
        } catch (RuntimeException ignored) {
        }
    }
}
