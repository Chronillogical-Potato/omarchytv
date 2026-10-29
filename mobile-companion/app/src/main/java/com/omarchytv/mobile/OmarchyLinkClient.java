package com.omarchytv.mobile;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Pure-Java `omarchy-link` client for OmarchyTV Mobile.
 *
 * Features:
 * - Parallel `/24` local Wi-Fi subnet scanner probing ports `8999` (`omarchy-link` / MCP daemon),
 *   `5555` (Android TV Wireless ADB), and `47800` (Omarchy Link v1 socket).
 * - Dual-format HMAC-SHA256 request signing:
 *   - `Authorization: OTV1 pc=<clientId>,ts=<ts>,nonce=<nonce>,sig=<base64url>`
 *   - `X-Omarchy-Timestamp`, `X-Omarchy-Nonce`, `X-Omarchy-Signature: sha256=<hex>`
 * - Real-time theme switching (`PUT /v1/theme`), D-Pad/media remote keys (`POST /v1/remote/key`),
 *   AI / terminal prompt (`POST /v1/prompt`), custom JPEG wallpaper upload (`PUT /v1/wallpaper`),
 *   and custom PNG app banner upload (`PUT /v1/banners/<pkg>`).
 */
public final class OmarchyLinkClient {

    public static final int DEFAULT_LINK_PORT = 8999;
    public static final int ALT_LINK_PORT = 47800;
    public static final int ADB_PORT = 5555;

    private static final SecureRandom RANDOM = new SecureRandom();

    public static final class DiscoveredTv {
        public final String ip;
        public final boolean port8999Open;
        public final boolean port5555Open;
        public final boolean port47800Open;
        public final String tvName;
        public final String version;
        public final String activeTheme;
        public final long latencyMs;

        public DiscoveredTv(String ip, boolean port8999Open, boolean port5555Open, boolean port47800Open,
                            String tvName, String version, String activeTheme, long latencyMs) {
            this.ip = ip;
            this.port8999Open = port8999Open;
            this.port5555Open = port5555Open;
            this.port47800Open = port47800Open;
            this.tvName = tvName;
            this.version = version;
            this.activeTheme = activeTheme;
            this.latencyMs = latencyMs;
        }

        public int preferredLinkPort() {
            if (port8999Open) return DEFAULT_LINK_PORT;
            if (port47800Open) return ALT_LINK_PORT;
            return DEFAULT_LINK_PORT;
        }
    }

    public static final class PingResult {
        public final boolean ok;
        public final String tvName;
        public final String version;
        public final int versionCode;
        public final String activeTheme;
        public final long latencyMs;
        public final JSONObject raw;

        public PingResult(boolean ok, String tvName, String version, int versionCode,
                          String activeTheme, long latencyMs, JSONObject raw) {
            this.ok = ok;
            this.tvName = tvName;
            this.version = version;
            this.versionCode = versionCode;
            this.activeTheme = activeTheme;
            this.latencyMs = latencyMs;
            this.raw = raw;
        }
    }

    public static final class HttpResult {
        public final int statusCode;
        public final byte[] body;
        public final String signatureHeader;

        public HttpResult(int statusCode, byte[] body, String signatureHeader) {
            this.statusCode = statusCode;
            this.body = body;
            this.signatureHeader = signatureHeader;
        }

        public String bodyText() {
            return body == null ? "" : new String(body, StandardCharsets.UTF_8);
        }

        public JSONObject bodyJson() {
            try {
                return new JSONObject(bodyText());
            } catch (JSONException e) {
                return new JSONObject();
            }
        }
    }

    private final String host;
    private final int port;
    private final String clientId;
    private final byte[] secretKey;

    public OmarchyLinkClient(String host, int port, String clientId, String secretToken) {
        this.host = host != null ? host.trim() : "127.0.0.1";
        this.port = port > 0 ? port : DEFAULT_LINK_PORT;
        this.clientId = (clientId == null || clientId.trim().isEmpty()) ? "omarchymobile01" : clientId.trim();
        this.secretKey = deriveKeyBytes(secretToken);
    }

