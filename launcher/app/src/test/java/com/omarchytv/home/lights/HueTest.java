package com.omarchytv.home.lights;

import com.omarchytv.home.AmbilightController;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class HueTest {

    @Test
    public void rgbToXy_primariesAndWhiteAndBlack() {
        double[] r = HueColor.rgbToXy(0xFF0000);
        assertEquals(0.6915, r[0], 0.002);
        assertEquals(0.3083, r[1], 0.002);

        double[] g = HueColor.rgbToXy(0x00FF00);
        assertTrue("green inside Gamut C", g[0] > 0.15 && g[1] > 0.65);

        double[] b = HueColor.rgbToXy(0x0000FF);
        assertEquals(0.1532, b[0], 0.005);
        assertEquals(0.0475, b[1], 0.005);

        double[] w = HueColor.rgbToXy(0xFFFFFF);
        assertEquals(0.3227, w[0], 0.015);
        assertEquals(0.3290, w[1], 0.015);

        double[] k = HueColor.rgbToXy(0x000000);
        assertEquals(0.3127, k[0], 0.0001);
        assertEquals(0.3290, k[1], 0.0001);
    }

    @Test
    public void bri_clampsTo1And254() {
        assertEquals(254, HueColor.bri(0xFFFFFF, 100));
        assertEquals(1, HueColor.bri(0x000000, 100));
        assertEquals(1, HueColor.bri(0xFFFFFF, 0));
        int half = HueColor.bri(0x7AA2F7, 50);
        assertTrue(half > 40 && half < 140);
    }

    @Test
    public void parsePair_handlesSuccessLinkButtonAndUnexpected() {
        HueBridge.PairResult ok = HueBridge.parsePair("[{\"success\":{\"username\":\"abc123XYZ_-token\"}}]");
        assertEquals("abc123XYZ_-token", ok.username);
        assertNull(ok.error);

        HueBridge.PairResult btn = HueBridge.parsePair(
                "[{\"error\":{\"type\":101,\"address\":\"\",\"description\":\"link button not pressed\"}}]");
        assertNull(btn.username);
        assertTrue(btn.buttonNotPressed);

        HueBridge.PairResult bad = HueBridge.parsePair("not json");
        assertNull(bad.username);
        assertNotNull(bad.error);
    }

    @Test
    public void parseDiscovery_andGroupsOrdering() throws Exception {
        List<String> ips = HueBridge.parseDiscovery("[{\"id\":\"001788\",\"internalipaddress\":\"192.168.1.20\"}]");
        assertEquals(List.of("192.168.1.20"), ips);

        List<HueBridge.Group> gs = HueBridge.parseGroups("{"
                + "\"1\":{\"name\":\"Living Room\",\"type\":\"Room\",\"lights\":[\"1\",\"2\"]},"
                + "\"2\":{\"name\":\"TV Area\",\"type\":\"Entertainment\",\"lights\":[\"3\",\"4\",\"5\"]},"
                + "\"3\":{\"name\":\"Other\",\"type\":\"LightGroup\",\"lights\":[\"1\"]}}");
        assertEquals(2, gs.size());
        assertEquals("TV Area", gs.get(0).name);
        assertEquals("Entertainment", gs.get(0).type);
        assertEquals("Living Room", gs.get(1).name);

        String body = HueBridge.actionBody(0x7AA2F7, 80, true).toString();
        assertTrue(body.contains("\"on\":true"));
        assertTrue(body.contains("\"xy\":"));

        assertEquals("[\"light.tv_strip\",\"light.sofa\"]",
                HomeAssistantHub.entityIds("light.tv_strip, bogus, light.sofa").toString());
        assertEquals("cinema", AmbilightController.nextEnabledMode("video"));
        assertEquals("theme", AmbilightController.nextEnabledMode("cinema"));
    }
}
