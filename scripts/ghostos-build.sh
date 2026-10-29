#!/usr/bin/env bash
set -euo pipefail

DEVICE="${DEVICE:-larry}"
JOBS="${JOBS:-$(nproc)}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
# Keep the large LineageOS checkout outside the GhostOS repository by default.
ANDROID_DIR="${ANDROID_DIR:-${GHOSTOS_ANDROID_DIR:-$ROOT/../ghostos-android16-larry}}"
MANIFEST="$ROOT/manifests/ghostos-23.2.xml"

usage() {
  echo "Usage: DEVICE=larry|oscaro JOBS=<n> $0"
  echo
  echo "This bootstrap uses the verified LineageOS 23.2 (Android 16) foundation."
  echo "Android 17 migration is a separate bring-up gate; it is not fabricated here."
}

command -v repo >/dev/null || { echo "ERROR: repo is required" >&2; exit 1; }
[[ -f "$MANIFEST" ]] || { echo "ERROR: missing $MANIFEST" >&2; exit 1; }

mkdir -p "$ANDROID_DIR"
ANDROID_DIR="$(cd "$ANDROID_DIR" && pwd -P)"

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

echo "==> Preparing $DEVICE"
source build/envsetup.sh
breakfast "$DEVICE"

echo "==> Building $DEVICE"
# LineageOS provides the device build target; run the standard release target.
m bacon

echo "==> Build completed for $DEVICE"
