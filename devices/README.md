# GhostOS device ports

GhostOS uses a common Android/GhostOS layer with small, independently maintained device ports.

## Port matrix

| Device | Codename | SoC | Foundation | Status |
|---|---|---|---|---|
| OnePlus Nord CE 3 Lite 5G | `larry` | SM6375 / Snapdragon 695 | LineageOS 24.0 / Android 17 (pinned manifest) | **Primary port** |
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

`larry` is the first hardware target. The active Android 17 bring-up uses the pinned LineageOS 24.0 manifest at `manifests/ghostos-24.0.xml`, including the larry device tree, SM6375 common tree, Oplus hardware, kernel and vendor repositories. The separate 23.2 manifest remains in the repository but is not selected by the current Android 17 build script.

The port remains experimental: no complete Soong/product build, flashable image or physical boot is verified. After a device is booted, `scripts/ghostos-device-smoke.sh` performs read-only checks for the larry codename, API level and required GhostOS packages. It only reports boot properties as observations; it does not verify AVB signatures or hardware functionality.

GhostOS should consume those upstream foundations through manifests and apply only GhostOS-specific changes in this project or dedicated GhostOS device repositories.
