// SPDX-License-Identifier: GPL-3.0-or-later
// GeneralsX @feature Android port 23/11/2026 In-app driver pack download with SHA-256 check. A pack whose checksum does not match is rejected and nothing on disk is changed.
//
// Download a driver .zip, verify it, then install it the same way
// the existing import code does: unzip to a temp dir, validate
// meta.json and the driver .so, then move the folder into place.
// A pack whose checksum does not match is rejected and nothing
// on disk is changed. No new dependency: HttpURLConnection,
// MessageDigest and ZipInputStream are all in the platform.

package com.generalsx.zerohour;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class DriverDownload {

    public static final class Result {
        public final boolean ok;
        public final String message;

        public Result(boolean ok, String message) {
            this.ok = ok;
            this.message = message;
        }
    }

    private DriverDownload() {}

    /** Download, verify and install one driver pack. Returns ok=false with a reason on any failure. */
    public static Result downloadAndInstall(File filesDir, String zipUrl, String expectedSha256) {
        if (zipUrl == null || zipUrl.isEmpty()) {
            return new Result(false, "No download URL for this driver.");
        }
        File tmpZip;
        try {
            tmpZip = File.createTempFile("driver-", ".zip", filesDir);
            downloadToFile(zipUrl, tmpZip);
            if (expectedSha256 != null && !expectedSha256.isEmpty()) {
                String actual = sha256Hex(tmpZip);
                if (!expectedSha256.toLowerCase(Locale.US).equals(actual)) {
                    tmpZip.delete();
                    return new Result(false, "Checksum mismatch, driver not installed.");
                }
            }
            Result installed = installZip(filesDir, tmpZip);
            tmpZip.delete();
            return installed;
        } catch (IOException e) {
            return new Result(false, "Download failed: " + e.getMessage());
        } catch (Exception e) {
            return new Result(false, "Download failed: " + e.getMessage());
        }
    }

    private static void downloadToFile(String zipUrl, File out) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(zipUrl).openConnection();
        connection.setConnectTimeout(30000);
        connection.setReadTimeout(120000);
        connection.setRequestProperty("User-Agent", "GeneralsZH-Android");
        int status = connection.getResponseCode();
        if (status != HttpURLConnection.HTTP_OK) {
            throw new IOException("HTTP " + status);
        }
        try (InputStream in = connection.getInputStream();
             FileOutputStream fileOut = new FileOutputStream(out)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                fileOut.write(buffer, 0, read);
            }
        } finally {
            connection.disconnect();
        }
    }

    /** Lower-case hex SHA-256 of a file. Public so Setup can show it in the log. */
    public static String sha256Hex(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        StringBuilder hex = new StringBuilder();
        for (byte b : digest.digest()) {
            hex.append(String.format(Locale.US, "%02x", b));
        }
        return hex.toString();
    }

    /**
     * Install a .zip the same way the existing import does: extract
     * to custom_driver.tmp, find meta.json, check its library file
     * exists, then move the folder into place as custom_driver/.
     * Zip-slip entries (../ or absolute paths) are skipped.
     * A bare libvulkan_*.so with no meta.json gets a minimal one,
     * the same as ARMSX2 does for freedreno-builder packs.
     */
    public static Result installZip(File filesDir, File zipFile) {
        File tmpDir = new File(filesDir, "custom_driver.tmp");
        deleteTree(tmpDir);
        try (ZipInputStream zip = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.contains("..") || name.startsWith("/")) {
                    continue;
                }
                File target = new File(tmpDir, name);
                if (!target.getCanonicalPath().startsWith(tmpDir.getCanonicalPath())) {
                    continue;
                }
                if (entry.isDirectory()) {
                    target.mkdirs();
                    continue;
                }
                target.getParentFile().mkdirs();
                try (FileOutputStream out = new FileOutputStream(target)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = zip.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                }
            }
        } catch (IOException e) {
            deleteTree(tmpDir);
            return new Result(false, "Could not read driver zip: " + e.getMessage());
        }
        File metaDir = findMetaDir(tmpDir);
        if (metaDir == null) {
            // Bare .so pack: synthesise a minimal meta.json so the
            // rest of the driver code can treat it like any pack.
            File so = findDriverSo(tmpDir);
            if (so == null) {
                deleteTree(tmpDir);
                return new Result(false, "Zip has no meta.json and no libvulkan .so.");
            }
            writeMinimalMeta(so.getParentFile(), so.getName());
            metaDir = so.getParentFile();
        }
        CustomDriverMeta meta = CustomDriverMeta.fromDirectory(metaDir);
        if (meta.libraryName.isEmpty() || !new File(metaDir, meta.libraryName).isFile()) {
            deleteTree(tmpDir);
            return new Result(false, "meta.json names a driver file that is not in the zip.");
        }
        File dest = new File(filesDir, "custom_driver");
        deleteTree(dest);
        if (!metaDir.renameTo(dest)) {
            deleteTree(tmpDir);
            return new Result(false, "Could not install driver.");
        }
        deleteTree(tmpDir);
        try {
            File cfg = new File(filesDir, "custom_driver.cfg");
            try (FileOutputStream out = new FileOutputStream(cfg)) {
                out.write(meta.libraryName.getBytes("UTF-8"));
            }
            new File(filesDir, "custom_driver.auto").delete();
        } catch (IOException e) {
            return new Result(false, "Driver installed but could not save its name: " + e.getMessage());
        }
        return new Result(true, "Installed " + meta.displayName());
    }

    private static File findMetaDir(File dir) {
        File[] files = dir.listFiles();
        if (files == null) {
            return null;
        }
        for (File file : files) {
            if (file.isFile() && file.getName().equals("meta.json")) {
                return dir;
            }
            if (file.isDirectory()) {
                File found = findMetaDir(file);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static File findDriverSo(File dir) {
        File[] files = dir.listFiles();
        if (files == null) {
            return null;
        }
        for (File file : files) {
            if (file.isFile() && file.getName().startsWith("libvulkan") && file.getName().endsWith(".so")) {
                return file;
            }
            if (file.isDirectory()) {
                File found = findDriverSo(file);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static void writeMinimalMeta(File dir, String libraryName) {
        try {
            String json = "{\"name\":\"" + libraryName + "\",\"libraryName\":\"" + libraryName + "\"}";
            try (FileOutputStream out = new FileOutputStream(new File(dir, "meta.json"))) {
                out.write(json.getBytes("UTF-8"));
            }
        } catch (IOException ignored) {
            // Install will fail at the meta check below, with a reason.
        }
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteTree(child);
                }
            }
        }
        file.delete();
    }
}
