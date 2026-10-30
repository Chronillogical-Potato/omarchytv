package com.omarchytv.home.link;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * v2.10.1: user themes pushed from the PC (PUT /v1/themes/&lt;name&gt;, opt-in on the PC side).
 * Layout: &lt;link dir&gt;/themes/&lt;name&gt;/theme.json + backgrounds/NN-*.{jpg,png,webp}.
 * Upload = a ZIP with theme.json and backgrounds/&lt;file&gt;; checked strictly: safe names only (no paths, no "..",
 * no hidden files), magic bytes per image, at most 32 images, every theme together at most 50 MB (uncompressed
 * size counted while extracting, so a ZIP bomb stops at the cap). Extracted to a temp dir, then renamed in place.
 */
public final class CustomThemes {
    public static final long CAP = 50L * 1024 * 1024;
    static final int MAX_IMAGES = 32, JSON_MAX = 64 * 1024;
    public static final Pattern NAME = Pattern.compile("^[a-z0-9][a-z0-9-]{0,39}$");
    static final Pattern FILE = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{0,63}\\.(jpg|jpeg|png|webp)$");
    static final Pattern HEX = Pattern.compile("^#[0-9a-fA-F]{6}$");

    private CustomThemes() {}

    public static File root(File linkDir) {
        return new File(linkDir, "themes");
    }

    public static long used(File linkDir, String except) {
        long n = 0;
        File[] ts = root(linkDir).listFiles();
        if (ts != null) for (File t : ts) if (t.isDirectory() && !t.getName().equals(except) && NAME.matcher(t.getName()).matches()) n += size(t);
        return n;
    }

    static long size(File f) {
        if (f.isFile()) return f.length();
        long n = 0;
        File[] k = f.listFiles();
        if (k != null) for (File c : k) n += size(c);
        return n;
    }

    static boolean del(File f) {
        File[] k = f.isDirectory() ? f.listFiles() : null;
        if (k != null) for (File c : k) del(c);
        return f.delete();
    }

