package com.omarchytv.mobile;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPInputStream;

/**
 * Built-in Universal Smart TV & LG webOS LAN Bridge Server (`:8765`) + `.ipk` validator
 * + Samsung Tizen (`:8001/api/v2/`) / Hisense VIDAA DIAL launcher.
 *
 * Implements the full `omarchy-tv webos-serve` contract PLUS Universal WebTV hosting directly on the phone:
 * - `GET /v1/status` (and `/status.json`, `/`): live active theme, palette, wallpaper state, remote key queue
 * - `GET /v1/themes`: all 23+ Omarchy themes and color palettes
 * - `GET /v1/wallpaper`: active custom wallpaper image (`image/jpeg` or `image/png`)
 * - `GET /omarchytv.ipk`: serves validated LG webOS `.ipk` package over LAN HTTP for direct TV install
 * - `GET /tv` (and `/tv/*`): serves the full 1080p HTML5/Canvas OmarchyTV Launcher for Samsung Tizen,
 *   Hisense VIDAA, LG webOS, Fire TV, and any Smart TV browser on the LAN with live theme & remote sync!
 */
public final class WebOsBridgeServer implements Closeable {

    public static final int DEFAULT_PORT = 8765;
    public static final int SAMSUNG_TIZEN_PORT = 8001;
    public static final String EXPECTED_WEBOS_APP_ID = "com.omarchytv.home";

    public interface WebAssetResolver {
        byte[] openAsset(String relativeAssetPath) throws IOException;
    }

    public static final class IpkMetadata {
        public final boolean valid;
        public final String appId;
        public final String version;
        public final String title;
        public final String vendor;
        public final String error;

        public IpkMetadata(boolean valid, String appId, String version, String title, String vendor, String error) {
            this.valid = valid;
            this.appId = appId;
            this.version = version;
            this.title = title;
            this.vendor = vendor;
            this.error = error;
        }
    }

    private ServerSocket serverSocket;
    private ExecutorService pool;
    private volatile boolean running;
    private final long startTimeMs = System.currentTimeMillis();

    private volatile String activeThemeName = "tokyo-night";
    private volatile JSONObject activePalette = new JSONObject();
    private volatile byte[] customWallpaperBytes;
    private volatile String customWallpaperMime = "image/jpeg";
    private volatile File servedIpkFile;
    private volatile WebAssetResolver webAssetResolver;
    private volatile long remoteKeySeq = 0;
    private volatile String lastRemoteKey = "";
    private volatile String lastPrompt = "";
    private volatile List<ThemeCatalog.ThemeEntry> themeEntries;

    public WebOsBridgeServer(List<ThemeCatalog.ThemeEntry> themes) {
        this.themeEntries = themes;
        if (themes != null) {
            for (ThemeCatalog.ThemeEntry e : themes) {
                if ("tokyo-night".equals(e.id)) {
                    this.activePalette = e.rawColors;
                    break;
                }
            }
        }
    }

    public void setWebAssetResolver(WebAssetResolver resolver) {
        this.webAssetResolver = resolver;
    }

