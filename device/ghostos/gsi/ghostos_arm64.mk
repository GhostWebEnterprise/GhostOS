# SPDX-License-Identifier: Apache-2.0
# Keep generic_arm64 BoardConfig and all upstream Treble/VNDK behavior.
$(call inherit-product, $(SRC_TARGET_DIR)/product/aosp_arm64.mk)
# aosp_arm64 guards this include on TARGET_PRODUCT; a derived product must add it.
MODULE_BUILD_FROM_SOURCE := true
$(call inherit-product, $(SRC_TARGET_DIR)/product/gsi_release.mk)
$(call inherit-product, vendor/ghostos/config/common.mk)
PRODUCT_NAME := ghostos_arm64
PRODUCT_DEVICE := generic_arm64
PRODUCT_BRAND := GhostOS
PRODUCT_MODEL := GhostOS Universal ARM64
PRODUCT_MANUFACTURER := GhostWebEnterprise
