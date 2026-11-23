// SPDX-License-Identifier: GPL-3.0-or-later
// GeneralsX @feature Android port 23/11/2026 Driver pack metadata. Reads the meta.json the import code already validates, so Setup shows a name and version instead of only the .so filename.
//
// Reads the meta.json that the existing import code already
// validates, so Setup can show a name and a version instead of
// only the .so filename. No new dependency: the file is small
// enough to parse by hand.

package com.generalsx.zerohour;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public final class CustomDriverMeta {

    public final String name;
    public final String version;
    public final String author;
    public final String libraryName;

    public CustomDriverMeta(String name, String version, String author, String libraryName) {
        this.name = name;
        this.version = version;
        this.author = author;
        this.libraryName = libraryName;
    }

    public static CustomDriverMeta fromDirectory(File driverDir) {
        File meta = new File(driverDir, "meta.json");
        String name = driverDir.getName();
        String version = "";
        String author = "";
        String libraryName = "";
        if (meta.isFile()) {
            try {
                String json = new String(Files.readAllBytes(meta.toPath()), StandardCharsets.UTF_8);
                name = stringField(json, "name", name);
                version = stringField(json, "driverVersion", stringField(json, "packageVersion", ""));
                author = stringField(json, "author", "");
                libraryName = stringField(json, "libraryName", "");
            } catch (IOException ignored) {
                // Keep the directory name; a broken meta.json is
                // already rejected by the import code.
            }
        }
        if (libraryName.isEmpty()) {
            File[] files = driverDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.getName().startsWith("libvulkan") && file.getName().endsWith(".so")) {
                        libraryName = file.getName();
                        break;
                    }
                }
            }
        }
        return new CustomDriverMeta(name, version, author, libraryName);
    }

    /** Display line for Setup, e.g. "Turnip 25.3.0 (K11MCH1)". */
    public String displayName() {
        String text = name;
        if (!version.isEmpty()) {
            text += " " + version;
        }
        if (!author.isEmpty()) {
            text += " (" + author + ")";
        }
        return text;
    }

    private static String stringField(String json, String key, String fallback) {
        String needle = "\"" + key + "\"";
        int keyAt = json.indexOf(needle);
        if (keyAt < 0) {
            return fallback;
        }
        int colon = json.indexOf(':', keyAt + needle.length());
        if (colon < 0) {
            return fallback;
        }
        int open = json.indexOf('"', colon);
        if (open < 0) {
            return fallback;
        }
        int close = json.indexOf('"', open + 1);
        if (close < 0) {
            return fallback;
        }
        return json.substring(open + 1, close);
    }
}
