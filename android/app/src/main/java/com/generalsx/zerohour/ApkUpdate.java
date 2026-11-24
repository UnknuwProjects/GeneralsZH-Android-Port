// SPDX-License-Identifier: GPL-3.0-or-later
// GeneralsX @feature Android port 23/11/2026 In-app APK update download from this fork's
// GitHub Releases. The launcher checks the latest release, downloads the APK into the
// public Downloads folder with a progress bar and speed, and offers an Open action that
// hands the file to the system installer. The app never installs anything itself and
// never asks for the install permission (REQUEST_INSTALL_PACKAGES is deliberately not
// declared — see AndroidManifest.xml): opening the APK is the player's decision.
//
// No new dependency: HttpURLConnection and org.json are already in use (UpdateManager,
// DriverDownload). The engine update path (UpdateManager.check) is untouched; this is
// the APK's own update, for when a newer engine alone is not enough.

package com.generalsx.zerohour;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Environment;
import android.os.SystemClock;
import android.util.Log;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ApkUpdate {

    private static final String TAG = "GXApkUpdate";

    /** The fork's releases, not upstream's: players of this fork get this fork's builds. */
    static final String RELEASES_LATEST_URL =
        "https://api.github.com/repos/UnknuwProjects/GeneralsZH-Android-Port/releases/latest";

    private static final String USER_AGENT = "GeneralsZH-Android";

    /** An APK smaller than this cannot be a real build; treat it as a broken release. */
    private static final long MIN_APK_SIZE = 1024L * 1024L;

    private ApkUpdate() {}

    /** One release's APK, as the releases API describes it. */
    public static final class Release {
        public final String tag;
        public final String assetName;
        public final long assetSize;
        public final String downloadUrl;

        public Release(String tag, String assetName, long assetSize, String downloadUrl) {
            this.tag = tag;
            this.assetName = assetName;
            this.assetSize = assetSize;
            this.downloadUrl = downloadUrl;
        }
    }

    /** Thrown when the player cancels a download; the partial file is deleted. */
    public static final class Cancelled extends IOException {
        public Cancelled() {
            super("cancelled");
        }
    }

    /** Progress reports, throttled by the downloader to a few per second. */
    public interface Progress {
        void onProgress(long downloaded, long total, long bytesPerSecond);
    }

    /**
     * The latest release and its APK asset. Blocking; call off the UI thread.
     * @throws IOException on any HTTP failure (including the API's 403 when the
     *         unauthenticated rate limit is hit, and 404 when no release exists yet),
     *         or when the release carries no usable APK asset.
     */
    public static Release checkLatest() throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(RELEASES_LATEST_URL).openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        conn.setUseCaches(false);
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setRequestProperty("Accept", "application/vnd.github+json");
        String body;
        try {
            int status = conn.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IOException("HTTP " + status);
            }
            try (InputStream in = conn.getInputStream()) {
                body = new String(readAll(in), StandardCharsets.UTF_8);
            }
        } finally {
            conn.disconnect();
        }
        try {
            JSONObject json = new JSONObject(body);
            String tag = json.optString("tag_name", "");
            if (tag.isEmpty()) {
                throw new IOException("release has no tag");
            }
            JSONArray assets = json.optJSONArray("assets");
            if (assets == null) {
                throw new IOException("release has no assets");
            }
            JSONObject apk = null;
            for (int i = 0; i < assets.length(); i++) {
                JSONObject asset = assets.optJSONObject(i);
                if (asset == null) {
                    continue;
                }
                String name = asset.optString("name", "");
                if (!name.endsWith(".apk")) {
                    continue;
                }
                // Prefer this fork's own build name; any .apk still counts,
                // so a renamed asset does not silently hide the release.
                if (apk == null || name.startsWith("GeneralsXZH-android-")) {
                    apk = asset;
                }
            }
            if (apk == null) {
                throw new IOException("no APK in the latest release");
            }
            String name = apk.optString("name", "");
            long size = apk.optLong("size", 0);
            String url = apk.optString("browser_download_url", "");
            if (url.isEmpty() || size < MIN_APK_SIZE) {
                throw new IOException("APK asset looks wrong: " + name);
            }
            return new Release(tag, name, size, url);
        } catch (JSONException e) {
            throw new IOException("bad release response: " + e.getMessage());
        }
    }

    /**
     * Whether a release tag is newer than the installed versionName.
     *
     * The releases API does not expose versionCode, so the tag is all there is:
     * strip a leading "v", then compare dot-separated numbers left to right,
     * a missing part counting as 0. A tag with a pre-release suffix ("-beta",
     * "-rc1") is older than the same numbers without one, so a 1.0.0-beta
     * never outranks the 1.0.0 release, and the 1.0.0 release does outrank
     * an installed 1.0.0-beta. A tag that is not a version at all is never
     * "newer": offering a download we cannot order would be worse than
     * missing one.
     */
    public static boolean isNewerThan(String installedVersionName, String releaseTag) {
        int[] installed = parseVersion(installedVersionName);
        int[] release = parseVersion(releaseTag);
        if (installed == null || release == null) {
            return false;
        }
        int parts = Math.max(installed.length, release.length);
        for (int i = 0; i < parts; i++) {
            int a = i < installed.length ? installed[i] : 0;
            int b = i < release.length ? release[i] : 0;
            if (b != a) {
                return b > a;
            }
        }
        // Same numbers: a plain release beats a pre-release of those numbers.
        return !hasPreRelease(releaseTag) && hasPreRelease(installedVersionName);
    }

    private static int[] parseVersion(String version) {
        if (version == null) {
            return null;
        }
        String v = version.trim();
        if (v.startsWith("v") || v.startsWith("V")) {
            v = v.substring(1);
        }
        // Cut the pre-release suffix ("-beta") and anything after it.
        int dash = v.indexOf('-');
        if (dash >= 0) {
            v = v.substring(0, dash);
        }
        if (v.isEmpty()) {
            return null;
        }
        String[] parts = v.split("\\.");
        int[] numbers = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty()) {
                return null;
            }
            try {
                numbers[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                return null;
            }
            if (numbers[i] < 0) {
                return null;
            }
        }
        return numbers;
    }

    private static boolean hasPreRelease(String version) {
        if (version == null) {
            return false;
        }
        String v = version.trim();
        if (v.startsWith("v") || v.startsWith("V")) {
            v = v.substring(1);
        }
        return v.indexOf('-') >= 0;
    }

    /** Where the APK lands: the public Downloads folder, so the player finds it outside the app too. */
    public static File downloadsFile(String assetName) {
        return new File(Environment.getExternalStorageDirectory(), "Download/" + assetName);
    }

    /** True when the release's APK is already sitting in Downloads at the right size. */
    public static boolean alreadyDownloaded(Release release) {
        File file = downloadsFile(release.assetName);
        return file.isFile() && file.length() == release.assetSize;
    }

    /**
     * Downloads the APK to Downloads, reporting progress a few times a second.
     * Blocking; call off the UI thread. Writes to a ".part" file first and moves it
     * into place only when the size matches, so a failed or cancelled download
     * never leaves a broken APK behind.
     * @throws Cancelled when the player cancels; the partial file is deleted.
     * @throws IOException on any network or file failure, or a short download.
     */
    public static void downloadApk(String url, File out, long expectedSize,
                                   Progress progress, AtomicBoolean cancelled) throws IOException {
        File parent = out.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("cannot create " + parent);
        }
        File tmp = new File(out.getPath() + ".part");
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(120000);
        conn.setUseCaches(false);
        conn.setRequestProperty("User-Agent", USER_AGENT);
        long total = expectedSize > 0 ? expectedSize : conn.getContentLengthLong();
        boolean moved = false;
        try {
            int status = conn.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IOException("HTTP " + status);
            }
            long downloaded = 0;
            long lastReportAt = SystemClock.elapsedRealtime();
            long lastReportBytes = 0;
            try (InputStream in = conn.getInputStream();
                 OutputStream fileOut = new FileOutputStream(tmp)) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) {
                    if (cancelled.get()) {
                        throw new Cancelled();
                    }
                    fileOut.write(buf, 0, n);
                    downloaded += n;
                    long now = SystemClock.elapsedRealtime();
                    if (progress != null && now - lastReportAt >= 250) {
                        long dtMs = now - lastReportAt;
                        long speed = dtMs > 0 ? (downloaded - lastReportBytes) * 1000 / dtMs : 0;
                        progress.onProgress(downloaded, total, speed);
                        lastReportAt = now;
                        lastReportBytes = downloaded;
                    }
                }
            }
            if (cancelled.get()) {
                throw new Cancelled();
            }
            if (expectedSize > 0 && downloaded != expectedSize) {
                throw new IOException("incomplete download: " + downloaded + " of " + expectedSize + " bytes");
            }
            if (progress != null) {
                progress.onProgress(downloaded, total, 0);
            }
            if (out.exists() && !out.delete()) {
                throw new IOException("cannot replace " + out);
            }
            if (!tmp.renameTo(out)) {
                throw new IOException("cannot move " + tmp + " to " + out);
            }
            moved = true;
        } finally {
            conn.disconnect();
            if (!moved) {
                tmp.delete();
            }
        }
    }

    /**
     * An ACTION_VIEW intent for the downloaded APK. The system installer takes over from
     * here — including its own "unknown sources" prompt, which is the player's decision.
     * This app never installs anything itself and never asks for the install permission.
     */
    public static Intent openApkIntent(Context ctx, File apk) {
        Uri uri = FileProvider.getUriForFile(ctx, ctx.getPackageName() + ".fileprovider", apk);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    /** The installed APK's versionName, or "" when it cannot be read. */
    public static String installedVersionName(Context ctx) {
        try {
            PackageInfo info = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            return info.versionName != null ? info.versionName : "";
        } catch (Exception e) {
            Log.w(TAG, "cannot read installed version", e);
            return "";
        }
    }

    /** "44.2 MB", "850 KB", "512 B" — one decimal, never more precision than a download needs. */
    public static String formatSize(long bytes) {
        if (bytes >= 1024L * 1024L) {
            return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
        }
        if (bytes >= 1024L) {
            return String.format(Locale.ROOT, "%.0f KB", bytes / 1024.0);
        }
        return bytes + " B";
    }

    /** "1.2 MB/s", "850 KB/s", "0 KB/s" — the same scale as formatSize. */
    public static String formatSpeed(long bytesPerSecond) {
        if (bytesPerSecond <= 0) {
            return "0 KB/s";
        }
        if (bytesPerSecond >= 1024L * 1024L) {
            return String.format(Locale.ROOT, "%.1f MB/s", bytesPerSecond / (1024.0 * 1024.0));
        }
        return String.format(Locale.ROOT, "%.0f KB/s", bytesPerSecond / 1024.0);
    }

    private static byte[] readAll(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }
}
