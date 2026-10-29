package com.omarchytv.home.lights;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Tiny blocking HTTP helper for LAN hubs. Never logs URLs (Hue usernames live in the path). */
final class Http {
    private Http() {
    }

    static String request(String method, String url, String body, String bearer, int timeoutMs) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(timeoutMs);
        c.setReadTimeout(timeoutMs);
        c.setRequestMethod(method);
        if (bearer != null) c.setRequestProperty("Authorization", "Bearer " + bearer);
        if (body != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            try (OutputStream os = c.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }
        int code = c.getResponseCode();
        InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
        String out = in == null ? "" : read(in);
        c.disconnect();
        if (code >= 400) throw new Exception("http " + code);
        return out;
    }

    static String read(InputStream in) throws Exception {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        long total = 0;
        while ((n = in.read(buf)) > 0) {
            total += n;
            if (total > 32L * 1024 * 1024) throw new Exception("too_large");
            bo.write(buf, 0, n);
        }
        in.close();
        return new String(bo.toByteArray(), StandardCharsets.UTF_8);
    }
}
