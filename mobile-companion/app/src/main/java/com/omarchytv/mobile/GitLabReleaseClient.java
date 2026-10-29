package com.omarchytv.mobile;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dual-Mirror (GitLab + GitHub) & Dual-Platform (Android TV `.apk` + LG webOS `.ipk`)
 * Release & Theme Resolver for OmarchyTV Mobile.
 */
public final class GitLabReleaseClient {

    public static final String GITLAB_PROJECT_ID = "87958120";
    public static final String GITLAB_RELEASES_URL =
            "https://gitlab.com/api/v4/projects/87958120/releases";
    public static final String GITHUB_RELEASES_URL =
            "https://api.github.com/repos/piermark/omarchytv/releases";

    public static final String GITLAB_THEMES_RAW_URL =
            "https://gitlab.com/piermark/omarchytv/-/raw/main/launcher/app/src/main/assets/omarchy_themes.json";
    public static final String GITHUB_THEMES_RAW_URL =
            "https://raw.githubusercontent.com/piermark/omarchytv/main/launcher/app/src/main/assets/omarchy_themes.json";

    public static final String EXPECTED_SIGNING_CERT_SHA256 =
            "90aa68d9f45e623eadfd349ab1eb66042356f95ff6347a4568c3f3a78f8ea33d";

    private static final Pattern SHA256_HEX_PATTERN =
            Pattern.compile("([a-fA-F0-9]{64})");

    public interface DownloadProgressListener {
        void onProgress(long bytesRead, long totalBytes);
    }

    public static final class ReleaseAssetInfo {
        public final String tagName;
        public final String releaseTitle;
        public final String platform; // "android-tv" or "lg-webos"
        public final String assetName;
        public final String downloadUrl;
        public final String expectedSha256;
        public final String expectedCertSha256;
        public final String mirrorSource; // "GitLab" or "GitHub"
        public final int versionScore;

        public ReleaseAssetInfo(String tagName, String releaseTitle, String platform,
                                String assetName, String downloadUrl, String expectedSha256,
                                String expectedCertSha256, String mirrorSource, int versionScore) {
            this.tagName = tagName;
            this.releaseTitle = releaseTitle;
            this.platform = platform;
            this.assetName = assetName;
            this.downloadUrl = downloadUrl;
            this.expectedSha256 = expectedSha256 != null ? expectedSha256.toLowerCase(Locale.ROOT) : "";
            this.expectedCertSha256 = expectedCertSha256 != null
                    ? expectedCertSha256.toLowerCase(Locale.ROOT)
                    : EXPECTED_SIGNING_CERT_SHA256;
            this.mirrorSource = mirrorSource;
            this.versionScore = versionScore;
        }
    }

    public static final class DualPlatformCatalog {
        public final ReleaseAssetInfo androidTvApk;
        public final ReleaseAssetInfo lgWebOsIpk;
        public final boolean gitLabReachable;
        public final boolean gitHubReachable;
        public final String activeMirrorLabel;

        public DualPlatformCatalog(ReleaseAssetInfo androidTvApk, ReleaseAssetInfo lgWebOsIpk,
                                   boolean gitLabReachable, boolean gitHubReachable, String activeMirrorLabel) {
            this.androidTvApk = androidTvApk;
            this.lgWebOsIpk = lgWebOsIpk;
            this.gitLabReachable = gitLabReachable;
            this.gitHubReachable = gitHubReachable;
            this.activeMirrorLabel = activeMirrorLabel;
        }
    }

    private final String gitlabReleasesUrl;
    private final String githubReleasesUrl;
    private final String gitlabThemesUrl;
    private final String githubThemesUrl;

    public GitLabReleaseClient() {
        this(GITLAB_RELEASES_URL, GITHUB_RELEASES_URL, GITLAB_THEMES_RAW_URL, GITHUB_THEMES_RAW_URL);
    }

    public GitLabReleaseClient(String gitlabReleasesUrl, String githubReleasesUrl,
                               String gitlabThemesUrl, String githubThemesUrl) {
        this.gitlabReleasesUrl = gitlabReleasesUrl;
        this.githubReleasesUrl = githubReleasesUrl;
        this.gitlabThemesUrl = gitlabThemesUrl;
        this.githubThemesUrl = githubThemesUrl;
    }

