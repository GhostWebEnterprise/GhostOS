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
  init) mkdir -p .repo; : > .repo/manifest.xml ;;
  sync)
    test -f .repo/manifest.xml || { echo 'repo sync before repo init' >&2; exit 1; }
    mkdir -p build
    printf 'breakfast() { :; }\nm() { :; }\n' > build/envsetup.sh
    ;;
  *) echo "unexpected repo command: $1" >&2; exit 1 ;;
esac
MOCK
chmod +x "$WORK/bin/repo"
export PATH="$WORK/bin:$PATH"
export GHOSTOS_REPO_LOG="$WORK/repo.log"

run_case() {
  local case_name="$1" expected_init="$2" checkout="$WORK/$1"
  : > "$GHOSTOS_REPO_LOG"
  ANDROID_DIR="$checkout" DEVICE=larry JOBS=2 bash "$ROOT/scripts/ghostos-build.sh" > "$WORK/$case_name.out"
  local count
  count="$(grep -c '^init ' "$GHOSTOS_REPO_LOG" || true)"
  test "$count" = "$expected_init" || {
    echo "$case_name: expected $expected_init repo init calls, found $count" >&2
    exit 1
  }
  grep -q '^sync ' "$GHOSTOS_REPO_LOG" || { echo "$case_name: repo sync not called" >&2; exit 1; }
  cmp "$ROOT/manifests/ghostos-23.2.xml" "$checkout/.repo/local_manifests/ghostos.xml"
}

run_case fresh 1
mkdir -p "$WORK/partial/.repo/local_manifests"
printf '<manifest name="preserve"/>\n' > "$WORK/partial/.repo/local_manifests/keep.xml"
run_case partial 1
printf '<manifest name="preserve"/>\n' | cmp - "$WORK/partial/.repo/local_manifests/keep.xml"
mkdir -p "$WORK/existing/.repo"
: > "$WORK/existing/.repo/manifest.xml"
run_case existing 0

# The builder must refuse both an Android source tree inside GhostOS and an
# Android target that contains the GhostOS checkout. No repo command may run.
INSIDE="$ROOT/.ghostos-build-bootstrap-test-target-$$-$RANDOM"
: > "$GHOSTOS_REPO_LOG"
if ANDROID_DIR="$INSIDE" DEVICE=larry JOBS=2 bash "$ROOT/scripts/ghostos-build.sh" > "$WORK/inside.out" 2>&1; then
  echo "Android source directory inside GhostOS was unexpectedly accepted" >&2
  exit 1
fi
grep -q "must be outside the GhostOS repository" "$WORK/inside.out"
test ! -s "$GHOSTOS_REPO_LOG"

: > "$GHOSTOS_REPO_LOG"
if ANDROID_DIR="$ROOT/.." DEVICE=larry JOBS=2 bash "$ROOT/scripts/ghostos-build.sh" > "$WORK/ancestor.out" 2>&1; then
  echo "Android source directory containing GhostOS was unexpectedly accepted" >&2
  exit 1
fi
grep -q "must not contain the GhostOS repository" "$WORK/ancestor.out"
test ! -s "$GHOSTOS_REPO_LOG"

echo "GhostOS bootstrap regression: 3 init cases and 2 source-path safety cases passed"
