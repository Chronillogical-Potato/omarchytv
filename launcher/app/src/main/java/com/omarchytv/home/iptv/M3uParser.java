package com.omarchytv.home.iptv;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for user-supplied extended M3U / M3U8 IPTV playlists (#EXTM3U, #EXTINF with tvg-id,
 * tvg-name, tvg-logo, group-title, optional #EXTGRP). OmarchyTV bundles no playlists or channels.
 */
public final class M3uParser {
    public static final int MAX_CHANNELS = 20000;
    private static final Pattern ATTR = Pattern.compile("([A-Za-z0-9_-]+)=\"([^\"]*)\"");

    private M3uParser() {
    }

    public static final class Channel {
        public final String name, url, tvgId, tvgName, logo, group;

        public Channel(String name, String url, String tvgId, String tvgName, String logo, String group) {
            this.name = name;
            this.url = url;
            this.tvgId = tvgId;
            this.tvgName = tvgName;
            this.logo = logo;
            this.group = group;
        }

        /** Key used to match XMLTV programmes: tvg-id, else tvg-name, else display name. */
        public String epgKey() {
            return !tvgId.isEmpty() ? tvgId : (!tvgName.isEmpty() ? tvgName : name);
        }
    }

    public static List<Channel> parse(String text) {
        List<Channel> out = new ArrayList<>();
        if (text == null) return out;
        if (text.startsWith("\uFEFF")) text = text.substring(1);
        String pendingInfo = null, pendingGroup = null;
        for (String raw : text.split("\r?\n|\r")) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("#EXTINF")) {
                pendingInfo = line;
                pendingGroup = null;
            } else if (line.startsWith("#EXTGRP:")) {
                pendingGroup = line.substring(8).trim();
            } else if (line.startsWith("#")) {
                // #EXTM3U, #EXTVLCOPT, comments: ignored
            } else if (isStreamUrl(line)) {
                out.add(build(pendingInfo, pendingGroup, line));
                pendingInfo = null;
                pendingGroup = null;
                if (out.size() >= MAX_CHANNELS) break;
            }
        }
        return out;
    }

    static boolean isStreamUrl(String s) {
        String l = s.toLowerCase(Locale.ROOT);
        return l.startsWith("http://") || l.startsWith("https://") || l.startsWith("rtsp://")
                || l.startsWith("rtmp://") || l.startsWith("udp://") || l.startsWith("rtp://");
    }

    private static Channel build(String info, String extGrp, String url) {
        Map<String, String> a = new LinkedHashMap<>();
        String name = "";
        if (info != null) {
            Matcher m = ATTR.matcher(info);
            while (m.find()) a.put(m.group(1).toLowerCase(Locale.ROOT), m.group(2).trim());
            int comma = firstCommaOutsideQuotes(info);
            if (comma >= 0) name = info.substring(comma + 1).trim();
        }
        String tvgName = a.getOrDefault("tvg-name", "");
        if (name.isEmpty()) name = !tvgName.isEmpty() ? tvgName : url;
        String group = a.getOrDefault("group-title", "");
        if (group.isEmpty() && extGrp != null) group = extGrp;
        return new Channel(name, url, a.getOrDefault("tvg-id", ""), tvgName, a.getOrDefault("tvg-logo", ""), group);
    }

    /** The name follows the first comma outside quoted attributes (names may contain commas). */
    static int firstCommaOutsideQuotes(String s) {
        boolean q = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"') q = !q;
            else if (c == ',' && !q) return i;
        }
        return -1;
    }

    /** Distinct group names in playlist order ("" shown as "Other"). */
    public static List<String> groups(List<Channel> chans) {
        List<String> out = new ArrayList<>();
        for (Channel c : chans) {
            String g = c.group.isEmpty() ? "Other" : c.group;
            if (!out.contains(g)) out.add(g);
        }
        return out;
    }
}
