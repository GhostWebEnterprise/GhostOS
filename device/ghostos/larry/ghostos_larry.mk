# SPDX-License-Identifier: Apache-2.0
# Reuse the exact product composition and build-property overrides from the
# pinned LineageOS larry device tree. Override only GhostOS product identity.
$(call inherit-product, device/oneplus/larry/lineage_larry.mk)
$(call inherit-product, vendor/ghostos/config/common.mk)

# Device identifier. Keep these after all inherited product definitions.
PRODUCT_NAME := ghostos_larry
PRODUCT_DEVICE := larry
PRODUCT_BRAND := oneplus
PRODUCT_MODEL := GhostOS on larry (experimental)
PRODUCT_MANUFACTURER := oneplus
