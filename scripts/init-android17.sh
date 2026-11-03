#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
GHOSTOS_ROOT="$(cd "$SCRIPT_DIR/.." && pwd -P)"
ANDROID_DIR="${1:-${GHOSTOS_ANDROID17_DIR:-$GHOSTOS_ROOT/../ghostos-android17-larry}}"
MANIFEST="$GHOSTOS_ROOT/manifests/ghostos-24.0.xml"

command -v repo >/dev/null || { echo "repo is required" >&2; exit 1; }
[[ -f "$MANIFEST" ]] || { echo "missing GhostOS Android 17 manifest: $MANIFEST" >&2; exit 1; }

mkdir -p "$ANDROID_DIR"
ANDROID_DIR="$(cd "$ANDROID_DIR" && pwd -P)"

# Never initialize a full Android source tree inside (or over) the GhostOS repo.
case "$ANDROID_DIR/" in
  "$GHOSTOS_ROOT/"*)
    echo "Android source directory must be outside the GhostOS repository: $ANDROID_DIR" >&2
    exit 1
    ;;
esac
case "$GHOSTOS_ROOT/" in
  "$ANDROID_DIR/"*)
    echo "Android source directory must not contain the GhostOS repository: $ANDROID_DIR" >&2
    exit 1
    ;;
esac

cd "$ANDROID_DIR"
# Reinitialize explicitly so an AOSP-only android17-release checkout cannot be
# mistaken for the LineageOS 24.0 device build foundation.
repo init -u https://github.com/LineageOS/android.git -b lineage-24.0 --git-lfs
mkdir -p .repo/local_manifests
cp "$MANIFEST" .repo/local_manifests/ghostos.xml
repo sync -c --no-clone-bundle --no-tags --optimized-fetch --prune -j"${JOBS:-8}"

echo "repo init/sync commands returned successfully in: $ANDROID_DIR"
echo "This confirms command completion only; it does not verify project worktrees or run a product build."
echo "The six device/common/kernel/hardware/vendor projects are pinned by manifests/ghostos-24.0.xml."
echo "Next gate: run lunch ghostos_larry-userdebug and the first full Soong/product build."
