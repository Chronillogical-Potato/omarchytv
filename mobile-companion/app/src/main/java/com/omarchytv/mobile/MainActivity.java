package com.omarchytv.mobile;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * OmarchyTV Mobile (`com.omarchytv.mobile`) — Universal Smart TV Companion & Wireless Installer.
 *
 * Supports:
 * 1. Android TV / Google TV (`ADB :5555` + `omarchy-link :8999`)
 * 2. LG webOS TV (`:8765` LAN Bridge + `.ipk` Debian `ar` inspector & HTTP server)
 * 3. Samsung Tizen, Hisense VIDAA & Universal Smart TVs (`http://<phone-ip>:8765/tv` HTML5/Canvas
 *    launcher + Samsung `:8001/api/v2/` 1-tap browser launch)
 */
public final class MainActivity extends Activity {

    private static final String PREFS_NAME = "omarchytv_mobile_prefs";
    private static final int TAB_LINK = 0;
    private static final int TAB_UPDATE = 1;
    private static final int TAB_THEMES = 2;
    private static final int TAB_REMOTE = 3;
    private static final int TAB_DEBLOAT = 4;

    public static final int MODE_ANDROID_TV = 0;
    public static final int MODE_LG_WEBOS = 1;
    public static final int MODE_SAMSUNG_UNIVERSAL = 2;

    private final ExecutorService bgPool = Executors.newCachedThreadPool();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private Typeface monoRegular;
    private Typeface monoBold;

    private List<ThemeCatalog.ThemeEntry> themes = new ArrayList<>();
    private ThemeCatalog.ThemeEntry currentTheme;
    private WebOsBridgeServer bridgeServer;
    private int targetPlatformMode = MODE_ANDROID_TV;
    private int currentTab = TAB_LINK;

    // Connection settings
    private String tvIp = "192.168.1.50";
    private int linkPort = OmarchyLinkClient.DEFAULT_LINK_PORT;
    private int adbPort = AdbTcpClient.DEFAULT_PORT;
    private String secretToken = "omarchy-default-secret";
    private String selectedBrand = "sony-bravia";

    // UI references
    private LinearLayout rootLayout;
    private TextView topBarLeft;
    private TextView topBarRight;
    private final Button[] tabButtons = new Button[5];
    private FrameLayout contentFrame;
    private TextView bottomStatusText;
    private TextView logConsoleText;
    private final List<String> consoleLines = new ArrayList<>();

    // Cached release metadata
    private GitLabReleaseClient.DualPlatformCatalog latestReleaseCatalog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        loadFontsAndThemes();
        loadPrefs();
        startEmbeddedBridgeServer();

        rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setBackgroundColor(currentTheme.bgColor());

        buildTopWaybar();
        buildWorkspaceTabBar();

        contentFrame = new FrameLayout(this);
        LinearLayout.LayoutParams contentLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        rootLayout.addView(contentFrame, contentLp);

        buildBottomStatusBar();
        setContentView(rootLayout);

