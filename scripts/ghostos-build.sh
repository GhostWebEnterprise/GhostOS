#!/usr/bin/env bash
set -euo pipefail

DEVICE="${DEVICE:-larry}"
JOBS="${JOBS:-$(nproc)}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ANDROID_DIR="${ANDROID_DIR:-$ROOT/android}"
MANIFEST="$ROOT/manifests/ghostos-23.2.xml"
GHOSTOS_WITH_SIGNAL="${GHOSTOS_WITH_SIGNAL:-false}"

case "$GHOSTOS_WITH_SIGNAL" in
  true|false) ;;
  *) echo "ERROR: GHOSTOS_WITH_SIGNAL must be true or false" >&2; exit 1 ;;
esac

usage() {
  echo "Usage: DEVICE=larry|oscaro JOBS=<n> GHOSTOS_WITH_SIGNAL=false|true $0"
  echo
  echo "This bootstrap uses the verified LineageOS 23.2 (Android 16) foundation."
  echo "Android 17 migration is a separate bring-up gate; it is not fabricated here."
}

command -v repo >/dev/null || { echo "ERROR: repo is required" >&2; exit 1; }
[[ -f "$MANIFEST" ]] || { echo "ERROR: missing $MANIFEST" >&2; exit 1; }

mkdir -p "$ANDROID_DIR"
ANDROID_DIR="$(cd "$ANDROID_DIR" && pwd)"
if [[ ! -d "$ANDROID_DIR/.repo/repo" ]]; then
  echo "==> Initializing LineageOS 23.2 source tree"
  (cd "$ANDROID_DIR" && repo init -u https://github.com/LineageOS/android.git -b lineage-23.2 --git-lfs)
fi

mkdir -p "$ANDROID_DIR/.repo/local_manifests"
cp "$MANIFEST" "$ANDROID_DIR/.repo/local_manifests/ghostos.xml"

cd "$ANDROID_DIR"
echo "==> Syncing GhostOS device foundation"
repo sync -c --no-clone-bundle --no-tags -j"$JOBS"

echo "==> Configuring optional GhostWeb Signal ($GHOSTOS_WITH_SIGNAL)"
if [[ "$GHOSTOS_WITH_SIGNAL" == true ]]; then
  python3 "$ROOT/scripts/ghostos-signal.py" --android-root "$ANDROID_DIR" --device "$DEVICE"
else
  python3 "$ROOT/scripts/ghostos-signal.py" --android-root "$ANDROID_DIR" --device "$DEVICE" --disable
fi

echo "==> Preparing $DEVICE"
source build/envsetup.sh
breakfast "$DEVICE"

echo "==> Building $DEVICE"
# LineageOS provides the device build target; run the standard release target.
m bacon

echo "==> Build completed for $DEVICE"
