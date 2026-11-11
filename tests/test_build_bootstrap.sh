#!/usr/bin/env bash
# Offline regression for repo init ordering and safe Android source paths.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
WORK="$(mktemp -d)"
INSIDE=""
cleanup() {
  rm -rf -- "$WORK"
  if [[ -n "${INSIDE:-}" ]]; then
    rm -rf -- "$INSIDE"
  fi
}
trap cleanup EXIT
mkdir -p "$WORK/bin"
cat > "$WORK/bin/repo" <<'MOCK'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >> "$GHOSTOS_REPO_LOG"
case "$1" in
  init)
    [[ "$*" == *"-b lineage-24.0"* ]] || { echo "LineageOS 24.0 branch not selected" >&2; exit 1; }
    [[ "$*" == *"--git-lfs"* ]] || { echo "Git LFS not enabled" >&2; exit 1; }
    mkdir -p .repo
    : > .repo/manifest.xml
    ;;
  sync)
    test -f .repo/manifest.xml || { echo 'repo sync before repo init' >&2; exit 1; }
    test -s .repo/local_manifests/ghostos.xml || { echo 'pinned larry manifest missing before repo sync' >&2; exit 1; }
    mkdir -p build
    cat > build/envsetup.sh <<'ENV'
lunch() { printf 'lunch %s\n' "$*" >> "$GHOSTOS_BUILD_LOG"; }
m() { printf 'm %s\n' "$*" >> "$GHOSTOS_BUILD_LOG"; }
ENV
    ;;
  *) echo "unexpected repo command: $1" >&2; exit 1 ;;
esac
MOCK
chmod +x "$WORK/bin/repo"
export PATH="$WORK/bin:$PATH"
export GHOSTOS_REPO_LOG="$WORK/repo.log"

# Keep GhostOS product identity while inheriting all pinned upstream larry
# device composition and firmware-derived build-property overrides.
grep -Fq 'inherit-product, device/oneplus/larry/lineage_larry.mk' \
  "$ROOT/device/ghostos/larry/ghostos_larry.mk" || {
  echo "GhostOS larry product must inherit the pinned upstream lineage_larry.mk" >&2
  exit 1
}

run_case() {
  local case_name="$1" checkout="$WORK/$1"
  : > "$GHOSTOS_REPO_LOG"
  export GHOSTOS_BUILD_LOG="$WORK/$case_name.build.log"
  : > "$GHOSTOS_BUILD_LOG"
  if ! ANDROID_DIR="$checkout" DEVICE=larry JOBS=2 bash "$ROOT/scripts/ghostos-build.sh" > "$WORK/$case_name.out" 2>&1; then
    cat "$WORK/$case_name.out" >&2
    exit 1
  fi
  test "$(grep -c '^init ' "$GHOSTOS_REPO_LOG" || true)" = "1"
  grep -q '^sync ' "$GHOSTOS_REPO_LOG" || { echo "$case_name: repo sync not called" >&2; exit 1; }
  cmp "$ROOT/manifests/ghostos-24.0.xml" "$checkout/.repo/local_manifests/ghostos.xml"
  cmp "$ROOT/device/ghostos/larry/ghostos_larry.mk" "$checkout/device/ghostos/larry/ghostos_larry.mk"
  cmp "$ROOT/vendor/ghostos/config/common.mk" "$checkout/vendor/ghostos/config/common.mk"
  cmp "$ROOT/packages/apps/GhostOSPrivacyCenter/Android.bp" "$checkout/packages/apps/GhostOSPrivacyCenter/Android.bp"
  grep -Fxq 'lunch ghostos_larry-userdebug' "$GHOSTOS_BUILD_LOG"
  grep -Fxq 'm bacon' "$GHOSTOS_BUILD_LOG"
}

run_case fresh
mkdir -p "$WORK/partial/.repo/local_manifests"
printf '<manifest name="preserve"/>\n' > "$WORK/partial/.repo/local_manifests/keep.xml"
run_case partial
printf '<manifest name="preserve"/>\n' | cmp - "$WORK/partial/.repo/local_manifests/keep.xml"
mkdir -p "$WORK/existing/.repo"
: > "$WORK/existing/.repo/manifest.xml"
run_case existing

# The builder must refuse both an Android source tree inside GhostOS and an
# Android target that contains the GhostOS checkout. No repo command may run.
INSIDE="$ROOT/.ghostos-build-bootstrap-test-target-$$-$RANDOM"
: > "$GHOSTOS_REPO_LOG"
if ANDROID_DIR="$INSIDE" DEVICE=larry JOBS=2 bash "$ROOT/scripts/ghostos-build.sh" > "$WORK/inside.out" 2>&1; then
  echo "Android source directory inside GhostOS was unexpectedly accepted" >&2
  exit 1
fi
grep -q "must be outside the GhostOS repository" "$WORK/inside.out"
if [[ -e "$INSIDE" ]]; then
  echo "Rejected in-repository Android target was created before the safety check" >&2
  exit 1
fi
rm -rf -- "$INSIDE"
test ! -s "$GHOSTOS_REPO_LOG"

: > "$GHOSTOS_REPO_LOG"
if ANDROID_DIR="$ROOT/.." DEVICE=larry JOBS=2 bash "$ROOT/scripts/ghostos-build.sh" > "$WORK/ancestor.out" 2>&1; then
  echo "Android source directory containing GhostOS was unexpectedly accepted" >&2
  exit 1
fi
grep -q "must not contain the GhostOS repository" "$WORK/ancestor.out"
test ! -s "$GHOSTOS_REPO_LOG"

# A filesystem-root target would otherwise pass both prefix checks; reject it
# before creating files or invoking repo init/sync.
: > "$GHOSTOS_REPO_LOG"
if ANDROID_DIR="/" DEVICE=larry JOBS=2 bash "$ROOT/scripts/ghostos-build.sh" > "$WORK/root.out" 2>&1; then
  echo "Filesystem-root Android source directory was unexpectedly accepted" >&2
  exit 1
fi
grep -q "must not be the filesystem root" "$WORK/root.out"
test ! -s "$GHOSTOS_REPO_LOG"

echo "GhostOS Android 17 bootstrap regression: 3 init cases and 3 source-path safety cases passed"
