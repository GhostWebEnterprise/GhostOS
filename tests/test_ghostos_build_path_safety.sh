#!/usr/bin/env bash
# Regression: reject unsafe Android source paths before mkdir or repo init.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
WORK="$(mktemp -d)"
INSIDE="$ROOT/.ghostos-android-source-path-test-$$"
cleanup() {
  rm -rf -- "$WORK"
  rm -rf -- "$INSIDE"
}
trap cleanup EXIT

mkdir -p "$WORK/bin"
cat > "$WORK/bin/repo" <<'MOCK'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >> "$GHOSTOS_REPO_LOG"
MOCK
chmod +x "$WORK/bin/repo"
export PATH="$WORK/bin:$PATH"
export GHOSTOS_REPO_LOG="$WORK/repo.log"
: > "$GHOSTOS_REPO_LOG"

# The default checkout location must be outside the repository.
grep -Fq 'ghostos-android23.2-larry' "$ROOT/scripts/ghostos-build.sh"
if grep -Fq 'ANDROID_DIR="${ANDROID_DIR:-$ROOT/android}"' "$ROOT/scripts/ghostos-build.sh"; then
  echo "Default Android source directory is inside GhostOS" >&2
  exit 1
fi

targets=("$INSIDE" "$ROOT/.." "/")
expected=(
  "must be outside the GhostOS repository"
  "must not contain the GhostOS repository"
  "must not be the filesystem root"
)
for index in "${!targets[@]}"; do
  target="${targets[$index]}"
  if ANDROID_DIR="$target" DEVICE=larry bash "$ROOT/scripts/ghostos-build.sh" > "$WORK/reject.out" 2>&1; then
    echo "Unsafe Android source directory was unexpectedly accepted: $target" >&2
    exit 1
  fi
  grep -Fq "${expected[$index]}" "$WORK/reject.out" || {
    echo "Unexpected rejection for Android source directory: $target" >&2
    cat "$WORK/reject.out" >&2
    exit 1
  }
  test ! -e "$INSIDE" || {
    echo "Rejected in-repository target was created before the safety check" >&2
    exit 1
  }
  test ! -s "$GHOSTOS_REPO_LOG" || {
    echo "repo was invoked for an unsafe path" >&2
    exit 1
  }
done

echo "GhostOS Android source-path safety regression: 3 unsafe paths rejected before filesystem mutation"
