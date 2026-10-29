package com.omarchytv.home;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Cinemarchy's @dhh37 channel: DHH's public YouTube uploads feed (Atom), merged with a short curated list.
 * Links and metadata only: title, thumbnail, watch link, published date. No video is downloaded and no
 * stream is extracted; a pick opens the YouTube app (or, when the user turns it on, the official embed).
 */
public final class DhhFeed {
    /** Resolved once from https://www.youtube.com/@dhh37 (canonical link / externalId), 2026-10-29. */
    public static final String CHANNEL_ID = "UCUkM9uMpWatT7gVWShgtKFw";
    public static final String FEED_URL = "https://www.youtube.com/feeds/videos.xml?channel_id=" + CHANNEL_ID;
    public static final String CHANNEL_URL = "https://www.youtube.com/@dhh37/videos";
    public static final long TTL_MS = 6L * 3600_000L;
    /** First open (nothing seen yet): uploads from the last 14 days get the NEW badge. */
    static final long FIRST_RUN_WINDOW_MS = 14L * 86400_000L;
    private static final int MAX = 30;

    public static final class Video {
        public final String id, title, link, thumb;
        public final long published;   // epoch ms; 0 = unknown (curated entries)

        Video(String id, String title, String link, String thumb, long published) {
            this.id = id;
            this.title = title;
            this.link = link;
            this.thumb = thumb;
            this.published = published;
        }
    }

    private DhhFeed() {
    }

    static boolean validId(String id) {
        return id != null && id.matches("[A-Za-z0-9_-]{11}");
    }

    static String watchUrl(String id) {
        return "https://www.youtube.com/watch?v=" + id;
    }

    static String thumbUrl(String id) {
        return "https://i.ytimg.com/vi/" + id + "/hqdefault.jpg";
    }

    /**
     * Real uploads on @dhh37, checked on the channel page (2026-10-29). Shown when the feed can't be read
     * (offline, or the feed endpoint refuses), and after the feed items.
     */
    public static List<Video> curated() {
        String[][] v = {
                {"F7fe9pa8OeE", "Omarchy Quattro"},
                {"jgSjG0IYw4o", "Omarchy running on 2011 X220 2GB RAM (with room to spare)"},
                {"sMxskir7Rug", "Omacon Keynote"},
                {"jqDZy2zaxcE", "Tmux comes to the next Omarchy"},
                {"L3EafsSCv80", "Omarchy 3"},
                {"TcHY0AEd2Uw", "Omarchy 2.0"},
                {"I5Mnni7cea8", "Omarchy"},
                {"Cft6mZDzIng", "Omarchy: The Unified Menu System"},
                {"YRqHxBY3wps", "Omarchy: Install extra theme"},
                {"P9qzvCkqLAA", "Omarchy: Change font"},
                {"c8sJ2gmA_Zo", "Omarchy: Change background"},
        };
        List<Video> out = new ArrayList<>();
        for (String[] e : v) out.add(new Video(e[0], e[1], watchUrl(e[0]), thumbUrl(e[0]), 0));
        return out;
    }

    /** Atom feed -> videos (newest first). Bad XML -> empty list. DTDs/entities are refused. */
    public static List<Video> parse(byte[] xml) {
        List<Video> out = new ArrayList<>();
        if (xml == null || xml.length == 0) return out;
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            f.setExpandEntityReferences(false);
            trySet(f, "http://apache.org/xml/features/disallow-doctype-decl", true);
            trySet(f, "http://xml.org/sax/features/external-general-entities", false);
            trySet(f, "http://xml.org/sax/features/external-parameter-entities", false);
            DocumentBuilder b = f.newDocumentBuilder();
            Document d = b.parse(new ByteArrayInputStream(xml));
            NodeList entries = d.getElementsByTagNameNS("*", "entry");
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < entries.getLength() && out.size() < MAX; i++) {
                Element e = (Element) entries.item(i);
                String id = text(e, "videoId");
                if (!validId(id) || !seen.add(id)) continue;
                String title = clean(text(e, "title"), 160);
                if (title.isEmpty()) continue;
                String thumb = thumbUrl(id);
                NodeList th = e.getElementsByTagNameNS("*", "thumbnail");
                if (th.getLength() > 0) {
                    String u = ((Element) th.item(0)).getAttribute("url");
                    if (u.startsWith("https://i") && u.contains(".ytimg.com/")) thumb = u;
                }
                out.add(new Video(id, title, watchUrl(id), thumb, time(text(e, "published"))));
            }
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
        Collections.sort(out, (a, c) -> Long.compare(c.published, a.published));
        return out;
    }

    /** Feed items first (newest first), then curated ones the feed did not already list. */
    public static List<Video> merge(List<Video> feed, List<Video> curated) {
        List<Video> out = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (Video v : feed) if (ids.add(v.id)) out.add(v);
        for (Video v : curated) if (ids.add(v.id)) out.add(v);
        return out;
    }

    /** NEW = published after the newest upload the user already saw (first run: within 14 days of now). */
    public static boolean isNew(Video v, long lastSeen, long now) {
        if (v.published <= 0) return false;
        return lastSeen > 0 ? v.published > lastSeen : v.published > now - FIRST_RUN_WINDOW_MS;
    }

    public static long newest(List<Video> list) {
        long m = 0;
        for (Video v : list) m = Math.max(m, v.published);
        return m;
    }

    private static void trySet(DocumentBuilderFactory f, String feature, boolean on) {
        try {
            f.setFeature(feature, on);
        } catch (Exception ignored) {
        }
    }

    private static String text(Element e, String local) {
        NodeList n = e.getElementsByTagNameNS("*", local);
        for (int i = 0; i < n.getLength(); i++) {
            Node x = n.item(i);
            if (x.getParentNode() == e) return x.getTextContent() == null ? "" : x.getTextContent().trim();
        }
        return n.getLength() > 0 && n.item(0).getTextContent() != null ? n.item(0).getTextContent().trim() : "";
    }

    static String clean(String s, int max) {
        String t = s == null ? "" : s.replaceAll("[\\p{Cntrl}]", " ").replaceAll("\\s+", " ").trim();
        return t.length() > max ? t.substring(0, max - 1) + "…" : t;
    }

    static long time(String iso) {
        if (iso == null || iso.isEmpty()) return 0;
        String s = iso.replaceAll("([+-]\\d\\d):(\\d\\d)$", "$1$2").replace("Z", "+0000");
        for (String p : new String[]{"yyyy-MM-dd'T'HH:mm:ssZ", "yyyy-MM-dd'T'HH:mm:ss.SSSZ"}) {
            try {
                SimpleDateFormat f = new SimpleDateFormat(p, Locale.ROOT);
                f.setTimeZone(TimeZone.getTimeZone("UTC"));
                return f.parse(s).getTime();
            } catch (ParseException ignored) {
            }
        }
        return 0;
    }
}
