package com.omarchytv.mobile;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Cipher;

/**
 * Pure-Java Android ADB over TCP (port 5555) protocol engine for OmarchyTV Mobile.
 *
 * Implements:
 * - 2048-bit RSA keypair generation & persistence (`adbkey` / `adbkey.pub`)
 * - 524-byte Android `RSAPublicKey` binary struct with Montgomery parameters (`n0inv`, `r^2 mod n`)
 *   and identity `omarchytv@mobile\0`
 * - `CNXN` (`0x4e584e43`) & `AUTH` (`TOKEN` -> `SIGNATURE` -> `RSAPUBLICKEY`)
 * - `OPEN` (`shell:...`) for command execution
 * - `OPEN` (`sync:`) + `SEND` / `DATA` / `DONE` / `OKAY` binary streaming to push `.apk` payloads
 *   to `/data/local/tmp/omarchytv.apk`
 * - 1-tap `pm install -r /data/local/tmp/omarchytv.apk` and safe default Home pinning
 *   (`cmd package set-home-activity com.omarchytv.launcher/.HomeV29` and `com.omarchytv.home/.MainActivity`)
 * - Safe reversible package disable/enable (`pm disable-user --user 0 <pkg>`, `pm enable <pkg>`)
 */
public final class AdbTcpClient implements Closeable {

    public static final int DEFAULT_PORT = 5555;

    /** Parsed "host" or "host:port" from user input; port falls back to the given default. */
    public static final class HostPort {
        public final String host;
        public final int port;
        HostPort(String host, int port) { this.host = host; this.port = port; }
    }

