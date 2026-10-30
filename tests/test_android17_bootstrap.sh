#!/usr/bin/env bash
# Offline regression for the AOSP-only Android 17 bootstrap.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$WORK/bin"

cat > "$WORK/bin/repo" <<'MOCK'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\t%s\n' "$PWD" "$*" >> "$GHOSTOS_REPO_LOG"
case "$1" in
  init)
    [[ "$*" == *"-b android17-release"* ]] || {
      echo "Android 17 branch not selected" >&2
      exit 1
    }
    mkdir -p .repo
    : > .repo/manifest.xml
    ;;
  sync)
    test -f .repo/manifest.xml || {
      echo "repo sync ran before repo init" >&2
      exit 1
    }
    test ! -e .repo/local_manifests/ghostos_larry.xml || {
      echo "Android 16 LineageOS larry manifest was mixed into Android 17" >&2
      exit 1
    }
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
ANDROID_DIR="$WORK/android17-source"

# Invoke from an unrelated working directory to prove paths are script-relative.
(
  cd "$WORK"
  bash "$ROOT/scripts/init-android17.sh" "$ANDROID_DIR"
)

test "$(wc -l < "$GHOSTOS_REPO_LOG")" -eq 2
grep -F "$ANDROID_DIR"$'\t'"init " "$GHOSTOS_REPO_LOG" >/dev/null
grep -F "$ANDROID_DIR"$'\t'"sync " "$GHOSTOS_REPO_LOG" >/dev/null
test -f "$ANDROID_DIR/.repo/manifest.xml"
test ! -e "$ANDROID_DIR/.repo/local_manifests/ghostos_larry.xml"

# A mistaken target inside the repository must fail before repo init/sync.
INSIDE="$ROOT/.android17-bootstrap-test-target"
rm -rf "$INSIDE"
if bash "$ROOT/scripts/init-android17.sh" "$INSIDE" > "$WORK/inside.log" 2>&1; then
  echo "Android 17 bootstrap unexpectedly accepted an in-repository target" >&2
  rm -rf "$INSIDE"
  exit 1
fi
grep -q "must be outside the GhostOS repository" "$WORK/inside.log"
rm -rf "$INSIDE"
test "$(wc -l < "$GHOSTOS_REPO_LOG")" -eq 2

echo "GhostOS Android 17 bootstrap regression: AOSP-only sync and path safety passed"
