# Android 17 port

GhostOS targets Android 17 (API 37) on the OnePlus Nord CE 3 Lite 5G (`larry`).

## Current source configuration

The current bootstrap and product manifest select LineageOS 24.0:

- `scripts/ghostos-build.sh` initializes `https://github.com/LineageOS/android.git` on `lineage-24.0`.
- `manifests/ghostos-24.0.xml` pins the larry device tree, SM6375 common tree, Oplus hardware, kernel and matching vendor repositories to explicit commit IDs.
- The manifest records the vendor firmware reference as `CPH2467_15.0.0.2000(EX01)`. Verify the exact handset and firmware before any device operation.
- GhostOS product staging remains separate from GhostWeb VPN; no VpnHood client or VPN service is part of the GhostOS product configuration.

This is a configured Android 17 bring-up target, **not a verified bootable device foundation**. Current GitHub validation checks API 37 resource/source compilation, manifest pins, bootstrap script behavior and host-side policy tests. The bootstrap regression tests use a mock `repo` executable; they do not perform a real source sync or Soong build.

## Remaining migration and device gates

1. Run a real `repo sync` of the pinned LineageOS 24.0 manifest in a suitably provisioned Linux build environment.
2. Resolve actual sync, dependency, proprietary vendor and build-system errors from their logs.
3. Run `source build/envsetup.sh`, `lunch ghostos_larry-userdebug` and `m bacon` against that complete checkout.
4. Validate generated target-files, boot/vendor images, VINTF, SELinux policy, AVB and signing configuration.
5. Boot the resulting image on the exact larry device and test modem, camera, fingerprint, Wi-Fi, Bluetooth, sensors, encryption and recovery.
6. Run relevant CTS/VTS and device smoke tests before describing the port as bootable or release-ready.

No full Android 17 Soong product build, flashable release image, AVB release verification or physical larry boot is currently claimed. Upstream AVB/device support remains experimental until those gates pass.
