package com.omarchytv.home;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * OmarchyTV "Aquarium" screensaver, homage edition (v2.13): my personal tribute to the 90s demoscene,
 * asciiquarium meets Future Crew's Second Reality (1993). Inspired by asciiquarium by Kirk Baucom (GPL);
 * no asciiquarium code or ASCII art is copied. All sprites, effects and music are my own original work.
 *
 * <p>The scene is a 112x36 character grid cycling through five demo-style parts (Surface, Descent, Mid waters,
 * Abyss with the glenz vector, End credits). The same deterministic tracker song as the web saver
 * ({@code webos/app/saver/homage.js}) is synthesised here at 48 kHz stereo; the VU meters read the real
 * per-channel RMS of the audio that was just written.</p>
 */
final class AquariumHomage {
    static final int COLS = 112, ROWS = 36, RULER_COL = 103;
    static final String[] PART_NAMES = {"SURFACE", "DESCENT", "MID WATERS", "ABYSS", "END CREDITS"};
    static final int[] BPM = {108, 88, 100, 66, 125};
    static final int[][][] CHORDS = {
            {{48, 52, 55, 59}, {45, 48, 52, 55}, {41, 45, 48, 52}, {43, 47, 50, 55}},
            {{45, 48, 52, 55}, {41, 45, 48, 52}, {38, 41, 45, 48}, {40, 43, 47, 50}},
            {{50, 53, 57, 60}, {43, 47, 50, 53}, {45, 48, 52, 55}, {48, 52, 55, 59}},
            {{48, 51, 55, 62}, {44, 48, 51, 55}, {41, 44, 48, 51}, {43, 46, 50, 55}},
            {{48, 51, 55, 58}, {44, 48, 51, 55}, {41, 44, 48, 51}, {43, 47, 50, 53}}
    };
    static final int[][] MEL = {
            {0, -1, -1, 2, -1, -1, 1, -1, 3, -1, -1, -1, 2, -1, 1, -1, 0, -1, -1, -1, 4, -1, 3, -1, 2, -1, -1, 1, -1, -1, -1, -1},
            {2, -1, 3, -1, 4, -1, -1, 3, -1, 2, -1, -1, 1, -1, -1, -1, 2, -1, 1, -1, 0, -1, -1, -1, -1, -1, 1, -1, 2, -1, -1, -1},
            {4, -1, -1, -1, 3, -1, 2, -1, -1, -1, 0, -1, 1, -1, 2, -1, 3, -1, -1, 2, -1, -1, 4, -1, -1, -1, -1, -1, 3, -1, -1, -1}
    };
    static final float[] PAN = {-0.75f, 0.75f, 0.6f, -0.6f};
    static final int SINE = 0, TRI = 1, SAW = 2, SQUARE = 3;

    static double mf(int m) {
        return 440.0 * Math.pow(2, (m - 69) / 12.0);
    }

    static boolean leviathanWindow(double fr) {
        return fr > 0.45 && fr < 0.8;
    }

    // ---------------------------------------------------------------- song (same as homage.js)

    static final class Ev {
        boolean part;
        int p, ch, wave;
        double t, f, dur, vel, atk, rel;
        double[] arp;
    }

    static List<Ev> songEvents(double t0, double t1, double partSec) {
        List<Ev> out = new ArrayList<>();
        int k0 = Math.max(0, (int) Math.floor(t0 / partSec)), k1 = (int) Math.floor(t1 / partSec);
        for (int k = k0; k <= k1; k++) {
            int p = k % 5, cyc = k / 5;
            double ps = k * partSec, sd = 15.0 / BPM[p];
            if (ps >= t0 && ps < t1) {
                Ev e = new Ev();
                e.part = true;
                e.p = p;
                e.t = ps;
                out.add(e);
            }
            int s = Math.max(0, (int) Math.ceil((t0 - ps) / sd - 1e-9));
            for (; ; s++) {
                double t = ps + s * sd;
                if (t >= t1 || t >= ps + partSec - 0.05) break;
                genStep(out, p, s, cyc, t, sd, (t - ps) / partSec);
            }
        }
        return out;
    }

    private static void ev(List<Ev> out, int ch, double t, int m, double dur, double vel, int wave, double atk, double rel, double[] arp) {
        Ev e = new Ev();
        e.ch = ch;
        e.t = t;
        e.f = mf(m);
        e.dur = dur;
        e.vel = vel;
        e.wave = wave;
        e.atk = atk;
        e.rel = rel;
        e.arp = arp;
        out.add(e);
    }

    private static void ev(List<Ev> out, int ch, double t, int m, double dur, double vel, int wave) {
        ev(out, ch, t, m, dur, vel, wave, 0.008, 0.08, null);
    }

    private static void genStep(List<Ev> out, int p, int s, int cyc, double t, double sd, double fr) {
        int bar = s / 16, st = s % 16;
        int[] ch = CHORDS[p][bar % 4];
        int n = ch.length;
        int[] mel = MEL[(bar / 2 + cyc + p) % 3];
        int mi = s % 32;
        int tr = (cyc % 2 == 1 && p == 4) ? 2 : 0;
        if (p == 0) {
            if (st == 0 || st == 6 || st == 8 || st == 14) ev(out, 0, t, ch[0] - 12, sd * 1.6, 0.34, SAW);
            if (s % 2 == 0) ev(out, 1, t, ch[(s / 2) % n] + 12, sd * 1.2, 0.16, TRI);
            if (st == 0) for (int i = 0; i < 3; i++) ev(out, 2, t, ch[i] + 12, sd * 15, 0.05, TRI, 0.35, 1.2, null);
            if (mel[mi] >= 0 && bar % 4 >= 2) ev(out, 3, t, ch[mel[mi] % n] + 24, sd * 2.5, 0.06, SQUARE, 0.01, 0.18, null);
        } else if (p == 1) {
            if (st == 0 || st == 8) ev(out, 0, t, ch[0] - 12, sd * 7, 0.3, SINE, 0.02, 0.4, null);
            if (s % 4 == 0) ev(out, 1, t, ch[(n - 1) - (s / 4) % n] + 12, sd * 3, 0.12, TRI, 0.01, 0.3, null);
            if (st == 0) for (int j = 0; j < 3; j++) ev(out, 2, t, ch[j] + 12, sd * 15.5, 0.055, SINE, 0.6, 1.8, null);
            if (s % 32 == 0) ev(out, 3, t, 88, 0.3, 0.07, SINE, 0.005, 0.25, null);
            if (s % 32 == 3) ev(out, 3, t, 88, 0.3, 0.025, SINE, 0.005, 0.25, null);
        } else if (p == 2) {
            if (st == 0 || st == 3 || st == 6 || st == 10 || st == 12)
                ev(out, 0, t, ch[0] - 12 + (st == 10 ? 7 : 0), sd * 1.4, 0.32, SAW);
            if (st % 4 == 0)
                ev(out, 1, t, ch[0] + 12, sd * 2, 0.07, SQUARE, 0.006, 0.06, new double[]{mf(ch[0] + 12), mf(ch[1] + 12), mf(ch[2] + 12)});
            if (st == 0) for (int q = 0; q < 3; q++) ev(out, 2, t, ch[q] + 12, sd * 15, 0.05, TRI, 0.4, 1.4, null);
            if (mel[mi] >= 0) ev(out, 3, t, ch[mel[mi] % n] + 24, sd * 2.2, 0.05, TRI, 0.01, 0.2, null);
        } else if (p == 3) {
            if (st == 0) ev(out, 0, t, ch[0] - 24, sd * 15.5, 0.36, SINE, 0.5, 2.0, null);
            if (st == 0) for (int a = 0; a < n; a++) ev(out, 2, t, ch[a] + (a % 2 == 1 ? 12 : 0), sd * 15.8, 0.06, SAW, 1.2, 3.0, null);
            if (st == 0) ev(out, 3, t, 88, 0.35, 0.06, SINE, 0.005, 0.3, null);
            if (st == 6) ev(out, 3, t, 88, 0.35, 0.02, SINE, 0.005, 0.3, null);
            if (leviathanWindow(fr) && s % 2 == 0) ev(out, 1, t, ch[(s / 2) % n] + 24, sd * 1.6, 0.09, TRI, 0.008, 0.3, null);
        } else {
            int[] bl = {0, 0, 12, 0, 7, 0, 10, 12};
            if (s % 2 == 0) ev(out, 0, t, ch[0] - 12 + bl[(s / 2) % 8] + tr, sd * 1.7, 0.3, SAW);
            ev(out, 1, t, ch[(s + (s >> 2)) % n] + 12 + tr, sd * 0.9, 0.1, TRI);
            if (st % 8 == 0) for (int b = 0; b < 3; b++) ev(out, 2, t, ch[b] + 12 + tr, sd * 7.5, 0.045, TRI, 0.15, 0.8, null);
            if (mel[mi] >= 0) ev(out, 3, t, ch[mel[mi] % n] + 24 + tr, sd * 2, 0.055, SQUARE, 0.01, 0.15, null);
        }
    }

