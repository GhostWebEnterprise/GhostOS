#!/usr/bin/env bash
# Offline regression tests for the read-only Larry ADB smoke harness.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
SCRIPT="$ROOT/scripts/ghostos-device-smoke.sh"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$WORK/bin"

cat > "$WORK/bin/adb" <<'MOCK'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >> "$MOCK_ADB_LOG"
if [[ "${1:-}" == "devices" ]]; then
  printf 'List of devices attached\n'
  case "${MOCK_DEVICE_COUNT:-1}" in
    0) ;;
    1) printf 'test-serial device product:larry model:GhostOS\n' ;;
    2)
      printf 'test-serial device product:larry model:GhostOS\n'
      printf 'second-serial device product:larry model:GhostOS\n'
      ;;
    *) exit 90 ;;
  esac
  exit 0
fi
if [[ "${1:-}" == "-s" && "${2:-}" == "test-serial" && "${3:-}" == "shell" ]]; then
  shift 3
  case "${1:-}" in
    getprop)
      case "${2:-}" in
        ro.product.device) printf '%s\n' "${MOCK_DEVICE:-larry}" ;;
        ro.build.version.sdk) printf '%s\n' "${MOCK_SDK:-37}" ;;
        ro.build.fingerprint) printf '%s\n' "ghostos/larry/larry:17/TEST/1:userdebug/test-keys" ;;
        ro.build.version.security_patch) printf '%s\n' "2026-11-01" ;;
        ro.boot.verifiedbootstate) printf '%s\n' "orange" ;;
        ro.boot.flash.locked) printf '%s\n' "0" ;;
        ro.boot.vbmeta.device_state) printf '%s\n' "unlocked" ;;
        *) printf '\n' ;;
      esac
      ;;
    pm)
      [[ "${2:-}" == "path" ]] || exit 91
      package="${3:-}"
      [[ "$package" != "${MOCK_MISSING_PACKAGE:-}" ]] || exit 1
      printf 'package:/system/priv-app/%s/%s.apk\n' "$package" "$package"
      ;;
    *) exit 92 ;;
  esac
  exit 0
fi
printf 'unexpected adb command: %s\n' "$*" >&2
exit 93
MOCK
chmod +x "$WORK/bin/adb"

run_smoke() {
  local log="$1"
  shift
  env ADB="$WORK/bin/adb" MOCK_ADB_LOG="$log" "$@" bash "$SCRIPT"
}

expect_failure() {
  local expected="$1" output="$2" log="$3"
  shift 3
  if run_smoke "$log" "$@" > "$output" 2>&1; then
    echo "Expected smoke harness failure containing: $expected" >&2
    exit 1
  fi
  grep -Fq "$expected" "$output" || {
    cat "$output" >&2
    echo "Smoke harness failed for an unexpected reason; wanted: $expected" >&2
    exit 1
  }
}

SUCCESS_LOG="$WORK/success.log"
run_smoke "$SUCCESS_LOG" > "$WORK/success.out" 2>&1
grep -Fq 'DEVICE_SMOKE_STATUS: PASS' "$WORK/success.out"
grep -Fq 'Package present: org.ghostos.privacy' "$WORK/success.out"
if grep -Fq 'Package present: org.ghostos.securitycenter' "$WORK/success.out"; then
  echo "Default Larry bring-up check unexpectedly requires the later Security Center package" >&2
  exit 1
fi
grep -Fq 'not independent AVB/signature verification' "$WORK/success.out"

# The Security Center/Auditor packages are optional on the base GSI branch and
# can be required explicitly by a later stacked product branch.
ALL_APPS='org.ghostos.privacy,org.ghostos.securitycenter,org.ghostos.auditor'
run_smoke "$WORK/all-apps.log" GHOSTOS_REQUIRED_PACKAGES="$ALL_APPS" > "$WORK/all-apps.out" 2>&1
grep -Fq 'Package present: org.ghostos.privacy' "$WORK/all-apps.out"
grep -Fq 'Package present: org.ghostos.securitycenter' "$WORK/all-apps.out"
grep -Fq 'Package present: org.ghostos.auditor' "$WORK/all-apps.out"

# The harness may only issue read-only ADB queries.
for log in "$SUCCESS_LOG" "$WORK/all-apps.log"; do
  if grep -E '(^| )(install|push|reboot|flash|root|remount|sideload)( |$)' "$log"; then
    echo "Read-only device smoke harness issued a mutating ADB command" >&2
    exit 1
  fi
done

expect_failure 'expected exactly one online ADB device' "$WORK/no-device.out" "$WORK/no-device.log" MOCK_DEVICE_COUNT=0
expect_failure 'expected exactly one online ADB device' "$WORK/multi-device.out" "$WORK/multi-device.log" MOCK_DEVICE_COUNT=2
expect_failure 'expected device larry' "$WORK/wrong-device.out" "$WORK/wrong-device.log" MOCK_DEVICE=other
expect_failure 'expected Android API 37 or newer' "$WORK/old-api.out" "$WORK/old-api.log" MOCK_SDK=36
expect_failure 'required GhostOS package is not installed: org.ghostos.auditor' "$WORK/missing-package.out" "$WORK/missing-package.log" GHOSTOS_REQUIRED_PACKAGES=org.ghostos.privacy,org.ghostos.securitycenter,org.ghostos.auditor MOCK_MISSING_PACKAGE=org.ghostos.auditor

echo "GhostOS read-only Larry device smoke harness regression: PASS"
