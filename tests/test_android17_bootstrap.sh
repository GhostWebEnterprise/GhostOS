#!/usr/bin/env bash
# Offline regression for the LineageOS 24.0 / Android 17 larry bootstrap.
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
    [[ "$*" == *"https://github.com/LineageOS/android.git"* ]] || {
      echo "LineageOS manifest URL not selected" >&2
      exit 1
    }
    [[ "$*" == *"-b lineage-24.0"* ]] || {
      echo "LineageOS 24.0 / Android 17 branch not selected" >&2
      exit 1
    }
    [[ "$*" == *"--git-lfs"* ]] || {
      echo "Git LFS was not enabled for vendor sources" >&2
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
    test -s .repo/local_manifests/ghostos.xml || {
      echo "pinned GhostOS larry manifest was not applied before repo sync" >&2
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
cmp "$ROOT/manifests/ghostos-24.0.xml" "$ANDROID_DIR/.repo/local_manifests/ghostos.xml"
grep -Fq 'revision="c61f542b529d561e8b4e1d2dcf33027e437c3740"' "$ANDROID_DIR/.repo/local_manifests/ghostos.xml"
grep -Fq 'revision="16a0967e6b5a4a23aa35e1bf857e84c543733db7"' "$ANDROID_DIR/.repo/local_manifests/ghostos.xml"

# A mistaken target inside the repository must fail before repo init/sync.
INSIDE="$ROOT/.android17-bootstrap-test-target"
rm -rf "$INSIDE"
if bash "$ROOT/scripts/init-android17.sh" "$INSIDE" > "$WORK/inside.log" 2>&1; then
  echo "Android 17 bootstrap unexpectedly accepted an in-repository target" >&2
  rm -rf "$INSIDE"
  exit 1
fi
grep -q "must be outside the GhostOS repository" "$WORK/inside.log"
if [[ -e "$INSIDE" ]]; then
  echo "Rejected in-repository Android target was created before the safety check" >&2
  rm -rf "$INSIDE"
  exit 1
fi
rm -rf "$INSIDE"
test "$(wc -l < "$GHOSTOS_REPO_LOG")" -eq 2

echo "GhostOS LineageOS 24.0 / Android 17 bootstrap regression: pinned manifest and path safety passed"
