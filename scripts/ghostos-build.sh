#!/usr/bin/env bash
set -euo pipefail

DEVICE="${DEVICE:-larry}"
JOBS="${JOBS:-$(nproc)}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
ANDROID_DIR="${ANDROID_DIR:-${GHOSTOS_ANDROID_DIR:-$ROOT/../ghostos-android17-larry}}"
MANIFEST="$ROOT/manifests/ghostos-24.0.xml"
LINEAGE_BRANCH="lineage-24.0"

case "$DEVICE" in
  larry) PRODUCT="ghostos_larry" ;;
  *)
    echo "ERROR: only the GhostOS larry product is currently defined (requested: $DEVICE)" >&2
    exit 1
    ;;
esac

usage() {
  echo "Usage: DEVICE=larry JOBS=<n> $0"
  echo
  echo "This bootstrap uses LineageOS 24.0 / Android 17 (AOSP android-17.0.0_r1)."
  echo "It stages the GhostOS larry product, vendor config and Privacy Center."
  echo "The current upstream vendor tree is based on CPH2467_15.0.0.2000(EX01)."
  echo "AVB in the upstream device foundation is experimental; this is not a release build."
}

command -v repo >/dev/null || { echo "ERROR: repo is required" >&2; exit 1; }
[[ -f "$MANIFEST" ]] || { echo "ERROR: missing $MANIFEST" >&2; exit 1; }

# Resolve existing symlinks and missing path components before creating the target.
# This lets the safety checks reject an in-repository path without leaving a
# directory behind in the GhostOS checkout.
ANDROID_DIR="$(python3 -c 'import os, sys; print(os.path.realpath(sys.argv[1]))' "$ANDROID_DIR")"

# The root directory is not a valid Android source checkout target.
if [[ "$ANDROID_DIR" == "/" ]]; then
  echo "ERROR: Android source directory must not be the filesystem root: $ANDROID_DIR" >&2
  exit 1
fi

# Never initialize or sync a full Android source tree inside or above GhostOS.
case "$ANDROID_DIR/" in
  "$ROOT/"*)
    echo "ERROR: Android source directory must be outside the GhostOS repository: $ANDROID_DIR" >&2
    exit 1
    ;;
esac
case "$ROOT/" in
  "$ANDROID_DIR/"*)
    echo "ERROR: Android source directory must not contain the GhostOS repository: $ANDROID_DIR" >&2
    exit 1
    ;;
esac

# A full LineageOS 24.0 sync and Soong build must not start on a small host.
# 56 GiB visible RAM is the practical floor for a 64-GB-class Linux builder;
# keep at least 400 GB free on the filesystem that will hold the source tree.
MIN_RAM_GIB="${GHOSTOS_MIN_RAM_GIB:-56}"
MIN_DISK_GB="${GHOSTOS_MIN_DISK_GB:-400}"
[[ "$MIN_RAM_GIB" =~ ^[1-9][0-9]*$ ]] || {
  echo "ERROR: GHOSTOS_MIN_RAM_GIB must be a positive integer" >&2
  exit 1
}
[[ "$MIN_DISK_GB" =~ ^[1-9][0-9]*$ ]] || {
  echo "ERROR: GHOSTOS_MIN_DISK_GB must be a positive integer" >&2
  exit 1
}
[[ -r /proc/meminfo ]] || {
  echo "ERROR: Linux /proc/meminfo is required; run the Android 17 build on a Linux host" >&2
  exit 1
}
MEM_TOTAL_KIB="$(awk '/^MemTotal:/ { print $2; exit }' /proc/meminfo)"
[[ "$MEM_TOTAL_KIB" =~ ^[1-9][0-9]*$ ]] || {
  echo "ERROR: could not determine total Linux memory from /proc/meminfo" >&2
  exit 1
}
MIN_RAM_KIB=$((MIN_RAM_GIB * 1024 * 1024))
if (( MEM_TOTAL_KIB < MIN_RAM_KIB )); then
  echo "ERROR: GhostOS Android 17 full build requires at least ${MIN_RAM_GIB} GiB RAM; detected $((MEM_TOTAL_KIB / 1024 / 1024)) GiB" >&2
  exit 1
