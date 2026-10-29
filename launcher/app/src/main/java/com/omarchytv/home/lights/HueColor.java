package com.omarchytv.home.lights;

/**
 * sRGB -> CIE 1931 xy conversion for Philips Hue lights (original implementation of the publicly
 * documented formula: sRGB gamma expansion, Wide-Gamut D65 matrix, then clamp to the lamp gamut).
 * Pure Java, no Android dependencies, so it is unit-tested on the JVM.
 */
public final class HueColor {
    /** Gamut C (most current Hue colour lamps): red, green, blue corners. */
    static final double[][] GAMUT_C = {{0.6915, 0.3083}, {0.17, 0.7}, {0.1532, 0.0475}};

    private HueColor() {
    }

    /** @return {x, y} for an 0xRRGGBB (alpha ignored). Black returns the D65 white point. */
    public static double[] rgbToXy(int rgb) {
        double r = gamma(((rgb >> 16) & 0xFF) / 255.0);
        double g = gamma(((rgb >> 8) & 0xFF) / 255.0);
        double b = gamma((rgb & 0xFF) / 255.0);
        double X = r * 0.664511 + g * 0.154324 + b * 0.162028;
        double Y = r * 0.283881 + g * 0.668433 + b * 0.047685;
        double Z = r * 0.000088 + g * 0.072310 + b * 0.986039;
        double sum = X + Y + Z;
        if (sum <= 0) return new double[]{0.3127, 0.3290};
        double[] xy = {X / sum, Y / sum};
        xy = clampToGamut(xy, GAMUT_C);
        return new double[]{round4(xy[0]), round4(xy[1])};
    }

    /** Hue "bri" 1..254 from the colour's luminance scaled by a 0..100 user brightness. */
    public static int bri(int rgb, int brightnessPct) {
        int pct = Math.max(0, Math.min(100, brightnessPct));
        int max = Math.max((rgb >> 16) & 0xFF, Math.max((rgb >> 8) & 0xFF, rgb & 0xFF));
        int v = (int) Math.round(max / 255.0 * 254 * pct / 100.0);
        return Math.max(1, Math.min(254, v));
    }

    private static double gamma(double c) {
        return c > 0.04045 ? Math.pow((c + 0.055) / 1.055, 2.4) : c / 12.92;
    }

    private static double round4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }

    static double[] clampToGamut(double[] p, double[][] g) {
        if (inside(p, g)) return p;
        double[] best = null;
        double bestD = Double.MAX_VALUE;
        for (int i = 0; i < 3; i++) {
            double[] c = closest(g[i], g[(i + 1) % 3], p);
            double d = (c[0] - p[0]) * (c[0] - p[0]) + (c[1] - p[1]) * (c[1] - p[1]);
            if (d < bestD) {
                bestD = d;
                best = c;
            }
        }
        return best;
    }

    private static boolean inside(double[] p, double[][] g) {
        double d1 = cross(p, g[0], g[1]), d2 = cross(p, g[1], g[2]), d3 = cross(p, g[2], g[0]);
        boolean neg = d1 < 0 || d2 < 0 || d3 < 0, pos = d1 > 0 || d2 > 0 || d3 > 0;
        return !(neg && pos);
    }

    private static double cross(double[] p, double[] a, double[] b) {
        return (p[0] - b[0]) * (a[1] - b[1]) - (a[0] - b[0]) * (p[1] - b[1]);
    }

    private static double[] closest(double[] a, double[] b, double[] p) {
        double abx = b[0] - a[0], aby = b[1] - a[1];
        double t = ((p[0] - a[0]) * abx + (p[1] - a[1]) * aby) / (abx * abx + aby * aby);
        t = Math.max(0, Math.min(1, t));
        return new double[]{a[0] + abx * t, a[1] + aby * t};
    }
}
