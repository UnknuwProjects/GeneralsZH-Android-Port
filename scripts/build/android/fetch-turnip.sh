#!/bin/bash
# Fetch a prebuilt Mesa Turnip driver (adrenotools ADPKG format) and stage it
# for bundling into the APK as the default fallback Vulkan driver.
#
# This is ONLY a fallback: TryLoadCustomVulkanDriver() in SDL3Main.cpp only
# picks it up if custom_driver.cfg isn't already set (no user-imported driver)
# AND the device's stock driver reports less than Vulkan 1.3. It's a no-op on
# Mali/PowerVR/anything non-Adreno, and a no-op on Adreno phones whose stock
# driver already handles 1.3 -- see SetupActivity.applyRecommendedDriverIfNeeded().
#
# Pinned release: same build validated in the wild by the fadi-labib/
# Generals-Android community port (v25.3.0-rc.11, targets Adreno 6xx/7xx).
# The SHA-256 pin (TURNIP_SHA256 below) is still TODO -- until it is filled
# in, the zip is validated by content instead: must unzip to an AArch64 .so
# plus a meta.json naming it.
set -euo pipefail

TURNIP_VERSION="v25.3.0-rc.11"
TURNIP_URL="https://github.com/K11MCH1/AdrenoToolsDrivers/releases/download/${TURNIP_VERSION}/Turnip_v25.3.0_R11.zip"
# GeneralsX @feature Android port 23/11/2026 SHA-256 pin for the bundled Turnip
# zip. Fill this in from the K11MCH1 release page, verified by downloading the
# zip once and running sha256sum on it -- do not copy a checksum from memory.
# Until it is filled in the script keeps the content checks below and warns,
# instead of refusing a build that has no other way to stage the driver.
TURNIP_SHA256="TODO"
DEST="${GX_DEFAULT_DRIVER:-${HOME}/GeneralsX/android-staging/default_driver}"
TMP="$(mktemp -d)"
trap 'rm -rf "${TMP}"' EXIT

if [[ -f "${DEST}/meta.json" ]]; then
    echo "Default Turnip driver already staged at ${DEST}"
    exit 0
fi

echo "==> Downloading Turnip ${TURNIP_VERSION}"
curl -fL -o "${TMP}/turnip.zip" "${TURNIP_URL}"
if [[ -n "${TURNIP_SHA256}" && "${TURNIP_SHA256}" != "TODO" ]]; then
    ACTUAL_SHA256="$(sha256sum "${TMP}/turnip.zip" | awk '{print $1}')"
    if [[ "${ACTUAL_SHA256}" != "${TURNIP_SHA256}" ]]; then
        echo "ERROR: Turnip zip checksum mismatch (expected ${TURNIP_SHA256}, got ${ACTUAL_SHA256})"
        exit 1
    fi
else
    echo "WARNING: TURNIP_SHA256 is not pinned yet -- relying on the content checks below"
fi
unzip -q "${TMP}/turnip.zip" -d "${TMP}/extracted"

META_FILE="$(find "${TMP}/extracted" -name meta.json | head -1)"
if [[ -z "${META_FILE}" ]]; then
    echo "ERROR: meta.json not found in ${TURNIP_URL} (not a valid adrenotools ADPKG)"
    exit 1
fi
DRIVER_SRC_DIR="$(dirname "${META_FILE}")"

LIBRARY_NAME="$(grep -o '"libraryName"[[:space:]]*:[[:space:]]*"[^"]*"' "${META_FILE}" | sed -E 's/.*"([^"]+)"$/\1/')"
if [[ -z "${LIBRARY_NAME}" || ! -f "${DRIVER_SRC_DIR}/${LIBRARY_NAME}" ]]; then
    echo "ERROR: meta.json has no usable libraryName, or the named file is missing"
    exit 1
fi

if command -v file >/dev/null 2>&1; then
    ARCH="$(file -b "${DRIVER_SRC_DIR}/${LIBRARY_NAME}")"
    if [[ "${ARCH}" != *"ARM aarch64"* && "${ARCH}" != *"AArch64"* ]]; then
        echo "ERROR: ${LIBRARY_NAME} is not an AArch64 shared object (got: ${ARCH})"
        exit 1
    fi
fi
# Capture to a variable first, rather than piping `strings` straight into
# `grep -q`: grep exits right after the first match and closes its end of
# the pipe, and `strings` (still mid-write on a multi-MB .so) gets SIGPIPE
# for it, which `pipefail` above then reports as the pipeline failing even
# though the match was found -- same hazard already documented and worked
# around in build-local-sandboxed.sh's DXVK strings check.
driver_strings="$(strings "${DRIVER_SRC_DIR}/${LIBRARY_NAME}" 2>/dev/null)"
if ! grep -qiE "turnip|freedreno|mesa" <<< "${driver_strings}"; then
    echo "ERROR: ${LIBRARY_NAME} doesn't look like a Turnip/Mesa driver (no identifying strings found)"
    exit 1
fi

mkdir -p "$(dirname "${DEST}")"
rm -rf "${DEST}"
mkdir -p "${DEST}"
cp "${META_FILE}" "${DRIVER_SRC_DIR}/${LIBRARY_NAME}" "${DEST}/"
echo "==> Staged Turnip ${TURNIP_VERSION} (${LIBRARY_NAME}) at ${DEST}"