    private static byte[] deriveKeyBytes(String token) {
        if (token == null || token.trim().isEmpty()) {
            return "omarchy-default-secret".getBytes(StandardCharsets.UTF_8);
        }
        String t = token.trim();
        if (t.matches("^[0-9a-fA-F]{64}$")) {
            byte[] out = new byte[32];
            for (int i = 0; i < 32; i++) {
                out[i] = (byte) Integer.parseInt(t.substring(i * 2, i * 2 + 2), 16);
            }
            return out;
        }
        return t.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Pings the TV's `omarchy-link` daemon (`/ping` or `/v1/status` or `/v1/info`).
     */
    public PingResult ping() throws IOException {
        long t0 = System.currentTimeMillis();
        for (String path : new String[]{"/ping", "/v1/status", "/v1/info"}) {
            try {
                HttpResult res = sendSignedRequest("GET", path, "application/json", new byte[0]);
                if (res.statusCode == 200) {
                    long dt = Math.max(1L, System.currentTimeMillis() - t0);
                    JSONObject j = res.bodyJson();
                    String name = j.optString("name", j.optString("tvName", "OmarchyTV"));
                    String ver = j.optString("version", j.optString("versionName", "2.13.0-beta"));
                    int vc = j.optInt("versionCode", 19);
                    String theme = j.optString("theme", "tokyo-night");
                    JSONObject themeObj = j.optJSONObject("theme");
                    if (themeObj != null) {
                        theme = themeObj.optString("name", theme);
                    }
                    return new PingResult(true, name, ver, vc, theme, dt, j);
                }
            } catch (IOException ignored) {
            }
        }
        throw new IOException("No response from omarchy-link at " + host + ":" + port);
    }

    /**
     * Switches the active Omarchy theme on the TV in real time via HMAC-signed `PUT /v1/theme`.
     */
    public JSONObject switchTheme(ThemeCatalog.ThemeEntry theme) throws IOException {
        JSONObject payload = new JSONObject();
        try {
            payload.put("name", theme.id);
            payload.put("mode", theme.mode);
            payload.put("accent", theme.accentHex.toLowerCase(Locale.ROOT));
            payload.put("accent2", theme.accent2Hex.toLowerCase(Locale.ROOT));
            payload.put("colors", theme.rawColors);
        } catch (JSONException e) {
            throw new IOException(e);
        }
        byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);
        HttpResult res = sendSignedRequest("PUT", "/v1/theme", "application/json", body);
        if (res.statusCode != 200) {
            throw new IOException("Theme switch failed (HTTP " + res.statusCode + "): " + res.bodyText());
        }
        return res.bodyJson();
    }

