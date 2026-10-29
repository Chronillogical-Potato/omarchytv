package com.omarchytv.home.lights;

/**
 * A smart-lights hub OmarchyTV can paint with the active Omarchy theme. Implementations do blocking
 * network I/O and must be called off the main thread (see {@link Lights}).
 * Add a hub: implement this, give it an id, and return it from {@link Lights#current}.
 */
public interface LightsHub {
    /** Stable id stored in prefs: "hue", "homeassistant". */
    String id();

    /** True when enough is configured (address + credentials + target) to send colours. */
    boolean configured();

    /**
     * Paint the chosen lights.
     *
     * @param accent        primary theme colour 0xRRGGBB
     * @param accent2       secondary theme colour
     * @param bg            theme background colour
     * @param brightnessPct user brightness 0..100
     * @param dim           cinema mode: very low, warm-ish bias level
     */
    void apply(int accent, int accent2, int bg, int brightnessPct, boolean dim) throws Exception;

    /** Short human status for the settings row ("ok · 3 lights", "error: unauthorized"). Never contains secrets. */
    String test();
}