fi

DISK_PROBE="$ANDROID_DIR"
while [[ ! -d "$DISK_PROBE" ]]; do
  PARENT_DIR="$(dirname "$DISK_PROBE")"
  [[ "$PARENT_DIR" != "$DISK_PROBE" ]] || {
    echo "ERROR: could not locate an existing filesystem for $ANDROID_DIR" >&2
    exit 1
  }
  DISK_PROBE="$PARENT_DIR"
done
DISK_AVAILABLE_KIB="$(df -Pk "$DISK_PROBE" | awk 'END { print $4 }')"
[[ "$DISK_AVAILABLE_KIB" =~ ^[0-9]+$ ]] || {
  echo "ERROR: could not determine free disk space for $DISK_PROBE" >&2
  exit 1
}
MIN_DISK_KIB=$((MIN_DISK_GB * 1000000 / 1024))
if (( DISK_AVAILABLE_KIB < MIN_DISK_KIB )); then
  echo "ERROR: GhostOS Android 17 full build requires at least ${MIN_DISK_GB} GB free disk; detected $((DISK_AVAILABLE_KIB * 1024 / 1000000000)) GB" >&2
  exit 1
fi
echo "==> Host preflight passed: $((MEM_TOTAL_KIB / 1024 / 1024)) GiB RAM and $((DISK_AVAILABLE_KIB * 1024 / 1000000000)) GB free disk"

mkdir -p "$ANDROID_DIR"
ANDROID_DIR="$(cd "$ANDROID_DIR" && pwd -P)"

cd "$ANDROID_DIR"

# Always select the LineageOS 24.0 manifest. Re-initializing is intentional:
# an existing AOSP-only android17-release checkout must not receive LineageOS
# device projects under the wrong top-level manifest.
echo "==> Initializing LineageOS $LINEAGE_BRANCH source tree"
repo init -u https://github.com/LineageOS/android.git -b "$LINEAGE_BRANCH" --git-lfs

mkdir -p ".repo/local_manifests"
cp "$MANIFEST" ".repo/local_manifests/ghostos.xml"
echo "==> Syncing pinned GhostOS larry Android 17 foundation"
repo sync -c --no-clone-bundle --no-tags -j"$JOBS"

echo "==> Staging GhostOS Android product inputs"
for source in "$ROOT/device/ghostos" "$ROOT/vendor/ghostos" "$ROOT/packages/apps/GhostOSPrivacyCenter"; do
  [[ -d "$source" ]] || { echo "ERROR: missing GhostOS source directory: $source" >&2; exit 1; }
done
mkdir -p "$ANDROID_DIR/device/ghostos" "$ANDROID_DIR/vendor/ghostos" "$ANDROID_DIR/packages/apps"
cp -a "$ROOT/device/ghostos/." "$ANDROID_DIR/device/ghostos/"
cp -a "$ROOT/vendor/ghostos/." "$ANDROID_DIR/vendor/ghostos/"
cp -a "$ROOT/packages/apps/GhostOSPrivacyCenter" "$ANDROID_DIR/packages/apps/"
test -f "$ANDROID_DIR/device/ghostos/larry/AndroidProducts.mk"
test -f "$ANDROID_DIR/device/ghostos/larry/ghostos_larry.mk"
test -f "$ANDROID_DIR/vendor/ghostos/config/common.mk"
test -f "$ANDROID_DIR/packages/apps/GhostOSPrivacyCenter/Android.bp"

echo "==> Selecting GhostOS product $PRODUCT-userdebug"
source build/envsetup.sh
lunch "$PRODUCT-userdebug"

echo "==> Building GhostOS $DEVICE"
# This is a development bring-up build only. Upstream AVB test-key and
# verification-disabled flags must be replaced and independently reviewed
# before any production image or bootloader-relock attempt.
m bacon

echo "==> GhostOS $DEVICE Android 17 build command completed"
