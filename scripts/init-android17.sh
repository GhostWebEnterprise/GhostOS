#!/usr/bin/env bash
set -euo pipefail

ROOT="${1:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)}"
ROOT="$(cd "$ROOT" && pwd -P)"
ANDROID_DIR="${ANDROID_DIR:-${GHOSTOS_ANDROID17_DIR:-$ROOT/../ghostos-android17-larry}}"
MANIFEST="$ROOT/manifests/ghostos-24.0.xml"
LINEAGE_BRANCH="lineage-24.0"

command -v repo >/dev/null || { echo "repo is required" >&2; exit 1; }
[[ -f "$MANIFEST" ]] || { echo "missing Android 17 manifest: $MANIFEST" >&2; exit 1; }

ANDROID_DIR="$(python3 -c 'import os, sys; print(os.path.realpath(sys.argv[1]))' "$ANDROID_DIR")"
[[ "$ANDROID_DIR" != "/" ]] || { echo "Android source directory must not be filesystem root" >&2; exit 1; }
case "$ANDROID_DIR/" in
  "$ROOT/"*) echo "Android source directory must be outside GhostOS: $ANDROID_DIR" >&2; exit 1 ;;
esac
case "$ROOT/" in
  "$ANDROID_DIR/"*) echo "Android source directory must not contain GhostOS: $ANDROID_DIR" >&2; exit 1 ;;
esac

mkdir -p "$ANDROID_DIR"
ANDROID_DIR="$(cd "$ANDROID_DIR" && pwd -P)"
cd "$ANDROID_DIR"

# Android 17 device/vendor projects are aligned to the LineageOS 24.0 manifest,
# not mixed into the AOSP android17-release manifest with LineageOS 23.2 pins.
repo init -u https://github.com/LineageOS/android.git -b "$LINEAGE_BRANCH" --git-lfs
mkdir -p .repo/local_manifests
cp "$MANIFEST" .repo/local_manifests/ghostos.xml
repo sync -c --no-clone-bundle --no-tags --optimized-fetch --prune -j"${JOBS:-8}"

echo "LineageOS 24.0 repo init/sync completed in: $ANDROID_DIR"
echo "This is source-sync evidence only; lunch, Soong build, AVB signing and device boot are not verified."
