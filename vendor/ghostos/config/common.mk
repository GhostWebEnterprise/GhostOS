# SPDX-License-Identifier: Apache-2.0
PRODUCT_PACKAGES += GhostOSPrivacyCenter
PRODUCT_PACKAGE_OVERLAYS += vendor/ghostos/overlay
PRODUCT_SYSTEM_PROPERTIES += ro.ghostos.channel=experimental
# GhostWire is a normal presigned system app, never a platform-signed privileged app.
# The build driver requires an approved APK and stages this makefile atomically.
include vendor/ghostos/prebuilts/ghostwire.mk
