#!/usr/bin/env bash
# Regression: reject unsafe Android source paths before mkdir or repo init.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
WORK="$(mktemp -d)"
cleanup() {
  rm -rf -- "$WORK"
  rm -f -- "$ROOT"/.ghostos-android-source-path-test-*
}
trap cleanup EXIT

mkdir -p "$WORK/bin"
cat > "$WORK/bin/repo" <<'MOCK'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\\n' "$*" >> "$GHOSTOS_REPO_LOG"
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

INSIDE="$ROOT/.ghostos-android-source-path-test-$$"
for target in "$INSIDE" "$ROOT/.." "/"; do
  if ANDROID_DIR="$target" DEVICE=larry bash "$ROOT/scripts/ghostos-build.sh" > "$WORK/reject.out" 2>&1; then
    echo "Unsafe Android source directory was unexpectedly accepted: $target" >&2
    exit 1
  fi
done

grep -q "must be outside the GhostOS repository" "$WORK/reject.out" || {
  echo "In-repository path rejection was not reported" >&2
  exit 1
}
test ! -e "$INSIDE" || {
  echo "Rejected in-repository target was created before the safety check" >&2
  exit 1
}
grep -q "must not contain the GhostOS repository" "$WORK/reject.out" || {
  echo "Ancestor path rejection was not reported" >&2
  exit 1
}
grep -q "must not be the filesystem root" "$WORK/reject.out" || {
  echo "Filesystem-root path rejection was not reported" >&2
  exit 1
}
test ! -s "$GHOSTOS_REPO_LOG" || {
  echo "repo was invoked for an unsafe path" >&2
  exit 1
}

echo "GhostOS Android source-path safety regression: 3 unsafe paths rejected before filesystem mutation"
