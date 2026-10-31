#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
GHOSTOS_ROOT="$(cd "$SCRIPT_DIR/.." && pwd -P)"
ANDROID_DIR="${1:-${GHOSTOS_ANDROID17_DIR:-$GHOSTOS_ROOT/../ghostos-android17-larry}}"

command -v repo >/dev/null || { echo "repo is required" >&2; exit 1; }

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

# This is an AOSP Android 17 source bootstrap only. The current larry device,
# common and kernel trees are pinned to LineageOS 23.2 (Android 16) in
# manifests/ghostos_larry.xml. Do not mix those unported trees into the
# android17-release manifest; adapt and validate an Android 17 device closure
# separately before adding any local device manifest.
repo init --partial-clone --no-use-superproject \
  -u https://android.googlesource.com/platform/manifest \
  -b android17-release

repo sync -c --no-tags --optimized-fetch --prune -j"${JOBS:-8}"

echo "GhostOS AOSP Android 17 source foundation synced in: $ANDROID_DIR"
echo "No larry device manifest was applied: the current LineageOS 23.2 device trees target Android 16."
echo "Next gate: port larry device/common/kernel trees to Android 17, validate vendor dependencies, then run lunch/build."
