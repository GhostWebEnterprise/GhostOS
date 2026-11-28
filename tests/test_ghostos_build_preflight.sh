#!/usr/bin/env bash
# Offline regression for GhostOS Android build RAM/disk preflight.
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

# Insufficient RAM must fail before creating the source directory or invoking repo.
RAM_TARGET="$WORK/ram-too-small"
if ANDROID_DIR="$RAM_TARGET" DEVICE=larry GHOSTOS_MIN_RAM_GIB=999999 GHOSTOS_MIN_DISK_GB=1 \
    bash "$ROOT/scripts/ghostos-build.sh" > "$WORK/ram.log" 2>&1; then
  echo "Build preflight unexpectedly accepted insufficient RAM" >&2
  exit 1
fi
grep -Fq "requires at least 999999 GiB RAM" "$WORK/ram.log"
test ! -e "$RAM_TARGET"
test ! -s "$GHOSTOS_REPO_LOG"

# Insufficient disk must fail before creating the source directory or invoking repo.
DISK_TARGET="$WORK/disk-too-small"
if ANDROID_DIR="$DISK_TARGET" DEVICE=larry GHOSTOS_MIN_RAM_GIB=1 GHOSTOS_MIN_DISK_GB=999999999 \
    bash "$ROOT/scripts/ghostos-build.sh" > "$WORK/disk.log" 2>&1; then
  echo "Build preflight unexpectedly accepted insufficient disk" >&2
  exit 1
fi
grep -Fq "requires at least 999999999 GB free disk" "$WORK/disk.log"
test ! -e "$DISK_TARGET"
test ! -s "$GHOSTOS_REPO_LOG"

# Regression: df -Pk reports KiB. 399 GB must not pass a 400 GB threshold.
cat > "$WORK/bin/df" <<'MOCK_DF'
#!/usr/bin/env bash
printf 'Filesystem 1024-blocks Used Available Capacity Mounted on\n'
printf '/dev/mock 500000000 100000000 399000000 20%% /\n'
MOCK_DF
chmod +x "$WORK/bin/df"
DISK_399_TARGET="$WORK/disk-399gb"
if ANDROID_DIR="$DISK_399_TARGET" DEVICE=larry GHOSTOS_MIN_RAM_GIB=1 GHOSTOS_MIN_DISK_GB=400 \
    bash "$ROOT/scripts/ghostos-build.sh" > "$WORK/disk-399.log" 2>&1; then
  echo "Build preflight incorrectly accepted 399 GB for a 400 GB minimum" >&2
  exit 1
fi
grep -Fq "requires at least 400 GB free disk" "$WORK/disk-399.log"
test ! -e "$DISK_399_TARGET"
test ! -s "$GHOSTOS_REPO_LOG"
rm "$WORK/bin/df"

# With explicit low test thresholds, preflight passes and repo init/sync is reached.
# The fake sync exits 23 intentionally so no source tree is downloaded.
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
grep -Fq "Initializing LineageOS 23.2 source tree" "$WORK/pass.log"
grep -Fq "Syncing GhostOS device foundation" "$WORK/pass.log"
grep -Fq "init " "$GHOSTOS_REPO_LOG"
grep -Fq "sync " "$GHOSTOS_REPO_LOG"

echo "GhostOS Linux build-host preflight regression: RAM, disk and preflight pass paths verified"