    /** Magic bytes: JPEG FF D8 FF, PNG 89 50 4E 47 0D 0A 1A 0A, WebP RIFF....WEBP. */
    static boolean magicOk(byte[] h, int n, String ext) {
        switch (ext) {
            case "jpg": case "jpeg": return n >= 3 && (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF;
            case "png": return n >= 8 && (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G' && h[4] == 0x0D && h[5] == 0x0A && h[6] == 0x1A && h[7] == 0x0A;
            case "webp": return n >= 12 && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F' && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P';
            default: return false;
        }
    }

    /** Thrown with an HTTP status and error code for the link response. */
    public static final class Bad extends Exception {
        public final int status;
        public final String code;

        Bad(int status, String code, String msg) {
            super(msg);
            this.status = status;
            this.code = code;
        }
    }

    /** Installs (or replaces) theme {@code name} from the ZIP at {@code zip}. Returns the theme's summary. */
    public static JSONObject install(File linkDir, String name, File zip) throws Bad, IOException {
        if (!NAME.matcher(name).matches()) throw new Bad(400, "bad_name", "theme name: [a-z0-9-], at most 40");
        long budget = CAP - used(linkDir, name);
        File root = root(linkDir);
        if (!root.isDirectory() && !root.mkdirs()) throw new IOException("no themes dir");
        File tmp = new File(root, ".in-" + LinkCrypto.hex(LinkCrypto.randomBytes(6)));
        File bgs = new File(tmp, "backgrounds");
        if (!bgs.mkdirs()) throw new IOException("mkdir");
        boolean ok = false;
        try {
            JSONObject theme = null;
            int images = 0;
            long total = 0;
            List<String> seen = new ArrayList<>();
            try (ZipInputStream zin = new ZipInputStream(new java.io.FileInputStream(zip))) {
                ZipEntry e;
                byte[] buf = new byte[64 * 1024];
                while ((e = zin.getNextEntry()) != null) {
                    String n = e.getName();
                    if (e.isDirectory()) {
                        if (n.equals("backgrounds/")) continue;
                        throw new Bad(400, "bad_zip", "unexpected folder " + n);
                    }
                    if (seen.contains(n)) throw new Bad(400, "bad_zip", "duplicate " + n);
                    seen.add(n);
                    if (n.equals("theme.json")) {
                        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                        int r;
                        while ((r = zin.read(buf)) > 0) {
                            bo.write(buf, 0, r);
                            if (bo.size() > JSON_MAX) throw new Bad(413, "too_large", "theme.json over 64 KiB");
                        }
                        try {
                            theme = new JSONObject(new String(bo.toByteArray(), StandardCharsets.UTF_8));
                        } catch (org.json.JSONException x) {
                            throw new Bad(400, "bad_theme", "theme.json is not JSON");
                        }
                        continue;
                    }
                    String f = n.startsWith("backgrounds/") ? n.substring("backgrounds/".length()) : null;
                    if (f == null || !FILE.matcher(f).matches() || f.contains("..")) throw new Bad(400, "bad_zip", "unsafe or unexpected entry");
                    if (++images > MAX_IMAGES) throw new Bad(413, "too_many", "at most 32 backgrounds");
                    String ext = f.substring(f.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
                    File out = new File(bgs, f);
                    if (!out.getCanonicalPath().startsWith(bgs.getCanonicalPath() + File.separator)) throw new Bad(400, "bad_zip", "path");
                    byte[] head = new byte[12];
                    int hn = readUpTo(zin, head);
                    if (!magicOk(head, hn, ext)) throw new Bad(415, "bad_image", f + ": content is not " + ext);
                    try (FileOutputStream fo = new FileOutputStream(out)) {
                        fo.write(head, 0, hn);
                        total += hn;
                        int r;
                        while ((r = zin.read(buf)) > 0) {
                            total += r;
                            if (total > budget) throw new Bad(413, "too_large", "themes on the TV: at most 50 MB in total");
                            fo.write(buf, 0, r);
                        }
                    }
                    if (total > budget) throw new Bad(413, "too_large", "themes on the TV: at most 50 MB in total");
                }
            } catch (java.util.zip.ZipException x) {
                throw new Bad(400, "bad_zip", "not a ZIP");
            }
            if (theme == null) throw new Bad(400, "bad_theme", "theme.json missing");
            JSONObject clean = cleanTheme(theme, name);
            try (FileOutputStream fo = new FileOutputStream(new File(tmp, "theme.json"))) {
                fo.write(clean.toString().getBytes(StandardCharsets.UTF_8));
            }
            File dst = new File(root, name);
            File old = new File(root, ".old-" + name);
            if (dst.exists() && !dst.renameTo(old)) throw new IOException("rename old");
            if (!tmp.renameTo(dst)) {
                old.renameTo(dst);
                throw new IOException("rename");
            }
            del(old);
            ok = true;
            return summary(dst);
        } finally {
            if (!ok) del(tmp);
        }
    }

    static int readUpTo(InputStream in, byte[] b) throws IOException {
        int n = 0;
        while (n < b.length) {
            int r = in.read(b, n, b.length - n);
            if (r <= 0) break;
            n += r;
        }
        return n;
    }

    /** theme.json: {mode, accent, accent2?, colors{role:#rrggbb}}; anything else dropped. */
    static JSONObject cleanTheme(JSONObject t, String name) throws Bad {
        try {
            JSONObject colors = new JSONObject();
            JSONObject in = t.optJSONObject("colors");
            if (in != null) for (Iterator<String> it = in.keys(); it.hasNext() && colors.length() < 64; ) {
                String k = it.next(), v = in.optString(k, "");
                if (k.matches("[a-z0-9_]{1,32}") && HEX.matcher(v).matches()) colors.put(k, v.toLowerCase(Locale.ROOT));
            }
            String accent = t.optString("accent", colors.optString("accent", ""));
            if (!HEX.matcher(accent).matches()) throw new Bad(400, "bad_theme", "accent must be #rrggbb");
            String accent2 = t.optString("accent2", colors.optString("color4", accent));
            if (!HEX.matcher(accent2).matches()) accent2 = accent;
            return new JSONObject().put("name", name).put("mode", "light".equals(t.optString("mode")) ? "light" : "dark")
                    .put("accent", accent.toLowerCase(Locale.ROOT)).put("accent2", accent2.toLowerCase(Locale.ROOT)).put("colors", colors);
        } catch (org.json.JSONException e) {
            throw new Bad(400, "bad_theme", e.getMessage());
        }
    }

    public static String[] names(File linkDir) {
        File[] ts = root(linkDir).listFiles();
        List<String> out = new ArrayList<>();
        if (ts != null) for (File t : ts) if (t.isDirectory() && NAME.matcher(t.getName()).matches() && new File(t, "theme.json").isFile()) out.add(t.getName());
        java.util.Collections.sort(out);
        return out.toArray(new String[0]);
    }

    public static File[] backgrounds(File linkDir, String name) {
        File[] f = new File(new File(root(linkDir), name), "backgrounds").listFiles((d, n) -> FILE.matcher(n).matches());
        if (f == null) return new File[0];
        Arrays.sort(f);
        return f;
    }

    static JSONObject summary(File dir) {
        try {
            JSONArray b = new JSONArray();
            for (File f : backgrounds(dir.getParentFile().getParentFile(), dir.getName())) b.put(f.getName());
            return new JSONObject().put("name", dir.getName()).put("bytes", size(dir)).put("backgrounds", b);
        } catch (org.json.JSONException e) {
            return new JSONObject();
        }
    }

    public static JSONObject list(File linkDir) throws org.json.JSONException {
        JSONArray a = new JSONArray();
        for (String n : names(linkDir)) a.put(summary(new File(root(linkDir), n)));
        return new JSONObject().put("themes", a).put("used", used(linkDir, null)).put("cap", CAP);
    }

    public static boolean delete(File linkDir, String name) {
        if (!NAME.matcher(name).matches()) return false;
        File d = new File(root(linkDir), name);
        return d.isDirectory() && del(d);
    }

    /** Config edit selecting theme {@code name} and its background {@code bg} (no background: keep the wallpaper). */
    public static void select(File linkDir, String name, int bg, JSONObject cfg) throws Exception {
        File dir = new File(root(linkDir), name);
        byte[] b = com.omarchytv.home.ApiCompat.readAllBytes(new File(dir, "theme.json"));
        JSONObject t = new JSONObject(new String(b, StandardCharsets.UTF_8));
        t.put("source", "custom");
        cfg.put("theme", t);
        File[] bgs = backgrounds(linkDir, name);
        if (bgs.length > 0) cfg.put("wallpaper", bgs[Math.floorMod(bg, bgs.length)].getAbsolutePath());
    }
}
