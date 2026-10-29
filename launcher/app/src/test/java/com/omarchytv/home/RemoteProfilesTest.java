package com.omarchytv.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;

public class RemoteProfilesTest {
    private static final String OK = "{\"id\": \"a\", \"name\": \"A\", \"match\": [{\"vendor\": \"0955\", \"product\": \"7217\"}],"
            + "\"buttons\": [{\"key\": \"BUTTON_12\", \"name\": \"Netflix\", \"default\": \"Opens Netflix\"}], \"notes\": \"n\"}";

    @Test
    public void validProfile() {
        List<RemoteProfiles.Profile> l = RemoteProfiles.parse("[" + OK + "]");
        assertEquals(1, l.size());
        RemoteProfiles.Profile p = l.get(0);
        assertEquals("a", p.id);
        assertEquals("A", p.name);
        assertTrue(p.matches(0x0955, 0x7217));
        assertFalse(p.matches(0x0955, 0x7218));
        assertEquals("Opens Netflix", p.button("BUTTON_12").defaultBehaviour);
        assertNull(p.button("BUTTON_11"));
        assertNotNull(RemoteProfiles.forDevice(l, 0x955, 0x7217));
        assertNull(RemoteProfiles.forDevice(l, 1, 2));
    }

    @Test
    public void badHexAndMissingFieldsAreDropped() {
        String badMatch = OK.replace("\"id\": \"a\"", "\"id\": \"b\"").replace("0955", "09zz");
        String noButtons = OK.replace("\"id\": \"a\"", "\"id\": \"c\"").replace("BUTTON_12", "button_12");
        String noName = OK.replace("\"id\": \"a\"", "\"id\": \"d\"").replace("\"name\": \"A\"", "\"name\": \"\"");
        String noId = OK.replace("\"id\": \"a\", ", "");
        assertEquals(0, RemoteProfiles.parse("[" + badMatch + "," + noButtons + "," + noName + "," + noId + ", 5, {}]").size());
        // one good match is enough, a bad one next to it is just dropped
        String mixed = OK.replace("[{", "[{\"vendor\": \"x\", \"product\": \"1\"}, {");
        assertEquals(1, RemoteProfiles.parse("[" + mixed + "]").get(0).match.size());
        assertEquals(0x0955, RemoteProfiles.hex("0x0955"));
        assertEquals(-1, RemoteProfiles.hex("12345"));
        assertEquals(-1, RemoteProfiles.hex(""));
    }

    @Test
    public void duplicateIdsKeepTheFirst() {
        String second = OK.replace("\"name\": \"A\"", "\"name\": \"Other\"");
        List<RemoteProfiles.Profile> l = RemoteProfiles.parse("[" + OK + "," + second + "]");
        assertEquals(1, l.size());
        assertEquals("A", l.get(0).name);
    }

    @Test
    public void malformedJsonGivesNothing() {
        assertTrue(RemoteProfiles.parse("not json").isEmpty());
        assertTrue(RemoteProfiles.parse("{}").isEmpty());
        assertTrue(RemoteProfiles.parse("[]").isEmpty());
    }

    /** The shipped file: a contributor's bad edit must fail here, since the parser would silently drop it. */
    @Test
    public void bundledRemotesJsonIsComplete() throws Exception {
        String json = new String(Files.readAllBytes(Paths.get("src/main/assets/remotes.json")), StandardCharsets.UTF_8);
        int entries = new org.json.JSONArray(json).length();
        List<RemoteProfiles.Profile> l = RemoteProfiles.parse(json);
        assertTrue(entries > 0);
        assertEquals("every entry must be valid and have a unique id", entries, l.size());
        Set<String> ids = new HashSet<>();
        for (RemoteProfiles.Profile p : l) {
            assertTrue(p.id, ids.add(p.id));
            assertFalse(p.id, p.match.isEmpty());
            assertFalse(p.id, p.buttons.isEmpty());
            for (RemoteProfiles.Button b : p.buttons) assertTrue(b.key, b.key.matches("[A-Z][A-Z0-9_]*"));
        }
        org.json.JSONArray raw = new org.json.JSONArray(json);
        for (int i = 0; i < raw.length(); i++) {
            org.json.JSONObject o = raw.getJSONObject(i);
            assertEquals(o.getString("id"), o.getJSONArray("match").length(), l.get(i).match.size());
            assertEquals(o.getString("id"), o.getJSONArray("buttons").length(), l.get(i).buttons.size());
        }
    }
}
