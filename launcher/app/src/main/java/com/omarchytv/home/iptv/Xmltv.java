package com.omarchytv.home.iptv;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/**
 * Minimal XMLTV EPG reader (programme start/stop/channel + title), with .gz support.
 * Pure Java (no Android XML APIs) so it runs in JVM unit tests; tolerant of large files.
 */
public final class Xmltv {
    private static final Pattern PROG = Pattern.compile("<programme\\b([^>]*)>(.*?)</programme>", Pattern.DOTALL);
    private static final Pattern ATTR = Pattern.compile("([a-zA-Z_-]+)=\"([^\"]*)\"");
    private static final Pattern TITLE = Pattern.compile("<title\\b[^>]*>(.*?)</title>", Pattern.DOTALL);

    private Xmltv() {
    }

    public static final class Programme {
        public final String channel, title;
        public final long start, stop;

        Programme(String channel, String title, long start, long stop) {
            this.channel = channel;
            this.title = title;
            this.start = start;
            this.stop = stop;
        }
    }

    /** Decompresses when the bytes start with the gzip magic number. */
    public static String decode(byte[] data) throws Exception {
        if (data.length > 2 && (data[0] & 0xFF) == 0x1F && (data[1] & 0xFF) == 0x8B) {
            try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(data))) {
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) {
                    bo.write(buf, 0, n);
                    if (bo.size() > 256 * 1024 * 1024) throw new Exception("epg too large");
                }
                return new String(bo.toByteArray(), StandardCharsets.UTF_8);
            }
        }
        return new String(data, StandardCharsets.UTF_8);
    }

    /** Programmes grouped by channel id, sorted by start. */
    public static Map<String, List<Programme>> parse(String xml) {
        Map<String, List<Programme>> out = new HashMap<>();
        if (xml == null) return out;
        Matcher m = PROG.matcher(xml);
        while (m.find()) {
            Map<String, String> a = new HashMap<>();
            Matcher am = ATTR.matcher(m.group(1));
            while (am.find()) a.put(am.group(1), am.group(2));
            String ch = a.get("channel");
            long st = time(a.get("start")), sp = time(a.get("stop"));
            if (ch == null || st < 0) continue;
            Matcher tm = TITLE.matcher(m.group(2));
            String title = tm.find() ? unescape(tm.group(1).trim()) : "";
            out.computeIfAbsent(ch, k -> new ArrayList<>()).add(new Programme(ch, title, st, sp < 0 ? st : sp));
        }
        for (List<Programme> l : out.values()) l.sort((x, y) -> Long.compare(x.start, y.start));
        return out;
    }

    /** "20261028210000 +0100" (offset optional, default UTC) -> epoch ms, or -1. */
    public static long time(String s) {
        if (s == null) return -1;
        String t = s.trim();
        try {
            if (t.length() >= 14) {
                String base = t.substring(0, 14);
                String off = t.length() > 14 ? t.substring(14).trim() : "+0000";
                if (off.isEmpty()) off = "+0000";
                SimpleDateFormat f = new SimpleDateFormat("yyyyMMddHHmmssZ", Locale.ROOT);
                return f.parse(base + off).getTime();
            }
        } catch (ParseException ignored) {
        }
        return -1;
    }

    /** {now, next} for a channel at {@code nowMs}; entries may be null. */
    public static Programme[] nowNext(List<Programme> progs, long nowMs) {
        Programme now = null, next = null;
        if (progs != null) {
            for (Programme p : progs) {
                if (p.start <= nowMs && nowMs < p.stop) now = p;
                else if (p.start > nowMs) {
                    next = p;
                    break;
                }
            }
        }
        return new Programme[]{now, next};
    }

    static String unescape(String s) {
        return s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                .replace("&apos;", "'").replace("&#39;", "'").replace("&amp;", "&");
    }
}
