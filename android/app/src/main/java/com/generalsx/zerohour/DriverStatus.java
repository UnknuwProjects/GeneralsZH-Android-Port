// SPDX-License-Identifier: GPL-3.0-or-later
// GeneralsX @feature Android port 23/11/2026 What custom driver was asked for versus what actually opened. A custom driver that fails to load falls back to stock silently, so without this Setup claims the custom driver is running when it is not.
//
// ARMSX2 keeps this distinction because a custom driver that fails
// to load falls back to the stock driver silently. Without it,
// Setup and the log both claim the custom driver is running when
// it is not. This class is only the value object and the log line;
// the native side fills it in from TryLoadCustomVulkanDriver(),
// and Setup reads it back to show the real driver in use.

package com.generalsx.zerohour;

public final class DriverStatus {

    public final String requested;
    public final String opened;
    public final boolean required;
    public final String failure;

    public DriverStatus(String requested, String opened, boolean required, String failure) {
        this.requested = requested == null ? "" : requested;
        this.opened = opened == null ? "" : opened;
        this.required = required;
        this.failure = failure == null ? "" : failure;
    }

    public static DriverStatus systemDriver() {
        return new DriverStatus("", "system", false, "");
    }

    /** True when the driver that was asked for is the one that opened. */
    public boolean isCustomDriverActive() {
        return !requested.isEmpty() && requested.equals(opened);
    }

    /** True when a custom driver was asked for but the stock driver answered instead. */
    public boolean fellBackToStock() {
        return !requested.isEmpty() && !requested.equals(opened);
    }

    /** One log line, e.g. for generals-stderr.log: [gxdriver] requested=... opened=... */
    public String logLine() {
        String line = "[gxdriver] requested=" + (requested.isEmpty() ? "system" : requested)
                + " opened=" + (opened.isEmpty() ? "unknown" : opened)
                + " required=" + required;
        if (!failure.isEmpty()) {
            line += " failure=" + failure;
        }
        return line;
    }

    /** Short line for the Setup card, e.g. "Running: Turnip 25.3.0" or "Running: system driver". */
    public String displayLine(String customDisplayName) {
        if (isCustomDriverActive()) {
            return "Running: " + customDisplayName;
        }
        if (fellBackToStock()) {
            return "Running: system driver (custom driver failed to load)";
        }
        return "Running: system driver";
    }
}
