#!/usr/bin/env bash
set -euo pipefail

DEVICE="${DEVICE:-larry}"
JOBS="${JOBS:-$(nproc)}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
# Keep the large LineageOS checkout outside the GhostOS repository by default.
ANDROID_DIR="${ANDROID_DIR:-${GHOSTOS_ANDROID_DIR:-$ROOT/../ghostos-android16-larry}}"
MANIFEST="$ROOT/manifests/ghostos-23.2.xml"

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
  echo "This bootstrap uses the verified LineageOS 23.2 (Android 16) foundation."
  echo "It stages the GhostOS larry product, vendor config and Privacy Center."
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
echo "==> Syncing upstream $DEVICE device foundation"
repo sync -c --no-clone-bundle --no-tags -j"$JOBS"

# The local GhostOS repository is not itself a repo project in the Android
# manifest. Stage only the Android product inputs into their canonical source
# paths; host-side policy models and scripts remain in the GhostOS repository.
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
# Build the currently selected GhostOS product, not the upstream LineageOS product.
m bacon

echo "==> GhostOS $DEVICE build command completed"