    /** Same envelope as the web version: linear attack, gentle decay to 65 %, linear release (no clicks). */
    static double env(Ev e, double dt) {
        if (dt < 0) return 0;
        if (dt < e.atk) return e.vel * dt / e.atk;
        if (dt < e.dur) return e.vel * (1 - 0.35 * (dt - e.atk) / Math.max(0.001, e.dur - e.atk));
        double r = (dt - e.dur) / e.rel;
        return r >= 1 ? 0 : e.vel * 0.65 * (1 - r);
    }

    static double depthAt(double t, double partSec) {
        int k = (int) Math.floor(t / partSec), p = k % 5;
        double fr = (t - k * partSec) / partSec;
        if (p == 0) return 6 + Math.sin(t * 0.4) * 1.5;
        if (p == 1) return 6 + ease(fr) * 214;
        if (p == 2) return 220 + fr * 180;
        if (p == 3) return 400 + ease(fr / 0.25) * 560 + fr * 40;
        return 1000;
    }

    static float clamp(float v, float a, float b) {
        return v < a ? a : (v > b ? b : v);
    }

    static double ease(double x) {
        x = Math.max(0, Math.min(1, x));
        return x * x * (3 - 2 * x);
    }

    static float easeF(double x) {
        return (float) ease(x);
    }

    static float tri(double x) {
        x = x - Math.floor(x);
        return (float) (x < 0.5 ? x * 2 : 2 - x * 2);
    }

    static int mix(int a, int b, float t) {
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return 0xFF000000 | (Math.round(ar + (br - ar) * t) << 16) | (Math.round(ag + (bg - ag) * t) << 8) | Math.round(ab + (bb - ab) * t);
    }

    // ---------------------------------------------------------------- original sprites

    private static final Map<Character, Character> MIR = new HashMap<>();

    static {
        String a = "<>()[]{}/\\◖◗▸◂▶◀◢◣◥◤▌▐╱╲◁▷‹›";
        for (int i = 0; i + 1 < a.length(); i += 2) {
            MIR.put(a.charAt(i), a.charAt(i + 1));
            MIR.put(a.charAt(i + 1), a.charAt(i));
        }
    }

    static String[] mirror(String[] lines) {
        int w = 0;
        for (String l : lines) w = Math.max(w, l.length());
        String[] out = new String[lines.length];
        for (int j = 0; j < lines.length; j++) {
            StringBuilder l = new StringBuilder(lines[j]);
            while (l.length() < w) l.append(' ');
            StringBuilder r = new StringBuilder();
            for (int k = l.length() - 1; k >= 0; k--) {
                char c = l.charAt(k);
                Character m = MIR.get(c);
                r.append(m != null ? m : c);
            }
            out[j] = r.toString();
        }
        return out;
    }

    static final class Species {
        final String[] art;
        final int col; // 0 fg 1 cyan 2 yellow 3 green 4 accent2 5 magenta 6 accent
        final float spd;
        final boolean school;

        Species(String[] art, int col, float spd, boolean school) {
            this.art = art;
            this.col = col;
            this.spd = spd;
            this.school = school;
        }
    }

    static final Species[] SPECIES = {
            new Species(new String[]{"≡≡▸"}, 0, 7.5f, true),
            new Species(new String[]{"~=◉>"}, 1, 6.5f, true),
            new Species(new String[]{" ◥◣ ", "▐█◉▶", " ◢◤ "}, 2, 4.2f, false),
            new Species(new String[]{"╱▀▀▀▀╲ ", "▐ ≈≈ ◉▶", "╲▄▄▄▄╱ "}, 3, 3.4f, false),
            new Species(new String[]{" .ooo.  ", "{ ◉   ●>", " 'ooo'  "}, 4, 2.6f, false),
            new Species(new String[]{"  ▲   ", "◁◖ ● )", "  ▼   "}, 5, 3.0f, false),
            new Species(new String[]{"∽∿∽∿∽∿◖◉>"}, 6, 3.8f, false),
            new Species(new String[]{"·◖◉>"}, 2, 2.4f, false)
    };
    static final int[][] POOLS = {{0, 1, 2, 3, 4, 5}, {0, 1, 3, 5, 6}, {0, 1, 2, 3, 4, 5, 6}, {6, 7, 1}, {1, 7}};
    static final int[] FISH_TARGET = {14, 16, 22, 9, 6};

