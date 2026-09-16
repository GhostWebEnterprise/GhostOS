# GhostOS

A privacy-focused Android ROM project with a common GhostOS layer and per-device ports.

## Initial target

- **OnePlus Nord CE 3 Lite 5G** — codename `larry`
- SoC: Qualcomm SM6375 / Snapdragon 695 5G
- Architecture: ARM64
- A/B dynamic partitions

The initial `larry` port is based on the existing open device-tree ecosystem and is intended to be adapted to GhostOS rather than copied blindly. The current public LineageOS tree provides the device-specific foundation and depends on `android_device_oneplus_sm6375-common`.

## Architecture

```text
AOSP / Android
      |
GhostOS Security Layer
  - SELinux hardening
  - permission defaults
  - exploit/memory hardening
  - privacy defaults
      |
GhostOS Framework / SystemUI
      |
GhostOS Apps
  - Auditor
  - Updater
  - Browser/WebView
  - Privacy Center
      |
Common device interface
      |
+---------+---------+---------+
| larry   | oscaro  | benz    | ...
| device  | device  | device  |
| tree    | tree    | tree    |
+---------+---------+---------+
```

## Build pipeline

1. Manifest
2. Device tree
3. Kernel + vendor integration
4. GhostOS Security Layer
5. SELinux policy
6. AVB + release signing
7. Android build
8. Tests
9. Fix the first real build/CI error
10. Rebuild and verify
11. Bootable device image
12. Flashable images + OTA
13. Release artifacts

## Device support policy

Device ports are isolated from the common GhostOS layer. A device is not marked release-ready until its kernel, vendor interface, proprietary extraction, AVB configuration, SELinux policy, boot/recovery path, radio/camera/audio, and OTA path are tested on real hardware.

## Current status

**Project initialized. `larry` is the first port. More devices are tracked in `devices/README.md`.**

This repository contains build orchestration and GhostOS-specific code/configuration. Device-specific proprietary blobs are not redistributed here.
