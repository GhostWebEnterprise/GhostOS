<div align="center">

<img src="GhostOS_GhostWeb_logo.png" width="220" alt="GhostOS by GhostWeb logo" />

</div>

# GhostOS

[![Website](https://img.shields.io/badge/Website-ghostweb.bot.cd-0b57d0?style=plastic&logo=googlechrome&logoColor=white)](https://ghostweb.bot.cd)
[![GitHub](https://img.shields.io/badge/GitHub-GhostWebEnterprise-181717?style=plastic&logo=github&logoColor=white)](https://github.com/GhostWebEnterprise)
[![Status](https://img.shields.io/badge/Status-Under%20Development-orange?style=plastic)](https://ghostweb.bot.cd/ghostos.html)[![Need support?](https://img.shields.io/badge/Need%20support%3F-Contact%3A%20support--ghostweb%40proton.me-6D4AFF?style=plastic&logo=protonmail&logoColor=white)](mailto:support-ghostweb@proton.me)

A privacy-focused Android ROM project with a common GhostOS layer and per-device ports.


[![Project Hub](https://img.shields.io/badge/Project%20Hub-ghostweb.bot.cd-0B57D0?style=plastic&logo=googlechrome&logoColor=white)](https://ghostweb.bot.cd/ghostos.html)
**Official GhostWeb project hub:** https://ghostweb.bot.cd

## Initial target

- **OnePlus Nord CE 3 Lite 5G** — codename `larry`
- SoC: Qualcomm SM6375 / Snapdragon 695 5G
- Architecture: ARM64
- A/B dynamic partitions

The initial `larry` port is based on the existing open device-tree ecosystem and is intended to be adapted to GhostOS rather than copied blindly.

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

**Under development and not public-release ready. `larry` is the first port. More devices are tracked in `devices/README.md`.**

This repository contains build orchestration and GhostOS-specific code/configuration. Device-specific proprietary blobs are not redistributed here.