    static final String[] SHARK = {"             ▄█▀             ", "  ▄▄▄▄▄▄▄▄▄▄██████▄▄▄▄▄▄▄▄▄▄  ", "◀█▀▀▀▀▀▀▀██████████████◉ ▀▀▄ ", "  ▀▀▀▀▀▀▀▀▀▀▀▄█▀▀▀▀▀▀ ▼▼▼▼▀▀  ", "               ▀             "};
    static final String[] SHARK_L = mirror(SHARK);
    static final String[] WHALE = {"       ▄▄▄▄▄▄▄▄▄▄▄▄▄          ", "   ▄██████████████████▄▄  ▄█▀", " ▐█◉██████████████████████▀  ", "  ▀▀▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▀▀▀       "};
    static final String[][] SPOUT = {{"   .  .  ", "    ::   ", "    ¦    "}, {" ·  ¦  · ", "  ·:¦:·  ", "    ¦    "}, {"·   :   ·", "  ` ¦ `  ", "    ¦    "}};
    static final String[] SHIP = {"       │╲        ", "       │▓╲   ⚑   ", "       │▓▓╲  │   ", "  ▄▄▄▄▄█▄▄▄▄█▄▄  ", "  ╲  OMARCHY  ╱  "};
    static final String[] DUCK = {" ◖°>", "◥██◤"};
    static final String[] MANTA = {"    ▁▂▄▆█▆▄▂▁    ", "◥◣▄▄  ◉   ◉  ▄▄◢◤", "       ╹╹╹       "};
    static final String[][] JELLY = {
            {"   .─▀▀▀─.   ", "  (  ● ●  )  ", "   `─┬─┬─'   ", "    / │ \\    ", "   ~  ~  ~   "},
            {"  .──▀▀▀──.  ", " (   ● ●   ) ", "  `──┬─┬──'  ", "    │ │ │    ", "    ~ ~ ~    "}};
    static final String[] ANGLER = {"     .---.               ", "    /     \\  ___         ", "   *       `/   \\___/|   ", "          /  ●  /    |   ", "         <  ▲▲▲      |   ", "          \\_____\\___\\|   "};
    static final String[] ANGLER_R = mirror(ANGLER);
    static final String[] LEVIATHAN = {
            "       .---.   .·°ºO                                 ",
            "   ___/     \\___                                     ",
            "  /   '-----'   \\____________________________________",
            " /  ●   ▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄       /  \\",
            "|      █████████████████████████████████████=====(    |",
            " \\      ▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀       \\__/",
            "  \\___,-----,___/¯¯¯¯¯¯¯¯¯¯¯¯¯¯¯¯¯¯¯¯¯¯¯¯¯¯¯¯¯¯¯¯¯¯¯¯"};
    static final String LOGO1 = "█▀█ █▀▄▀█ ▄▀█ █▀█ █▀▀ █ █ █ █", LOGO2 = "█▄█ █ ▀ █ █▀█ █▀▄ █▄▄ █▀█  █ ";
    static final String[] CASTLE = buildCastle();
    static final String GREETZ = "      OMARCHY TV  //  THE AQUARIUM  //  AN ASCIIQUARIUM X SECOND REALITY HOMAGE BY MARCO PORRO  //  CODE, PIXELS AND TRACKER TUNE: ALL ORIGINAL  //  GREETZ TO FUTURE CREW (SECOND REALITY, ASSEMBLY 1993), KIRK BAUCOM (ASCIIQUARIUM), DHH AND THE OMARCHY CREW, @ERIKRJOHANSSON, @TCBALLARD AND EVERY SCENER WHO EVER STAYED UP ALL NIGHT  //  PRESS M TO MUTE  //  ";
    static final String SMALL_SCROLL = "   ★ OMARCHY TV · AQUARIUM · ASCIIQUARIUM x SECOND REALITY 1993//2026 · CODE & PIXELS & MUSIC BY MARCO PORRO · GREETZ TO FUTURE CREW & KIRK BAUCOM & DHH ★   ";

