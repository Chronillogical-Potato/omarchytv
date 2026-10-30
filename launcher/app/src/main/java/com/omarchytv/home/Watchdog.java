package com.omarchytv.home;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Crash safety net (idea from Free My TV, MIT). Each launcher crash is recorded; on the 3rd within 5 minutes a
 * service in its own process (":watchdog", survives the launcher dying) puts the stock home back: via the TV's
 * own ADB when "Originale" is set up, else it opens the system home picker. It then stops forcing OmarchyTV
 * (HomeCheck no longer asks) until the user switches back from Settings.
 */
public final class Watchdog {
    private static final long WINDOW = 5 * 60_000L;
    private static final int LIMIT = 3;
    private static boolean installed;

    static synchronized void install(Context c) {
        if (installed) return;
        installed = true;
        final Context app = c.getApplicationContext();
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try {
                if (record(app, System.currentTimeMillis()) >= LIMIT)
                    app.startService(new Intent(app, RevertService.class));
            } catch (Throwable ignored) {
            }
            if (prev != null) prev.uncaughtException(t, e);
        });
    }

    static boolean crashedTooOften(Context c) {
        return recent(c, System.currentTimeMillis()).size() >= LIMIT;
    }

    static void clear(Context c) {
        //noinspection ResultOfMethodCallIgnored
        file(c).delete();
    }

    static synchronized int record(Context c, long now) throws java.io.IOException {
        List<Long> l = recent(c, now);
        l.add(now);
        StringBuilder sb = new StringBuilder();
        for (long at : l) sb.append(at).append('\n');
        try (FileOutputStream out = new FileOutputStream(file(c))) {
            out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        }
        return l.size();
    }

    static List<Long> recent(Context c, long now) {
        List<Long> l = new ArrayList<>();
        try {
            for (String s : new String(ApiCompat.readAllBytes(file(c)), StandardCharsets.UTF_8).split("\n")) {
                try {
                    long at = Long.parseLong(s.trim());
                    if (at <= now && now - at < WINDOW) l.add(at);
                } catch (NumberFormatException ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        return l;
    }

    private static File file(Context c) {
        return new File(c.getFilesDir(), "launcher-crashes.txt");
    }

    /** Runs in ":watchdog". */
    public static final class RevertService extends IntentService {
        public RevertService() {
            super("omarchytv-watchdog");
        }

        @Override
        protected void onHandleIntent(Intent intent) {
            HomeCheck.never(this);   // stop forcing / asking for OmarchyTV
            try {
                Originale.toOriginal(this);
            } catch (Throwable e) {
                Originale.fallback(this);
            }
        }
    }
}
