package com.omarchytv.home.lights;

import android.content.Context;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Picks the configured hub and pushes theme colours to it on a background thread. */
public final class Lights {
    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "omarchy-lights");
        t.setDaemon(true);
        return t;
    });

    private Lights() {
    }

    /** The configured hub, or null ("none"). */
    public static LightsHub current(Context c) {
        switch (LightsStore.hub(c)) {
            case "hue":
                return new HueBridge(LightsStore.get(c, "hue_ip"), LightsStore.get(c, "hue_user"), LightsStore.get(c, "hue_group"));
            case "homeassistant":
                return new HomeAssistantHub(LightsStore.get(c, "ha_url"), LightsStore.get(c, "ha_token"), LightsStore.get(c, "ha_entities"));
            default:
                return null;
        }
    }

    public static void push(Context c, int accent, int accent2, int bg, int brightnessPct, boolean dim) {
        LightsHub h = current(c);
        if (h == null || !h.configured()) return;
        EXEC.execute(() -> {
            try {
                h.apply(accent, accent2, bg, brightnessPct, dim);
            } catch (Throwable ignored) {
                // hub offline: silent, the settings page has a connection test
            }
        });
    }

    public static void run(Runnable r) {
        EXEC.execute(r);
    }
}
