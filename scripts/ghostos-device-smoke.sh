#!/usr/bin/env bash
set -euo pipefail

ADB="${ADB:-adb}"
DEVICE_SERIAL="${DEVICE_SERIAL:-}"

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

command -v "$ADB" >/dev/null 2>&1 || fail "adb executable was not found"

device_list="$("$ADB" devices 2>/dev/null)" || fail "could not query adb devices"
mapfile -t online_devices < <(
  awk 'NR > 1 && $2 == "device" { print $1 }' <<< "$device_list"
)

if [[ -z "$DEVICE_SERIAL" ]]; then
  [[ "${#online_devices[@]}" -eq 1 ]] || fail \
    "connect exactly one online device or set DEVICE_SERIAL explicitly"
  DEVICE_SERIAL="${online_devices[0]}"
else
  found=false
  for serial in "${online_devices[@]}"; do
    if [[ "$serial" == "$DEVICE_SERIAL" ]]; then
      found=true
      break
    fi
  done
  [[ "$found" == true ]] || fail "DEVICE_SERIAL is not an online adb device"
fi

adb_shell() {
  "$ADB" -s "$DEVICE_SERIAL" shell "$@"
}

read_property() {
  local key="$1"
  local label="$2"
  local value
  value="$(adb_shell getprop "$key" 2>/dev/null | tr -d '\r' || true)"
  [[ -n "$value" ]] || value="unknown"
  printf '%s=%s\n' "$label" "$value"
}

device="$(adb_shell getprop ro.product.device 2>/dev/null | tr -d '\r' || true)"
[[ "$device" == "larry" ]] || fail \
  "refusing non-larry device (ro.product.device=${device:-unknown})"

echo "SMOKE_MODE=READ_ONLY"
echo "DEVICE=larry"
read_property ro.build.version.release ANDROID_RELEASE
read_property ro.build.version.sdk ANDROID_API
read_property ro.build.version.security_patch SECURITY_PATCH
read_property ro.build.fingerprint BUILD_FINGERPRINT
read_property ro.boot.verifiedbootstate VERIFIED_BOOT_STATE
read_property ro.boot.flash.locked BOOTLOADER_LOCKED
read_property ro.boot.vbmeta.device_state VBMETA_DEVICE_STATE
read_property ro.boot.slot_suffix BOOT_SLOT
read_property ro.boot.slot_successful BOOT_SLOT_SUCCESSFUL
read_property ro.boot.slot_unbootable BOOT_SLOT_UNBOOTABLE
read_property ro.boot.retry_count BOOT_RETRY_COUNT
read_property ro.boot.veritymode DM_VERITY_MODE

selinux_mode="$(adb_shell getenforce 2>/dev/null | tr -d '\r' || true)"
printf 'SELINUX_MODE=%s\n' "${selinux_mode:-unknown}"

vbmeta_digest="$(adb_shell getprop ro.boot.vbmeta.digest 2>/dev/null | tr -d '\r' || true)"
if [[ -n "$vbmeta_digest" ]]; then
  echo "VBMETA_DIGEST=PRESENT_PLATFORM_REPORTED_ONLY"
else
  echo "VBMETA_DIGEST=UNKNOWN"
fi

for package in \
  org.ghostos.securitycenter \
  org.ghostos.auditor \
  org.ghostos.privacy; do
  package_path="$(adb_shell pm path "$package" 2>/dev/null | tr -d '\r' || true)"
  if [[ -n "$package_path" ]]; then
    printf 'PACKAGE_%s=INSTALLED_PATH_OBSERVED\n' "$package"
  else
    printf 'PACKAGE_%s=NOT_OBSERVED\n' "$package"
  fi
done

privacy_dump="$(adb_shell dumpsys package org.ghostos.privacy 2>/dev/null || true)"
if grep -q 'GhostModeQuickSettingsTile' <<< "$privacy_dump"; then
  echo "QS_TILE_COMPONENT=LISTED_IN_PACKAGE_DUMP_RUNTIME_NOT_PROVEN"
else
  echo "QS_TILE_COMPONENT=NOT_OBSERVED_OR_DUMP_UNAVAILABLE"
fi

connectivity_dump="$(adb_shell dumpsys connectivity 2>/dev/null || true)"
if grep -q 'TRANSPORT_VPN' <<< "$connectivity_dump"; then
  echo "VPN_TRANSPORT_DUMP=TOKEN_OBSERVED_DEFAULT_NETWORK_ATTRIBUTION_NOT_PROVEN"
else
  echo "VPN_TRANSPORT_DUMP=NOT_OBSERVED_OR_FORMAT_UNSUPPORTED"
fi

device_policy_dump="$(adb_shell dumpsys device_policy 2>/dev/null || true)"
if [[ -n "$device_policy_dump" ]]; then
  echo "DEVICE_POLICY_DUMP=AVAILABLE_REQUIRES_MANUAL_REVIEW"
else
  echo "DEVICE_POLICY_DUMP=UNAVAILABLE"
fi

update_engine_dump="$(adb_shell dumpsys update_engine 2>/dev/null || true)"
if [[ -n "$update_engine_dump" ]]; then
  echo "UPDATE_ENGINE_DUMP=AVAILABLE_REQUIRES_MANUAL_REVIEW"
else
  echo "UPDATE_ENGINE_DUMP=UNAVAILABLE"
fi

echo "LIMITATION=READ_ONLY_OBSERVATIONS_ONLY_NO_AVB_SIGNATURE_OR_BOOT_SUCCESS_CLAIM"
echo "LIMITATION=VPN_DUMPSYS_TOKEN_IS_NOT_DEFAULT_NETWORK_OR_PROVIDER_PROOF"
echo "LIMITATION=PACKAGE_AND_TILE_DECLARATIONS_DO_NOT_PROVE_RUNTIME_FUNCTION"
echo "NO_DEVICE_SETTINGS_OR_POLICY_WERE_CHANGED"
