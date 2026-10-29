package com.omarchytv.home;

import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class DhhFeedTest {
    private static byte[] sample() throws Exception {
        try (InputStream in = DhhFeedTest.class.getResourceAsStream("/dhh37/sample-feed.xml")) {
            assertNotNull("sample feed resource", in);
            java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
            byte[] b = new byte[8192];
            for (int n; (n = in.read(b)) > 0; ) o.write(b, 0, n);
            return o.toByteArray();
        }
    }

    @Test
    public void parsesSavedFeedNewestFirstAndDropsBrokenEntries() throws Exception {
        List<DhhFeed.Video> v = DhhFeed.parse(sample());
        assertEquals(3, v.size());
        assertEquals("F7fe9pa8OeE", v.get(0).id);
        assertEquals("Omarchy Quattro", v.get(0).title);
        assertEquals("https://www.youtube.com/watch?v=F7fe9pa8OeE", v.get(0).link);
        assertEquals("https://i1.ytimg.com/vi/F7fe9pa8OeE/hqdefault.jpg", v.get(0).thumb);
        assertEquals("Basecamp Five's Keyboard Combos", v.get(2).title);
        assertEquals(DhhFeed.time("2026-05-25T13:15:04-07:00"), v.get(2).published);
        // a thumbnail outside ytimg.com is replaced by the standard one
        assertEquals("https://i.ytimg.com/vi/jgSjG0IYw4o/hqdefault.jpg", v.get(1).thumb);
    }

    @Test
    public void badXmlAndEntitiesAreRefused() {
        assertTrue(DhhFeed.parse("<feed><entry>".getBytes(StandardCharsets.UTF_8)).isEmpty());
        assertTrue(DhhFeed.parse(null).isEmpty());
        String xxe = "<?xml version=\"1.0\"?><!DOCTYPE f [<!ENTITY x SYSTEM \"file:///etc/passwd\">]>"
                + "<feed xmlns=\"http://www.w3.org/2005/Atom\" xmlns:yt=\"http://www.youtube.com/xml/schemas/2015\">"
                + "<entry><yt:videoId>F7fe9pa8OeE</yt:videoId><title>&x;</title></entry></feed>";
        for (DhhFeed.Video x : DhhFeed.parse(xxe.getBytes(StandardCharsets.UTF_8))) assertFalse(x.title.contains("root:"));
    }

    @Test
    public void mergeKeepsFeedFirstAndCuratedAsFallback() throws Exception {
        List<DhhFeed.Video> feed = DhhFeed.parse(sample());
        List<DhhFeed.Video> m = DhhFeed.merge(feed, DhhFeed.curated());
        assertEquals("F7fe9pa8OeE", m.get(0).id);
        int quattro = 0;
        for (DhhFeed.Video x : m) if (x.id.equals("F7fe9pa8OeE")) quattro++;
        assertEquals(1, quattro);
        assertEquals(feed.size() + DhhFeed.curated().size() - 2, m.size());   // 2 curated ids are in the feed
        // offline: only the curated list, every entry a real watch link
        List<DhhFeed.Video> off = DhhFeed.merge(new ArrayList<>(), DhhFeed.curated());
        assertFalse(off.isEmpty());
        for (DhhFeed.Video x : off) {
            assertTrue(DhhFeed.validId(x.id));
            assertEquals(DhhFeed.watchUrl(x.id), x.link);
        }
    }

    @Test
    public void newBadgeFollowsLastSeen() throws Exception {
        List<DhhFeed.Video> v = DhhFeed.parse(sample());
        long now = DhhFeed.time("2026-10-29T08:00:00Z");
        long seen = DhhFeed.time("2026-09-02T09:30:00Z");
        assertTrue(DhhFeed.isNew(v.get(0), seen, now));
        assertFalse(DhhFeed.isNew(v.get(1), seen, now));
        // first run: last 14 days only
        assertTrue(DhhFeed.isNew(v.get(0), 0, now));
        assertFalse(DhhFeed.isNew(v.get(1), 0, now));
        assertFalse(DhhFeed.isNew(DhhFeed.curated().get(0), 0, now));
        assertEquals(v.get(0).published, DhhFeed.newest(v));
    }

    @Test
    public void channelIdIsTheVerifiedOne() {
        assertEquals("UCUkM9uMpWatT7gVWShgtKFw", DhhFeed.CHANNEL_ID);
        assertTrue(DhhFeed.FEED_URL.endsWith("channel_id=UCUkM9uMpWatT7gVWShgtKFw"));
    }
}
