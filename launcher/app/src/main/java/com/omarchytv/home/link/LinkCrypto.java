package com.omarchytv.home.link;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Omarchy Link v1 cryptography (omarchy-link/docs/PROTOCOL.md §4, §5, §7): SPAKE2 over the RFC 3526
 * 2048-bit MODP group with BigInteger maths, HMAC-SHA256, HKDF, base64url. Plain Java (no android.*),
 * so the JVM unit tests check it byte-for-byte against docs/vectors.json.
 */
public final class LinkCrypto {

    public static final BigInteger P = new BigInteger(
            "FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD129024E088A67CC74"
                    + "020BBEA63B139B22514A08798E3404DDEF9519B3CD3A431B302B0A6DF25F1437"
                    + "4FE1356D6D51C245E485B576625E7EC6F44C42E9A637ED6B0BFF5CB6F406B7ED"
                    + "EE386BFB5A899FA5AE9F24117C4B1FE649286651ECE45B3DC2007CB8A163BF05"
                    + "98DA48361C55D39A69163FA8FD24CF5F83655D23DCA3AD961C62F356208552BB"
                    + "9ED529077096966D670C354E4ABC9804F1746C08CA18217C32905E462E36CE3B"
                    + "E39E772C180E86039B2783A2EC07A28FB5C55DF06F4C52C9DE2BCBF695581718"
                    + "3995497CEA956AE515D2261898FA051015728E5A8AACAA68FFFFFFFFFFFFFFFF", 16);
    public static final BigInteger Q = P.subtract(BigInteger.ONE).shiftRight(1);
    public static final BigInteger G = BigInteger.valueOf(2);
    public static final int ELEMENT_BYTES = 256;
    public static final BigInteger M = hashToGroup("OmarchyTV-Link SPAKE2 M v1");
    public static final BigInteger N = hashToGroup("OmarchyTV-Link SPAKE2 N v1");

    private static final SecureRandom RANDOM = new SecureRandom();

    private LinkCrypto() {
    }

    // ---- encodings ---------------------------------------------------------------------------------

    public static String b64u(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    /** Decodes base64url with or without padding; throws IllegalArgumentException on garbage. */
    public static byte[] unb64u(String text) {
        String t = text == null ? "" : text.trim();
        while (t.endsWith("=")) t = t.substring(0, t.length() - 1);
        return Base64.getUrlDecoder().decode(t);
    }

    public static String hex(byte[] b) {
        StringBuilder s = new StringBuilder(b.length * 2);
        for (byte x : b) s.append(Character.forDigit((x >> 4) & 15, 16)).append(Character.forDigit(x & 15, 16));
        return s.toString();
    }

    public static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] randomBytes(int n) {
        byte[] b = new byte[n];
        RANDOM.nextBytes(b);
        return b;
    }

    // ---- hashes ------------------------------------------------------------------------------------

