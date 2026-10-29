package com.omarchytv.home.iptv;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * IPTV state. Playlists + EPG URL live in omarchytv.json under {@code cinemarchy.iptv} (so the PC can
 * push them via omarchy-link); favourites and last channel are in private prefs. Downloads are cached in
 * the app's private cache dir. Only user-supplied playlists: nothing is bundled.
 */
public final class IptvStore {
    private static final String PREFS = "omarchy_iptv";

    private IptvStore() {
    }

    public static final class Playlist {
        public final String name, url;

        Playlist(String name, String url) {
            this.name = name;
            this.url = url;
        }
    }

    public static List<Playlist> playlists(JSONObject root) {
        List<Playlist> out = new ArrayList<>();
        JSONObject iptv = iptv(root);
        JSONArray a = iptv == null ? null : iptv.optJSONArray("playlists");
        if (a == null) return out;
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            String url = o.optString("url", "").trim();
            if (!validUrl(url)) continue;
            out.add(new Playlist(o.optString("name", "Playlist " + (i + 1)).trim(), url));
        }
        return out;
    }

    public static String epgUrl(JSONObject root) {
        JSONObject iptv = iptv(root);
        String u = iptv == null ? "" : iptv.optString("epg", "").trim();
        return validUrl(u) ? u : "";
    }

    static JSONObject iptv(JSONObject root) {
        JSONObject c = root == null ? null : root.optJSONObject("cinemarchy");
        return c == null ? null : c.optJSONObject("iptv");
    }

    public static boolean validUrl(String u) {
        return u != null && u.matches("(?i)https?://[^\\s]{3,2000}");
    }

    /** Adds (or renames) a playlist inside a mutable config root. */
    public static void addPlaylist(JSONObject root, String name, String url) throws Exception {
        if (!validUrl(url)) throw new IllegalArgumentException("bad url");
        JSONObject c = root.optJSONObject("cinemarchy");
        if (c == null) root.put("cinemarchy", c = new JSONObject());
        JSONObject iptv = c.optJSONObject("iptv");
        if (iptv == null) c.put("iptv", iptv = new JSONObject());
        JSONArray a = iptv.optJSONArray("playlists");
        if (a == null) iptv.put("playlists", a = new JSONArray());
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null && url.equals(o.optString("url"))) {
                o.put("name", name);
                return;
            }
        }
        a.put(new JSONObject().put("name", name == null || name.isEmpty() ? "Playlist" : name).put("url", url));
    }

    // ---- favourites / last channel ---------------------------------------------------------------

    static SharedPreferences p(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static Set<String> favorites(Context c) {
        return new HashSet<>(p(c).getStringSet("fav", new HashSet<>()));
    }

    public static boolean toggleFavorite(Context c, String url) {
        Set<String> f = favorites(c);
        boolean added = f.add(url);
        if (!added) f.remove(url);
        p(c).edit().putStringSet("fav", f).apply();
        return added;
    }

    /** Remembers the channel being tuned; the previous one becomes the "last channel". */
    public static void tuned(Context c, String url) {
        String cur = p(c).getString("cur", "");
        if (url.equals(cur)) return;
        p(c).edit().putString("prev", cur).putString("cur", url).apply();
    }

    public static String lastChannel(Context c) {
        return p(c).getString("prev", "");
    }

    public static String currentChannel(Context c) {
        return p(c).getString("cur", "");
    }

    // ---- downloads (blocking: call off the main thread) -----------------------------------------

    public static byte[] fetch(Context c, String url, String cacheName, long maxAgeMs) throws Exception {
        File f = new File(c.getCacheDir(), "iptv-" + cacheName);
        if (f.isFile() && System.currentTimeMillis() - f.lastModified() < maxAgeMs) return Files.readAllBytes(f.toPath());
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(6000);
        conn.setReadTimeout(15000);
        conn.setInstanceFollowRedirects(true);
        try (InputStream in = conn.getInputStream(); FileOutputStream out = new FileOutputStream(f)) {
            byte[] buf = new byte[16384];
            int n;
            long total = 0;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > 128L * 1024 * 1024) throw new Exception("too large");
                out.write(buf, 0, n);
            }
        } catch (Exception e) {
            if (f.isFile()) return Files.readAllBytes(f.toPath());   // offline: stale cache is better than nothing
            throw e;
        } finally {
            conn.disconnect();
        }
        return Files.readAllBytes(f.toPath());
    }

    public static String key(String url) {
        return Integer.toHexString(url.hashCode());
    }

    private static volatile Map<String, List<Xmltv.Programme>> epg;

    public static Map<String, List<Xmltv.Programme>> epg() {
        return epg;
    }

    public static void loadEpg(Context c, String url) {
        if (url.isEmpty()) return;
        try {
            epg = Xmltv.parse(Xmltv.decode(fetch(c, url, "epg-" + key(url), 6L * 3600_000)));
        } catch (Throwable ignored) {
        }
    }
}
