#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

cat > "$WORK/fake-adb" <<'FAKE'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >> "$FAKE_ADB_LOG"

if [[ "${1:-}" == "devices" ]]; then
  printf 'List of devices attached\n'
  if [[ "${FAKE_MULTIPLE:-0}" == "1" ]]; then
    printf 'larry-test-1 device product:larry model:OnePlus\n'
    printf 'larry-test-2 device product:larry model:OnePlus\n'
  else
    printf 'larry-test device product:larry model:OnePlus\n'
  fi
  exit 0
fi

if [[ "${1:-}" == "-s" ]]; then
  shift 2
  [[ "${1:-}" == "shell" ]] || exit 90
  shift
  case "${1:-}" in
    getprop)
      case "${2:-}" in
        ro.product.device) printf '%s\n' "${FAKE_DEVICE:-larry}" ;;
        ro.build.version.release) echo 16 ;;
        ro.build.version.sdk) echo 36 ;;
        ro.build.version.security_patch) echo 2026-11-01 ;;
        ro.build.fingerprint) echo ghostos/larry/test ;;
        ro.boot.verifiedbootstate) echo orange ;;
        ro.boot.flash.locked) echo 0 ;;
        ro.boot.vbmeta.device_state) echo unlocked ;;
        ro.boot.slot_suffix) echo _a ;;
        ro.boot.slot_successful) echo 1 ;;
        ro.boot.slot_unbootable) echo 0 ;;
        ro.boot.retry_count) echo 0 ;;
        ro.boot.veritymode) echo enforcing ;;
        ro.boot.vbmeta.digest) echo test-digest ;;
        *) echo ;;
      esac
      ;;
    getenforce) echo Enforcing ;;
    pm)
      [[ "${2:-}" == "path" ]] || exit 90
      printf 'package:/system_ext/app/%s/base.apk\n' "$3"
      ;;
    dumpsys)
      case "${2:-}" in
        package) echo 'GhostModeQuickSettingsTile service declaration' ;;
        connectivity) echo 'Default network capabilities: INTERNET' ;;
        device_policy) echo 'Device policy dump available' ;;
        update_engine) echo 'Update engine status: IDLE' ;;
        *) exit 90 ;;
      esac
      ;;
    *) exit 90 ;;
  esac
  exit 0
fi

exit 90
FAKE
chmod +x "$WORK/fake-adb"

export ADB="$WORK/fake-adb"
export DEVICE_SERIAL="larry-test"
export FAKE_ADB_LOG="$WORK/adb.log"
output="$("$ROOT/scripts/ghostos-device-smoke.sh")"
grep -Fq 'SMOKE_MODE=READ_ONLY' <<< "$output"
grep -Fxq 'DEVICE=larry' <<< "$output"
grep -Fxq 'ANDROID_API=36' <<< "$output"
grep -Fxq 'SELINUX_MODE=Enforcing' <<< "$output"
grep -Fxq 'PACKAGE_org.ghostos.securitycenter=INSTALLED_PATH_OBSERVED' <<< "$output"
grep -Fxq 'QS_TILE_COMPONENT=LISTED_IN_PACKAGE_DUMP_RUNTIME_NOT_PROVEN' <<< "$output"
grep -Fq 'DEFAULT_NETWORK_OR_PROVIDER_PROOF' <<< "$output" && exit 1 || true
grep -Fxq 'NO_DEVICE_SETTINGS_OR_POLICY_WERE_CHANGED' <<< "$output"

if grep -E '(^|[[:space:]])(flash|reboot|install|uninstall|setprop|settings|dpm)([[:space:]]|$)' "$FAKE_ADB_LOG"; then
  echo "unexpected mutating adb command was issued" >&2
  exit 1
fi

if FAKE_DEVICE="not-larry" "$ROOT/scripts/ghostos-device-smoke.sh" >/dev/null 2>&1; then
  echo "smoke harness accepted a non-larry device" >&2
  exit 1
fi

: > "$FAKE_ADB_LOG"
if FAKE_MULTIPLE=1 env -u DEVICE_SERIAL "$ROOT/scripts/ghostos-device-smoke.sh" >/dev/null 2>&1; then
  echo "smoke harness accepted ambiguous multiple devices" >&2
  exit 1
fi
if grep -q -- '-s ' "$FAKE_ADB_LOG"; then
  echo "smoke harness selected a device without an explicit serial" >&2
  exit 1
fi

echo "GhostOS read-only larry device smoke harness: PASS"
