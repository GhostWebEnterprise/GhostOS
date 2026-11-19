#!/usr/bin/env bash
# Read-only smoke check for a booted GhostOS larry device.
# This script never installs, flashes, reboots, remounts, or changes device state.
set -euo pipefail

ADB_BIN="${ADB:-adb}"
EXPECTED_DEVICE="${GHOSTOS_EXPECTED_DEVICE:-larry}"
MIN_SDK="${GHOSTOS_MIN_SDK:-37}"

fail() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

command -v "$ADB_BIN" >/dev/null 2>&1 || fail "adb executable not found: $ADB_BIN"
[[ "$EXPECTED_DEVICE" =~ ^[a-zA-Z0-9_-]+$ ]] || fail "invalid expected device codename"
[[ "$MIN_SDK" =~ ^[0-9]+$ ]] || fail "GHOSTOS_MIN_SDK must be a non-negative integer"

devices_output="$("$ADB_BIN" devices)" || fail "adb devices failed"
mapfile -t online_serials < <(awk 'NR > 1 && $2 == "device" { print $1 }' <<< "$devices_output")
(( ${#online_serials[@]} == 1 )) || fail "expected exactly one online ADB device; found ${#online_serials[@]}"
serial="${online_serials[0]}"

getprop() {
  local name="$1" value
  value="$("$ADB_BIN" -s "$serial" shell getprop "$name")" || fail "could not read Android property: $name"
  printf '%s' "$value" | tr -d '\r'
}

actual_device="$(getprop ro.product.device)"
[[ "$actual_device" == "$EXPECTED_DEVICE" ]] || fail "expected device $EXPECTED_DEVICE; observed ${actual_device:-<empty>}"

sdk="$(getprop ro.build.version.sdk)"
[[ "$sdk" =~ ^[0-9]+$ ]] || fail "Android SDK level is missing or invalid: ${sdk:-<empty>}"
(( sdk >= MIN_SDK )) || fail "expected Android API $MIN_SDK or newer; observed API $sdk"

REQUIRED_PACKAGES_CSV="${GHOSTOS_REQUIRED_PACKAGES:-org.ghostos.privacy}"
[[ -n "$REQUIRED_PACKAGES_CSV" ]] || fail "GHOSTOS_REQUIRED_PACKAGES must not be empty"
IFS=',' read -r -a required_packages <<< "$REQUIRED_PACKAGES_CSV"
for package in "${required_packages[@]}"; do
  [[ "$package" =~ ^[A-Za-z0-9_]+(\.[A-Za-z0-9_]+)+$ ]] || fail "invalid Android package name: $package"
  if ! package_path="$("$ADB_BIN" -s "$serial" shell pm path "$package" 2>/dev/null)"; then
    fail "required GhostOS package is not installed: $package"
  fi
  package_path="$(printf '%s' "$package_path" | tr -d '\r')"
  [[ "$package_path" == package:* ]] || fail "required GhostOS package is not installed: $package"
  printf 'Package present: %s\n' "$package"
done

printf 'DEVICE_SMOKE_STATUS: PASS\n'
printf 'Observed device codename: %s\n' "$actual_device"
printf 'Observed Android API level: %s\n' "$sdk"
for property in \
  ro.build.fingerprint \
  ro.build.version.security_patch \
  ro.boot.verifiedbootstate \
  ro.boot.flash.locked \
  ro.boot.vbmeta.device_state; do
  value="$(getprop "$property")"
  printf 'Observed %s=%s\n' "$property" "${value:-<empty>}"
done
printf '%s\n' "Scope: read-only identity and package-presence checks only. Observed boot properties are not independent AVB/signature verification; this does not test hardware, radio, camera, or boot reliability."