    /**
     * Splits "192.168.1.50:37123" into host and port. Accepts a bare host (uses {@code defaultPort}).
     * Throws IllegalArgumentException for an empty host or a port outside 1..65535.
     */
    public static HostPort parseHostPort(String input, int defaultPort) {
        String s = input == null ? "" : input.trim();
        int port = defaultPort;
        int colon = s.lastIndexOf(':');
        if (colon >= 0 && s.indexOf(':') == colon) {
            String p = s.substring(colon + 1).trim();
            s = s.substring(0, colon).trim();
            try { port = Integer.parseInt(p); } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid ADB port: " + p);
            }
        }
        if (s.isEmpty()) throw new IllegalArgumentException("Empty host");
        if (port < 1 || port > 65535) throw new IllegalArgumentException("ADB port out of range: " + port);
        return new HostPort(s, port);
    }
    public static final String REMOTE_APK_TMP = "/data/local/tmp/omarchytv.apk";

    public static final int CMD_SYNC = 0x434e5953;
    public static final int CMD_CNXN = 0x4e584e43;
    public static final int CMD_AUTH = 0x48545541;
    public static final int CMD_OPEN = 0x4e45504f;
    public static final int CMD_OKAY = 0x59414b4f;
    public static final int CMD_CLSE = 0x45534c43;
    public static final int CMD_WRTE = 0x45545257;
    public static final int CMD_STLS = 0x534c5453;

    public static final int ADB_VERSION = 0x01000001;
    public static final int ADB_MAXDATA = 256 * 1024;

    public static final int AUTH_TYPE_TOKEN = 1;
    public static final int AUTH_TYPE_SIGNATURE = 2;
    public static final int AUTH_TYPE_RSAPUBLICKEY = 3;

    /** ASN.1 DigestInfo header for SHA-1 used by adbd's RSA AUTH_SIGNATURE verification. */
    private static final byte[] SHA1_DIGEST_INFO_PREFIX = new byte[]{
            0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e,
            0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14
    };

    public interface ApprovalListener {
        void onWaitingForTvApproval(String keyFingerprint);
    }

    public interface ProgressListener {
        void onProgress(long bytesSent, long totalBytes, String stage);
    }

    public static final class Packet {
        public int command;
        public int arg0;
        public int arg1;
        public byte[] payload;
    }

    public static final class InstalledLauncherInfo {
        public final boolean installed;
        public final String packageName;
        public final String versionName;
        public final int versionCode;

        public InstalledLauncherInfo(boolean installed, String packageName, String versionName, int versionCode) {
            this.installed = installed;
            this.packageName = packageName;
            this.versionName = versionName;
            this.versionCode = versionCode;
        }
    }

    private final Socket socket;
    private final DataInputStream in;
    private final OutputStream out;
    private final String banner;
    private int nextLocalStreamId = 1;

    private AdbTcpClient(Socket socket, DataInputStream in, OutputStream out, String banner) {
        this.socket = socket;
        this.in = in;
        this.out = out;
        this.banner = banner;
    }

    public String getBanner() {
        return banner;
    }

    /**
     * Connects to the TV's Wireless ADB TCP port (default 5555) and completes the CNXN/AUTH handshake.
     */
    public static AdbTcpClient connect(String host, int port, File keyDir, int approvalTimeoutMs,
                                       ApprovalListener approvalListener) throws IOException {
        KeyPair keyPair = loadOrGenerateKeyPair(keyDir);
        String fingerprint = computePublicKeyFingerprint(keyPair.getPublic());
        Socket sock = new Socket();
        try {
            sock.connect(new InetSocketAddress(host, port), 4_000);
            sock.setTcpNoDelay(true);
            sock.setSoTimeout(10_000);
        } catch (IOException e) {
            try { sock.close(); } catch (IOException ignored) {}
            throw new IOException("Cannot reach TV ADB at " + host + ":" + port + " (" + e.getMessage() + ")", e);
        }

        DataInputStream dis = new DataInputStream(sock.getInputStream());
        OutputStream os = sock.getOutputStream();

        try {
            writePacket(os, CMD_CNXN, ADB_VERSION, ADB_MAXDATA,
                    "host::features=shell_v2,cmd,stat_v2\0".getBytes(StandardCharsets.US_ASCII));

            boolean triedSignature = false;
            boolean sentPublicKey = false;

            while (true) {
                Packet pkt;
                try {
                    pkt = readPacket(dis);
                } catch (SocketTimeoutException ste) {
                    throw new IOException(sentPublicKey
                            ? "Timed out waiting for 'Allow debugging' approval on TV screen"
                            : "Timed out waiting for ADB handshake from TV", ste);
                }

                if (pkt.command == CMD_CNXN) {
                    sock.setSoTimeout(30_000);
                    String b = pkt.payload != null
                            ? new String(pkt.payload, StandardCharsets.UTF_8).replace("\0", "").trim()
                            : "device";
                    return new AdbTcpClient(sock, dis, os, b);
                }

                if (pkt.command == CMD_STLS) {
                    throw new IOException("TV requested ADB TLS (Android 11+ Wireless debugging). Pair once from a PC with `adb pair <ip>:<pair-port> <code>`, then `adb connect <ip>:<port>` and `adb tcpip 5555`, or use classic Network debugging on port " + port);
                }

                if (pkt.command != CMD_AUTH || pkt.arg0 != AUTH_TYPE_TOKEN) {
                    throw new IOException("Unexpected ADB packet during handshake: 0x" + Integer.toHexString(pkt.command));
                }

                if (!triedSignature) {
                    byte[] sig = signTokenWithRsaPkcs1(keyPair.getPrivate(), pkt.payload);
                    writePacket(os, CMD_AUTH, AUTH_TYPE_SIGNATURE, 0, sig);
                    triedSignature = true;
                } else if (!sentPublicKey) {
                    byte[] pubBlob = encodeAndroidRsaPublicKey(keyPair.getPublic(), "omarchytv@mobile");
                    writePacket(os, CMD_AUTH, AUTH_TYPE_RSAPUBLICKEY, 0, pubBlob);
                    sentPublicKey = true;
                    if (approvalListener != null) {
                        approvalListener.onWaitingForTvApproval(fingerprint);
                    }
                    sock.setSoTimeout(Math.max(10_000, approvalTimeoutMs));
                } else {
                    throw new IOException("TV rejected ADB RSA key (" + fingerprint + ")");
                }
            }
        } catch (IOException e) {
            try { sock.close(); } catch (IOException ignored) {}
            throw e;
        }
    }

    /**
     * Executes a shell command over an ADB `shell:` stream and returns combined stdout/stderr.
     */
    public synchronized String shell(String command) throws IOException {
        int localId = nextLocalStreamId++;
        byte[] openData = ("shell:" + command + "\0").getBytes(StandardCharsets.UTF_8);
        writePacket(out, CMD_OPEN, localId, 0, openData);

        int remoteId = 0;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        while (true) {
            Packet pkt = readPacket(in);
            if (pkt.arg1 != localId) {
                continue;
            }
            if (pkt.command == CMD_OKAY) {
                remoteId = pkt.arg0;
            } else if (pkt.command == CMD_WRTE) {
                if (pkt.payload != null && pkt.payload.length > 0) {
                    output.write(pkt.payload);
                }
                writePacket(out, CMD_OKAY, localId, pkt.arg0, null);
            } else if (pkt.command == CMD_CLSE) {
                if (remoteId == 0 && pkt.arg0 == 0) {
                    throw new IOException("ADB rejected shell command: " + command);
                }
                writePacket(out, CMD_CLSE, localId, pkt.arg0, null);
                return new String(output.toByteArray(), StandardCharsets.UTF_8);
            }
        }
    }

    /**
     * Streams a local file to `remotePath` on the TV using the ADB `sync:` (`SEND` / `DATA` / `DONE`) protocol.
     */
    public synchronized void pushFile(File localFile, String remotePath, int unixMode,
                                      ProgressListener progress) throws IOException {
        if (localFile == null || !localFile.isFile()) {
            throw new IOException("Local file does not exist: " + localFile);
        }
        long totalBytes = localFile.length();
        int localId = nextLocalStreamId++;

        writePacket(out, CMD_OPEN, localId, 0, "sync:\0".getBytes(StandardCharsets.US_ASCII));
        Packet openReply = waitForStreamPacket(localId);
        if (openReply.command != CMD_OKAY) {
            throw new IOException("Failed to open ADB sync stream");
        }
        int remoteId = openReply.arg0;

        try {
            String spec = remotePath + "," + unixMode;
            byte[] specBytes = spec.getBytes(StandardCharsets.UTF_8);
            ByteBuffer sendHdr = ByteBuffer.allocate(8 + specBytes.length).order(ByteOrder.LITTLE_ENDIAN);
            sendHdr.put((byte) 'S').put((byte) 'E').put((byte) 'N').put((byte) 'D');
            sendHdr.putInt(specBytes.length);
            sendHdr.put(specBytes);

            writePacket(out, CMD_WRTE, localId, remoteId, sendHdr.array());
            expectOkay(localId);

            byte[] fileBuf = new byte[32 * 1024];
            long sent = 0;
            try (FileInputStream fis = new FileInputStream(localFile)) {
                int n;
                while ((n = fis.read(fileBuf)) > 0) {
                    ByteBuffer dataChunk = ByteBuffer.allocate(8 + n).order(ByteOrder.LITTLE_ENDIAN);
                    dataChunk.put((byte) 'D').put((byte) 'A').put((byte) 'T').put((byte) 'A');
                    dataChunk.putInt(n);
                    dataChunk.put(fileBuf, 0, n);

                    writePacket(out, CMD_WRTE, localId, remoteId, dataChunk.array());
                    expectOkay(localId);
                    sent += n;
                    if (progress != null) {
                        progress.onProgress(sent, totalBytes, "Pushing APK to TV");
                    }
                }
            }

            int mtimeSec = (int) (localFile.lastModified() / 1000L);
            ByteBuffer doneMsg = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
            doneMsg.put((byte) 'D').put((byte) 'O').put((byte) 'N').put((byte) 'E');
            doneMsg.putInt(mtimeSec);
            writePacket(out, CMD_WRTE, localId, remoteId, doneMsg.array());
            expectOkay(localId);

            // Read sync status reply (OKAY\0\0\0\0 or FAIL<len><reason>)
            Packet statusPkt = waitForStreamWrite(localId, remoteId);
            byte[] resp = statusPkt.payload != null ? statusPkt.payload : new byte[0];
            if (resp.length >= 4) {
                String code = new String(resp, 0, 4, StandardCharsets.US_ASCII);
                if ("FAIL".equals(code)) {
                    String errMsg = resp.length > 8
                            ? new String(resp, 8, resp.length - 8, StandardCharsets.UTF_8)
                            : "sync transfer failed";
                    throw new IOException("ADB sync FAIL: " + errMsg);
                }
                if (!"OKAY".equals(code)) {
                    throw new IOException("Unexpected ADB sync status: " + code);
                }
            } else {
                throw new IOException("Truncated ADB sync status response");
            }
        } finally {
            writePacket(out, CMD_CLSE, localId, remoteId, null);
            // Drain CLSE ack if present
            try {
                Packet p = readPacket(in);
                if (p.command == CMD_CLSE && p.arg1 == localId) {
                    // closed cleanly
                }
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * Pushes `apkFile` to `/data/local/tmp/omarchytv.apk`, runs `pm install -r`, and pins OmarchyTV as Home.
     * Never touches `com.google.android.tungsten.setupwraith` so Wi-Fi never drops on Android 14.
     */
    public String installAndPinHome(File apkFile, boolean pinDefaultHome, ProgressListener progress) throws IOException {
        if (progress != null) {
            progress.onProgress(0, apkFile.length(), "Uploading APK over ADB sync");
        }
        pushFile(apkFile, REMOTE_APK_TMP, 0644, progress);

        if (progress != null) {
            progress.onProgress(apkFile.length(), apkFile.length(), "Running pm install -r " + REMOTE_APK_TMP);
        }
        String installOut = shell("pm install -r " + REMOTE_APK_TMP);
        shell("rm -f " + REMOTE_APK_TMP);

        if (installOut == null || !installOut.contains("Success")) {
            throw new IOException("pm install -r failed: " + (installOut == null ? "no output" : installOut.trim()));
        }

        StringBuilder summary = new StringBuilder("pm install -r: Success");
        if (pinDefaultHome) {
            if (progress != null) {
                progress.onProgress(apkFile.length(), apkFile.length(), "Pinning OmarchyTV as Home activity");
            }
            // Support both com.omarchytv.launcher/.HomeV29 and com.omarchytv.home/.MainActivity
            String outLauncher = shell("cmd package set-home-activity com.omarchytv.launcher/.HomeV29");
            String outHome = shell("cmd package set-home-activity --user 0 com.omarchytv.home/.MainActivity");
            shell("am start -a android.intent.action.MAIN -c android.intent.category.HOME");
            summary.append(" | Home pinned (")
                    .append(outLauncher != null && !outLauncher.toLowerCase().contains("error")
                            ? "com.omarchytv.launcher/.HomeV29"
                            : "com.omarchytv.home/.MainActivity")
                    .append(")");
            if (outHome != null && outHome.contains("Success")) {
                summary.append(" [verified]");
            }
        }
        return summary.toString();
    }

    /**
     * Queries the TV via `dumpsys package` for `com.omarchytv.home` or `com.omarchytv.launcher`.
     */
    public InstalledLauncherInfo queryInstalledLauncher() throws IOException {
        for (String pkg : new String[]{"com.omarchytv.home", "com.omarchytv.launcher"}) {
            String dump = shell("dumpsys package " + pkg);
            if (dump != null && dump.contains("versionName=")) {
                Matcher vn = Pattern.compile("versionName=([^\\s\\r\\n]+)").matcher(dump);
                Matcher vc = Pattern.compile("versionCode=(\\d+)").matcher(dump);
                String versionName = vn.find() ? vn.group(1) : "unknown";
                int versionCode = vc.find() ? Integer.parseInt(vc.group(1)) : 0;
                return new InstalledLauncherInfo(true, pkg, versionName, versionCode);
            }
        }
        return new InstalledLauncherInfo(false, "com.omarchytv.home", "not installed", 0);
    }

    /**
     * Safely disables a bloatware package for user 0 (`pm disable-user --user 0 <pkg>`).
     * Refuses any package in `DebloatProfiles.PROTECTED_PACKAGES`.
     */
    public String disablePackageUser0(String pkg) throws IOException {
        if (DebloatProfiles.isProtected(pkg)) {
            throw new IOException("Refusing to disable protected package: " + pkg);
        }
        if (!pkg.matches("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")) {
            throw new IOException("Invalid package name: " + pkg);
        }
        return shell("pm disable-user --user 0 " + pkg).trim();
    }

    /**
     * Re-enables a previously disabled package (`pm enable <pkg>`).
     */
    public String enablePackage(String pkg) throws IOException {
        if (!pkg.matches("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")) {
            throw new IOException("Invalid package name: " + pkg);
        }
        String out = shell("pm enable " + pkg).trim();
        if (out.isEmpty() || out.toLowerCase().contains("error")) {
            out = shell("pm enable --user 0 " + pkg).trim();
        }
        return out;
    }

    /**
     * Lists all currently disabled packages on the TV (`pm list packages -d`).
     */
    public Set<String> listDisabledPackages() throws IOException {
        String out = shell("pm list packages -d");
        Set<String> disabled = new LinkedHashSet<>();
        if (out != null) {
            for (String line : out.split("\\r?\\n")) {
                String clean = line.trim();
                if (clean.startsWith("package:")) {
                    disabled.add(clean.substring("package:".length()).trim());
                }
            }
        }
        return disabled;
    }

    public void sendKeyEvent(int androidKeyCode) throws IOException {
        shell("input keyevent " + androidKeyCode);
    }

    public void sendInputText(String text) throws IOException {
        if (text == null || text.isEmpty()) return;
        String escaped = text.replace(" ", "%s").replace("'", "").replace("\"", "").replace(";", "");
        shell("input text '" + escaped + "'");
    }

    private Packet waitForStreamPacket(int localId) throws IOException {
        while (true) {
            Packet p = readPacket(in);
            if (p.arg1 == localId) {
                return p;
            }
        }
    }

    private void expectOkay(int localId) throws IOException {
        Packet p = waitForStreamPacket(localId);
        if (p.command != CMD_OKAY) {
            throw new IOException("Expected ADB OKAY on stream " + localId + ", got 0x" + Integer.toHexString(p.command));
        }
    }

    private Packet waitForStreamWrite(int localId, int remoteId) throws IOException {
        while (true) {
            Packet p = readPacket(in);
            if (p.arg1 != localId) continue;
            if (p.command == CMD_OKAY) continue;
            if (p.command == CMD_WRTE) {
                writePacket(out, CMD_OKAY, localId, remoteId, null);
                return p;
            }
            if (p.command == CMD_CLSE) {
                throw new IOException("ADB sync stream closed before status reply");
            }
        }
    }

    @Override
    public void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    public static Packet readPacket(DataInputStream in) throws IOException {
        byte[] hdr = new byte[24];
        in.readFully(hdr);
        ByteBuffer b = ByteBuffer.wrap(hdr).order(ByteOrder.LITTLE_ENDIAN);
        Packet p = new Packet();
        p.command = b.getInt();
        p.arg0 = b.getInt();
        p.arg1 = b.getInt();
        int len = b.getInt();
        b.getInt(); // checksum
        int magic = b.getInt();
        if ((p.command ^ 0xffffffff) != magic) {
            throw new IOException("Corrupt ADB packet header (bad magic)");
        }
        if (len < 0 || len > 4 * 1024 * 1024) {
            throw new IOException("Invalid ADB payload length: " + len);
        }
        p.payload = new byte[len];
        if (len > 0) {
            in.readFully(p.payload);
        }
        return p;
    }

    public static void writePacket(OutputStream out, int command, int arg0, int arg1, byte[] payload) throws IOException {
        int len = payload == null ? 0 : payload.length;
        int checksum = 0;
        if (payload != null) {
            for (byte v : payload) {
                checksum += (v & 0xff);
            }
        }
        ByteBuffer b = ByteBuffer.allocate(24 + len).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(command);
        b.putInt(arg0);
        b.putInt(arg1);
        b.putInt(len);
        b.putInt(checksum);
        b.putInt(command ^ 0xffffffff);
        if (payload != null) {
            b.put(payload);
        }
        out.write(b.array());
        out.flush();
    }

    /**
     * Loads or generates the persistent 2048-bit RSA keypair for Wireless ADB authentication.
     */
    public static synchronized KeyPair loadOrGenerateKeyPair(File keyDir) throws IOException {
        if (!keyDir.isDirectory() && !keyDir.mkdirs()) {
            throw new IOException("Cannot create key directory: " + keyDir);
        }
        File privFile = new File(keyDir, "omarchytv_adbkey.pk8");
        File pubFile = new File(keyDir, "omarchytv_adbkey.pub");
        try {
            KeyFactory kf = KeyFactory.getInstance("RSA");
            if (privFile.isFile() && pubFile.isFile()) {
                try {
                    PublicKey pub = kf.generatePublic(new X509EncodedKeySpec(readBytes(pubFile)));
                    PrivateKey priv = kf.generatePrivate(new PKCS8EncodedKeySpec(readBytes(privFile)));
                    return new KeyPair(pub, priv);
                } catch (Exception ignored) {
                    // regenerate if corrupted
                }
            }
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            KeyPair pair = kpg.generateKeyPair();
            writeBytesAtomic(privFile, pair.getPrivate().getEncoded());
            writeBytesAtomic(pubFile, pair.getPublic().getEncoded());
            return pair;
        } catch (GeneralSecurityException e) {
            throw new IOException("RSA key initialization failed: " + e.getMessage(), e);
        }
    }

    /**
     * Signs a 20-byte ADB AUTH_TOKEN using raw RSA PKCS#1 v1.5 padding + SHA-1 DigestInfo prefix.
     */
    public static byte[] signTokenWithRsaPkcs1(PrivateKey privateKey, byte[] token) throws IOException {
        if (token == null) token = new byte[0];
        try {
            byte[] padded = new byte[256];
            padded[0] = 0x00;
            padded[1] = 0x01;
            int psEnd = 256 - SHA1_DIGEST_INFO_PREFIX.length - token.length - 1;
            for (int i = 2; i < psEnd; i++) {
                padded[i] = (byte) 0xff;
            }
            padded[psEnd] = 0x00;
            System.arraycopy(SHA1_DIGEST_INFO_PREFIX, 0, padded, psEnd + 1, SHA1_DIGEST_INFO_PREFIX.length);
            System.arraycopy(token, 0, padded, psEnd + 1 + SHA1_DIGEST_INFO_PREFIX.length, token.length);

            Cipher rsa = Cipher.getInstance("RSA/ECB/NoPadding");
            rsa.init(Cipher.ENCRYPT_MODE, privateKey);
            return rsa.doFinal(padded);
        } catch (GeneralSecurityException e) {
            throw new IOException("Failed to sign ADB token: " + e.getMessage(), e);
        }
    }

    /**
     * Builds the 524-byte little-endian Android `RSAPublicKey` struct (64 words, `n0inv`, `n`, `r^2`, `e`),
     * Base64-encodes it, and appends `" " + identity + "\0"`.
     */
    public static byte[] encodeAndroidRsaPublicKey(PublicKey publicKey, String identity) {
        RSAPublicKey rsa = (RSAPublicKey) publicKey;
        BigInteger n = rsa.getModulus();
        BigInteger r32 = BigInteger.ONE.shiftLeft(32);
        BigInteger n0inv = r32.subtract(n.mod(r32).modInverse(r32)).mod(r32);
        BigInteger rr = BigInteger.ONE.shiftLeft(4096).mod(n);

        ByteBuffer struct = ByteBuffer.allocate(524).order(ByteOrder.LITTLE_ENDIAN);
        struct.putInt(64); // 2048 bits / 32 bits per word
        struct.putInt(n0inv.intValue());
        struct.put(toLittleEndianUnsigned(n, 256));
        struct.put(toLittleEndianUnsigned(rr, 256));
        struct.putInt(rsa.getPublicExponent().intValue());

        String b64 = Base64.getEncoder().encodeToString(struct.array());
        String full = b64 + " " + (identity == null ? "omarchytv@mobile" : identity) + "\0";
        return full.getBytes(StandardCharsets.US_ASCII);
    }

    public static String computePublicKeyFingerprint(PublicKey publicKey) {
        try {
            byte[] sha = MessageDigest.getInstance("SHA-256").digest(publicKey.getEncoded());
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8 && i < sha.length; i++) {
                if (i > 0) sb.append(':');
                sb.append(String.format("%02X", sha[i] & 0xff));
            }
            return sb.toString();
        } catch (Exception e) {
            return "RSA-2048";
        }
    }

    private static byte[] toLittleEndianUnsigned(BigInteger val, int byteLen) {
        byte[] be = val.toByteArray();
        byte[] le = new byte[byteLen];
        for (int i = 0; i < byteLen && i < be.length; i++) {
            le[i] = be[be.length - 1 - i];
        }
        return le;
    }

    private static byte[] readBytes(File f) throws IOException {
        try (FileInputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream((int) f.length());
            byte[] buf = new byte[4096];
            int r;
            while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
            return out.toByteArray();
        }
    }

    private static void writeBytesAtomic(File f, byte[] data) throws IOException {
        File tmp = new File(f.getParentFile(), f.getName() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(data);
            out.getFD().sync();
        }
        if (!tmp.renameTo(f)) {
            throw new IOException("Failed to rename " + tmp + " to " + f);
        }
    }
}
