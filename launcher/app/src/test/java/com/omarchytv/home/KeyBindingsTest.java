package com.omarchytv.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import org.json.JSONObject;
import org.junit.Test;

public class KeyBindingsTest {
    private static final LauncherConfig.KeyNames NAMES = n -> n.equals("BUTTON_12") ? 199 : n.equals("PROG_RED") ? 183 : 0;

    private static Map<Integer, LauncherConfig.KeyBinding[]> parse(String json) throws Exception {
        return LauncherConfig.keyBindings(new JSONObject(json).optJSONObject("keys"), NAMES);
    }

    @Test
    public void validBindingsOfAllKindsAndPresses() throws Exception {
        LauncherConfig.KeyBinding[] b = parse("{\"keys\": {\"BUTTON_12\": {"
                + "\"single\": {\"app\": \"org.smarttube.stable\"}, \"double\": {\"action\": \"walker\"},"
                + "\"triple\": {\"intent\": \"intent:#Intent;action=android.settings.SETTINGS;end\"},"
                + "\"long\": {\"action\": \"voice\"}}}}").get(199);
        assertNotNull(b);
        assertEquals("app", b[0].type);
        assertEquals("org.smarttube.stable", b[0].value);
        assertEquals("action", b[1].type);
        assertEquals("walker", b[1].value);
        assertEquals("intent", b[2].type);
        assertEquals("intent:#Intent;action=android.settings.SETTINGS;end", b[2].value);
        assertEquals("voice", b[LauncherConfig.LONG_PRESS].value);
    }

    @Test
    public void emptyOrMissingBindsNothing() throws Exception {
        assertTrue(parse("{}").isEmpty());
        assertTrue(parse("{\"keys\": {}}").isEmpty());
        assertTrue(LauncherConfig.keyBindings(null, NAMES).isEmpty());
        assertTrue(parse("{\"keys\": {\"BUTTON_12\": {}}}").isEmpty());
    }

    @Test
    public void unknownKeyNamesAreDropped() throws Exception {
        Map<Integer, LauncherConfig.KeyBinding[]> m = parse("{\"keys\": {\"NOPE\": {\"single\": {\"app\": \"a.b\"}},"
                + "\"PROG_RED\": {\"single\": {\"app\": \"a.b\"}}, \"BUTTON_12\": \"x\"}}");
        assertEquals(1, m.size());
        assertNotNull(m.get(183));
    }

    @Test
    public void invalidBindingsAreDropped() throws Exception {
        assertNull(parse("{\"keys\": {\"BUTTON_12\": {"
                + "\"single\": {\"app\": \"\"}, \"double\": {\"action\": \"nope\"},"
                + "\"triple\": {\"app\": \"a.b\", \"action\": \"walker\"}, \"long\": {\"intent\": \"http://x\"}}}}").get(199));
        LauncherConfig.KeyBinding[] b = parse("{\"keys\": {\"BUTTON_12\": {\"single\": {\"app\": 5}, \"double\": [],"
                + "\"long\": {\"action\": \"home\"}}}}").get(199);
        assertNotNull(b);
        assertNull(b[0]);
        assertNull(b[1]);
        assertEquals("home", b[3].value);
    }

    @Test
    public void everyActionIdIsAccepted() throws Exception {
        for (String id : LauncherConfig.KEY_ACTIONS) {
            assertNotNull(id, parse("{\"keys\": {\"BUTTON_12\": {\"single\": {\"action\": \"" + id + "\"}}}}").get(199));
        }
    }

    @Test
    public void quickPressesFallBackToTheHighestBoundCountBelow() throws Exception {
        LauncherConfig.KeyBinding[] b = parse("{\"keys\": {\"BUTTON_12\": {\"single\": {\"app\": \"a.b\"},"
                + "\"triple\": {\"action\": \"home\"}}}}").get(199);
        assertEquals("a.b", LauncherConfig.keyPressBinding(b, 1).value);
        assertEquals("a.b", LauncherConfig.keyPressBinding(b, 2).value);
        assertEquals("home", LauncherConfig.keyPressBinding(b, 3).value);
        assertTrue(LauncherConfig.keyWaitsForMore(b, 1));
        assertTrue(LauncherConfig.keyWaitsForMore(b, 2));
        assertTrue(!LauncherConfig.keyWaitsForMore(b, 3));
        LauncherConfig.KeyBinding[] single = parse("{\"keys\": {\"BUTTON_12\": {\"single\": {\"app\": \"a.b\"}}}}").get(199);
        assertTrue(!LauncherConfig.keyWaitsForMore(single, 1));
        LauncherConfig.KeyBinding[] longOnly = parse("{\"keys\": {\"BUTTON_12\": {\"long\": {\"action\": \"home\"}}}}").get(199);
        assertNull(LauncherConfig.keyPressBinding(longOnly, 1));
        assertTrue(!LauncherConfig.keyWaitsForMore(longOnly, 1));
    }
}
