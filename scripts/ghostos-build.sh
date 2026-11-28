#!/usr/bin/env bash
set -euo pipefail

DEVICE="${DEVICE:-larry}"
JOBS="${JOBS:-$(nproc)}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
ANDROID_DIR="${ANDROID_DIR:-${GHOSTOS_ANDROID_DIR:-$ROOT/../ghostos-android23.2-larry}}"
MANIFEST="$ROOT/manifests/ghostos-23.2.xml"

usage() {
  echo "Usage: DEVICE=larry|oscaro JOBS=<n> $0"
  echo
  echo "This bootstrap uses the LineageOS 23.2 / Android 16 source baseline."
  echo "No full product build, flashable image or physical-device boot is verified by this script."
  echo "Android 17 migration is a separate bring-up gate."
}

command -v repo >/dev/null || { echo "ERROR: repo is required" >&2; exit 1; }
[[ -f "$MANIFEST" ]] || { echo "ERROR: missing $MANIFEST" >&2; exit 1; }

# Resolve existing symlinks and missing path components before creating the target.
# This lets the safety checks reject an in-repository path without leaving a
# directory behind in the GhostOS checkout.
ANDROID_DIR="$(python3 -c 'import os, sys; print(os.path.realpath(sys.argv[1]))' "$ANDROID_DIR")"

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

# Do not start a full LineageOS sync on a host that cannot complete a product build.
# The defaults match a 64-GB-class Linux builder with at least 400 GB free disk.
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
  echo "ERROR: Linux /proc/meminfo is required; run the Android build on a Linux host" >&2
  exit 1
}
MEM_TOTAL_KIB="$(awk '/^MemTotal:/ { print $2; exit }' /proc/meminfo)"
[[ "$MEM_TOTAL_KIB" =~ ^[1-9][0-9]*$ ]] || {
  echo "ERROR: could not determine total Linux memory from /proc/meminfo" >&2
  exit 1
}
MIN_RAM_KIB=$((MIN_RAM_GIB * 1024 * 1024))
if (( MEM_TOTAL_KIB < MIN_RAM_KIB )); then
  echo "ERROR: GhostOS full build requires at least ${MIN_RAM_GIB} GiB RAM; detected $((MEM_TOTAL_KIB / 1024 / 1024)) GiB" >&2
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
# df -Pk reports KiB; convert decimal GB to KiB (1 GB = 1,000,000,000 bytes).
MIN_DISK_KIB=$((MIN_DISK_GB * 1000000000 / 1024))
if (( DISK_AVAILABLE_KIB < MIN_DISK_KIB )); then
  echo "ERROR: GhostOS full build requires at least ${MIN_DISK_GB} GB free disk; detected $((DISK_AVAILABLE_KIB * 1024 / 1000000000)) GB" >&2
  exit 1
fi
echo "==> Host preflight passed: $((MEM_TOTAL_KIB / 1024 / 1024)) GiB RAM and $((DISK_AVAILABLE_KIB * 1024 / 1000000000)) GB free disk"

mkdir -p "$ANDROID_DIR"
ANDROID_DIR="$(cd "$ANDROID_DIR" && pwd -P)"
cd "$ANDROID_DIR"

# A fresh checkout has no .repo/manifest.xml. Initialize it before creating
# local_manifests; otherwise a new checkout incorrectly skips repo init.
if [[ ! -f ".repo/manifest.xml" ]]; then
  echo "==> Initializing LineageOS 23.2 source tree"
  repo init -u https://github.com/LineageOS/android.git -b lineage-23.2 --git-lfs
fi

mkdir -p ".repo/local_manifests"
cp "$MANIFEST" ".repo/local_manifests/ghostos.xml"
echo "==> Syncing GhostOS device foundation"
repo sync -c --no-clone-bundle --no-tags -j"$JOBS"

# Overlay GhostOS-owned apps only after the upstream device tree is synced.
if [[ "$DEVICE" == "larry" ]]; then
  DEVICE="$DEVICE" ANDROID_DIR="$ANDROID_DIR" bash "$ROOT/scripts/integrate-ghostos-apps.sh" "$ANDROID_DIR"
else
  echo "==> Skipping larry-only GhostOS app overlay for $DEVICE"
fi

echo "==> Preparing $DEVICE"
source build/envsetup.sh
breakfast "$DEVICE"

echo "==> Building $DEVICE"
# LineageOS provides the device build target; run the standard release target.
m bacon

echo "==> Build completed for $DEVICE"