    public static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static byte[] hmac(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            // SecretKeySpec refuses an empty key; HMAC zero-pads keys to the 64-byte block, so it is the same key.
            mac.init(new SecretKeySpec(key.length == 0 ? new byte[64] : key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** RFC 5869 HKDF-SHA256. */
    public static byte[] hkdf(byte[] ikm, byte[] salt, byte[] info, int length) {
        byte[] prk = hmac(salt == null || salt.length == 0 ? new byte[32] : salt, ikm);
        byte[] out = new byte[length];
        byte[] block = new byte[0];
        int pos = 0;
        for (int counter = 1; pos < length; counter++) {
            byte[] in = new byte[block.length + info.length + 1];
            System.arraycopy(block, 0, in, 0, block.length);
            System.arraycopy(info, 0, in, block.length, info.length);
            in[in.length - 1] = (byte) counter;
            block = hmac(prk, in);
            int n = Math.min(block.length, length - pos);
            System.arraycopy(block, 0, out, pos, n);
            pos += n;
        }
        return out;
    }

    /** Constant-time compare (MessageDigest.isEqual is constant-time since Java 6u17 / all Android versions). */
    public static boolean equal(byte[] a, byte[] b) {
        return a != null && b != null && MessageDigest.isEqual(a, b);
    }

    // ---- group -------------------------------------------------------------------------------------

    /** H2G(label) = (int_be(SHA256(label||0) || … || SHA256(label||8)) mod p)^2 mod p. */
    public static BigInteger hashToGroup(String label) {
        byte[] l = utf8(label);
        byte[] expanded = new byte[9 * 32];
        for (int i = 0; i < 9; i++) {
            byte[] in = new byte[l.length + 1];
            System.arraycopy(l, 0, in, 0, l.length);
            in[l.length] = (byte) i;
            System.arraycopy(sha256(in), 0, expanded, i * 32, 32);
        }
        return new BigInteger(1, expanded).mod(P).modPow(BigInteger.valueOf(2), P);
    }

    public static byte[] elementBytes(BigInteger x) {
        byte[] raw = x.toByteArray();
        byte[] out = new byte[ELEMENT_BYTES];
        int n = Math.min(raw.length, ELEMENT_BYTES);
        System.arraycopy(raw, raw.length - n, out, ELEMENT_BYTES - n, n);
        return out;
    }

    /** 256 bytes, 1 &lt; X &lt; p-1 and X^q = 1 (prime-order subgroup), else IllegalArgumentException. */
    public static BigInteger parseElement(byte[] data) {
        if (data == null || data.length != ELEMENT_BYTES) throw new IllegalArgumentException("group element must be 256 bytes");
        BigInteger x = new BigInteger(1, data);
        if (x.compareTo(BigInteger.ONE) <= 0 || x.compareTo(P.subtract(BigInteger.ONE)) >= 0
                || !x.modPow(Q, P).equals(BigInteger.ONE)) {
            throw new IllegalArgumentException("not an element of the prime-order subgroup");
        }
        return x;
    }

    /** w = int_be(SHA256("OTV1 spake2 w" || 0x00 || code)) mod q; the code is exactly 6 digits. */
    public static BigInteger codeScalar(String code) {
        if (code == null || !code.matches("[0-9]{6}")) throw new IllegalArgumentException("pairing code must be 6 digits");
        byte[] prefix = utf8("OTV1 spake2 w");
        byte[] c = utf8(code);
        byte[] in = new byte[prefix.length + 1 + c.length];
        System.arraycopy(prefix, 0, in, 0, prefix.length);
        System.arraycopy(c, 0, in, prefix.length + 1, c.length);
        return new BigInteger(1, sha256(in)).mod(Q);
    }

    /** Uniform in [1, q-1]. */
    public static BigInteger randomScalar() {
        BigInteger r;
        do {
            r = new BigInteger(Q.bitLength(), RANDOM);
        } while (r.signum() == 0 || r.compareTo(Q) >= 0);
        return r;
    }

    private static void lp(java.io.ByteArrayOutputStream out, byte[] x) {
        long n = x.length;
        for (int i = 0; i < 8; i++) out.write((int) (n >>> (8 * i)) & 0xFF);
        out.write(x, 0, x.length);
    }

    public static byte[] transcript(String pcId, String tvId, BigInteger t, BigInteger s, BigInteger k, BigInteger w) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(1200);
        lp(out, utf8("pc:" + pcId));
        lp(out, utf8("tv:" + tvId));
        lp(out, elementBytes(t));
        lp(out, elementBytes(s));
        lp(out, elementBytes(k));
        lp(out, elementBytes(w));
        return out.toByteArray();
    }

    /** Key schedule from the transcript (§4). */
    public static final class Keys {
        public final byte[] ttHash, confirmPc, confirmTv, token;

        Keys(byte[] tt) {
            ttHash = sha256(tt);
            byte[] ke = java.util.Arrays.copyOfRange(ttHash, 0, 16), ka = java.util.Arrays.copyOfRange(ttHash, 16, 32);
            confirmPc = hmac(hmac(ka, utf8("OTV1 confirm pc")), ttHash);
            confirmTv = hmac(hmac(ka, utf8("OTV1 confirm tv")), ttHash);
            token = hkdf(ke, ttHash, utf8("OTV1 token"), 32);
        }
    }

    /** The TV, SPAKE2 party B: S = g^y · N^w, K = (T · M^(q-w))^y. */
    public static final class TvPairing {
        public final BigInteger s;
        public final Keys keys;

        public TvPairing(String code, String tvId, String pcId, byte[] tBytes, BigInteger y) {
            BigInteger w = codeScalar(code);
            BigInteger t = parseElement(tBytes);
            if (y == null) y = randomScalar();
            s = G.modPow(y, P).multiply(N.modPow(w, P)).mod(P);
            BigInteger k = t.multiply(M.modPow(Q.subtract(w), P)).mod(P).modPow(y, P);
            keys = new Keys(transcript(pcId, tvId, t, s, k, w));
        }
    }

    /** The PC side, party A; only used by tests and the JVM harness. */
    public static final class PcPairing {
        final BigInteger w, x, t;
        final String pcId;

        public PcPairing(String code, String pcId, BigInteger x) {
            this.w = codeScalar(code);
            this.x = x != null ? x : randomScalar();
            this.pcId = pcId;
            this.t = G.modPow(this.x, P).multiply(M.modPow(w, P)).mod(P);
        }

        public byte[] message() {
            return elementBytes(t);
        }

        public Keys finish(String tvId, byte[] sBytes) {
            BigInteger s = parseElement(sBytes);
            BigInteger k = s.multiply(N.modPow(Q.subtract(w), P)).mod(P).modPow(x, P);
            return new Keys(transcript(pcId, tvId, t, s, k, w));
        }
    }

    // ---- requests, responses, events ---------------------------------------------------------------

    public static byte[] requestString(String method, String target, String ts, String nonce, byte[] body) {
        return utf8("OTV1\n" + method.toUpperCase(java.util.Locale.ROOT) + "\n" + target + "\n" + ts + "\n" + nonce
                + "\n" + hex(sha256(body)));
    }

    /** As {@link #requestString} for a body that was streamed to disk: {@code bodySha} = hex(SHA-256(body)). */
    public static byte[] requestStringHex(String method, String target, String ts, String nonce, String bodySha) {
        return utf8("OTV1\n" + method.toUpperCase(java.util.Locale.ROOT) + "\n" + target + "\n" + ts + "\n" + nonce + "\n" + bodySha);
    }

    public static String signRequest(byte[] token, String method, String target, String ts, String nonce, byte[] body) {
        return b64u(hmac(token, requestString(method, target, ts, nonce, body)));
    }

    public static String signResponse(byte[] token, String nonce, int status, byte[] body) {
        return b64u(hmac(token, utf8("OTV1-R\n" + nonce + "\n" + status + "\n" + hex(sha256(body)))));
    }

    public static byte[] wsSessionKey(byte[] token, String clientNonce, String serverNonce) {
        return hmac(token, utf8("OTV1-WS\n" + clientNonce + "\n" + serverNonce));
    }

    public static String seal(byte[] ks, String payload) {
        return b64u(hmac(ks, utf8(payload))) + " " + payload;
    }

    /** Returns the payload when the MAC matches the exact payload bytes, else null. */
    public static String open(byte[] ks, String frame) {
        int sp = frame.indexOf(' ');
        if (sp <= 0) return null;
        String payload = frame.substring(sp + 1);
        try {
            return equal(unb64u(frame.substring(0, sp)), hmac(ks, utf8(payload))) ? payload : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ---- optional payload encryption (2.13): AES-256-GCM, negotiated with "X-OTV-Enc: A256GCM" ----
    public static final String ENC_HEADER = "X-OTV-Enc";
    public static final String ENC_ALG = "A256GCM";

    static byte[] encKey(byte[] token) {
        return hkdf(token, utf8("OTV1 enc salt"), utf8("OTV1 enc"), 32);
    }

    /** iv(12) || ciphertext || tag(16). aad binds the payload to one request nonce and direction. */
    public static byte[] seal(byte[] token, String aad, byte[] plain) throws java.security.GeneralSecurityException {
        byte[] iv = new byte[12];
        new java.security.SecureRandom().nextBytes(iv);
        javax.crypto.Cipher c = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
        c.init(javax.crypto.Cipher.ENCRYPT_MODE, new javax.crypto.spec.SecretKeySpec(encKey(token), "AES"),
                new javax.crypto.spec.GCMParameterSpec(128, iv));
        c.updateAAD(utf8(aad));
        byte[] ct = c.doFinal(plain);
        byte[] out = new byte[12 + ct.length];
        System.arraycopy(iv, 0, out, 0, 12);
        System.arraycopy(ct, 0, out, 12, ct.length);
        return out;
    }

    public static byte[] open(byte[] token, String aad, byte[] sealed) throws java.security.GeneralSecurityException {
        if (sealed.length < 28) throw new java.security.GeneralSecurityException("short ciphertext");
        javax.crypto.Cipher c = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
        c.init(javax.crypto.Cipher.DECRYPT_MODE, new javax.crypto.spec.SecretKeySpec(encKey(token), "AES"),
                new javax.crypto.spec.GCMParameterSpec(128, sealed, 0, 12));
        c.updateAAD(utf8(aad));
        return c.doFinal(sealed, 12, sealed.length - 12);
    }
}
