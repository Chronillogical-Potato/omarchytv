package com.omarchytv.home.lights;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Home Assistant via its documented REST API: POST {url}/api/services/light/turn_on with a
 * long-lived access token (Bearer). The token is stored only in private prefs and never logged.
 */
public final class HomeAssistantHub implements LightsHub {
    final String url, token, entities;

    public HomeAssistantHub(String url, String token, String entities) {
        this.url = url == null ? "" : url.trim().replaceAll("/+$", "");
        this.token = token == null ? "" : token.trim();
        this.entities = entities == null ? "" : entities.trim();
    }

    @Override
    public String id() {
        return "homeassistant";
    }

    @Override
    public boolean configured() {
        return url.startsWith("http") && !token.isEmpty() && !entities.isEmpty();
    }

    /** Comma-separated light.* ids -> JSON array (invalid ids dropped). */
    public static JSONArray entityIds(String csv) {
        JSONArray a = new JSONArray();
        for (String s : csv.split(",")) {
            String e = s.trim();
            if (e.matches("light\\.[a-z0-9_]+")) a.put(e);
        }
        return a;
    }

    public static JSONObject turnOnBody(String csv, int rgb, int brightnessPct, boolean dim) throws Exception {
        return new JSONObject()
                .put("entity_id", entityIds(csv))
                .put("rgb_color", new JSONArray().put((rgb >> 16) & 0xFF).put((rgb >> 8) & 0xFF).put(rgb & 0xFF))
                .put("brightness_pct", dim ? Math.min(brightnessPct, 12) : Math.max(1, Math.min(100, brightnessPct)))
                .put("transition", dim ? 2 : 1);
    }

    @Override
    public void apply(int accent, int accent2, int bg, int brightnessPct, boolean dim) throws Exception {
        if (!configured()) return;
        Http.request("POST", url + "/api/services/light/turn_on", turnOnBody(entities, dim ? accent2 : accent, brightnessPct, dim).toString(), token, 3000);
    }

    @Override
    public String test() {
        if (!url.startsWith("http")) return "no url";
        if (token.isEmpty()) return "no token";
        try {
            Http.request("GET", url + "/api/", null, token, 3000);
            return "ok · Home Assistant";
        } catch (Exception e) {
            String m = String.valueOf(e.getMessage());
            return m.contains("401") ? "error: unauthorized" : "error: unreachable";
        }
    }
}