        String phoneIp = OmarchyLinkClient.detectLocalIpv4();
        appendLog("omarchy-mobile 2.13.0 ready | Phone LAN: " + phoneIp + ":8765");
        appendLog("WebTV & webOS Bridge active at http://" + phoneIp + ":8765/tv");
        switchTab(TAB_LINK);
    }

    @Override
    protected void onDestroy() {
        if (bridgeServer != null) {
            bridgeServer.stop();
        }
        bgPool.shutdownNow();
        super.onDestroy();
    }

    private void loadFontsAndThemes() {
        monoRegular = Typeface.MONOSPACE;
        monoBold = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD);
        try (InputStream in = getAssets().open("omarchy_themes.json")) {
            themes = ThemeCatalog.loadFromStream(in);
        } catch (IOException ignored) {
        }
        if (themes.isEmpty()) {
            themes = ThemeCatalog.parseThemesJson("{\"themes\":{\"tokyo-night\":{\"mode\":\"dark\",\"background\":\"#1a1b26\",\"foreground\":\"#a9b1d6\",\"accent\":\"#7aa2f7\",\"bright_magenta\":\"#bb9af7\",\"green\":\"#9ece6a\",\"red\":\"#f7768e\",\"yellow\":\"#e0af68\"}}}");
        }
        currentTheme = themes.get(0);
    }

    private void loadPrefs() {
        SharedPreferences sp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        tvIp = sp.getString("tv_ip", "192.168.1.50");
        linkPort = sp.getInt("link_port", OmarchyLinkClient.DEFAULT_LINK_PORT);
        adbPort = sp.getInt("adb_port", AdbTcpClient.DEFAULT_PORT);
        secretToken = sp.getString("secret_token", "omarchy-default-secret");
        targetPlatformMode = sp.getInt("platform_mode", MODE_ANDROID_TV);
        String savedThemeId = sp.getString("theme_id", "tokyo-night");
        for (ThemeCatalog.ThemeEntry t : themes) {
            if (t.id.equals(savedThemeId)) {
                currentTheme = t;
                break;
            }
        }
    }

    /** Accepts "ip" or "ip:adbPort" (Wireless debugging uses a random port). Keeps old values on bad input. */
    private void applyHostInput(String raw) {
        try {
            AdbTcpClient.HostPort hp = AdbTcpClient.parseHostPort(raw, AdbTcpClient.DEFAULT_PORT);
            tvIp = hp.host;
            adbPort = hp.port;
        } catch (IllegalArgumentException e) {
            appendLog("Ignoring address: " + e.getMessage());
        }
    }

    private void savePrefs() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putString("tv_ip", tvIp)
                .putInt("link_port", linkPort)
                .putInt("adb_port", adbPort)
                .putString("secret_token", secretToken)
                .putInt("platform_mode", targetPlatformMode)
                .putString("theme_id", currentTheme != null ? currentTheme.id : "tokyo-night")
                .apply();
    }

    private void startEmbeddedBridgeServer() {
        bridgeServer = new WebOsBridgeServer(themes);
        bridgeServer.setActiveTheme(currentTheme.id, currentTheme.rawColors);
        bridgeServer.setWebAssetResolver(relPath -> {
            try (InputStream in = getAssets().open(relPath)) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
                return bos.toByteArray();
            }
        });
        bgPool.execute(() -> {
            try {
                bridgeServer.start(WebOsBridgeServer.DEFAULT_PORT);
            } catch (IOException e) {
                try {
                    bridgeServer.start(0);
                } catch (IOException ignored) {
                }
            }
        });
    }

    private void buildTopWaybar() {
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setBackgroundColor(0xFF13141C);
        int padH = dp(12);
        int padV = dp(8);
        topBar.setPadding(padH, padV, padH, padV);

        topBarLeft = new TextView(this);
        topBarLeft.setTypeface(monoBold);
        topBarLeft.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        topBarLeft.setTextColor(currentTheme.accentColor());
        topBarLeft.setText("▊ OMARCHY-MOBILE 2.13.0-beta");
        LinearLayout.LayoutParams lpLeft = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        topBar.addView(topBarLeft, lpLeft);

        topBarRight = new TextView(this);
        topBarRight.setTypeface(monoBold);
        topBarRight.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        topBarRight.setTextColor(currentTheme.greenColor());
        topBarRight.setText("[ATV · webOS · TIZEN · HISENSE]");
        topBar.addView(topBarRight);

        rootLayout.addView(topBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void buildWorkspaceTabBar() {
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.setBackgroundColor(0xFF16161E);

        LinearLayout tabRow = new LinearLayout(this);
        tabRow.setOrientation(LinearLayout.HORIZONTAL);
        tabRow.setPadding(dp(6), dp(6), dp(6), dp(6));

        String[] labels = new String[]{
                "[1:LINK]", "[2:UPDATE]", "[3:THEMES]", "[4:REMOTE]", "[5:DEBLOAT]"
        };
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            Button b = makeTuiButton(labels[i], false);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(36));
            lp.rightMargin = dp(6);
            b.setOnClickListener(v -> switchTab(idx));
            tabButtons[i] = b;
            tabRow.addView(b, lp);
        }
        hsv.addView(tabRow);
        rootLayout.addView(hsv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void buildBottomStatusBar() {
        LinearLayout bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.VERTICAL);
        bottomBar.setBackgroundColor(0xFF13141C);
        bottomBar.setPadding(dp(10), dp(6), dp(10), dp(8));

        logConsoleText = new TextView(this);
        logConsoleText.setTypeface(monoRegular);
        logConsoleText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f);
        logConsoleText.setTextColor(currentTheme.fgColor());
        logConsoleText.setMaxLines(3);
        bottomBar.addView(logConsoleText);

        bottomStatusText = new TextView(this);
        bottomStatusText.setTypeface(monoBold);
        bottomStatusText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        bottomStatusText.setTextColor(currentTheme.accent2Color());
        updateBottomStatusBar();
        bottomBar.addView(bottomStatusText);

        rootLayout.addView(bottomBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void updateBottomStatusBar() {
        if (bottomStatusText == null) return;
        String modeLabel = targetPlatformMode == MODE_ANDROID_TV ? "ANDROID-TV"
                : (targetPlatformMode == MODE_LG_WEBOS ? "LG-webOS" : "SAMSUNG/UNIVERSAL");
        String phoneIp = OmarchyLinkClient.detectLocalIpv4();
        bottomStatusText.setText(String.format(Locale.ROOT,
                "TARGET: %s (%s) │ BRIDGE :8765/tv ON (%s) │ THEME: %s",
                modeLabel, tvIp, phoneIp, currentTheme.id));
    }

    private void appendLog(String msg) {
        mainHandler.post(() -> {
            consoleLines.add(">_ " + msg);
            while (consoleLines.size() > 3) {
                consoleLines.remove(0);
            }
            if (logConsoleText != null) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < consoleLines.size(); i++) {
                    if (i > 0) sb.append('\n');
                    sb.append(consoleLines.get(i));
                }
                logConsoleText.setText(sb.toString());
            }
            updateBottomStatusBar();
        });
    }

    private void switchTab(int idx) {
        currentTab = idx;
        rootLayout.setBackgroundColor(currentTheme.bgColor());
        topBarLeft.setTextColor(currentTheme.accentColor());
        topBarRight.setTextColor(currentTheme.greenColor());

        for (int i = 0; i < tabButtons.length; i++) {
            styleTabButton(tabButtons[i], i == idx);
        }

        contentFrame.removeAllViews();
        ScrollView sv = new ScrollView(this);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(10), dp(8), dp(10), dp(16));

        if (idx == TAB_LINK) {
            populateLinkTab(page);
        } else if (idx == TAB_UPDATE) {
            populateUpdateTab(page);
        } else if (idx == TAB_THEMES) {
            populateThemesTab(page);
        } else if (idx == TAB_REMOTE) {
            populateRemoteTab(page);
        } else if (idx == TAB_DEBLOAT) {
            populateDebloatTab(page);
        }

        sv.addView(page);
        contentFrame.addView(sv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        updateBottomStatusBar();
    }

    // =========================================================================
    // [1:LINK] — Universal Smart TV Mode Selector, LAN Scanner & Bridge
    // =========================================================================
    private void populateLinkTab(LinearLayout page) {
        TuiBoxView modeBox = makeBox("UNIVERSAL TV PLATFORM MODE");
        TextView modeHint = makeLabel("Select your Smart TV platform (all modes run in parallel):", 11f, currentTheme.fgColor());
        modeBox.addView(modeHint);

        LinearLayout modeRow = new LinearLayout(this);
        modeRow.setOrientation(LinearLayout.VERTICAL);
        modeRow.setPadding(0, dp(6), 0, 0);

        String[] modes = new String[]{
                "[ ANDROID TV / GOOGLE TV (ADB :5555 · OMARCHY-LINK :8999) ]",
                "[ LG webOS TV (:8765 LAN BRIDGE · .IPK INSTALLER) ]",
                "[ SAMSUNG TIZEN · HISENSE VIDAA · ALL TVs (:8001 / :8765/tv) ]"
        };
        for (int m = 0; m < modes.length; m++) {
            final int modeVal = m;
            Button mb = makeTuiButton(modes[m], targetPlatformMode == modeVal);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(38));
            lp.bottomMargin = dp(6);
            mb.setOnClickListener(v -> {
                targetPlatformMode = modeVal;
                savePrefs();
                appendLog("Switched target mode -> " + modes[modeVal]);
                switchTab(TAB_LINK);
            });
            modeRow.addView(mb, lp);
        }
        modeBox.addView(modeRow);
        page.addView(modeBox, boxParams());

        // Phone Built-in Universal Bridge Server (:8765)
        String phoneIp = OmarchyLinkClient.detectLocalIpv4();
        int port = bridgeServer != null ? bridgeServer.getLocalPort() : WebOsBridgeServer.DEFAULT_PORT;
        TuiBoxView bridgeBox = makeBox("BUILT-IN PHONE BRIDGE (:8765 WEBOS + UNIVERSAL WEBTV)");
        bridgeBox.addView(makeLabel(
                "● LIVE ON PHONE: http://" + phoneIp + ":" + port + "/tv\n"
                        + "• LG webOS Status Bridge: http://" + phoneIp + ":" + port + "/v1/status\n"
                        + "• Samsung Tizen / Hisense / Any TV: Open http://" + phoneIp + ":" + port + "/tv\n"
                        + "  in TV Browser or tap 1-Tap Launch below — syncs themes & remote live!",
                11f, currentTheme.greenColor()));
        page.addView(bridgeBox, boxParams());

        // Manual IP + LAN Subnet Discovery
        TuiBoxView connBox = makeBox("TV LAN DISCOVERY & PAIRING");
        final EditText ipInput = makeInput(adbPort == AdbTcpClient.DEFAULT_PORT ? tvIp : tvIp + ":" + adbPort, "TV IP or IP:ADB-port (e.g. 192.168.1.50 or 192.168.1.50:37123)");
        final EditText tokenInput = makeInput(secretToken, "omarchy-link Shared Secret / PIN");
        connBox.addView(makeLabel("Target TV IP Address:", 11f, currentTheme.accentColor()));
        connBox.addView(ipInput, rowParams());
        connBox.addView(makeLabel("omarchy-link HMAC Secret / Token:", 11f, currentTheme.accentColor()));
        connBox.addView(tokenInput, rowParams());

        final TextView scanResultText = makeLabel(
                "Ready to scan /24 Wi-Fi subnet (:8999, :5555, :47800, :8001)...",
                11f, currentTheme.yellowColor());
        connBox.addView(scanResultText, rowParams());

        Button btnScan = makeTuiButton("[ AUTO-SCAN WI-FI SUBNET (/24) ]", true);
        btnScan.setOnClickListener(v -> {
            applyHostInput(ipInput.getText().toString());
            secretToken = tokenInput.getText().toString().trim();
            savePrefs();
            scanResultText.setText("Scanning local /24 subnet on ports 8999, 5555, 47800...");
            appendLog("Scanning LAN /24 for OmarchyTV / Android TV / Smart TVs...");
            bgPool.execute(() -> {
                List<OmarchyLinkClient.DiscoveredTv> found = OmarchyLinkClient.scanSubnet(null, 180, secretToken);
                mainHandler.post(() -> {
                    if (found.isEmpty()) {
                        scanResultText.setText("Scan complete: 0 hosts on :8999/:5555 (use manual IP or :8765/tv).");
                        appendLog("Subnet scan finished (0 open :8999/:5555 hosts)");
                    } else {
                        OmarchyLinkClient.DiscoveredTv first = found.get(0);
                        tvIp = first.ip;
                        ipInput.setText(first.ip);
                        savePrefs();
                        StringBuilder sb = new StringBuilder();
                        for (OmarchyLinkClient.DiscoveredTv d : found) {
                            sb.append("✓ ").append(d.tvName).append(" [").append(d.ip).append("] ")
                                    .append(d.port8999Open ? ":8999 " : "")
                                    .append(d.port5555Open ? ":5555 " : "")
                                    .append("(").append(d.latencyMs).append("ms)\n");
                        }
                        scanResultText.setText(sb.toString().trim());
                        appendLog("Discovered TV: " + first.tvName + " at " + first.ip);
                    }
                });
            });
        });
        connBox.addView(btnScan, rowParams());

        Button btnPing = makeTuiButton("[ PING OMARCHY-LINK (:8999) & ADB (:5555) ]", false);
        btnPing.setOnClickListener(v -> {
            applyHostInput(ipInput.getText().toString());
            secretToken = tokenInput.getText().toString().trim();
            savePrefs();
            appendLog("Probing " + tvIp + " (:8999 / :5555)...");
            bgPool.execute(() -> {
                OmarchyLinkClient.DiscoveredTv d = OmarchyLinkClient.probeHost(
                        tvIp, linkPort, adbPort, 47800, 1500, secretToken);
                mainHandler.post(() -> {
                    if (d != null) {
                        scanResultText.setText("CONNECTED: " + d.tvName + " | ver=" + d.version
                                + " | theme=" + d.activeTheme + " | " + d.latencyMs + "ms");
                        appendLog("Connected to " + d.tvName + " (" + d.ip + ")");
                    } else {
                        scanResultText.setText("Host " + tvIp + " unreachable on :8999/:5555");
                        appendLog("No response from " + tvIp + " on :8999/:5555");
                    }
                });
            });
        });
        connBox.addView(btnPing, rowParams());

        Button btnSamsungLaunch = makeTuiButton(
                "[ 1-TAP LAUNCH ON SAMSUNG TIZEN / SMART TV (:8001 -> :8765/tv) ]", false);
        btnSamsungLaunch.setOnClickListener(v -> {
            applyHostInput(ipInput.getText().toString());
            savePrefs();
            String webTvUrl = "http://" + OmarchyLinkClient.detectLocalIpv4() + ":"
                    + (bridgeServer != null ? bridgeServer.getLocalPort() : 8765) + "/tv";
            appendLog("Launching " + webTvUrl + " on Smart TV " + tvIp + ":8001...");
            bgPool.execute(() -> {
                try {
                    String msg = WebOsBridgeServer.launchOnSamsungTizenOrSmartTv(tvIp, 8001, webTvUrl);
                    appendLog(msg);
                    mainHandler.post(() -> scanResultText.setText("✓ " + msg));
                } catch (Exception e) {
                    appendLog("SmartView :8001 note: open " + webTvUrl + " directly on TV browser");
                    mainHandler.post(() -> scanResultText.setText(
                            "Open " + webTvUrl + " in your Samsung / Hisense / LG TV Browser!"));
                }
            });
        });
        connBox.addView(btnSamsungLaunch, rowParams());

        page.addView(connBox, boxParams());
    }

    // =========================================================================
    // [2:UPDATE] — Dual-Mirror (GitLab + GitHub) & Dual-Platform (.APK + .IPK)
    // =========================================================================
    private void populateUpdateTab(LinearLayout page) {
        TuiBoxView mirrorBox = makeBox("DUAL-MIRROR RELEASE ENGINE (GITLAB + GITHUB)");
        mirrorBox.addView(makeLabel(
                "• Primary Mirror : gitlab.com/piermark/omarchytv (Project #87958120)\n"
                        + "• Fallback Mirror: github.com/piermark/omarchytv/releases\n"
                        + "• Signing Cert   : 90aa68d9f45e623e...a4568c3f3a78f8ea33d",
                10.5f, currentTheme.fgColor()));

        final TextView releaseStatus = makeLabel(
                "Tap [CHECK LATEST RELEASES] to query GitLab + GitHub for Android TV (.apk) & LG webOS (.ipk).",
                11f, currentTheme.yellowColor());
        mirrorBox.addView(releaseStatus, rowParams());

        Button btnCheck = makeTuiButton("[ CHECK LATEST RELEASES (GITLAB + GITHUB) ]", true);
        btnCheck.setOnClickListener(v -> {
            releaseStatus.setText("Querying GitLab API & GitHub API in parallel...");
            appendLog("Checking releases on GitLab (#87958120) + GitHub...");
            bgPool.execute(() -> {
                try {
                    GitLabReleaseClient client = new GitLabReleaseClient();
                    GitLabReleaseClient.DualPlatformCatalog cat = client.fetchDualPlatformReleases();
                    latestReleaseCatalog = cat;
                    StringBuilder sb = new StringBuilder();
                    sb.append("Mirrors: ").append(cat.activeMirrorLabel).append("\n");
                    if (cat.androidTvApk != null) {
                        sb.append("• Android TV APK: ").append(cat.androidTvApk.tagName)
                                .append(" (").append(cat.androidTvApk.assetName).append(")\n")
                                .append("  SHA-256: ").append(shortHash(cat.androidTvApk.expectedSha256)).append("\n");
                    }
                    if (cat.lgWebOsIpk != null) {
                        sb.append("• LG webOS IPK  : ").append(cat.lgWebOsIpk.tagName)
                                .append(" (").append(cat.lgWebOsIpk.assetName).append(")\n")
                                .append("  SHA-256: ").append(shortHash(cat.lgWebOsIpk.expectedSha256));
                    }
                    mainHandler.post(() -> releaseStatus.setText(sb.toString().trim()));
                    appendLog("Resolved releases via " + cat.activeMirrorLabel);
                } catch (Exception e) {
                    mainHandler.post(() -> releaseStatus.setText("Error: " + e.getMessage()));
                    appendLog("Release check error: " + e.getMessage());
                }
            });
        });
        mirrorBox.addView(btnCheck, rowParams());
        page.addView(mirrorBox, boxParams());

        // Android TV 1-Tap Wireless ADB Installer
        TuiBoxView apkBox = makeBox("ANDROID TV WIRELESS ADB INSTALLER (:5555)");
        apkBox.addView(makeLabel(
                "1. Downloads latest omarchytv-release-2.13.0-beta.apk & verifies SHA-256\n"
                        + "2. Pushes via pure-Java ADB SYNC to /data/local/tmp/omarchytv.apk\n"
                        + "3. Runs `pm install -r` & pins default Home (`com.omarchytv.launcher/.HomeV29`)",
                10.5f, currentTheme.fgColor()));
        final TextView apkProgress = makeLabel("Status: Idle (Target TV: " + tvIp + ":5555)", 11f, currentTheme.greenColor());
        apkBox.addView(apkProgress, rowParams());

        Button btnInstallApk = makeTuiButton("[ 1-TAP INSTALL / UPDATE ANDROID TV (.APK) ]", true);
        btnInstallApk.setOnClickListener(v -> {
            apkProgress.setText("Fetching release metadata & downloading APK...");
            appendLog("Starting 1-tap Android TV APK install to " + tvIp + ":5555...");
            bgPool.execute(() -> {
                try {
                    GitLabReleaseClient rc = new GitLabReleaseClient();
                    if (latestReleaseCatalog == null) {
                        latestReleaseCatalog = rc.fetchDualPlatformReleases();
                    }
                    GitLabReleaseClient.ReleaseAssetInfo apkInfo = latestReleaseCatalog.androidTvApk;
                    if (apkInfo == null) throw new IOException("No Android TV APK found in release catalog");

                    File dest = new File(getCacheDir(), "omarchytv-latest.apk");
                    rc.downloadAndVerifyAsset(apkInfo, dest, (read, total) -> {
                        int pct = total > 0 ? (int) ((read * 100L) / total) : -1;
                        mainHandler.post(() -> apkProgress.setText(
                                "Downloading APK: " + (read / 1024) + " KB" + (pct >= 0 ? " (" + pct + "%)" : "")));
                    });
                    mainHandler.post(() -> apkProgress.setText("SHA-256 verified ✓ Pushing over ADB :5555..."));

                    try (AdbTcpClient adb = AdbTcpClient.connect(tvIp, adbPort, getFilesDir(), 10_000,
                            fp -> mainHandler.post(() -> apkProgress.setText("Accept RSA prompt on TV (" + fp + ")")))) {
                        String out = adb.installAndPinHome(dest, true, (sent, total, stage) -> mainHandler.post(() ->
                                apkProgress.setText(stage + ": " + (sent / 1024) + " / " + (total / 1024) + " KB")));
                        mainHandler.post(() -> apkProgress.setText("✓ Installed & Pinned on Android TV!\n" + out.trim()));
                        appendLog("Android TV APK installed & Home pinned on " + tvIp);
                    }
                } catch (Exception e) {
                    mainHandler.post(() -> apkProgress.setText("Install status: " + e.getMessage()));
                    appendLog("ADB install note: " + e.getMessage());
                }
            });
        });
        apkBox.addView(btnInstallApk, rowParams());
        page.addView(apkBox, boxParams());

        // LG webOS .ipk Fetcher, Validator & LAN HTTP Server
        TuiBoxView ipkBox = makeBox("LG webOS (.IPK) INSPECTOR & LAN SERVER (:8765)");
        String phoneIp = OmarchyLinkClient.detectLocalIpv4();
        ipkBox.addView(makeLabel(
                "Downloads `omarchytv-webos-2.11.0-testing.ipk`, validates Debian `!<arch>` + `appinfo.json` "
                        + "(`com.omarchytv.home`), and serves it at http://" + phoneIp + ":8765/omarchytv.ipk",
                10.5f, currentTheme.fgColor()));
        final TextView ipkStatus = makeLabel("Status: Ready to fetch & validate LG webOS .ipk", 11f, currentTheme.accent2Color());
        ipkBox.addView(ipkStatus, rowParams());

        Button btnStageIpk = makeTuiButton("[ FETCH, VERIFY & SERVE LG webOS .IPK (:8765) ]", false);
        btnStageIpk.setOnClickListener(v -> {
            ipkStatus.setText("Fetching LG webOS .ipk from GitLab/GitHub...");
            appendLog("Downloading & inspecting LG webOS .ipk...");
            bgPool.execute(() -> {
                try {
                    GitLabReleaseClient rc = new GitLabReleaseClient();
                    if (latestReleaseCatalog == null) {
                        latestReleaseCatalog = rc.fetchDualPlatformReleases();
                    }
                    GitLabReleaseClient.ReleaseAssetInfo ipkInfo = latestReleaseCatalog.lgWebOsIpk;
                    if (ipkInfo == null) throw new IOException("No LG webOS .ipk found in release catalog");

                    File dest = new File(getCacheDir(), "omarchytv-webos-2.11.0-testing.ipk");
                    rc.downloadAndVerifyAsset(ipkInfo, dest, null);
                    WebOsBridgeServer.IpkMetadata meta = WebOsBridgeServer.inspectIpkPackage(dest);
                    if (!meta.valid) {
                        throw new IOException("IPK validation failed: " + meta.error);
                    }
                    if (bridgeServer != null) {
                        bridgeServer.setServedIpkFile(dest);
                    }
                    mainHandler.post(() -> ipkStatus.setText(
                            "✓ Verified IPK: id=" + meta.appId + " v" + meta.version
                                    + " | Live at http://" + phoneIp + ":8765/omarchytv.ipk"));
                    appendLog("Staged verified LG webOS IPK (" + meta.appId + " v" + meta.version + ") on :8765");
                } catch (Exception e) {
                    mainHandler.post(() -> ipkStatus.setText("IPK note: " + e.getMessage()));
                    appendLog("IPK status: " + e.getMessage());
                }
            });
        });
        ipkBox.addView(btnStageIpk, rowParams());
        page.addView(ipkBox, boxParams());
    }

    // =========================================================================
    // [3:THEMES] — 23 Official Omarchy Themes + Live Wallpaper & Banner Push
    // =========================================================================
    private void populateThemesTab(LinearLayout page) {
        TuiBoxView hdrBox = makeBox("23 OFFICIAL OMARCHY THEMES (LIVE MULTI-TV SYNC)");
        hdrBox.addView(makeLabel(
                "Tap any theme to switch the phone UI, Android TV (`PUT /v1/theme` on :8999), "
                        + "AND LG webOS / Samsung Tizen / Hisense (`:8765/v1/status`) simultaneously!",
                10.5f, currentTheme.fgColor()));

        Button btnSyncRemote = makeTuiButton("[ SYNC LIVE THEME CATALOG (GITLAB + GITHUB) ]", false);
        btnSyncRemote.setOnClickListener(v -> {
            appendLog("Syncing live omarchy_themes.json from GitLab/GitHub...");
            bgPool.execute(() -> {
                try {
                    GitLabReleaseClient rc = new GitLabReleaseClient();
                    String json = rc.fetchRemoteThemesJson();
                    List<ThemeCatalog.ThemeEntry> remote = ThemeCatalog.parseThemesJson(json);
                    if (!remote.isEmpty()) {
                        themes = remote;
                        appendLog("Synced " + remote.size() + " themes from remote mirror ✓");
                        mainHandler.post(() -> switchTab(TAB_THEMES));
                    }
                } catch (Exception e) {
                    appendLog("Using bundled 23 Omarchy themes (" + e.getMessage() + ")");
                }
            });
        });
        hdrBox.addView(btnSyncRemote, rowParams());
        page.addView(hdrBox, boxParams());

        TuiBoxView gridBox = makeBox("THEME PALETTE SELECTOR (" + themes.size() + " PRESETS)");
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(2);

        for (final ThemeCatalog.ThemeEntry entry : themes) {
            boolean isSelected = entry.id.equals(currentTheme.id);
            Button tb = makeTuiButton(
                    (isSelected ? "● " : "○ ") + entry.displayName + " [" + entry.accentHex + "]",
                    isSelected);
            tb.setTextColor(entry.accentColor());
            GridLayout.LayoutParams glp = new GridLayout.LayoutParams();
            glp.width = 0;
            glp.height = dp(40);
            glp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            glp.setMargins(dp(3), dp(3), dp(3), dp(3));

            tb.setOnClickListener(v -> applyAndPushTheme(entry));
            grid.addView(tb, glp);
        }
        gridBox.addView(grid);
        page.addView(gridBox, boxParams());
    }

    private void applyAndPushTheme(ThemeCatalog.ThemeEntry entry) {
        currentTheme = entry;
        savePrefs();
        if (bridgeServer != null) {
            bridgeServer.setActiveTheme(entry.id, entry.rawColors);
        }
        appendLog("Applied theme '" + entry.id + "' locally & on :8765 WebOS/Universal bridge");
        switchTab(TAB_THEMES);

        bgPool.execute(() -> {
            try {
                OmarchyLinkClient c = new OmarchyLinkClient(tvIp, linkPort, "omarchymobile01", secretToken);
                c.switchTheme(entry);
                appendLog("Pushed theme '" + entry.id + "' to Android TV " + tvIp + ":" + linkPort + " ✓");
            } catch (Exception ignored) {
            }
        });
    }

    // =========================================================================
    // [4:REMOTE] — Tactile D-Pad, Media, HDMI Inputs & Terminal/AI Prompt
    // =========================================================================
    private void populateRemoteTab(LinearLayout page) {
        TuiBoxView dpadBox = makeBox("TACTILE D-PAD & NAVIGATION (ALL SMART TVs)");
        GridLayout pad = new GridLayout(this);
        pad.setColumnCount(3);

        String[][] gridKeys = new String[][]{
                {"BACK", "back"}, {"▲ UP", "up"}, {"HOME", "home"},
                {"◀ LEFT", "left"}, {"● OK", "select"}, {"▶ RIGHT", "right"},
                {"MENU", "menu"}, {"▼ DOWN", "down"}, {"MUTE", "mute"}
        };
        for (String[] kv : gridKeys) {
            final String label = kv[0];
            final String key = kv[1];
            Button b = makeTuiButton("[ " + label + " ]", "select".equals(key));
            GridLayout.LayoutParams glp = new GridLayout.LayoutParams();
            glp.width = 0;
            glp.height = dp(46);
            glp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            glp.setMargins(dp(4), dp(4), dp(4), dp(4));
            b.setOnClickListener(v -> dispatchRemoteKey(key));
            pad.addView(b, glp);
        }
        dpadBox.addView(pad);
        page.addView(dpadBox, boxParams());

        TuiBoxView mediaBox = makeBox("VOLUME, MEDIA & HDMI QUICK INPUTS");
        GridLayout mediaGrid = new GridLayout(this);
        mediaGrid.setColumnCount(4);
        String[][] mediaKeys = new String[][]{
                {"VOL -", "volume_down"}, {"PLAY/PAUSE", "play_pause"}, {"VOL +", "volume_up"}, {"LIVE TV", "tv"},
                {"HDMI 1", "hdmi1"}, {"HDMI 2", "hdmi2"}, {"HDMI 3", "hdmi3"}, {"HDMI 4", "hdmi4"}
        };
        for (String[] kv : mediaKeys) {
            final String label = kv[0];
            final String key = kv[1];
            Button b = makeTuiButton(label, false);
            GridLayout.LayoutParams glp = new GridLayout.LayoutParams();
            glp.width = 0;
            glp.height = dp(38);
            glp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            glp.setMargins(dp(3), dp(3), dp(3), dp(3));
            b.setOnClickListener(v -> dispatchRemoteKey(key));
            mediaGrid.addView(b, glp);
        }
        mediaBox.addView(mediaGrid);
        page.addView(mediaBox, boxParams());

        TuiBoxView promptBox = makeBox("TERMINAL / AI PROMPT BAR (>_ SEND TO TV)");
        final EditText promptInput = makeInput("", ">_ Send command or AI prompt to TV...");
        promptBox.addView(promptInput, rowParams());
        Button btnSendPrompt = makeTuiButton("[ PUSH COMMAND / PROMPT TO TV ]", true);
        btnSendPrompt.setOnClickListener(v -> {
            final String txt = promptInput.getText().toString().trim();
            if (txt.isEmpty()) return;
            if (bridgeServer != null) {
                bridgeServer.setLastPrompt(txt);
            }
            appendLog("Sending prompt to TV: " + txt);
            bgPool.execute(() -> {
                try {
                    OmarchyLinkClient c = new OmarchyLinkClient(tvIp, linkPort, "omarchymobile01", secretToken);
                    c.sendPromptOrCommand(txt);
                    appendLog("Prompt delivered to " + tvIp + ":" + linkPort + " ✓");
                } catch (Exception e) {
                    appendLog("Prompt queued on :8765 bridge (" + e.getMessage() + ")");
                }
            });
        });
        promptBox.addView(btnSendPrompt, rowParams());
        page.addView(promptBox, boxParams());
    }

    private void dispatchRemoteKey(String key) {
        if (bridgeServer != null) {
            bridgeServer.queueRemoteKey(key);
        }
        appendLog("Remote key [" + key + "] -> :8999 / :8765");
        bgPool.execute(() -> {
            try {
                OmarchyLinkClient c = new OmarchyLinkClient(tvIp, linkPort, "omarchymobile01", secretToken);
                c.sendRemoteKey(key);
            } catch (Exception ignored) {
            }
        });
    }

    // =========================================================================
    // [5:DEBLOAT] — Safe Reversible Android TV Debloat & 1-Tap Restore
    // =========================================================================
    private void populateDebloatTab(LinearLayout page) {
        TuiBoxView warnBox = makeBox("SAFETY LOCK & REVERSIBLE DEBLOAT (--user 0)");
        warnBox.addView(makeLabel(
                "⚠ SAFE MODE ONLY: Uses `pm disable-user --user 0 <pkg>` (100% reversible, no root).\n"
                        + "🔒 HARDCODED DENYLIST: setupwraith, gms, gsf, mediashell (Chromecast), "
                        + "and Live TV / TIF tuner packages can NEVER be disabled.",
                10.5f, currentTheme.yellowColor()));

        Button btnRestoreAll = makeTuiButton("[ 1-TAP RESTORE ALL STOCK PACKAGES (pm enable) ]", true);
        btnRestoreAll.setOnClickListener(v -> {
            appendLog("Restoring all disabled packages on " + tvIp + ":5555...");
            bgPool.execute(() -> {
                try {
                    try (AdbTcpClient adb = AdbTcpClient.connect(tvIp, adbPort, getFilesDir(), 8_000, null)) {
                        Set<String> disabled = adb.listDisabledPackages();
                        int count = 0;
                        for (String pkg : disabled) {
                            adb.enablePackage(pkg);
                            count++;
                        }
                        appendLog("Restored " + count + " packages on " + tvIp + " ✓");
                    }
                } catch (Exception e) {
                    appendLog("Restore status: " + e.getMessage());
                }
            });
        });
        warnBox.addView(btnRestoreAll, rowParams());
        page.addView(warnBox, boxParams());

        // Brand selector
        TuiBoxView brandBox = makeBox("TV BRAND PROFILE SELECTOR");
        LinearLayout brandRow = new LinearLayout(this);
        brandRow.setOrientation(LinearLayout.VERTICAL);
        for (final DebloatProfiles.BrandProfile bp : DebloatProfiles.allProfiles()) {
            Button b = makeTuiButton("[ " + bp.displayName.toUpperCase(Locale.ROOT) + " ]", bp.id.equals(selectedBrand));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(34));
            lp.bottomMargin = dp(4);
            b.setOnClickListener(v -> {
                selectedBrand = bp.id;
                switchTab(TAB_DEBLOAT);
            });
            brandRow.addView(b, lp);
        }
        brandBox.addView(brandRow);
        page.addView(brandBox, boxParams());

        // Package list for selected brand
        DebloatProfiles.BrandProfile activeProfile = DebloatProfiles.getProfile(selectedBrand);
        TuiBoxView pkgBox = makeBox("CURATED PACKAGES: " + activeProfile.displayName.toUpperCase(Locale.ROOT));
        for (final DebloatProfiles.PackageEntry rule : activeProfile.packages) {
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setPadding(0, dp(4), 0, dp(8));

            String badge = rule.protectedSystem ? "PROTECTED / LOCKED" : "SAFE --user 0";
            item.addView(makeLabel("• " + rule.label + " [" + badge + "]", 11.5f,
                    rule.protectedSystem ? currentTheme.yellowColor() : currentTheme.accentColor()));
            item.addView(makeLabel("  " + rule.packageName, 10f, currentTheme.fgColor()));

            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            Button btnDis = makeTuiButton("[ DISABLE ]", false);
            Button btnEn = makeTuiButton("[ ENABLE ]", false);

            btnDis.setOnClickListener(v -> runDebloatAction(rule.packageName, true));
            btnEn.setOnClickListener(v -> runDebloatAction(rule.packageName, false));

            LinearLayout.LayoutParams lpBtn = new LinearLayout.LayoutParams(0, dp(34), 1f);
            lpBtn.rightMargin = dp(6);
            actions.addView(btnDis, lpBtn);
            actions.addView(btnEn, new LinearLayout.LayoutParams(0, dp(34), 1f));
            item.addView(actions);
            pkgBox.addView(item);
        }
        page.addView(pkgBox, boxParams());
    }

    private void runDebloatAction(String pkg, boolean disable) {
        if (disable && DebloatProfiles.isProtected(pkg)) {
            appendLog("BLOCKED: " + pkg + " is in PROTECTED_PACKAGES!");
            return;
        }
        appendLog((disable ? "Disabling " : "Enabling ") + pkg + " on " + tvIp + ":5555...");
        bgPool.execute(() -> {
            try {
                try (AdbTcpClient adb = AdbTcpClient.connect(tvIp, adbPort, getFilesDir(), 8_000, null)) {
                    String out = disable ? adb.disablePackageUser0(pkg) : adb.enablePackage(pkg);
                    appendLog(out.trim());
                }
            } catch (Exception e) {
                appendLog("ADB debloat note: " + e.getMessage());
            }
        });
    }

    // =========================================================================
    // UI Helpers
    // =========================================================================
    private TuiBoxView makeBox(String title) {
        TuiBoxView box = new TuiBoxView(this, title, monoBold);
        box.applyColors(0xFF16161E, currentTheme.accentColor(), currentTheme.accent2Color());
        return box;
    }

    private TextView makeLabel(String text, float sp, int color) {
        TextView tv = new TextView(this);
        tv.setTypeface(monoRegular);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        tv.setTextColor(color);
        tv.setText(text);
        return tv;
    }

    private EditText makeInput(String initial, String hint) {
        EditText et = new EditText(this);
        et.setTypeface(monoRegular);
        et.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        et.setTextColor(currentTheme.fgColor());
        et.setHintTextColor(0xFF565F89);
        et.setHint(hint);
        et.setText(initial);
        et.setSingleLine(true);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF1A1B26);
        bg.setStroke(dp(1), currentTheme.accentColor());
        bg.setCornerRadius(dp(3));
        et.setBackground(bg);
        et.setPadding(dp(10), dp(8), dp(10), dp(8));
        return et;
    }

    private Button makeTuiButton(String text, boolean highlighted) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setTypeface(monoBold);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        b.setText(text);
        b.setPadding(dp(10), dp(4), dp(10), dp(4));
        styleTabButton(b, highlighted);
        return b;
    }

    private void styleTabButton(Button b, boolean active) {
        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(dp(3));
        if (active) {
            gd.setColor(currentTheme.accentColor());
            gd.setStroke(dp(1), currentTheme.accent2Color());
            b.setTextColor(0xFF13141C);
        } else {
            gd.setColor(0xFF1F2335);
            gd.setStroke(dp(1), currentTheme.accentColor());
            b.setTextColor(currentTheme.fgColor());
        }
        b.setBackground(gd);
    }

    private LinearLayout.LayoutParams boxParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(10);
        return lp;
    }

    private LinearLayout.LayoutParams rowParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(6);
        return lp;
    }

    private int dp(int val) {
        return Math.round(val * getResources().getDisplayMetrics().density);
    }

    private static String shortHash(String h) {
        if (h == null || h.length() < 16) return h == null ? "n/a" : h;
        return h.substring(0, 12) + "..." + h.substring(h.length() - 8);
    }
}