    private static String rep(char c, int n) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < n; i++) s.append(c);
        return s.toString();
    }

    private static String put(String line, int x, String s) {
        return line.substring(0, x) + s + line.substring(x + s.length());
    }

    static String[] buildCastle() {
        int w = LOGO1.length() + 6, mid = w / 2;
        String tw = "▐█▌";
        String r0 = put(put(put(rep(' ', w), 2, " ▲ "), w - 5, " ▲ "), mid - 1, " ⚑ ");
        String r1 = put(put(put(rep(' ', w), 2, tw), w - 5, tw), mid - 1, tw);
        String r2 = put(put(rep('▄', w), 2, tw), w - 5, tw);
        r2 = "▐" + r2.substring(1, w - 1) + "▌";
        StringBuilder cren = new StringBuilder();
        for (int i = 0; i < w; i++) cren.append(i % 3 == 0 ? '▄' : ' ');
        String gap = "█" + rep(' ', w - 2) + "█";
        return new String[]{r0, r1, cren.toString(), r2, gap, "█  " + LOGO1 + "  █", "█  " + LOGO2 + "  █", gap,
                "█" + rep('▄', mid - 3) + "█▀▀▀▀█" + rep('▄', w - mid - 5) + "█"};
    }

    // ---------------------------------------------------------------- world state

    static final class Fish {
        Species sp;
        String[] art;
        int dir, z;
        float x, y, spd, wa, wf, ph, bub, eaten;
    }

    static final class Bubble {
        float x, y, age, ph, vy;
        boolean near;
    }

    static final class Big {
        float x, y, w, h, yo;
        int dir;
        boolean fixed;

        Big(float x, float y, float w, float h, int dir) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.dir = dir;
        }
    }

    private final Random rnd = new Random(1993);
    private final List<Fish> fish = new ArrayList<>();
    private final List<Bubble> bubbles = new ArrayList<>();
    private final List<float[]> bursts = new ArrayList<>(); // x, y, age
    private final Big shark = new Big(-40, 15, 30, 5, 1), manta = new Big(120, 10, 17, 3, -1), lensB = new Big(40, 18, 12, 10, 0),
            angler = new Big(110, 24, 25, 6, -1), glenzBox = new Big(40, 10.5f, 24, 14, 0);
    private final Big[] jelly = {new Big(14, 12, 13, 5, 0), new Big(84, 20, 13, 5, 0)};

    private final Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint add = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Map<Character, boolean[]> chunky = new HashMap<>();
    private float cw = 17, ch = 30, fs = 26;
    private double lastSec = -1;
    private Theme T;
    private double depth;

    final float partSec;
    final float[] vu = new float[4];
    volatile int bpmNow = BPM[0];

    AquariumHomage(Typeface tf, float partSec) {
        this.partSec = partSec;
        tp.setTypeface(Typeface.create(tf, Typeface.BOLD));
        tp.setTextAlign(Paint.Align.LEFT);
        add.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.ADD));
        for (int i = 0; i < 4; i++) spawnFish(0, false);
    }

    private void spawnFish(int part, boolean fromEdge) {
        int[] pool = POOLS[part];
        Species sp = SPECIES[pool[rnd.nextInt(pool.length)]];
        int dir = rnd.nextFloat() < 0.5f ? 1 : -1;
        int z = rnd.nextFloat() < 0.35f ? 0 : (rnd.nextFloat() < 0.6f ? 1 : 2);
        float lane = 7 + rnd.nextFloat() * 22;
        int n = sp.school ? 3 + rnd.nextInt(4) : 1;
        if (part == 0) lane = 19 + rnd.nextFloat() * 11;
        float baseX = fromEdge ? (dir > 0 ? -8 - rnd.nextFloat() * 20 : RULER_COL + 4 + rnd.nextFloat() * 20) : rnd.nextFloat() * 95;
        float spd = sp.spd * (z == 0 ? 0.5f : (z == 1 ? 0.8f : 1f)) * (0.8f + rnd.nextFloat() * 0.4f);
        String[] art = dir > 0 ? sp.art : mirror(sp.art);
        for (int i = 0; i < n; i++) {
            Fish f = new Fish();
            f.sp = sp;
            f.art = art;
            f.dir = dir;
            f.z = z;
            f.x = baseX - dir * (i * (2.5f + rnd.nextFloat() * 4));
            f.y = lane + (rnd.nextFloat() - 0.5f) * 3;
            f.spd = spd * (0.88f + rnd.nextFloat() * 0.24f);
            f.wa = 0.25f + rnd.nextFloat() * 0.6f;
            f.wf = 0.6f + rnd.nextFloat() * 1.4f;
            f.ph = rnd.nextFloat() * 6.28f;
            f.bub = 2 + rnd.nextFloat() * 6;
            fish.add(f);
        }
    }

    private int speciesColor(int c) {
        switch (c) {
            case 1: return T.cyan;
            case 2: return T.yellow;
            case 3: return T.green;
            case 4: return T.accent2;
            case 5: return T.magenta;
            case 6: return T.accent;
            default: return T.fg;
        }
    }

    private int bg(double d) {
        return mix(T.bg, 0xFF000000, clamp((float) (d / 1000), 0, 1) * 0.8f);
    }

    // ---------------------------------------------------------------- text helpers (Skia caches each glyph once)

    private void txt(Canvas c, String s, float col, float row, int color, float alpha, float sc, float clipR) {
        if (alpha <= 0.01f) return;
        tp.setTextSize(fs * sc);
        tp.setColor(color);
        tp.setAlpha(Math.round(255 * clamp(alpha, 0, 1)));
        float y = (row - 0.12f * sc) * ch + ch * sc * 0.92f;
        char[] one = new char[1];
        for (int i = 0; i < s.length(); i++) {
            char k = s.charAt(i);
            if (k == ' ') continue;
            float cx = col + i * sc;
            if (cx < -2 || cx > clipR - sc) continue;
            one[0] = k;
            c.drawText(one, 0, 1, cx * cw, y, tp);
        }
    }

    private void sprite(Canvas c, String[] lines, float col, float row, int color, Integer hi, float alpha, float sc) {
        for (int r = 0; r < lines.length; r++) {
            String l = lines[r];
            boolean h = hi != null && (l.indexOf('◉') >= 0 || l.indexOf('●') >= 0 || l.indexOf('*') >= 0);
            txt(c, l, col, row + r * sc, h ? hi : color, alpha, sc, RULER_COL);
        }
    }

    /** VGA 8x16 style chunky glyph: the bundled Caskaydia Mono (OFL) rasterised at 8x16, drawn as blocks. */
    private boolean[] chunk(char k) {
        boolean[] b = chunky.get(k);
        if (b != null) return b;
        Bitmap bm = Bitmap.createBitmap(8, 16, Bitmap.Config.ARGB_8888);
        Canvas cc = new Canvas(bm);
        Paint p = new Paint();
        p.setTypeface(tp.getTypeface());
        p.setTextSize(14);
        p.setColor(Color.WHITE);
        cc.drawText(String.valueOf(k), 0, 13, p);
        b = new boolean[128];
        for (int i = 0; i < 128; i++) b[i] = Color.alpha(bm.getPixel(i % 8, i / 8)) > 100;
        bm.recycle();
        chunky.put(k, b);
        return b;
    }

    private void copper(Canvas c, float y0, float y1, int n, double sec, float amp, float alpha, float w) {
        int[] cols = {T.accent, T.magenta, T.cyan, T.yellow, T.green, T.red, T.accent2};
        for (int i = 0; i < n; i++) {
            float cy = (float) (y0 + (y1 - y0) * (0.5 + 0.5 * Math.sin(sec * 1.3 + i * 0.62)));
            float hh = Math.max(4, ch * 0.42f) * amp;
            int col = cols[i % cols.length];
            fill.setShader(new LinearGradient(0, cy - hh, 0, cy + hh, new int[]{col & 0x00FFFFFF, col, col & 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
            fill.setAlpha(Math.round(255 * alpha));
            c.drawRect(0, cy - hh, w, cy + hh, fill);
        }
        fill.setShader(null);
        fill.setAlpha(255);
    }

    // ---------------------------------------------------------------- glenz vector

    private static final float[][] GV = {{0, 1.3f, 0}, {0, -1.3f, 0}, {1.3f, 0, 0}, {-1.3f, 0, 0}, {0, 0, 1.3f}, {0, 0, -1.3f}};
    private static final int[][] GF = {{0, 2, 4}, {0, 4, 3}, {0, 3, 5}, {0, 5, 2}, {1, 4, 2}, {1, 3, 4}, {1, 5, 3}, {1, 2, 5}};
    private final float[][] tri = new float[32][11]; // 3 verts xyz, colour, z

    private void glenz(Canvas c, float cxg, float cyg, float rad, double sec, float alpha) {
        double ax = sec * 0.9, ay = sec * 1.37, az = sec * 0.41;
        float cax = (float) Math.cos(ax), sax = (float) Math.sin(ax), cay = (float) Math.cos(ay), say = (float) Math.sin(ay), caz = (float) Math.cos(az), saz = (float) Math.sin(az);
        float bounce = (float) Math.abs(Math.sin(sec * 1.6)), sq = 1 - 0.12f * (float) Math.pow(1 - bounce, 6);
        float cxp = cxg * cw, cyp = (cyg - bounce * 2.2f) * ch, R = rad * ch;
        int ti = 0;
        for (int f = 0; f < GF.length; f++) {
            float[] a = GV[GF[f][0]], b = GV[GF[f][1]], d = GV[GF[f][2]];
            float[] ab = mid(a, b), bd = mid(b, d), da = mid(d, a);
            float[][][] sub = {{a, ab, da}, {ab, b, bd}, {da, bd, d}, {ab, bd, da}};
            for (int s = 0; s < 4; s++) {
                float[] t = tri[ti++];
                float zs = 0;
                for (int v = 0; v < 3; v++) {
                    float x = sub[s][v][0], y = sub[s][v][1], z = sub[s][v][2], q;
                    q = x * cay - z * say; z = x * say + z * cay; x = q;
                    q = y * cax - z * sax; z = y * sax + z * cax; y = q;
                    q = x * caz - y * saz; y = x * saz + y * caz; x = q;
                    t[v * 3] = x; t[v * 3 + 1] = y; t[v * 3 + 2] = z;
                    zs += z;
                }
                t[9] = ((s == 3 ? 1 : 0) + f) % 2;
                t[10] = zs / 3;
            }
        }
        float[][] order = Arrays.copyOf(tri, 32);
        Arrays.sort(order, (m, n) -> Float.compare(n[10], m[10]));
        fill.setColor(0xFF000000);
        fill.setAlpha(Math.round(255 * 0.18f * alpha * (0.5f + 0.5f * bounce)));
        c.drawOval(cxp - R * 0.9f, (cyg + rad * 1.2f) * ch - R * 0.16f, cxp + R * 0.9f, (cyg + rad * 1.2f) * ch + R * 0.16f, fill);
        fill.setAlpha(255);
        for (float[] t : order) {
            float[] px = new float[3], py = new float[3];
            for (int v = 0; v < 3; v++) {
                float k = 3.4f / (3.4f + t[v * 3 + 2]);
                px[v] = cxp + t[v * 3] * R * k;
                py[v] = cyp + t[v * 3 + 1] * R * k * sq;
            }
            float ux = t[3] - t[0], uy = t[4] - t[1], vx = t[6] - t[0], vy = t[7] - t[1];
            boolean front = (ux * vy - uy * vx) < 0;
            path.reset();
            path.moveTo(px[0], py[0]);
            path.lineTo(px[1], py[1]);
            path.lineTo(px[2], py[2]);
            path.close();
            add.setStyle(Paint.Style.FILL);
            add.setColor(t[9] > 0.5f ? T.cyan : T.magenta);
            add.setAlpha(Math.round(255 * alpha * (front ? 0.42f : 0.2f)));
            c.drawPath(path, add);
            if (front) {
                fill.setStyle(Paint.Style.STROKE);
                fill.setStrokeWidth(1.2f);
                fill.setColor(T.fgBright);
                fill.setAlpha(Math.round(255 * alpha * 0.35f));
                c.drawPath(path, fill);
                fill.setStyle(Paint.Style.FILL);
                fill.setAlpha(255);
            }
        }
    }

    private static float[] mid(float[] a, float[] b) {
        return new float[]{(a[0] + b[0]) / 2, (a[1] + b[1]) / 2, (a[2] + b[2]) / 2};
    }

    // ---------------------------------------------------------------- simulation

    private void simulate(float dt, double sec, int part, double fr) {
        int alive = 0;
        for (int i = fish.size() - 1; i >= 0; i--) {
            Fish f = fish.get(i);
            if (f.eaten > 0) {
                f.eaten -= dt;
                if (f.eaten <= 0) fish.remove(i);
                continue;
            }
            f.x += f.dir * f.spd * dt;
            if ((f.dir > 0 && f.x > RULER_COL + 6) || (f.dir < 0 && f.x < -14)) {
                fish.remove(i);
                continue;
            }
            if (part == 0 && f.y < 19) f.y += dt * 2;
            f.bub -= dt;
            if (f.bub <= 0) {
                f.bub = 3 + rnd.nextFloat() * 7;
                Bubble b = new Bubble();
                b.x = f.x + (f.dir > 0 ? f.art[0].length() * 0.9f : 0);
                b.y = f.y;
                b.ph = rnd.nextFloat() * 6;
                b.near = f.z > 0;
                b.vy = 1.6f + rnd.nextFloat();
                bubbles.add(b);
            }
            alive++;
        }
        while (alive < FISH_TARGET[part]) {
            spawnFish(part, true);
            alive += 2;
        }
        float top = part == 0 ? 19.5f : 5.6f;
        for (int b = bubbles.size() - 1; b >= 0; b--) {
            Bubble u = bubbles.get(b);
            u.age += dt;
            u.y -= u.vy * dt;
            if (u.y < top || u.age > 9) bubbles.remove(b);
        }
        while (bubbles.size() > 120) bubbles.remove(0);
        for (int u = bursts.size() - 1; u >= 0; u--) {
            bursts.get(u)[2] += dt;
            if (bursts.get(u)[2] > 1) bursts.remove(u);
        }
        boolean sharkOn = part == 2 || (part == 3 && !leviathanWindow(fr));
        shark.x += shark.dir * 9 * dt;
        if (shark.x > RULER_COL + 10) { shark.dir = -1; shark.y = part == 3 ? 6 : 10 + rnd.nextFloat() * 12; }
        if (shark.x < -40) { shark.dir = 1; shark.y = part == 3 ? 6 : 10 + rnd.nextFloat() * 12; }
        if (sharkOn) {
            float mouth = shark.dir > 0 ? shark.x + 27 : shark.x + 1, my = shark.y + shark.yo + 2;
            for (Fish g : fish) {
                if (g.eaten > 0 || g.z == 0) continue;
                if (Math.abs(g.x - mouth) < 2.2f && Math.abs(g.y - my) < 1.6f) {
                    g.eaten = 0.01f;
                    bursts.add(new float[]{g.x, g.y - 0.5f, 0});
                    for (int q = 0; q < 4; q++) {
                        Bubble b = new Bubble();
                        b.x = g.x + rnd.nextFloat() * 2;
                        b.y = g.y;
                        b.age = rnd.nextFloat();
                        b.near = true;
                        b.vy = 2 + rnd.nextFloat();
                        bubbles.add(b);
                    }
                }
            }
        }
        manta.x += manta.dir * 2.6f * dt;
        if (manta.x < -20) { manta.dir = 1; manta.y = 8 + rnd.nextFloat() * 6; }
        if (manta.x > RULER_COL + 4) { manta.dir = -1; manta.y = 8 + rnd.nextFloat() * 6; }
        angler.x += angler.dir * 2.2f * dt;
        if (angler.x < -26) angler.dir = 1;
        if (angler.x > RULER_COL + 2) angler.dir = -1;
        for (int j = 0; j < 2; j++) {
            jelly[j].x = (j == 1 ? 82 : 12) + (float) Math.sin(sec * 0.21 + j * 2) * 8;
            jelly[j].y = (j == 1 ? 19 : 11) + (float) Math.sin(sec * 0.6 + j) * 2.5f;
        }
        lensB.x = 8 + tri(sec / 23) * (RULER_COL - 22);
        lensB.y = 8 + tri(sec / 13.7) * 18;
        List<Big> act = new ArrayList<>();
        if (sharkOn) act.add(shark);
        if (part == 2) { act.add(manta); act.add(lensB); act.add(jelly[0]); act.add(jelly[1]); }
        if (part == 3) { act.add(angler); glenzBox.fixed = true; act.add(glenzBox); }
        for (Big a : act) {
            if (a.fixed || a == lensB) continue;
            float push = 0;
            for (Big b : act) {
                if (a == b) continue;
                float ox = Math.min(a.x + a.w, b.x + b.w) - Math.max(a.x, b.x) + 2;
                float ay = a.y + a.yo + a.h / 2, by = b.y + b.yo + b.h / 2, need = (a.h + b.h) / 2 + 1;
                if (ox > 0 && Math.abs(ay - by) < need) push += (ay <= by ? -1 : 1) * (need - Math.abs(ay - by));
            }
            if (push != 0) a.yo += clamp(push, -1, 1) * 6 * dt;
            else a.yo *= Math.max(0, 1 - dt * 0.4f);
            a.yo = clamp(a.yo, 6 - a.y, 30 - a.h - a.y);
        }
    }

    // ---------------------------------------------------------------- drawing

    private void drawScene(Canvas c, double sec, int part, double fr) {
        float sf = (float) sec;
        float water = (float) (18 + (0 - depth) / 10) - 0.6f;
        float W = c.getWidth() > 0 ? COLS * cw : 1920;
        if (water > 4) {
            fill.setShader(new LinearGradient(0, 4 * ch, 0, water * ch, mix(T.bg, 0xFF000000, 0.5f), mix(T.bg, T.accent2, 0.25f), Shader.TileMode.CLAMP));
            c.drawRect(0, 5.3f * ch, W, water * ch, fill);
            fill.setShader(null);
            c.save();
            c.clipRect(0, 5.3f * ch, W, water * ch);
            copper(c, 6.5f * ch, (water - 1.5f) * ch, 6, sec * 0.7, 1.2f, 0.55f, W);
            c.restore();
            float shipX = (float) ((sec * 1.6) % (COLS + 40)) - 20, roll = (float) Math.sin(sec * 1.2) * 0.25f;
            sprite(c, SHIP, shipX, water - 4.3f + roll, T.fgBright, null, 1, 1);
            float wx = RULER_COL - (float) ((sec * 1.1 + 30) % (COLS + 40));
            float wy = water + 1.6f - (float) Math.max(0, Math.sin(sec * 0.35)) * 0.9f;
            sprite(c, WHALE, wx, wy, T.accent, T.fgBright, 1, 1);
            if (Math.sin(sec * 0.35) > 0.2) sprite(c, SPOUT[(int) (sec * 4) % 3], wx + 1, water - 3, T.cyan, null, 0.9f, 1);
            for (int d = 0; d < 3; d++) {
                float dx = ((shipX + 52 + d * 6) % (RULER_COL + 10)) - 4 + (float) Math.sin(sec * 0.3 + d);
                float dy = water - 1.3f + (float) Math.sin(sec * 2.1 + d) * 0.18f;
                sprite(c, DUCK, dx, dy, T.yellow, null, 1, 1);
            }
        }
        if (water > 4 && water < 40) {
            StringBuilder l1 = new StringBuilder(), l2 = new StringBuilder();
            for (int k = 0; k < COLS; k++) {
                double v = Math.sin(k * 0.35 - sec * 2.2) + Math.sin(k * 0.13 + sec * 1.3);
                l1.append(v > 1.1 ? '^' : (v > -0.6 ? '~' : '-'));
                l2.append(Math.sin(k * 0.21 + sec * 1.7) > 0.3 ? '~' : ' ');
            }
            txt(c, l1.toString(), 0, water, T.cyan, 1, 1, RULER_COL);
            txt(c, l2.toString(), 0, water + 1, T.accent, 0.45f, 1, RULER_COL);
        }
        float seabedVis = part == 2 ? easeF(fr * 6) : (part == 3 ? 1 : (part == 4 ? 1 - easeF(fr * 5) : 0));
        float bedRow = 31.2f + (1 - seabedVis) * 8;
        for (int z = 0; z < 3; z++) {
            float sc = z == 0 ? 0.72f : (z == 1 ? 0.86f : 1f), dim = z == 0 ? 0.6f : (z == 1 ? 0.3f : 0f);
            int bgc = bg(depth);
            for (Fish f : fish) {
                if (f.z != z || f.eaten > 0) continue;
                int col = speciesColor(f.sp.col);
                if (dim > 0) col = mix(col, bgc, dim);
                sprite(c, f.art, f.x, f.y + (float) Math.sin(sec * f.wf + f.ph) * f.wa, col, z == 2 ? T.fgBright : null, 1, sc);
            }
        }
        for (Bubble b : bubbles) {
            String g = b.age < 1.2f ? "." : (b.age < 2.6f ? "o" : "O");
            txt(c, g, b.x + (float) Math.sin(b.age * 3 + b.ph) * 0.4f, b.y, T.cyan, 0.75f * (b.near ? 1 : 0.5f), b.near ? 1 : 0.75f, RULER_COL);
        }
        for (float[] br : bursts) txt(c, br[2] < 0.4f ? "*CHOMP*" : "* · °", br[0], br[1], T.red, 1 - br[2], 1, RULER_COL);
        if (part == 2 || (part == 1 && fr > 0.5)) {
            float mA = part == 1 ? easeF((fr - 0.5) * 3) : 1;
            sprite(c, manta.dir > 0 ? mirror(MANTA) : MANTA, manta.x, manta.y + manta.yo, T.accent2, T.fgBright, mA, 1);
        }
        if (part == 2) {
            int jf = (int) (sec * 2.6) % 2;
            for (int j = 0; j < 2; j++) sprite(c, JELLY[jf], jelly[j].x, jelly[j].y + jelly[j].yo, j == 1 ? T.cyan : T.magenta, T.accent, 1, 1);
        }
        if (part == 2 || (part == 3 && !leviathanWindow(fr))) {
            sprite(c, shark.dir > 0 ? SHARK : SHARK_L, shark.x, shark.y + shark.yo, T.accent, T.red, 1, 1);
        }
        if (part == 3) {
            float lit = 0.6f + 0.4f * (float) Math.sin(sec * 3);
            sprite(c, angler.dir < 0 ? ANGLER : ANGLER_R, angler.x, angler.y + angler.yo, mix(T.magenta, 0xFF000000, 0.25f), T.yellow, 1, 1);
            if (angler.dir < 0) txt(c, "*", angler.x + 3, angler.y + angler.yo + 2, T.yellow, lit, 1, RULER_COL);
            if (leviathanWindow(fr)) {
                float lf = (float) ((fr - 0.45) / 0.35), lx = RULER_COL - lf * (COLS + 60) + 4;
                sprite(c, LEVIATHAN, lx, 7.2f, mix(T.accent2, 0xFF000000, 0.2f), T.cyan, 0.85f, 1);
                txt(c, "[ leviathan · sonar-lock ]", lx + 10, 6.2f, T.green, 0.8f, 1, RULER_COL);
            }
        }
        if (seabedVis > 0) {
            fill.setColor(T.accent);
            fill.setAlpha(Math.round(255 * 0.25f * seabedVis));
            c.drawRect(0, bedRow * ch, RULER_COL * cw, bedRow * ch + 1.5f, fill);
            fill.setAlpha(255);
            StringBuilder sand = new StringBuilder();
            for (int k = 0; k < RULER_COL; k++) sand.append((k * 7 + 3) % 11 < 3 ? '░' : ((k * 5) % 13 < 2 ? '▒' : '·'));
            txt(c, sand.toString(), 0, bedRow + 2.5f, mix(T.yellow, 0xFF000000, 0.55f), seabedVis, 1, RULER_COL);
            int[] kelp = {4, 9, 21, 27, 78, 83, 95, 99};
            for (int k = 0; k < kelp.length; k++)
                for (int sg = 0; sg < 5 + k % 3; sg++)
                    txt(c, (((int) (sec * 2) + sg + k) % 2 == 1) ? "(" : ")", kelp[k] + (float) Math.sin(sec * 1.6 + sg * 0.55 + k) * 0.6f,
                            bedRow + 1.6f - sg * 0.9f, k % 2 == 1 ? T.green : T.cyan, seabedVis * 0.9f, 1, RULER_COL);
            float cx = 33 + (float) (Math.sin(sec / 97) * 26 + Math.sin(sec / 41) * 4);
            sprite(c, CASTLE, cx, bedRow - 6.6f, T.accent, null, seabedVis, 1);
            float crabX = (float) ((sec * 1.3) % (RULER_COL + 30)) - 15;
            txt(c, ((int) (sec * 5) % 2 == 1) ? "(\\/)(°,,°)(\\/)" : "( /)(°,,°)(\\ )", crabX, bedRow + 1.5f, T.red, seabedVis, 1, RULER_COL);
        }
        if (part == 3) glenz(c, 52, 18.5f, 5.5f, sec, easeF(fr * 8));
        if (part == 4) glenz(c, 52, 10.5f, 3.6f, sec, 0.9f);
    }

    private void hud(Canvas c, double sec, int part, float hudA, boolean soundOn, String statusOverride) {
        String head = "[ omarchy://abyss-node · part " + (part + 1) + "/5 " + PART_NAMES[part] + " · depth " + Math.round(depth) + "m · bio-sonar: ONLINE "
                + (statusOverride != null ? "· " + statusOverride + " " : "") + "]";
        float a = statusOverride != null ? Math.max(hudA, 0.85f) : hudA;
        if (a > 0.01f) {
            c.save();
            c.clipRect(0, 0, 66 * cw, 1.2f * ch);
            txt(c, head, 1.5f, 0.15f, T.fgBright, a, 1, 66);
            c.restore();
            String vg = "▁▂▃▄▅▆▇█";
            StringBuilder v = new StringBuilder("ST3 ");
            for (int k = 0; k < 4; k++) {
                float l = soundOn ? vu[k] : 0;
                v.append('C').append(k + 1).append(':').append(vg.charAt((int) clamp(Math.round(l * 7), 0, 7))).append(' ');
            }
            v.append("· ").append(BPM[part]).append(" BPM · ♪ ").append(soundOn ? "ON" : "MUTED").append(" (M)");
            c.save();
            c.clipRect(67 * cw, 0, COLS * cw, 1.2f * ch);
            txt(c, v.toString(), 68, 0.15f, soundOn ? T.green : T.fgDim, a, 1, COLS);
            c.restore();
            String tick = "   theme: " + T.name + " · omarchy-link palette sync · glenz vector ready · lens online · asciiquarium x second reality   ";
            double off = (sec * 6) % tick.length();
            String s2 = tick + tick;
            int o = (int) off;
            c.save();
            c.clipRect(1.5f * cw, 1.15f * ch, (RULER_COL - 0.5f) * cw, 2.15f * ch);
            txt(c, s2.substring(o, Math.min(s2.length(), o + RULER_COL + 2)), 1.5f - (float) (off % 1), 1.2f, T.fgDim, a * 0.9f, 1, RULER_COL);
            c.restore();
        }
        c.save();
        c.clipRect((RULER_COL + 0.5f) * cw, 5.4f * ch, COLS * cw, 33.4f * ch);
        int first = (int) Math.floor((depth - 140) / 50) * 50;
        for (int mm = first; mm < depth + 160; mm += 50) {
            float row = (float) (18 + (mm - depth) / 10);
            if (mm < 0 || Math.abs(row - 0.5f - 17.5f) < 1.1f) continue;
            txt(c, "─ " + mm + "m", RULER_COL + 1, row - 0.5f, mm % 100 != 0 ? T.fgDim : T.fg, 0.7f, 1, COLS + 4);
        }
        txt(c, "◄" + Math.round(depth), RULER_COL + 1, 17.5f, T.yellow, 0.9f, 1, COLS + 4);
        c.restore();
    }

    private void smallScroller(Canvas c, double sec) {
        int len = SMALL_SCROLL.length(), off = (int) (sec * 12) % len;
        float ph = (float) (sec * 12 - Math.floor(sec * 12));
        c.save();
        c.clipRect(0, 3.5f * ch, RULER_COL * cw, 5.4f * ch);
        for (int i = 0; i < 72; i++) {
            char k = SMALL_SCROLL.charAt((off + i) % len);
            if (k == ' ') continue;
            txt(c, String.valueOf(k), 16 + i - ph, 4.05f + (float) Math.sin(sec * 3.4 + i * 0.22) * 0.3f,
                    i % 3 == 0 ? T.yellow : (i % 2 == 1 ? T.cyan : T.fgBright), 1, 1, COLS);
        }
        c.restore();
    }

    private void bigScroller(Canvas c, double sec, float rowC) {
        float px = Math.max(3, Math.round(ch * 0.4f)), cwid = 8 * px + px, speed = 160;
        float x0 = -(float) ((sec * speed) % cwid);
        int first = (int) ((sec * speed) / cwid), n = (int) Math.ceil(RULER_COL * cw / cwid) + 1;
        for (int i = 0; i < n; i++) {
            char k = GREETZ.charAt((first + i) % GREETZ.length());
            if (k == ' ') continue;
            boolean[] bits = chunk(k);
            float bx = x0 + i * cwid, by = rowC * ch + (float) Math.sin(sec * 2.2 + bx / 140) * ch * 2.2f;
            if (bx > RULER_COL * cw) break;
            for (int y = 0; y < 16; y++) {
                fill.setColor(mix(T.yellow, T.magenta, y / 15f));
                for (int x = 0; x < 8; x++)
                    if (bits[y * 8 + x]) c.drawRect(bx + x * px, by + y * px, bx + x * px + px - 1, by + y * px + px - 1, fill);
            }
        }
    }

    /** Porthole lens: the scene is re-drawn magnified inside a bouncing circle (no read-back, GPU friendly). */
    private void lens(Canvas c, double sec, int part, double fr, float alpha) {
        if (alpha <= 0.02f) return;
        float R = 5.2f * ch, cx = (lensB.x + 6) * cw, cy = (lensB.y + 5) * ch, mag = 1.7f;
        path.reset();
        path.addCircle(cx, cy, R, Path.Direction.CW);
        c.save();
        c.clipPath(path);
        fill.setColor(bg(depth));
        c.drawCircle(cx, cy, R, fill);
        c.scale(mag, mag, cx, cy);
        drawScene(c, sec, part, fr);
        c.restore();
        fill.setShader(new RadialGradient(cx - R * 0.35f, cy - R * 0.4f, R * 1.2f, new int[]{0x2EFFFFFF, 0x00FFFFFF, 0x59000000}, new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, R, fill);
        fill.setShader(null);
        fill.setStyle(Paint.Style.STROKE);
        fill.setStrokeWidth(Math.max(3, ch * 0.18f));
        fill.setColor(T.fg);
        fill.setAlpha(Math.round(255 * alpha));
        c.drawCircle(cx, cy, R, fill);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(T.fgDim);
        for (int k = 0; k < 8; k++) {
            double an = k * 0.785 + 0.39;
            c.drawRect(cx + (float) Math.cos(an) * R - 3, cy + (float) Math.sin(an) * R - 3, cx + (float) Math.cos(an) * R + 3, cy + (float) Math.sin(an) * R + 3, fill);
        }
        fill.setAlpha(255);
    }

    /** Draws one frame at song time {@code sec}. */
    void draw(Canvas c, double sec, boolean soundOn, String statusOverride, float hudBoost) {
        int w = c.getWidth(), h = c.getHeight();
        if (w <= 0 || h <= 0) return;
        T = Theme.cur();
        cw = w / (float) COLS;
        ch = h / (float) ROWS;
        fs = Math.min(cw * 1.65f, ch * 0.88f);
        float dt = lastSec < 0 ? 0 : clamp((float) (sec - lastSec), 0, 0.1f);
        lastSec = sec;
        int k = (int) Math.floor(sec / partSec), part = k % 5;
        double fr = (sec - k * partSec) / partSec;
        depth = depthAt(sec, partSec);
        bpmNow = BPM[part];
        simulate(dt, sec, part, fr);
        c.drawColor(bg(depth));
        float sx = Math.round(Math.sin(sec / 53) * 7 + Math.sin(sec / 211) * 5), sy = Math.round(Math.cos(sec / 67) * 5);
        c.save();
        c.translate(sx, sy);
        copper(c, 2.15f * ch, 3.25f * ch, 5, sec, 0.55f, 0.8f, COLS * cw);
        drawScene(c, sec, part, fr);
        if (part == 2) lens(c, sec, part, fr, easeF(fr * 10) * easeF((1 - fr) * 10));
        if (part == 4) {
            copper(c, 18 * ch, 28 * ch, 7, sec * 1.4, 1.4f, 0.35f, COLS * cw);
            bigScroller(c, sec, 19.5f);
        } else smallScroller(c, sec);
        double cyc = (sec + 3) % 45;
        float hudA = cyc < 10 ? 1 : (cyc < 12 ? 1 - (float) (cyc - 10) / 2 : (cyc > 43 ? (float) (cyc - 43) / 2 : 0));
        if (hudBoost > 0) hudA = 1;
        hud(c, sec, part, hudA, soundOn, statusOverride);
        c.restore();
        double edge = Math.min(fr * partSec, (1 - fr) * partSec);
        if (edge < 0.8) {
            fill.setColor(0xFF000000);
            fill.setAlpha(Math.round(255 * (float) (1 - edge / 0.8)));
            c.drawRect(0, 0, w, h, fill);
            fill.setAlpha(255);
        }
    }

    // ---------------------------------------------------------------- 48 kHz stereo tracker synth

    interface Gate {
        boolean playing(); // false = muted / saver hidden: output silence but keep the clock

        boolean alive();
    }

    private static final int TABLE = 2048;
    private static final float[] SIN = new float[TABLE];

    static {
        for (int i = 0; i < TABLE; i++) SIN[i] = (float) Math.sin(i * 2 * Math.PI / TABLE);
    }

    static final class Voice {
        Ev e;
        double ph, detune = 1;
    }

    /** Runs on its own thread until {@code gate.alive()} is false. Song time starts at 0 = saver start. */
    void runSynth(Gate gate) {
        final int sr = 48000, buf = 1024;
        AudioTrack track = null;
        try {
            int minBuf = AudioTrack.getMinBufferSize(sr, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);
            track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sr).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
                    .setBufferSizeInBytes(Math.max(minBuf, buf * 8))
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build();
            track.play();
            // Write 8 blocks per AudioTrack.write(): some vendor audio stacks (Hisense, #57) leak a JNI global
            // reference per write call, which overflowed the 51,200-entry table after ~5 min at 47 writes/s.
            final int batch = 8;
            short[] pcm = new short[buf * 2 * batch];
            int off = 0;
            float[] gl = new float[4], gr = new float[4];
            for (int k = 0; k < 4; k++) {
                double a = (PAN[k] + 1) * Math.PI / 4;
                gl[k] = (float) Math.cos(a);
                gr[k] = (float) Math.sin(a);
            }
            List<Voice> voices = new ArrayList<>();
            long n = 0;
            double sched = 0;
            double padCut = 1800, padTarget = 1800, send2 = 0.35, sendTarget = 0.35;
            float bassA = 0, bassB = 0, padA = 0, padB = 0;
            // Schroeder reverb: 4 combs + 2 allpass per side, slightly different lengths left/right
            int[] combLen = {1557, 1617, 1491, 1422, 1277, 1356, 1188, 1116};
            float[][] comb = new float[8][];
            int[] ci = new int[8];
            for (int i = 0; i < 8; i++) comb[i] = new float[combLen[i] * 2];
            float[][] ap = {new float[556], new float[441], new float[579], new float[464]};
            int[] ai = new int[4];
            float[] sum = new float[4];
            float[] chv = new float[4];
            while (gate.alive()) {
                double tBuf = n / (double) sr, tEnd = (n + buf) / (double) sr;
                if (tEnd + 0.05 > sched) {
                    for (Ev e : songEvents(sched, tEnd + 0.3, partSec)) {
                        if (e.part) {
                            padTarget = e.p == 3 ? 480 : (e.p == 1 ? 1100 : 1800);
                            sendTarget = e.p == 3 ? 0.8 : 0.35;
                            continue;
                        }
                        Voice v = new Voice();
                        v.e = e;
                        if (e.ch == 2) v.detune = (voices.size() % 2 == 1) ? 1.004 : 0.996;
                        voices.add(v);
                    }
                    sched = tEnd + 0.3;
                }
                boolean on = gate.playing();
                Arrays.fill(sum, 0);
                for (int i = 0; i < buf; i++) {
                    double t = (n + i) / (double) sr;
                    chv[0] = chv[1] = chv[2] = chv[3] = 0;
                    for (int vi = 0; vi < voices.size(); vi++) {
                        Voice v = voices.get(vi);
                        Ev e = v.e;
                        double dt = t - e.t;
                        if (dt < 0) continue;
                        double amp = env(e, dt);
                        if (dt > e.dur + e.rel) continue;
                        double f = e.f;
                        if (e.arp != null) f = e.arp[(int) (dt / 0.02) % e.arp.length];
                        f *= v.detune;
                        v.ph += f / sr;
                        v.ph -= Math.floor(v.ph);
                        float s;
                        switch (e.wave) {
                            case TRI: s = (float) (v.ph < 0.5 ? v.ph * 4 - 1 : 3 - v.ph * 4); break;
                            case SAW: s = (float) (v.ph * 2 - 1); break;
                            case SQUARE: s = v.ph < 0.5 ? 0.8f : -0.8f; break;
                            default: s = SIN[(int) (v.ph * TABLE) & (TABLE - 1)];
                        }
                        chv[e.ch] += s * (float) amp;
                    }
                    // bass lowpass (~900 Hz), pad lowpass follows the part (abyss closes it)
                    float kb = 0.11f;
                    bassA += kb * (chv[0] - bassA);
                    bassB += kb * (bassA - bassB);
                    chv[0] = bassB;
                    padCut += (padTarget - padCut) * 0.00002;
                    send2 += (sendTarget - send2) * 0.00002;
                    float kp = (float) (1 - Math.exp(-2 * Math.PI * padCut / sr));
                    padA += kp * (chv[2] - padA);
                    padB += kp * (padA - padB);
                    chv[2] = padB;
                    float L = 0, R = 0;
                    for (int k = 0; k < 4; k++) {
                        L += chv[k] * gl[k];
                        R += chv[k] * gr[k];
                        sum[k] += chv[k] * chv[k];
                    }
                    float wet = (float) (chv[2] * send2 + chv[3] * 0.3 + chv[1] * 0.08);
                    float rl = 0, rr = 0;
                    for (int c2 = 0; c2 < 8; c2++) {
                        float[] cb = comb[c2];
                        int len = combLen[c2];
                        float o = cb[ci[c2]];
                        cb[ci[c2]] = wet * 0.25f + o * 0.84f;
                        ci[c2] = (ci[c2] + 1) % len;
                        if (c2 < 4) rl += o; else rr += o;
                    }
                    for (int a2 = 0; a2 < 4; a2++) {
                        float[] ab = ap[a2];
                        float in = a2 < 2 ? rl : rr, o = ab[ai[a2]];
                        float y = -in + o;
                        ab[ai[a2]] = in + o * 0.5f;
                        ai[a2] = (ai[a2] + 1) % ab.length;
                        if (a2 < 2) rl = y; else rr = y;
                    }
                    L = (L + rl * 0.35f) * 0.75f;
                    R = (R + rr * 0.35f) * 0.75f;
                    L = L / (1 + Math.abs(L) * 0.3f);
                    R = R / (1 + Math.abs(R) * 0.3f);
                    pcm[off + i * 2] = on ? (short) Math.max(-32767, Math.min(32767, L * 32000)) : 0;
                    pcm[off + i * 2 + 1] = on ? (short) Math.max(-32767, Math.min(32767, R * 32000)) : 0;
                }
                for (int vi = voices.size() - 1; vi >= 0; vi--) {
                    Ev e = voices.get(vi).e;
                    if (tBuf - e.t > e.dur + e.rel + 0.05) voices.remove(vi);
                }
                for (int k = 0; k < 4; k++) vu[k] = on ? Math.min(1f, (float) Math.sqrt(sum[k] / buf) * 9f) : 0;
                n += buf;
                off += buf * 2;
                if (off == pcm.length) { track.write(pcm, 0, pcm.length); off = 0; }
            }
        } catch (Throwable ignored) {
        } finally {
            if (track != null) {
                try { track.stop(); } catch (Throwable ignored) { }
                try { track.release(); } catch (Throwable ignored) { }
            }
        }
    }
}
