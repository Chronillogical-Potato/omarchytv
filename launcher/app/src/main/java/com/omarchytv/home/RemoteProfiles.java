package com.omarchytv.home;

import android.content.Context;
import android.view.InputDevice;
import android.view.KeyEvent;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Remotes whose buttons can be remapped (assets/remotes.json, see launcher/REMOTES.md). Only a listed button on a
 * remote that matches a profile (HID vendor and product id) is ever intercepted by {@link KeyMapService}.
 */
final class RemoteProfiles {

    static final class Button {
        /** KeyEvent keycode name without KEYCODE_, e.g. BUTTON_12. */
        final String key, name, defaultBehaviour;

        Button(String key, String name, String defaultBehaviour) {
            this.key = key;
            this.name = name;
            this.defaultBehaviour = defaultBehaviour;
        }
    }

    static final class Profile {
        final String id, name, notes;
        /** {vendor, product} pairs. */
        final List<int[]> match;
        final List<Button> buttons;

        Profile(String id, String name, String notes, List<int[]> match, List<Button> buttons) {
            this.id = id;
            this.name = name;
            this.notes = notes;
            this.match = match;
            this.buttons = buttons;
        }

        boolean matches(int vendor, int product) {
            for (int[] m : match) if (m[0] == vendor && m[1] == product) return true;
            return false;
        }

        Button button(String key) {
            for (Button b : buttons) if (b.key.equals(key)) return b;
            return null;
        }
    }

    private static List<Profile> cached;

    /** The bundled profiles (empty if the asset is unreadable). */
    static synchronized List<Profile> load(Context c) {
        if (cached == null) {
            try (InputStream in = c.getAssets().open("remotes.json")) {
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
                cached = parse(new String(out.toByteArray(), StandardCharsets.UTF_8));
            } catch (Exception e) {
                cached = Collections.emptyList();
            }
        }
        return cached;
    }

    /**
     * Parses the profile list. Dropped: entries without id or name, with no valid match or no valid button, buttons
     * whose key is not an uppercase keycode name, malformed hex ids, and any profile whose id was already seen
     * (the first one wins). Malformed JSON gives an empty list.
     */
    static List<Profile> parse(String json) {
        List<Profile> out = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        try {
            JSONArray a = new JSONArray(json);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null) continue;
                String id = o.optString("id").trim(), name = o.optString("name").trim();
                if (id.isEmpty() || name.isEmpty() || ids.contains(id)) continue;
                List<int[]> match = new ArrayList<>();
                JSONArray ma = o.optJSONArray("match");
                for (int k = 0; ma != null && k < ma.length(); k++) {
                    JSONObject m = ma.optJSONObject(k);
                    int v = m == null ? -1 : hex(m.optString("vendor")), p = m == null ? -1 : hex(m.optString("product"));
                    if (v >= 0 && p >= 0) match.add(new int[]{v, p});
                }
                List<Button> buttons = new ArrayList<>();
                JSONArray ba = o.optJSONArray("buttons");
                for (int k = 0; ba != null && k < ba.length(); k++) {
                    JSONObject b = ba.optJSONObject(k);
                    if (b == null) continue;
                    String key = b.optString("key").trim(), bn = b.optString("name").trim();
                    if (!key.matches("[A-Z0-9_]+") || bn.isEmpty()) continue;
                    buttons.add(new Button(key, bn, b.optString("default").trim()));
                }
                if (match.isEmpty() || buttons.isEmpty()) continue;
                ids.add(id);
                out.add(new Profile(id, name, o.optString("notes").trim(), Collections.unmodifiableList(match),
                        Collections.unmodifiableList(buttons)));
            }
        } catch (Exception e) {
            return new ArrayList<>();
        }
        return out;
    }

    /** "0955" or "0x0955" to its value; -1 if it is not 1 to 4 hex digits. */
    static int hex(String s) {
        String h = s == null ? "" : s.trim();
        if (h.startsWith("0x") || h.startsWith("0X")) h = h.substring(2);
        return h.matches("[0-9a-fA-F]{1,4}") ? Integer.parseInt(h, 16) : -1;
    }

    /** The profile for this vendor / product id, or null. */
    static Profile forDevice(List<Profile> profiles, int vendor, int product) {
        for (Profile p : profiles) if (p.matches(vendor, product)) return p;
        return null;
    }

    /** True if this key event comes from a profiled remote that lists the key as a button. */
    static boolean handles(Context c, KeyEvent e) {
        InputDevice d = e.getDevice();
        if (d == null) return false;
        Profile p = forDevice(load(c), d.getVendorId(), d.getProductId());
        if (p == null) return false;
        String name = KeyEvent.keyCodeToString(e.getKeyCode());
        return p.button(name.startsWith("KEYCODE_") ? name.substring(8) : name) != null;
    }
}
