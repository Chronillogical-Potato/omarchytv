package com.omarchytv.home;

import android.os.Build;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * API 24/25 (Fire OS 6) stand-ins for java.util.Base64 and java.nio.file.Files, which are API 26 (#59, #56).
 * On API 26+ and in JVM unit tests (SDK_INT reads 0 there) java.util.Base64 is used unchanged.
 */
public final class ApiCompat {
    private ApiCompat() { }

    private static boolean javaBase64() {
        return Build.VERSION.SDK_INT >= 26 || Build.VERSION.SDK_INT == 0;
    }

    public static String b64(byte[] d) {
        if (javaBase64()) return java.util.Base64.getEncoder().encodeToString(d);
        return android.util.Base64.encodeToString(d, android.util.Base64.NO_WRAP);
    }

    public static byte[] unb64(String s) {
        if (javaBase64()) return java.util.Base64.getDecoder().decode(s);
        return android.util.Base64.decode(s, android.util.Base64.NO_WRAP);
    }

    public static String b64url(byte[] d) {
        if (javaBase64()) return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(d);
        return android.util.Base64.encodeToString(d, android.util.Base64.URL_SAFE | android.util.Base64.NO_PADDING | android.util.Base64.NO_WRAP);
    }

    public static byte[] unb64url(String s) {
        if (javaBase64()) return java.util.Base64.getUrlDecoder().decode(s);
        return android.util.Base64.decode(s, android.util.Base64.URL_SAFE | android.util.Base64.NO_PADDING | android.util.Base64.NO_WRAP);
    }

    public static byte[] readAllBytes(File f) throws IOException {
        try (InputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream((int) Math.max(32, Math.min(f.length(), 1 << 24)));
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    public static void write(File f, byte[] data) throws IOException {
        try (OutputStream out = new FileOutputStream(f)) { out.write(data); }
    }
}
