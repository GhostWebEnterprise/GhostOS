# GhostOS device ports

GhostOS uses a common Android/GhostOS layer with small, independently maintained device ports.

## Port matrix

| Device | Codename | SoC | Foundation | Status |
|---|---|---|---|---|
| OnePlus Nord CE 3 Lite 5G | `larry` | SM6375 / Snapdragon 695 | LineageOS 23.2 device tree + SM6375 common | **Primary port** |
| OnePlus Nord CE 2 Lite 5G | `oscaro` | SM6375 / Snapdragon 695 | LineageOS device tree/common | Candidate |
| OnePlus Nord CE 4 5G | `benz` | Snapdragon 7 Gen 3 family | Existing community/Lineage ecosystem | Candidate |
| OnePlus Nord CE 5G | `ebba` | SM7225 | Existing community device tree | Candidate |

Candidate does not mean bootable. Each candidate must pass the same hardware validation gates before release.

## Per-device requirements

Each port must provide:

- Android product definition
- BoardConfig and partition layout
- device tree and init configuration
- kernel source/configuration or validated kernel integration
- vendor/proprietary extraction instructions
- VINTF manifests and compatibility matrices
- SELinux policy
- AVB/vbmeta configuration
- recovery/vendor_boot integration
- A/B and dynamic-partition support where applicable
- OTA metadata/update configuration
- hardware tests: display, touch, camera, audio, Bluetooth, Wi-Fi, NFC, GNSS, modem/VoLTE/5G, USB, sensors, fingerprint, charging and suspend/resume

## larry first

`larry` is the first hardware target. Its public LineageOS 23.2 device tree exists and declares a dependency on `android_device_oneplus_sm6375-common`. The kernel family is `android_kernel_oneplus_sm6375`.

GhostOS should consume those upstream foundations through manifests and apply only GhostOS-specific changes in this project or dedicated GhostOS device repositories.