    /**
     * Sends a tactile remote key command (`up`, `down`, `left`, `right`, `select`, `back`, `home`, `menu`,
     * `volume_up`, `volume_down`, `mute`, `play_pause`, `hdmi1`..`hdmi4`, `tv`) over `omarchy-link`.
     */
    public JSONObject sendRemoteKey(String key) throws IOException {
        JSONObject payload = new JSONObject();
        try {
            payload.put("key", key);
        } catch (JSONException e) {
            throw new IOException(e);
        }
        byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);
        HttpResult res = sendSignedRequest("POST", "/v1/remote/key", "application/json", body);
        if (res.statusCode != 200) {
            throw new IOException("Remote key '" + key + "' failed (HTTP " + res.statusCode + ")");
        }
        return res.bodyJson();
    }

    /**
     * Sends a terminal command or AI prompt (`>_ Send command or text to TV`) over `omarchy-link`.
     */
    public JSONObject sendPromptOrCommand(String text) throws IOException {
        JSONObject payload = new JSONObject();
        try {
            payload.put("prompt", text);
            payload.put("text", text);
            payload.put("source", "omarchytv-mobile");
        } catch (JSONException e) {
            throw new IOException(e);
        }
        byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);
        HttpResult res = sendSignedRequest("POST", "/v1/prompt", "application/json", body);
        if (res.statusCode != 200) {
            throw new IOException("Prompt send failed (HTTP " + res.statusCode + ")");
        }
        return res.bodyJson();
    }

    /**
     * Uploads a custom JPEG wallpaper (`PUT /v1/wallpaper`) to OmarchyTV.
     */
    public String pushWallpaperJpeg(byte[] jpegBytes) throws IOException {
        HttpResult res = sendSignedRequest("PUT", "/v1/wallpaper", "image/jpeg", jpegBytes);
        if (res.statusCode != 200) {
            throw new IOException("Wallpaper upload failed (HTTP " + res.statusCode + "): " + res.bodyText());
        }
        return res.bodyJson().optString("sha256", sha256Hex(jpegBytes));
    }

    /**
     * Uploads a custom PNG app icon/banner (`PUT /v1/banners/<package>`) to OmarchyTV.
     */
    public JSONObject pushAppBannerPng(String packageName, byte[] pngBytes) throws IOException {
        HttpResult res = sendSignedRequest("PUT", "/v1/banners/" + packageName, "image/png", pngBytes);
        if (res.statusCode != 200) {
            throw new IOException("App banner upload failed (HTTP " + res.statusCode + "): " + res.bodyText());
        }
        return res.bodyJson();
    }

    /**
     * Signs and sends an HTTP request with both `OTV1` Authorization and `X-Omarchy-Signature` headers.
     */
    public HttpResult sendSignedRequest(String method, String path, String contentType, byte[] body) throws IOException {
        if (body == null) body = new byte[0];
        String ts = String.valueOf(System.currentTimeMillis() / 1000L);
        byte[] nonceBytes = new byte[12];
        RANDOM.nextBytes(nonceBytes);
        String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(nonceBytes);

        byte[] canonical = buildCanonicalString(method, path, ts, nonce, body);
        byte[] mac = hmacSha256(secretKey, canonical);
        String sigB64u = Base64.getUrlEncoder().withoutPadding().encodeToString(mac);
        String sigHex = toHex(mac);

        URL url = new URL("http://" + host + ":" + port + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(3_500);
        conn.setReadTimeout(6_000);
        conn.setUseCaches(false);
        conn.setRequestMethod(method);
        conn.setRequestProperty("Authorization",
                "OTV1 pc=" + clientId + ",ts=" + ts + ",nonce=" + nonce + ",sig=" + sigB64u);
        conn.setRequestProperty("X-Omarchy-Client", clientId);
        conn.setRequestProperty("X-Omarchy-Timestamp", ts);
        conn.setRequestProperty("X-Omarchy-Nonce", nonce);
        conn.setRequestProperty("X-Omarchy-Signature", "sha256=" + sigHex);

        if ("POST".equals(method) || "PUT".equals(method)) {
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", contentType != null ? contentType : "application/json");
            conn.setFixedLengthStreamingMode(body.length);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body);
                os.flush();
            }
        }

        int code = conn.getResponseCode();
        InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
        byte[] respBytes = is != null ? readAll(is) : new byte[0];
        String otvSig = conn.getHeaderField("X-OTV-Sig");
        conn.disconnect();
        return new HttpResult(code, respBytes, otvSig);
    }

    public static byte[] buildCanonicalString(String method, String path, String ts, String nonce, byte[] body) {
        String s = "OTV1\n" + method.toUpperCase(Locale.ROOT) + "\n" + path + "\n"
                + ts + "\n" + nonce + "\n" + sha256Hex(body);
        return s.getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] hmacSha256(byte[] key, byte[] message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.length == 0 ? new byte[32] : key, "HmacSHA256"));
            return mac.doFinal(message);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static String sha256Hex(byte[] data) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(data == null ? new byte[0] : data);
            return toHex(d);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xf, 16));
            sb.append(Character.forDigit(b & 0xf, 16));
        }
        return sb.toString();
    }

    /**
     * Detects the phone's active IPv4 LAN address (e.g. `192.168.1.42`).
     */
    public static String detectLocalIpv4() {
        try {
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces != null && ifaces.hasMoreElements()) {
                NetworkInterface nif = ifaces.nextElement();
                if (!nif.isUp() || nif.isLoopback()) continue;
                Enumeration<InetAddress> addrs = nif.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "192.168.1.100";
    }

    /**
     * Parallel `/24` LAN subnet scanner probing ports `8999` (`omarchy-link`), `5555` (ADB), and `47800`.
     */
    public static List<DiscoveredTv> scanSubnet(String subnetPrefix, int connectTimeoutMs, String secretToken) {
        if (subnetPrefix == null || subnetPrefix.trim().isEmpty()) {
            String localIp = detectLocalIpv4();
            int lastDot = localIp.lastIndexOf('.');
            subnetPrefix = lastDot > 0 ? localIp.substring(0, lastDot) : "192.168.1";
        }
        final String prefix = subnetPrefix.endsWith(".")
                ? subnetPrefix.substring(0, subnetPrefix.length() - 1)
                : subnetPrefix;
        final int timeout = Math.max(80, Math.min(1500, connectTimeoutMs));

        ExecutorService pool = Executors.newFixedThreadPool(32);
        List<Callable<DiscoveredTv>> tasks = new ArrayList<>(254);
        for (int hostNum = 1; hostNum <= 254; hostNum++) {
            final String candidateIp = prefix + "." + hostNum;
            tasks.add(() -> probeHost(candidateIp, DEFAULT_LINK_PORT, ADB_PORT, ALT_LINK_PORT, timeout, secretToken));
        }

        List<DiscoveredTv> found = new ArrayList<>();
        try {
            List<Future<DiscoveredTv>> futures = pool.invokeAll(tasks, 12, TimeUnit.SECONDS);
            for (Future<DiscoveredTv> f : futures) {
                if (f.isDone() && !f.isCancelled()) {
                    try {
                        DiscoveredTv tv = f.get();
                        if (tv != null) found.add(tv);
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } finally {
            pool.shutdownNow();
        }
        return found;
    }

    /**
     * Probes a single host IP for `omarchy-link` (`linkPort`), Wireless ADB (`adbPort`), and `altLinkPort`.
     */
    public static DiscoveredTv probeHost(String ip, int linkPort, int adbPort, int altLinkPort,
                                         int timeoutMs, String secretToken) {
        long t0 = System.currentTimeMillis();
        boolean p8999 = isTcpPortOpen(ip, linkPort, timeoutMs);
        boolean p5555 = isTcpPortOpen(ip, adbPort, timeoutMs);
        boolean p47800 = (altLinkPort > 0 && altLinkPort != linkPort) && isTcpPortOpen(ip, altLinkPort, timeoutMs);
        if (!p8999 && !p5555 && !p47800) {
            return null;
        }
        long dt = Math.max(1L, System.currentTimeMillis() - t0);
        String tvName = "Android TV (" + ip + ")";
        String ver = p8999 || p47800 ? "2.13.0-beta" : "ADB Ready";
        String theme = "tokyo-night";

        if (p8999 || p47800) {
            int activePort = p8999 ? linkPort : altLinkPort;
            try {
                OmarchyLinkClient c = new OmarchyLinkClient(ip, activePort, "scanner01", secretToken);
                PingResult pr = c.ping();
                if (pr.ok) {
                    tvName = pr.tvName;
                    ver = pr.version;
                    theme = pr.activeTheme;
                    dt = pr.latencyMs;
                }
            } catch (Exception ignored) {
            }
        }
        return new DiscoveredTv(ip, p8999, p5555, p47800, tvName, ver, theme, dt);
    }

    public static boolean isTcpPortOpen(String ip, int port, int timeoutMs) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(ip, port), timeoutMs);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }
}
