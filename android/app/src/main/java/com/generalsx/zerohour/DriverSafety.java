// SPDX-License-Identifier: GPL-3.0-or-later
// GeneralsX @feature Android port 23/11/2026 Boot-failure counter for custom drivers. A driver that loads but then crashes is worse than one that fails to load, because the load-time fallback never sees it.
//
// A driver that loads but then crashes is worse than one that fails
// to load, because the load-time fallback never sees it. So count boots.
//
// - noteBootStarted(): call when the game starts with a custom
//   driver selected. Writes a small counter file in filesDir.
// - noteReachedMainMenu(): call when the main menu is reached.
//   Clears the counter.
// - shouldRevert(): true after 2 consecutive failed boots.
//   Setup then reverts to the stock driver (or GLES) on the next
//   launch and shows a notice. Reset returns to stock, it does
//   not re-apply the bundled Turnip blindly.

package com.generalsx.zerohour;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public final class DriverSafety {

    public static final int MAX_FAILED_BOOTS = 2;

    private static final String COUNTER_FILE = "custom_driver.failed_boots";
    private static final String REVERTED_FILE = "custom_driver.reverted";

    private DriverSafety() {}

    public static void noteBootStarted(File filesDir) {
        int count = readCount(filesDir) + 1;
        writeCount(filesDir, count);
    }

    public static void noteReachedMainMenu(File filesDir) {
        File counter = new File(filesDir, COUNTER_FILE);
        if (counter.exists()) {
            counter.delete();
        }
    }

    public static boolean shouldRevert(File filesDir) {
        return readCount(filesDir) >= MAX_FAILED_BOOTS;
    }

    public static void markReverted(File filesDir) {
        try {
            Files.write(new File(filesDir, REVERTED_FILE).toPath(),
                    "reverted to stock driver after failed boots\n".getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            // Best effort only; Setup checks the counter itself.
        }
        File counter = new File(filesDir, COUNTER_FILE);
        if (counter.exists()) {
            counter.delete();
        }
    }

    public static boolean wasReverted(File filesDir) {
        return new File(filesDir, REVERTED_FILE).exists();
    }

    public static void clearRevertedNote(File filesDir) {
        File note = new File(filesDir, REVERTED_FILE);
        if (note.exists()) {
            note.delete();
        }
    }

    private static int readCount(File filesDir) {
        try {
            String text = new String(
                    Files.readAllBytes(new File(filesDir, COUNTER_FILE).toPath()),
                    StandardCharsets.UTF_8).trim();
            return Integer.parseInt(text);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static void writeCount(File filesDir, int count) {
        try {
            Files.write(new File(filesDir, COUNTER_FILE).toPath(),
                    Integer.toString(count).getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            // Best effort only; a missing counter just means no revert.
        }
    }
}
