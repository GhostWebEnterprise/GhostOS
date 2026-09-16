#!/usr/bin/env bash
set -euo pipefail

ROOT="${1:-$PWD}
"
ROOT="${ROOT%$'\n'}"
cd "$ROOT"

command -v repo >/dev/null || { echo "repo is required" >&2; exit 1; }

# Android 17 is the active AOSP release branch.
repo init --partial-clone --no-use-superproject \
  -u https://android.googlesource.com/platform/manifest \
  -b android17-release

mkdir -p .repo/local_manifests
cp manifests/ghostos_larry.xml .repo/local_manifests/ghostos_larry.xml

repo sync -c --no-tags --optimized-fetch --prune -j"${JOBS:-8}"

echo "GhostOS source foundation synced."
echo "Next gate: adapt the larry device/common trees to Android 17, then run lunch/build and fix the first real error."
