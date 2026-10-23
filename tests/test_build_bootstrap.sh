#!/usr/bin/env bash
# Offline regression for repo init ordering. No Android checkout or build required.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
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
printf 'GhostOS bootstrap regression: 3 cases passed\n'
