// SPDX-License-Identifier: GPL-3.0-or-later
// GeneralsX @feature Android port 23/11/2026 Driver catalogue by GPU family. Each entry is a GitHub releases feed; a pack is only offered when it matches the detected GPU.
//
// Each entry is a GitHub releases feed. A pack is only offered
// when its family test matches the detected GPU, so a Mali phone
// is never offered Turnip and an Adreno phone is never offered
// MaliSX2. The releases API URL is the feed; the app picks the
// newest release asset that ends in .zip and, when the release
// publishes one, verifies its SHA-256 before installing.
//
// Sources: ARMSX2 driver list, K11MCH1 AdrenoToolsDrivers,
// bmdhacks/malisx2, StevenMXZ, MrPurple, GameHub 8Elite,
// Pojav freedreno-builder, ExynosTools, MdiEx.

package com.generalsx.zerohour;

import java.util.ArrayList;
import java.util.List;

public final class DriverCatalog {

    public static final class Source {
        public final String id;
        public final String label;
        public final String releasesApi;
        public final GpuInfo.Family family;
        public final String note;
        // SHA-256 of the pinned release asset, when one is known.
        // Empty means "no pinned checksum yet": the download then
        // goes ahead without a checksum check. Do not ship a source
        // with a checksum copied from an unverified page.
        public final String expectedSha256;

        public Source(String id, String label, String releasesApi,
                      GpuInfo.Family family, String note) {
            this(id, label, releasesApi, family, note, "");
        }

        public Source(String id, String label, String releasesApi,
                      GpuInfo.Family family, String note, String expectedSha256) {
            this.id = id;
            this.label = label;
            this.releasesApi = releasesApi;
            this.family = family;
            this.note = note;
            this.expectedSha256 = expectedSha256 == null ? "" : expectedSha256;
        }
    }

    private DriverCatalog() {}

    public static List<Source> all() {
        List<Source> sources = new ArrayList<>();
        sources.add(new Source("malisx2", "MaliSX2",
                "https://api.github.com/repos/bmdhacks/malisx2/releases",
                GpuInfo.Family.MALI,
                "Mali G31-G715, talks to the kbase kernel driver."));
        sources.add(new Source("turnip", "Turnip (ARMSX2)",
                "https://api.github.com/repos/bmdhacks/armsx2-turnip/releases",
                GpuInfo.Family.ADRENO,
                "Adreno 6xx/7xx. Not for Adreno 8xx."));
        sources.add(new Source("adrenotools", "AdrenoToolsDrivers (K11MCH1)",
                "https://api.github.com/repos/K11MCH1/AdrenoToolsDrivers/releases",
                GpuInfo.Family.ADRENO,
                "Community Turnip packs, Adreno only."));
        sources.add(new Source("stevenmxz-gen8", "StevenMXZ Adreno-Tools (Gen8)",
                "https://api.github.com/repos/StevenMXZ/Adreno-Tools-Drivers/releases",
                GpuInfo.Family.ADRENO,
                "Adreno 8xx / Snapdragon 8 Elite. Pick the Gen8 asset."));
        sources.add(new Source("purple-turnip", "MrPurple purple-turnip",
                "https://api.github.com/repos/MrPurple666/purple-turnip/releases",
                GpuInfo.Family.ADRENO,
                "Community Turnip, Adreno only."));
        sources.add(new Source("gamehub-8elite", "GameHub 8Elite",
                "https://api.github.com/repos/crueter/GameHub-8Elite-Drivers/releases",
                GpuInfo.Family.ADRENO,
                "Adreno 8xx packs."));
        sources.add(new Source("freedreno-builder", "Pojav freedreno-builder",
                "https://api.github.com/repos/PojavLauncherTeam/freedreno-builder/releases",
                GpuInfo.Family.ADRENO,
                "Bare .so, no meta.json. Older Adreno 6xx, Android 10."));
        sources.add(new Source("exynostools", "ExynosTools",
                "https://api.github.com/repos/WearyConcern1165/ExynosTools/releases",
                GpuInfo.Family.XCLIPSE,
                "Xclipse only."));
        sources.add(new Source("mdiex", "MdiEx",
                "https://api.github.com/repos/avavo/MdiEx/releases",
                GpuInfo.Family.XCLIPSE,
                "Xclipse only, one zip per Exynos chip."));
        return sources;
    }

    /** Sources that may be offered for this GPU. */
    public static List<Source> forGpu(String renderer) {
        GpuInfo.Family family = GpuInfo.familyOf(renderer);
        List<Source> result = new ArrayList<>();
        for (Source source : all()) {
            if (source.family != family) {
                continue;
            }
            if (family == GpuInfo.Family.MALI && !GpuInfo.isMaliSx2Supported(renderer)) {
                continue;
            }
            if (family == GpuInfo.Family.ADRENO && source.id.equals("stevenmxz-gen8")
                    && GpuInfo.adrenoModel(renderer) < 800) {
                continue;
            }
            if (family == GpuInfo.Family.ADRENO && source.id.equals("turnip")
                    && GpuInfo.adrenoModel(renderer) >= 800) {
                continue;
            }
            result.add(source);
        }
        return result;
    }
}
