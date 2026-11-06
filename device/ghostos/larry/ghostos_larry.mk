# SPDX-License-Identifier: Apache-2.0
# Product composition based on LineageOS lineage_larry.mk (2023 LineageOS).
# Hardware policy belongs to the pinned upstream device/common/kernel trees.
$(call inherit-product, $(SRC_TARGET_DIR)/product/core_64_bit.mk)
TARGET_SUPPORTS_OMX_SERVICE := false
$(call inherit-product, $(SRC_TARGET_DIR)/product/full_base_telephony.mk)
$(call inherit-product, vendor/lineage/config/common_full_phone.mk)
$(call inherit-product, device/oneplus/larry/device.mk)
$(call inherit-product, vendor/ghostos/config/common.mk)
PRODUCT_NAME := ghostos_larry
PRODUCT_DEVICE := larry
PRODUCT_BRAND := oneplus
PRODUCT_MODEL := GhostOS on larry (experimental)
PRODUCT_MANUFACTURER := oneplus
