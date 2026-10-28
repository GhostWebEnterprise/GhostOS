#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
GHOSTOS_ROOT="$(cd "$SCRIPT_DIR/.." && pwd -P)"
ANDROID_DIR="${1:-${GHOSTOS_ANDROID17_DIR:-$GHOSTOS_ROOT/../ghostos-android17-larry}}"

command -v repo >/dev/null || { echo "repo is required" >&2; exit 1; }
test -f "$GHOSTOS_ROOT/manifests/ghostos_larry.xml" || {
  echo "Missing GhostOS manifest: $GHOSTOS_ROOT/manifests/ghostos_larry.xml" >&2
  exit 1
}

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

# Android 17 source and the device manifest are kept in separate directories.
repo init --partial-clone --no-use-superproject \
  -u https://android.googlesource.com/platform/manifest \
  -b android17-release

mkdir -p .repo/local_manifests
cp "$GHOSTOS_ROOT/manifests/ghostos_larry.xml" .repo/local_manifests/ghostos_larry.xml

repo sync -c --no-tags --optimized-fetch --prune -j"${JOBS:-8}"

echo "GhostOS Android 17 source foundation synced in: $ANDROID_DIR"
echo "Next gate: adapt larry device/common trees to Android 17, then run lunch/build and fix the first real error."
