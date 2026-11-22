#!/usr/bin/env bash
# Offline regression for the Android 17 larry build host preflight and source staging.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$WORK/bin"

cat > "$WORK/bin/repo" <<'MOCK'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >> "$GHOSTOS_REPO_LOG"
case "$1" in
  init)
    mkdir -p .repo
    : > .repo/manifest.xml
    ;;
  sync)
    if [[ "${GHOSTOS_MOCK_REPO_SYNC_SUCCESS:-0}" == "1" ]]; then
      mkdir -p build
      cat > build/envsetup.sh <<'ENV'
lunch() { printf 'lunch %s\n' "$*" >> "$GHOSTOS_BUILD_LOG"; }
m() { printf 'm %s\n' "$*" >> "$GHOSTOS_BUILD_LOG"; }
ENV
      exit 0
    fi
    exit 23
    ;;
  *)
    echo "unexpected repo command: $*" >&2
    exit 1
    ;;
esac
MOCK
chmod +x "$WORK/bin/repo"
export PATH="$WORK/bin:$PATH"
export GHOSTOS_REPO_LOG="$WORK/repo.log"
: > "$GHOSTOS_REPO_LOG"

RAM_TARGET="$WORK/ram-too-small"
if ANDROID_DIR="$RAM_TARGET" DEVICE=larry GHOSTOS_MIN_RAM_GIB=999999 GHOSTOS_MIN_DISK_GB=1 \
    bash "$ROOT/scripts/ghostos-build.sh" > "$WORK/ram.log" 2>&1; then
  echo "Android 17 build unexpectedly accepted insufficient RAM" >&2
  exit 1
fi
grep -Fq "requires at least 999999 GiB RAM" "$WORK/ram.log"
test ! -e "$RAM_TARGET"
test ! -s "$GHOSTOS_REPO_LOG"

DISK_TARGET="$WORK/disk-too-small"
if ANDROID_DIR="$DISK_TARGET" DEVICE=larry GHOSTOS_MIN_RAM_GIB=1 GHOSTOS_MIN_DISK_GB=999999999 \
    bash "$ROOT/scripts/ghostos-build.sh" > "$WORK/disk.log" 2>&1; then
  echo "Android 17 build unexpectedly accepted insufficient disk" >&2
  exit 1
fi
grep -Fq "requires at least 999999999 GB free disk" "$WORK/disk.log"
test ! -e "$DISK_TARGET"
test ! -s "$GHOSTOS_REPO_LOG"

PASS_TARGET="$WORK/preflight-passed"
set +e
ANDROID_DIR="$PASS_TARGET" DEVICE=larry JOBS=1 GHOSTOS_MIN_RAM_GIB=1 GHOSTOS_MIN_DISK_GB=1 \
  bash "$ROOT/scripts/ghostos-build.sh" > "$WORK/pass.log" 2>&1
status=$?
set -e
[[ "$status" -eq 23 ]] || {
  echo "Expected fake repo sync exit 23 after preflight, got $status" >&2
  cat "$WORK/pass.log" >&2
  exit 1
}
grep -Fq "Host preflight passed:" "$WORK/pass.log"
grep -Fq "Initializing LineageOS lineage-24.0 source tree" "$WORK/pass.log"
grep -Fq "Syncing pinned GhostOS larry Android 17 foundation" "$WORK/pass.log"
grep -Fq "init " "$GHOSTOS_REPO_LOG"
grep -Fq "sync " "$GHOSTOS_REPO_LOG"

# Exercise the staging phase with a fake repo sync and fake lunch/m commands.
# This verifies the product inputs copied by the real script without claiming
# that a LineageOS source sync or Soong build has run.
STAGE_TARGET="$WORK/staging-passed"
export GHOSTOS_BUILD_LOG="$WORK/build.log"
: > "$GHOSTOS_BUILD_LOG"
ANDROID_DIR="$STAGE_TARGET" DEVICE=larry JOBS=1 GHOSTOS_MIN_RAM_GIB=1 GHOSTOS_MIN_DISK_GB=1 \
  GHOSTOS_MOCK_REPO_SYNC_SUCCESS=1 \
  bash "$ROOT/scripts/ghostos-build.sh" > "$WORK/staging.log" 2>&1

for app in GhostOSPrivacyCenter GhostOSSecurityCenter GhostOSAuditor; do
  test -f "$STAGE_TARGET/packages/apps/$app/Android.bp" || {
    echo "Android 17 staging omitted $app/Android.bp" >&2
    exit 1
  }
done
test -f "$STAGE_TARGET/packages/apps/GhostOSAuditor/AndroidManifest.xml"
test -f "$STAGE_TARGET/vendor/ghostos/config/common.mk"
test -f "$STAGE_TARGET/vendor/ghostos/prebuilts/ghostwire.mk"
grep -Fq "lunch ghostos_larry-userdebug" "$GHOSTOS_BUILD_LOG"
grep -Fq "m bacon" "$GHOSTOS_BUILD_LOG"
grep -Fq "GhostOS larry Android 17 build command completed" "$WORK/staging.log"

echo "GhostOS Android 17 build-host preflight and three-app staging regressions passed"
