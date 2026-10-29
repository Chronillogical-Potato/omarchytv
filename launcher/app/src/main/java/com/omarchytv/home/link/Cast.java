package com.omarchytv.home.link;

import org.json.JSONObject;

import java.io.File;
import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * One validated "Send to TV" request (PROTOCOL.md §6 Cast). Every field has passed the protocol rules before
 * an instance exists, so the launcher can only build its fixed intents from it.
 */
public final class Cast {
    public static final List<String> KINDS = Arrays.asList("url", "youtube", "video", "audio", "image", "text");
    static final Set<String> ACTIONS = new HashSet<>(Arrays.asList("play", "pause", "stop", "seek"));
    static final Pattern VIDEO_ID = Pattern.compile("^[A-Za-z0-9_-]{11}$");
    static final Pattern SHA = Pattern.compile("^[0-9a-f]{64}$");
    static final Pattern MIME = Pattern.compile("^[a-z0-9][a-z0-9.+-]{0,63}/[a-z0-9][a-z0-9.+-]{0,63}$");
    static final int URL_MAX = 4096, TEXT_MAX = 32 * 1024, TITLE_MAX = 200, FROM_MAX = 40;

    public String id, kind, app, url, videoId, mime, title, text, from;
    /** Optional http(s) subtitle track (.srt / .vtt) for video casts, or null. */
    public String subtitle;
    /** Uploaded file in the private cache (getCacheDir()/cast/&lt;sha256&gt;), or null. */
    public File file;
    /** Start offset, seconds (video/audio/youtube). */
    public double position;
    public boolean live;

    public static String appFor(String kind) {
        switch (kind) {
            case "youtube": return "youtube";
            case "video":
            case "audio": return "player";
            case "image": return "viewer";
            case "text": return "text";
            default: return "browser";
        }
    }