    /**
     * Queries both GitLab (`projects/87958120/releases`) and GitHub (`piermark/omarchytv/releases`)
     * and resolves the newest Android TV `.apk` and LG webOS `.ipk` across both mirrors.
     */
    public DualPlatformCatalog fetchDualPlatformReleases() throws IOException {
        ReleaseAssetInfo glApk = null;
        ReleaseAssetInfo glIpk = null;
        boolean glOk = false;

        try {
            String glJson = httpGetString(gitlabReleasesUrl, 6_000);
            JSONArray arr = new JSONArray(glJson);
            glOk = true;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject rel = arr.optJSONObject(i);
                if (rel == null) continue;
                ReleaseAssetInfo apk = parseGitLabRelease(rel, false);
                if (apk != null && (glApk == null || apk.versionScore > glApk.versionScore)) {
                    glApk = apk;
                }
                ReleaseAssetInfo ipk = parseGitLabRelease(rel, true);
                if (ipk != null && (glIpk == null || ipk.versionScore > glIpk.versionScore)) {
                    glIpk = ipk;
                }
            }
        } catch (Exception ignored) {
        }

        ReleaseAssetInfo ghApk = null;
        ReleaseAssetInfo ghIpk = null;
        boolean ghOk = false;

        try {
            String ghJson = httpGetString(githubReleasesUrl, 6_000);
            JSONArray arr = new JSONArray(ghJson);
            ghOk = true;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject rel = arr.optJSONObject(i);
                if (rel == null) continue;
                ReleaseAssetInfo apk = parseGitHubRelease(rel, false);
                if (apk != null && (ghApk == null || apk.versionScore > ghApk.versionScore)) {
                    ghApk = apk;
                }
                ReleaseAssetInfo ipk = parseGitHubRelease(rel, true);
                if (ipk != null && (ghIpk == null || ipk.versionScore > ghIpk.versionScore)) {
                    ghIpk = ipk;
                }
            }
        } catch (Exception ignored) {
        }

        if (!glOk && !ghOk) {
            throw new IOException("Both GitLab and GitHub release mirrors were unreachable");
        }

        ReleaseAssetInfo bestApk = pickNewest(glApk, ghApk);
        ReleaseAssetInfo bestIpk = pickNewest(glIpk, ghIpk);

        String mirrorLabel = (glOk && ghOk)
                ? "GitLab + GitHub (Dual Active)"
                : (glOk ? "GitLab (Primary)" : "GitHub (Fallback)");

        return new DualPlatformCatalog(bestApk, bestIpk, glOk, ghOk, mirrorLabel);
    }

    /**
     * Fetches the live `omarchy_themes.json` catalog from GitLab raw URL with GitHub raw URL fallback.
     */
    public String fetchRemoteThemesJson() throws IOException {
        try {
            String text = httpGetString(gitlabThemesUrl, 5_000);
            if (text != null && text.contains("tokyo-night")) {
                return text;
            }
        } catch (Exception ignored) {
        }
        String fallback = httpGetString(githubThemesUrl, 5_000);
        if (fallback != null && fallback.contains("tokyo-night")) {
            return fallback;
        }
        throw new IOException("Unable to fetch remote omarchy_themes.json from GitLab or GitHub");
    }

    /**
     * Downloads a release asset (`.apk` or `.ipk`) to `destFile` and verifies its SHA-256 digest if known.
     */
    public File downloadAndVerifyAsset(ReleaseAssetInfo asset, File destFile,
                                       DownloadProgressListener listener) throws IOException {
        if (asset == null || asset.downloadUrl == null || asset.downloadUrl.isEmpty()) {
            throw new IOException("Missing download URL for release asset");
        }
        File parent = destFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        HttpURLConnection conn = openFollowingRedirects(asset.downloadUrl, 12_000);
        long total = conn.getContentLengthLong();
        MessageDigest sha256;
        try {
            sha256 = MessageDigest.getInstance("SHA-256");
        } catch (Exception e) {
            throw new IOException(e);
        }

        long readTotal = 0;
        try (InputStream in = conn.getInputStream();
             FileOutputStream out = new FileOutputStream(destFile)) {
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                sha256.update(buf, 0, n);
                readTotal += n;
                if (listener != null) {
                    listener.onProgress(readTotal, total);
                }
            }
        } finally {
            conn.disconnect();
        }

        String actualSha256 = OmarchyLinkClient.toHex(sha256.digest());
        if (asset.expectedSha256 != null && asset.expectedSha256.length() == 64) {
            if (!asset.expectedSha256.equalsIgnoreCase(actualSha256)) {
                throw new IOException("SHA-256 mismatch! Expected " + asset.expectedSha256
                        + " but got " + actualSha256);
            }
        }
        return destFile;
    }

    public static String computeFileSha256(File f) throws IOException {
        try (FileInputStream in = new FileInputStream(f)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            return OmarchyLinkClient.toHex(md.digest());
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    private static String normalizeGitLabUploadUrl(String url) {
        if (url == null) return "";
        return url.replace(
                "https://gitlab.com/piermark/omarchytv/uploads/",
                "https://gitlab.com/-/project/" + GITLAB_PROJECT_ID + "/uploads/");
    }

    private static ReleaseAssetInfo pickNewest(ReleaseAssetInfo gl, ReleaseAssetInfo gh) {
        if (gl == null) return gh;
        if (gh == null) return gl;
        return gh.versionScore > gl.versionScore ? gh : gl;
    }

    private static ReleaseAssetInfo parseGitLabRelease(JSONObject rel, boolean wantWebOsIpk) {
        String tag = rel.optString("tag_name", "");
        String name = rel.optString("name", tag);
        String desc = rel.optString("description", "");
        String lowerTag = tag.toLowerCase(Locale.ROOT);
        String lowerName = name.toLowerCase(Locale.ROOT);
        if (lowerTag.contains("mobile") || lowerName.contains("mobile")) {
            return null;
        }
        boolean isWebOsTag = lowerTag.contains("webos") || lowerName.contains("webos");

        String assetName = "";
        String downloadUrl = "";

        JSONObject assets = rel.optJSONObject("assets");
        if (assets != null) {
            JSONArray links = assets.optJSONArray("links");
            if (links != null) {
                for (int i = 0; i < links.length(); i++) {
                    JSONObject lk = links.optJSONObject(i);
                    if (lk == null) continue;
                    String ln = lk.optString("name", "");
                    String lu = normalizeGitLabUploadUrl(lk.optString("direct_asset_url", lk.optString("url", "")));
                    if (wantWebOsIpk && (ln.endsWith(".ipk") || lu.endsWith(".ipk"))) {
                        assetName = ln;
                        downloadUrl = lu;
                        break;
                    } else if (!wantWebOsIpk && (ln.endsWith(".apk") || lu.endsWith(".apk"))
                            && !ln.contains("mobile") && !lu.contains("mobile")) {
                        assetName = ln;
                        downloadUrl = lu;
                        break;
                    }
                }
            }
        }

        if (downloadUrl.isEmpty() && desc != null && !desc.isEmpty()) {
            String ext = wantWebOsIpk ? ".ipk" : ".apk";
            Matcher um = Pattern.compile("(https://gitlab\\.com/[^\\s)\"']+uploads/[0-9a-fA-F]{32}/([^\\s)\"'/]+" + Pattern.quote(ext) + "))").matcher(desc);
            while (um.find()) {
                String candidateUrl = normalizeGitLabUploadUrl(um.group(1));
                String candidateName = um.group(2);
                if (!wantWebOsIpk && candidateName.toLowerCase(Locale.ROOT).contains("mobile")) {
                    continue;
                }
                assetName = candidateName;
                downloadUrl = candidateUrl;
                break;
            }
        }

        if (downloadUrl.isEmpty()) {
            // Check description markdown links or tag convention
            if (wantWebOsIpk && isWebOsTag) {
                assetName = "omarchytv-webos-2.11.0-testing.ipk";
                downloadUrl = "https://gitlab.com/piermark/omarchytv/-/releases/" + tag
                        + "/downloads/omarchytv-webos-2.11.0-testing.ipk";
            } else if (!wantWebOsIpk && !isWebOsTag && !tag.isEmpty()) {
                assetName = "omarchytv-release-2.13.0-beta.apk";
                downloadUrl = "https://gitlab.com/piermark/omarchytv/-/raw/" + tag
                        + "/omarchytv-release-2.13.0-beta.apk";
            }
        }

        if (downloadUrl.isEmpty()) return null;
        if (wantWebOsIpk && !isWebOsTag && !assetName.endsWith(".ipk")) return null;
        if (!wantWebOsIpk && isWebOsTag && !assetName.endsWith(".apk")) return null;

        String sha = extractFirstSha256(desc, EXPECTED_SIGNING_CERT_SHA256);
        int score = computeVersionScore(tag);
        return new ReleaseAssetInfo(
                tag,
                name,
                wantWebOsIpk ? "lg-webos" : "android-tv",
                assetName,
                downloadUrl,
                sha,
                EXPECTED_SIGNING_CERT_SHA256,
                "GitLab",
                score
        );
    }

    private static ReleaseAssetInfo parseGitHubRelease(JSONObject rel, boolean wantWebOsIpk) {
        String tag = rel.optString("tag_name", "");
        String name = rel.optString("name", tag);
        String body = rel.optString("body", "");
        boolean isWebOsTag = tag.toLowerCase(Locale.ROOT).contains("webos")
                || name.toLowerCase(Locale.ROOT).contains("webos");

        String assetName = "";
        String downloadUrl = "";
        JSONArray assets = rel.optJSONArray("assets");
        if (assets != null) {
            for (int i = 0; i < assets.length(); i++) {
                JSONObject a = assets.optJSONObject(i);
                if (a == null) continue;
                String an = a.optString("name", "");
                String au = a.optString("browser_download_url", "");
                if (wantWebOsIpk && an.endsWith(".ipk")) {
                    assetName = an;
                    downloadUrl = au;
                    break;
                } else if (!wantWebOsIpk && an.endsWith(".apk") && !an.contains("mobile")) {
                    assetName = an;
                    downloadUrl = au;
                    break;
                }
            }
        }

        if (downloadUrl.isEmpty()) {
            if (wantWebOsIpk && isWebOsTag) {
                assetName = "omarchytv-webos-2.11.0-testing.ipk";
                downloadUrl = "https://github.com/piermark/omarchytv/releases/download/" + tag + "/" + assetName;
            } else if (!wantWebOsIpk && !isWebOsTag && !tag.isEmpty()) {
                assetName = "omarchytv-release-2.13.0-beta.apk";
                downloadUrl = "https://github.com/piermark/omarchytv/releases/download/" + tag + "/" + assetName;
            }
        }

        if (downloadUrl.isEmpty()) return null;
        if (wantWebOsIpk && !isWebOsTag && !assetName.endsWith(".ipk")) return null;
        if (!wantWebOsIpk && isWebOsTag && !assetName.endsWith(".apk")) return null;

        String sha = extractFirstSha256(body, EXPECTED_SIGNING_CERT_SHA256);
        int score = computeVersionScore(tag);
        return new ReleaseAssetInfo(
                tag,
                name,
                wantWebOsIpk ? "lg-webos" : "android-tv",
                assetName,
                downloadUrl,
                sha,
                EXPECTED_SIGNING_CERT_SHA256,
                "GitHub",
                score
        );
    }

    public static String extractFirstSha256(String text, String ignoreHash) {
        if (text == null) return "";
        Matcher m = SHA256_HEX_PATTERN.matcher(text);
        while (m.find()) {
            String candidate = m.group(1).toLowerCase(Locale.ROOT);
            if (ignoreHash != null && candidate.equalsIgnoreCase(ignoreHash)) {
                continue;
            }
            return candidate;
        }
        return "";
    }

    public static int computeVersionScore(String tag) {
        if (tag == null) return 0;
        Matcher m = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)").matcher(tag);
        if (m.find()) {
            return Integer.parseInt(m.group(1)) * 10000
                    + Integer.parseInt(m.group(2)) * 100
                    + Integer.parseInt(m.group(3));
        }
        return 100;
    }

    private static String httpGetString(String urlStr, int timeoutMs) throws IOException {
        HttpURLConnection conn = openFollowingRedirects(urlStr, timeoutMs);
        try (InputStream in = conn.getInputStream()) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            return new String(bos.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            conn.disconnect();
        }
    }

    private static HttpURLConnection openFollowingRedirects(String urlStr, int timeoutMs) throws IOException {
        String current = urlStr;
        for (int hops = 0; hops < 5; hops++) {
            HttpURLConnection conn = (HttpURLConnection) new URL(current).openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            conn.setRequestProperty("User-Agent", "OmarchyTV-Mobile/2.13.0 (Android; Linux)");
            conn.setRequestProperty("Accept", "application/json, */*");
            int code = conn.getResponseCode();
            if (code == HttpURLConnection.HTTP_MOVED_PERM
                    || code == HttpURLConnection.HTTP_MOVED_TEMP
                    || code == HttpURLConnection.HTTP_SEE_OTHER
                    || code == 307 || code == 308) {
                String loc = conn.getHeaderField("Location");
                conn.disconnect();
                if (loc == null || loc.isEmpty()) break;
                current = loc;
                continue;
            }
            if (code >= 400) {
                conn.disconnect();
                throw new IOException("HTTP " + code + " for " + current);
            }
            return conn;
        }
        throw new IOException("Too many redirects for " + urlStr);
    }
}
