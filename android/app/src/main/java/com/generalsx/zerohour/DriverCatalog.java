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
// Pojav freedreno-builder, ExynosTools, MdiEx, The412Banner
// Banners-Turnip, Droid-Deck Drivers.
//
// Credit, per source (author, repo, license -- no license is
// claimed where the project declares none; the Mesa code the
// Turnip builders compile is MIT/GPL upstream):
//   MaliSX2 -- bmdhacks, github.com/bmdhacks/malisx2, no license declared
//   Turnip (ARMSX2) -- bmdhacks / ARMSX2 Contributors,
//     github.com/bmdhacks/armsx2-turnip, no license declared (Mesa: MIT)
//   AdrenoToolsDrivers -- K11MCH1, github.com/K11MCH1/AdrenoToolsDrivers,
//     no license declared
//   StevenMXZ Adreno-Tools -- StevenMXZ,
//     github.com/StevenMXZ/Adreno-Tools-Drivers, GPL-3.0
//   purple-turnip -- MrPurple666, github.com/MrPurple666/purple-turnip,
//     no license declared
//   GameHub 8Elite -- crueter, github.com/crueter/GameHub-8Elite-Drivers,
//     no license declared (extracted Qualcomm drivers, not Mesa builds;
//     last release Oct 2025, kept as an 8xx fallback only)
//   freedreno-builder -- PojavLauncherTeam,
//     github.com/PojavLauncherTeam/freedreno-builder, GPL-3.0
//     (archived Jan 2025, kept as the old-Adreno-6xx/Android-10 fallback)
//   ExynosTools -- WearyConcern1165, github.com/WearyConcern1165/ExynosTools,
//     no license declared
//   MdiEx -- avavo, github.com/avavo/MdiEx, MIT
//   Banners-Turnip -- The412Banner, github.com/The412Banner/Banners-Turnip,
//     GPL-3.0 (A8xx stack by whitebelyash, github.com/whitebelyash/mesa-unified)
//   Droid-Deck Turnip -- Droid-Deck, github.com/Droid-Deck/Drivers, GPL-3.0
//
// PowerVR has no pack anywhere: Mesa's PowerVR driver is Linux-only
// and nobody publishes an Android adrenotools pack for it. Mali has
// one pack only (MaliSX2); Mesa Panfrost is Linux-only too.

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
        // Credit shown under the download button: author, repo, license.
        public final String credit;
        // Asset picking, per source. Some releases ship many .zip assets
        // and the newest release is not always the right pack: Banners
        // ships Linux/Wayland variants next to the Android ones, and
        // K11MCH1's newest release is an extracted Qualcomm stock pack,
        // not Turnip. assetMustContain (case-insensitive, "" = any) and
        // assetMustNotContain (comma-separated, "" = none) are checked
        // against the asset's file name, on top of the .zip suffix.
        public final String assetMustContain;
        public final String assetMustNotContain;
        // SHA-256 of the pinned release asset, when one is known.
        // Empty means "no pinned checksum yet": the download then
        // goes ahead without a checksum check. Do not ship a source
        // with a checksum copied from an unverified page.
        public final String expectedSha256;

        public Source(String id, String label, String releasesApi,
                      GpuInfo.Family family, String note) {
            this(id, label, releasesApi, family, note, "", "", "");
        }

        public Source(String id, String label, String releasesApi,
                      GpuInfo.Family family, String note, String expectedSha256) {
            this(id, label, releasesApi, family, note, "", "", "", expectedSha256);
        }

        public Source(String id, String label, String releasesApi,
                      GpuInfo.Family family, String note, String credit,
                      String assetMustContain, String assetMustNotContain) {
            this(id, label, releasesApi, family, note, credit,
                    assetMustContain, assetMustNotContain, "");
        }

        public Source(String id, String label, String releasesApi,
                      GpuInfo.Family family, String note, String credit,
                      String assetMustContain, String assetMustNotContain,
                      String expectedSha256) {
            this.id = id;
            this.label = label;
            this.releasesApi = releasesApi;
            this.family = family;
            this.note = note;
            this.credit = credit == null ? "" : credit;
            this.assetMustContain = assetMustContain == null ? "" : assetMustContain;
            this.assetMustNotContain = assetMustNotContain == null ? "" : assetMustNotContain;
            this.expectedSha256 = expectedSha256 == null ? "" : expectedSha256;
        }
    }

    private DriverCatalog() {}

    public static List<Source> all() {
        List<Source> sources = new ArrayList<>();
        sources.add(new Source("malisx2", "MaliSX2",
                "https://api.github.com/repos/bmdhacks/malisx2/releases",
                GpuInfo.Family.MALI,
                "Mali G31-G715, talks to the kbase kernel driver.",
                "MaliSX2 by bmdhacks, github.com/bmdhacks/malisx2"));
        sources.add(new Source("turnip", "Turnip (ARMSX2)",
                "https://api.github.com/repos/bmdhacks/armsx2-turnip/releases",
                GpuInfo.Family.ADRENO,
                "Adreno 6xx/7xx. Not for Adreno 8xx.",
                "Turnip by bmdhacks / ARMSX2 Contributors, github.com/bmdhacks/armsx2-turnip (Mesa: MIT)"));
        sources.add(new Source("adrenotools", "AdrenoToolsDrivers (K11MCH1)",
                "https://api.github.com/repos/K11MCH1/AdrenoToolsDrivers/releases",
                GpuInfo.Family.ADRENO,
                "Community Turnip packs, Adreno only.",
                "AdrenoToolsDrivers by K11MCH1, github.com/K11MCH1/AdrenoToolsDrivers",
                "turnip", ""));
        sources.add(new Source("stevenmxz-gen8", "StevenMXZ Adreno-Tools (Gen8)",
                "https://api.github.com/repos/StevenMXZ/Adreno-Tools-Drivers/releases",
                GpuInfo.Family.ADRENO,
                "Adreno 8xx / Snapdragon 8 Elite. Pick the Gen8 asset.",
                "Adreno-Tools-Drivers by StevenMXZ, github.com/StevenMXZ/Adreno-Tools-Drivers, GPL-3.0"));
        sources.add(new Source("purple-turnip", "MrPurple purple-turnip",
                "https://api.github.com/repos/MrPurple666/purple-turnip/releases",
                GpuInfo.Family.ADRENO,
                "Community Turnip, Adreno only.",
                "purple-turnip by MrPurple666, github.com/MrPurple666/purple-turnip"));
        sources.add(new Source("gamehub-8elite", "GameHub 8Elite",
                "https://api.github.com/repos/crueter/GameHub-8Elite-Drivers/releases",
                GpuInfo.Family.ADRENO,
                "Adreno 8xx packs. Stale since Oct 2025, fallback only.",
                "GameHub 8Elite Drivers by crueter, github.com/crueter/GameHub-8Elite-Drivers (extracted Qualcomm drivers)"));
        sources.add(new Source("freedreno-builder", "Pojav freedreno-builder",
                "https://api.github.com/repos/PojavLauncherTeam/freedreno-builder/releases",
                GpuInfo.Family.ADRENO,
                "Bare .so, no meta.json. Older Adreno 6xx, Android 10. Archived Jan 2025, fallback only.",
                "freedreno-builder by PojavLauncherTeam, github.com/PojavLauncherTeam/freedreno-builder, GPL-3.0"));
        sources.add(new Source("exynostools", "ExynosTools",
                "https://api.github.com/repos/WearyConcern1165/ExynosTools/releases",
                GpuInfo.Family.XCLIPSE,
                "Xclipse only.",
                "ExynosTools by WearyConcern1165, github.com/WearyConcern1165/ExynosTools"));
        sources.add(new Source("mdiex", "MdiEx",
                "https://api.github.com/repos/avavo/MdiEx/releases",
                GpuInfo.Family.XCLIPSE,
                "Xclipse only, one zip per Exynos chip.",
                "MdiEx by avavo, github.com/avavo/MdiEx, MIT"));
        sources.add(new Source("banners-turnip", "Banners-Turnip (The412Banner)",
                "https://api.github.com/repos/The412Banner/Banners-Turnip/releases",
                GpuInfo.Family.ADRENO,
                "Turnip for Adreno 6xx/7xx/8xx, one pack for every Adreno (Banners-AIO).",
                "Banners-Turnip by The412Banner, github.com/The412Banner/Banners-Turnip, GPL-3.0 (A8xx stack by whitebelyash)",
                "Banners-AIO", "-Linux,-Wayland"));
        sources.add(new Source("droid-deck", "Droid-Deck Turnip (Droid-Deck)",
                "https://api.github.com/repos/Droid-Deck/Drivers/releases",
                GpuInfo.Family.ADRENO,
                "Weekly Turnip builds, Adreno 6xx/7xx/8xx.",
                "Droid-Deck Turnip Drivers by Droid-Deck, github.com/Droid-Deck/Drivers, GPL-3.0"));
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