    /** http/https only, a host, at most 4096 characters, no whitespace or control characters. */
    public static boolean httpUrl(String u) {
        if (u == null || u.isEmpty() || u.length() > URL_MAX) return false;
        for (int i = 0; i < u.length(); i++) {
            char ch = u.charAt(i);
            if (ch <= 0x20 || ch == 0x7F) return false;
        }
        String l = u.toLowerCase(Locale.ROOT);
        if (!l.startsWith("http://") && !l.startsWith("https://")) return false;
        try {
            URI x = new URI(u);
            String s = x.getScheme();
            return s != null && (s.equalsIgnoreCase("http") || s.equalsIgnoreCase("https")) && x.getHost() != null && !x.getHost().isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    /** Short description for logs: never the URL (its path carries the cast token). */
    @Override
    public String toString() {
        return "cast " + id + " " + kind + (file != null ? " file" : url != null ? " url" : "") + (live ? " live" : "");
    }

    /** Thrown by {@link #parse} with the protocol's status and error code. */
    static final class Bad extends Exception {
        final int status;
        final String code;

        Bad(int status, String code, String message) {
            super(message);
            this.status = status;
            this.code = code;
        }
    }

    /** Validates a POST /v1/cast body. {@code castDir} resolves {@code file}; {@code kinds} = what opens right now. */
    static Cast parse(JSONObject b, List<String> kinds, File castDir) throws Bad {
        Cast c = new Cast();
        Object idv = b.opt("id");
        c.id = idv instanceof String ? (String) idv : "";
        if (c.id.isEmpty() || c.id.length() > 64 || !LinkServer.printable(c.id, 64).equals(c.id)) throw new Bad(400, "bad_cast", "id");
        Object kv = b.opt("kind");
        c.kind = kv instanceof String ? (String) kv : "";
        if (!KINDS.contains(c.kind) || !kinds.contains(c.kind)) throw new Bad(415, "unsupported_kind", String.valueOf(LinkServer.printable(c.kind, 16)));
        c.app = appFor(c.kind);
        if (b.has("url") && !b.isNull("url")) {
            Object u = b.opt("url");
            if (!(u instanceof String) || !httpUrl((String) u)) throw new Bad(400, "bad_cast", "url must be http(s)");
            c.url = (String) u;
        }
        if (b.has("file") && !b.isNull("file")) {
            String f = b.optString("file", "");
            if (!SHA.matcher(f).matches()) throw new Bad(400, "bad_cast", "file");
            File ff = castDir == null ? null : new File(castDir, f);
            if (ff == null || !ff.isFile()) throw new Bad(404, "no_such_file", "PUT /v1/cast/file first");
            c.file = ff;
        }
        if (b.has("mime") && !b.isNull("mime")) {
            String m = b.optString("mime", "").toLowerCase(Locale.ROOT);
            int semi = m.indexOf(';');
            if (semi >= 0) m = m.substring(0, semi).trim();
            if (!MIME.matcher(m).matches()) throw new Bad(400, "bad_cast", "mime");
            c.mime = m;
        }
        c.title = LinkServer.printable(b.optString("title", ""), TITLE_MAX);
        c.from = LinkServer.printable(b.optString("from", ""), FROM_MAX);
        if (b.has("position") && !b.isNull("position")) {
            double p = b.optDouble("position", Double.NaN);
            if (Double.isNaN(p) || Double.isInfinite(p) || p < 0 || p > 1e7) throw new Bad(400, "bad_cast", "position");
            c.position = p;
        }
        c.live = b.optBoolean("live", false);
        if (b.has("subtitle") && !b.isNull("subtitle")) {
            Object sv = b.opt("subtitle");
            if (!(sv instanceof String) || !httpUrl((String) sv)) throw new Bad(400, "bad_cast", "subtitle must be http(s)");
            c.subtitle = (String) sv;
        }
        switch (c.kind) {
            case "youtube":
                c.videoId = b.optString("videoId", "");
                if (!VIDEO_ID.matcher(c.videoId).matches()) throw new Bad(400, "bad_cast", "videoId");
                c.url = null;    // the TV builds its own YouTube URI from the videoId only
                c.file = null;
                break;
            case "text": {
                Object t = b.opt("text");
                if (!(t instanceof String) || ((String) t).isEmpty() || LinkCrypto.utf8((String) t).length > TEXT_MAX) {
                    throw new Bad(400, "bad_cast", "text");
                }
                c.text = (String) t;
                c.url = null;
                c.file = null;
                break;
            }
            case "url":
                if (c.url == null) throw new Bad(400, "bad_cast", "url");
                c.file = null;
                break;
            default:   // video, audio, image
                if (c.url == null && c.file == null) throw new Bad(400, "bad_cast", "url or file");
                if (c.file != null) c.url = null;
        }
        return c;
    }

    /** POST /v1/cast/control body: {action, id?, position?}. */
    static String[] control(JSONObject b) throws Bad {
        String action = b.optString("action", "");
        if (!ACTIONS.contains(action)) throw new Bad(400, "bad_action", "play, pause, stop or seek");
        String id = b.has("id") && !b.isNull("id") ? b.optString("id", "") : "";
        if (id.length() > 64) throw new Bad(400, "bad_cast", "id");
        String pos = "";
        if ("seek".equals(action)) {
            double p = b.optDouble("position", Double.NaN);
            if (Double.isNaN(p) || Double.isInfinite(p) || p < 0 || p > 1e7) throw new Bad(400, "bad_cast", "position");
            pos = String.valueOf(p);
        }
        return new String[]{action, id, pos};
    }

    /**
     * Upload cache rules: delete files older than 24 h, then keep at most {@code maxFiles} files and
     * {@code maxBytes} in total, oldest first. {@code keep} (a sha, may be null) is never deleted.
     */
    public static void sweep(File dir, long nowMs, String keep, int maxFiles, long maxBytes) {
        File[] fs = dir == null ? null : dir.listFiles();
        if (fs == null) return;
        Arrays.sort(fs, (a, b2) -> Long.compare(a.lastModified(), b2.lastModified()));
        long total = 0;
        int n = 0;
        for (File f : fs) {
            boolean tmp = f.getName().startsWith(".");
            long age = nowMs - f.lastModified();
            if ((tmp && age > 3_600_000L) || (!tmp && age > 24 * 3_600_000L && !f.getName().equals(keep))) {
                f.delete();
                continue;
            }
            if (!tmp) {
                total += f.length();
                n++;
            }
        }
        for (File f : fs) {
            if (n <= maxFiles && total <= maxBytes) break;
            if (!f.exists() || f.getName().startsWith(".") || f.getName().equals(keep)) continue;
            total -= f.length();
            n--;
            f.delete();
        }
    }

    /**
     * v2.10 cast upload allowlist: the kind of a file from its first bytes ("image", "video", "audio", "text"),
     * or null when it is none of: JPEG, PNG, GIF, WebP, MP4/MOV/M4A (ftyp), Matroska/WebM, MPEG-TS, MP3, Ogg,
     * FLAC, WAV, UTF-8 text.
     */
    public static String sniff(java.io.File f) {
        byte[] b = new byte[512];
        int n = 0;
        try (java.io.InputStream in = new java.io.FileInputStream(f)) {
            for (int r; n < b.length && (r = in.read(b, n, b.length - n)) > 0; ) n += r;
        } catch (java.io.IOException e) {
            return null;
        }
        if (n >= 3 && u(b[0]) == 0xFF && u(b[1]) == 0xD8 && u(b[2]) == 0xFF) return "image";
        if (n >= 8 && u(b[0]) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return "image";
        if (n >= 4 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8') return "image";
        if (n >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F') {
            if (b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return "image";
            if (b[8] == 'W' && b[9] == 'A' && b[10] == 'V' && b[11] == 'E') return "audio";
            return null;
        }
        if (n >= 8 && b[4] == 'f' && b[5] == 't' && b[6] == 'y' && b[7] == 'p') return "video";
        if (n >= 4 && u(b[0]) == 0x1A && u(b[1]) == 0x45 && u(b[2]) == 0xDF && u(b[3]) == 0xA3) return "video";
        if (n >= 189 && u(b[0]) == 0x47 && u(b[188]) == 0x47) return "video";
        if (n >= 3 && b[0] == 'I' && b[1] == 'D' && b[2] == '3') return "audio";
        if (n >= 2 && u(b[0]) == 0xFF && (u(b[1]) & 0xE0) == 0xE0) return "audio";
        if (n >= 4 && b[0] == 'O' && b[1] == 'g' && b[2] == 'g' && b[3] == 'S') return "audio";
        if (n >= 4 && b[0] == 'f' && b[1] == 'L' && b[2] == 'a' && b[3] == 'C') return "audio";
        for (int i = 0; i < n; i++) {
            int c = u(b[i]);
            if (c == 0 || (c < 0x20 && c != '\n' && c != '\r' && c != '\t')) return null;
        }
        try {   // the prefix must decode as UTF-8 (a multi-byte char cut at the end is tolerated)
            java.nio.charset.StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(b, 0, Math.max(0, n - 3)));
        } catch (java.nio.charset.CharacterCodingException e) {
            return null;
        }
        return n > 0 ? "text" : null;
    }

    private static int u(byte x) {
        return x & 0xFF;
    }
}
