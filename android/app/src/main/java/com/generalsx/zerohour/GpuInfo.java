// SPDX-License-Identifier: GPL-3.0-or-later
// GeneralsX @feature Android port 23/11/2026 GPU detection for the driver catalogue. Classifies the GL_RENDERER string into a GPU family and recommends the driver pack built for it. Classification only, it never switches the renderer.
//
// The renderer string comes from a throwaway 1x1 EGL pbuffer, the
// same probe SetupActivity already uses. This class only classifies
// that string; it never switches the renderer by itself.

package com.generalsx.zerohour;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GpuInfo {

    public enum Family {
        ADRENO, MALI, XCLIPSE, POWERVR, OTHER
    }

    private static final Pattern ADRENO_MODEL =
            Pattern.compile("Adreno\\D*([0-9]{3})");
    // Mali models served by MaliSX2: G31, G51, G52, G57, G68, G76,
    // G77, G78, G310, G510, G610, G615, G620, G710, G715, G720 and Immortalis.
    private static final Pattern MALI_MODEL =
            Pattern.compile("\\bG(?:31|51|52|57|68|76|77|78|310|510|610|615|620|710|715|720)(?!\\d)");

    private GpuInfo() {}

    public static Family familyOf(String renderer) {
        if (renderer == null) {
            return Family.OTHER;
        }
        String name = renderer.toLowerCase();
        if (name.contains("adreno")) {
            return Family.ADRENO;
        }
        if (name.contains("mali") || name.contains("immortalis")) {
            return Family.MALI;
        }
        if (name.contains("xclipse")) {
            return Family.XCLIPSE;
        }
        if (name.contains("powervr")) {
            return Family.POWERVR;
        }
        return Family.OTHER;
    }

    public static int adrenoModel(String renderer) {
        if (renderer == null) {
            return 0;
        }
        Matcher matcher = ADRENO_MODEL.matcher(renderer);
        if (!matcher.find()) {
            return 0;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    public static boolean isMaliSx2Supported(String renderer) {
        if (renderer == null) {
            return false;
        }
        String name = renderer.toLowerCase();
        if (!name.contains("mali") && !name.contains("immortalis")) {
            return false;
        }
        return MALI_MODEL.matcher(renderer).find();
    }

    /** One-line recommendation, shown in Setup. Null means: use the system driver. */
    public static String recommendation(String renderer) {
        Family family = familyOf(renderer);
        if (family == Family.MALI && isMaliSx2Supported(renderer)) {
            return "MaliSX2";
        }
        if (family == Family.ADRENO) {
            int model = adrenoModel(renderer);
            if (model >= 800) {
                return "StevenMXZ Adreno-Tools (Gen8)";
            }
            if (model >= 600) {
                return "Turnip";
            }
        }
        if (family == Family.XCLIPSE) {
            return "MdiEx";
        }
        return null;
    }
}