    public synchronized int start(int port) throws IOException {
        stop();
        ServerSocket ss = new ServerSocket();
        ss.setReuseAddress(true);
        ss.bind(new InetSocketAddress("0.0.0.0", port), 16);
        this.serverSocket = ss;
        this.running = true;
        this.pool = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "otv-webos-bridge");
            t.setDaemon(true);
            return t;
        });
        Thread acceptThread = new Thread(() -> acceptLoop(ss), "otv-webos-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
        return ss.getLocalPort();
    }

    public synchronized void stop() {
        running = false;
        if (serverSocket != null) {
            try { serverSocket.close(); } catch (IOException ignored) {}
            serverSocket = null;
        }
        if (pool != null) {
            pool.shutdownNow();
            pool = null;
        }
    }

    @Override
    public void close() {
        stop();
    }

    public boolean isRunning() {
        return running && serverSocket != null && !serverSocket.isClosed();
    }

    public int getLocalPort() {
        return serverSocket != null ? serverSocket.getLocalPort() : DEFAULT_PORT;
    }

    public void setActiveTheme(String themeName, JSONObject palette) {
        this.activeThemeName = themeName != null ? themeName : "tokyo-night";
        if (palette != null) {
            this.activePalette = palette;
        }
    }

    public String getActiveThemeName() {
        return activeThemeName;
    }

    public void setCustomWallpaper(byte[] data, String mime) {
        this.customWallpaperBytes = data;
        this.customWallpaperMime = mime != null ? mime : "image/jpeg";
    }

    public void setServedIpkFile(File ipkFile) {
        this.servedIpkFile = ipkFile;
    }

    public synchronized void queueRemoteKey(String key) {
        this.remoteKeySeq++;
        this.lastRemoteKey = key != null ? key : "";
    }

    public synchronized void setLastPrompt(String prompt) {
        this.lastPrompt = prompt != null ? prompt : "";
    }

    private void acceptLoop(ServerSocket ss) {
        while (running && !ss.isClosed()) {
            try {
                Socket client = ss.accept();
                ExecutorService p = pool;
                if (p != null) {
                    p.execute(() -> handleClient(client));
                } else {
                    client.close();
                }
            } catch (IOException e) {
                break;
            }
        }
    }

    private void handleClient(Socket sock) {
        try (Socket s = sock) {
            s.setSoTimeout(10_000);
            InputStream in = new BufferedInputStream(s.getInputStream());
            OutputStream out = s.getOutputStream();

            String requestLine = readLine(in);
            if (requestLine == null || requestLine.isEmpty()) return;
            String[] parts = requestLine.split(" ");
            if (parts.length < 2) return;
            String method = parts[0].toUpperCase(Locale.ROOT);
            String rawPath = parts[1];
            String path = rawPath.split("\\?", 2)[0];

            while (true) {
                String hdr = readLine(in);
                if (hdr == null || hdr.isEmpty()) break;
            }

            if ("OPTIONS".equals(method)) {
                writeResponse(out, 204, "text/plain", new byte[0]);
                return;
            }

            if ("/v1/status".equals(path) || "/status.json".equals(path) || "/".equals(path)) {
                byte[] body = buildStatusPayload().toString().getBytes(StandardCharsets.UTF_8);
                writeResponse(out, 200, "application/json; charset=utf-8", body);
                return;
            }

            if ("/v1/themes".equals(path)) {
                byte[] body = buildThemesPayload().toString().getBytes(StandardCharsets.UTF_8);
                writeResponse(out, 200, "application/json; charset=utf-8", body);
                return;
            }

            if ("/v1/wallpaper".equals(path)) {
                byte[] wp = customWallpaperBytes;
                if (wp == null || wp.length == 0) {
                    byte[] err = "{\"error\":\"no wallpaper configured\"}".getBytes(StandardCharsets.UTF_8);
                    writeResponse(out, 404, "application/json; charset=utf-8", err);
                } else {
                    writeResponse(out, 200, customWallpaperMime, wp);
                }
                return;
            }

            if ("/omarchytv.ipk".equals(path)) {
                File f = servedIpkFile;
                if (f == null || !f.isFile()) {
                    byte[] err = "{\"error\":\"no ipk staged\"}".getBytes(StandardCharsets.UTF_8);
                    writeResponse(out, 404, "application/json; charset=utf-8", err);
                } else {
                    byte[] data = readFileBytes(f);
                    writeResponse(out, 200, "application/vnd.webos.ipk", data);
                }
                return;
            }

            if ("/tv".equals(path) || "/tv/".equals(path) || path.startsWith("/tv/")) {
                String rel = ("/tv".equals(path) || "/tv/".equals(path))
                        ? "webtv/index.html"
                        : "webtv/" + path.substring(4);
                if (rel.contains("..")) {
                    writeResponse(out, 404, "text/plain", new byte[0]);
                    return;
                }
                byte[] assetBytes = resolveWebTvAsset(rel);
                if (assetBytes != null) {
                    writeResponse(out, 200, guessMimeType(rel), assetBytes);
                    return;
                }
            }

            byte[] notFound = "{\"error\":\"not found\"}".getBytes(StandardCharsets.UTF_8);
            writeResponse(out, 404, "application/json; charset=utf-8", notFound);
        } catch (Exception ignored) {
        }
    }

    private byte[] resolveWebTvAsset(String relPath) {
        WebAssetResolver r = webAssetResolver;
        if (r != null) {
            try {
                byte[] b = r.openAsset(relPath);
                if (b != null) return b;
            } catch (IOException ignored) {
            }
        }
        // Fallback for local filesystem / unit test harness
        File f = new File("app/src/main/assets/" + relPath);
        if (!f.isFile()) {
            f = new File("app/src/main/assets/" + relPath);
        }
        if (f.isFile()) {
            try {
                return readFileBytes(f);
            } catch (IOException ignored) {
            }
        }
        return null;
    }

    private static String guessMimeType(String path) {
        String p = path.toLowerCase(Locale.ROOT);
        if (p.endsWith(".html")) return "text/html; charset=utf-8";
        if (p.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (p.endsWith(".json")) return "application/json; charset=utf-8";
        if (p.endsWith(".png")) return "image/png";
        if (p.endsWith(".jpg") || p.endsWith(".jpeg")) return "image/jpeg";
        if (p.endsWith(".ttf")) return "font/ttf";
        return "text/plain; charset=utf-8";
    }

    /**
     * Pushes `http://<phone-ip>:8765/tv` to Samsung Tizen (`:8001/api/v2/applications/org.tizen.browser`)
     * or Hisense VIDAA / DIAL REST endpoint so the TV opens OmarchyTV full-screen automatically.
     */
    public static String launchOnSamsungTizenOrSmartTv(String tvIp, int tizenPort, String webTvUrl) throws IOException {
        int port = tizenPort > 0 ? tizenPort : SAMSUNG_TIZEN_PORT;
        String infoUrl = "http://" + tvIp + ":" + port + "/api/v2/";
        String tvModel = "Samsung Tizen / Smart TV";
        try {
            HttpURLConnection c0 = (HttpURLConnection) new URL(infoUrl).openConnection();
            c0.setConnectTimeout(2500);
            c0.setReadTimeout(2500);
            if (c0.getResponseCode() == 200) {
                try (InputStream is = c0.getInputStream()) {
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    byte[] buf = new byte[2048];
                    int n;
                    while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
                    JSONObject j = new JSONObject(new String(bos.toByteArray(), StandardCharsets.UTF_8));
                    JSONObject dev = j.optJSONObject("device");
                    if (dev != null) {
                        tvModel = dev.optString("name", dev.optString("modelName", tvModel));
                    } else {
                        tvModel = j.optString("name", tvModel);
                    }
                }
            }
            c0.disconnect();
        } catch (Exception ignored) {
        }

        String launchEndpoint = "http://" + tvIp + ":" + port + "/api/v2/applications/org.tizen.browser";
        HttpURLConnection conn = (HttpURLConnection) new URL(launchEndpoint).openConnection();
        conn.setConnectTimeout(3000);
        conn.setReadTimeout(3000);
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        JSONObject payload = new JSONObject();
        try {
            payload.put("id", "org.tizen.browser");
            payload.put("url", webTvUrl);
        } catch (JSONException ignored) {
        }
        byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body);
            os.flush();
        }
        int code = conn.getResponseCode();
        conn.disconnect();
        if (code >= 200 && code < 300) {
            return "Launched OmarchyTV WebTV on " + tvModel + " (" + tvIp + ":" + port + ") -> " + webTvUrl;
        }
        throw new IOException("HTTP " + code + " from Smart TV launch endpoint at " + launchEndpoint);
    }

    public JSONObject buildStatusPayload() {
        JSONObject o = new JSONObject();
        try {
            long upSec = Math.max(1L, (System.currentTimeMillis() - startTimeMs) / 1000L);
            o.put("version", "2.11.0-testing");
            o.put("host", "omarchytv-mobile");
            o.put("platform", "Universal Smart TV Bridge (Android TV · LG webOS · Samsung Tizen · Hisense)");
            o.put("kernel", "Android Mobile Companion 2.13.0");
            o.put("cpu", 14);
            o.put("ram", 42);
            o.put("uptimeSec", upSec);
            o.put("theme", activeThemeName);
            o.put("palette", activePalette != null ? activePalette : new JSONObject());
            o.put("claude", "24.80");
            o.put("openai", "51.20");
            o.put("xai", "110.00");
            o.put("hasWallpaper", customWallpaperBytes != null && customWallpaperBytes.length > 0);
            o.put("deploy", "omarchytv-mobile 2.13.0 (Universal TV) ✓");
            o.put("remoteKeySeq", remoteKeySeq);
            o.put("lastRemoteKey", lastRemoteKey);
            o.put("lastPrompt", lastPrompt);
        } catch (JSONException ignored) {
        }
        return o;
    }

    public JSONObject buildThemesPayload() {
        JSONObject root = new JSONObject();
        JSONArray arr = new JSONArray();
        try {
            if (themeEntries != null) {
                for (ThemeCatalog.ThemeEntry t : themeEntries) {
                    JSONObject item = new JSONObject();
                    item.put("name", t.id);
                    JSONObject themeObj = new JSONObject();
                    themeObj.put("name", t.id);
                    themeObj.put("mode", t.mode);
                    themeObj.put("accent", t.accentHex);
                    themeObj.put("accent2", t.accent2Hex);
                    themeObj.put("colors", t.rawColors);
                    item.put("theme", themeObj);
                    item.put("backgrounds", 1);
                    arr.put(item);
                }
            }
            root.put("themes", arr);
        } catch (JSONException ignored) {
        }
        return root;
    }

    /**
     * Pure-Java parser & validator for LG webOS `.ipk` packages (Debian `ar` archive containing `data.tar.gz` -> `appinfo.json`).
     */
    public static IpkMetadata inspectIpkPackage(File ipkFile) {
        if (ipkFile == null || !ipkFile.isFile()) {
            return new IpkMetadata(false, "", "", "", "", "IPK file does not exist");
        }
        try (FileInputStream fis = new FileInputStream(ipkFile)) {
            byte[] magic = new byte[8];
            if (fis.read(magic) != 8 || !"!<arch>\n".equals(new String(magic, StandardCharsets.US_ASCII))) {
                return new IpkMetadata(false, "", "", "", "", "Invalid Debian ar magic header (expected !<arch>)");
            }
            while (true) {
                byte[] hdr = new byte[60];
                int r = readFullyOrEof(fis, hdr);
                if (r < 60) break;
                String name = new String(hdr, 0, 16, StandardCharsets.US_ASCII).trim();
                if (name.endsWith("/")) name = name.substring(0, name.length() - 1);
                String sizeStr = new String(hdr, 48, 10, StandardCharsets.US_ASCII).trim();
                int size = sizeStr.isEmpty() ? 0 : Integer.parseInt(sizeStr);
                byte[] payload = new byte[size];
                if (readFullyOrEof(fis, payload) != size) break;
                if ((size & 1) == 1) {
                    fis.read();
                }
                if (name.startsWith("data.tar")) {
                    JSONObject appInfo = extractAppInfoFromTarGz(payload);
                    if (appInfo != null) {
                        String id = appInfo.optString("id", "");
                        String ver = appInfo.optString("version", "");
                        String title = appInfo.optString("title", "OmarchyTV");
                        String vendor = appInfo.optString("vendor", "OmarchyTV");
                        if (!EXPECTED_WEBOS_APP_ID.equals(id)) {
                            return new IpkMetadata(false, id, ver, title, vendor,
                                    "Unexpected appinfo.json id: " + id + " (expected " + EXPECTED_WEBOS_APP_ID + ")");
                        }
                        if (ver.isEmpty()) {
                            return new IpkMetadata(false, id, ver, title, vendor, "Missing version in appinfo.json");
                        }
                        return new IpkMetadata(true, id, ver, title, vendor, null);
                    }
                }
            }
            return new IpkMetadata(false, "", "", "", "", "appinfo.json not found in data.tar.gz");
        } catch (Exception e) {
            return new IpkMetadata(false, "", "", "", "", "IPK parse error: " + e.getMessage());
        }
    }

    private static JSONObject extractAppInfoFromTarGz(byte[] gzBytes) throws Exception {
        try (GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(gzBytes))) {
            byte[] block = new byte[512];
            while (readFullyOrEof(gis, block) == 512) {
                if (block[0] == 0) break;
                String entryName = readTarString(block, 0, 100);
                String sizeOctal = readTarString(block, 124, 12).trim();
                long entrySize = sizeOctal.isEmpty() ? 0 : Long.parseLong(sizeOctal, 8);
                byte[] content = new byte[(int) entrySize];
                if (entrySize > 0) {
                    readFullyOrEof(gis, content);
                    long rem = entrySize % 512;
                    if (rem != 0) {
                        byte[] pad = new byte[(int) (512 - rem)];
                        readFullyOrEof(gis, pad);
                    }
                }
                if (entryName.endsWith("/appinfo.json") || "appinfo.json".equals(entryName)
                        || "./appinfo.json".equals(entryName)) {
                    return new JSONObject(new String(content, StandardCharsets.UTF_8));
                }
            }
        }
        return null;
    }

    private static String readTarString(byte[] buf, int offset, int maxLen) {
        int end = offset;
        while (end < offset + maxLen && buf[end] != 0) end++;
        return new String(buf, offset, end - offset, StandardCharsets.UTF_8);
    }

    private static int readFullyOrEof(InputStream in, byte[] buf) throws IOException {
        int off = 0;
        while (off < buf.length) {
            int n = in.read(buf, off, buf.length - off);
            if (n < 0) break;
            off += n;
        }
        return off;
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(128);
        while (true) {
            int b = in.read();
            if (b < 0) return bos.size() == 0 ? null : bos.toString("ISO-8859-1");
            if (b == '\n') {
                String s = bos.toString("ISO-8859-1");
                return s.endsWith("\r") ? s.substring(0, s.length() - 1) : s;
            }
            bos.write(b);
            if (bos.size() > 8192) break;
        }
        return bos.toString("ISO-8859-1");
    }

    private static void writeResponse(OutputStream out, int status, String contentType, byte[] body) throws IOException {
        String reason = status == 200 ? "OK" : status == 204 ? "No Content" : status == 404 ? "Not Found" : "Error";
        String headers = "HTTP/1.1 " + status + " " + reason + "\r\n"
                + "Content-Type: " + contentType + "\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "Access-Control-Allow-Origin: *\r\n"
                + "Access-Control-Allow-Methods: GET, POST, PUT, OPTIONS\r\n"
                + "Access-Control-Allow-Headers: *\r\n"
                + "Cache-Control: no-store\r\n"
                + "Connection: close\r\n\r\n";
        out.write(headers.getBytes(StandardCharsets.ISO_8859_1));
        if (body.length > 0) {
            out.write(body);
        }
        out.flush();
    }

    private static byte[] readFileBytes(File f) throws IOException {
        try (FileInputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream((int) f.length());
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }
}
