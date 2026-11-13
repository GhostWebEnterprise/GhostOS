#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ANDROID_DIR="${1:-${ANDROID_DIR:-$ROOT/android}}"
DEVICE="${DEVICE:-larry}"

if [[ "$DEVICE" != "larry" ]]; then
  echo "ERROR: GhostOS app integration is currently defined only for larry (got: $DEVICE)" >&2
  exit 2
fi

DEVICE_MK="$ANDROID_DIR/device/oneplus/larry/device.mk"
[[ -f "$DEVICE_MK" ]] || { echo "ERROR: missing larry device makefile: $DEVICE_MK" >&2; exit 1; }
[[ -f "$ROOT/packages/apps/GhostOSSecurityCenter/Android.bp" ]] || { echo "ERROR: Security Center Soong module is missing" >&2; exit 1; }
[[ -f "$ROOT/packages/apps/GhostOSAuditor/Android.bp" ]] || { echo "ERROR: GhostOS Auditor Soong module is missing" >&2; exit 1; }
[[ -f "$ROOT/packages/apps/GhostOSPrivacyCenter/Android.bp" ]] || { echo "ERROR: GhostOS Privacy Center Soong module is missing" >&2; exit 1; }
[[ -f "$ROOT/vendor/ghostos/config.mk" ]] || { echo "ERROR: GhostOS product config is missing" >&2; exit 1; }

mkdir -p "$ANDROID_DIR/packages/apps/GhostOSSecurityCenter" "$ANDROID_DIR/packages/apps/GhostOSAuditor" "$ANDROID_DIR/packages/apps/GhostOSPrivacyCenter" "$ANDROID_DIR/vendor/ghostos"
cp -a "$ROOT/packages/apps/GhostOSSecurityCenter/." "$ANDROID_DIR/packages/apps/GhostOSSecurityCenter/"
cp -a "$ROOT/packages/apps/GhostOSAuditor/." "$ANDROID_DIR/packages/apps/GhostOSAuditor/"
cp -a "$ROOT/packages/apps/GhostOSPrivacyCenter/." "$ANDROID_DIR/packages/apps/GhostOSPrivacyCenter/"
cp "$ROOT/vendor/ghostos/config.mk" "$ANDROID_DIR/vendor/ghostos/config.mk"

if ! grep -Fq 'inherit-product, vendor/ghostos/config.mk' "$DEVICE_MK"; then
  cat >> "$DEVICE_MK" <<'EOF'

# GhostOS first-party system applications.
$(call inherit-product, vendor/ghostos/config.mk)
EOF
fi

grep -Fq 'inherit-product, vendor/ghostos/config.mk' "$DEVICE_MK"
grep -Fq 'GhostOSSecurityCenter' "$ANDROID_DIR/vendor/ghostos/config.mk"
grep -Fq 'GhostOSAuditor' "$ANDROID_DIR/vendor/ghostos/config.mk"
grep -Fq 'GhostOSPrivacyCenter' "$ANDROID_DIR/vendor/ghostos/config.mk"
echo "GhostOS Security Center and Auditor integrated into the larry product configuration."
