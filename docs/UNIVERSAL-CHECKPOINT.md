# GhostOS Universal — paused development checkpoint

Saved at the user's request on 2026-10-15. **No Android image has been compiled,
booted, or verified. This is an incomplete development checkpoint, not a release.**

## Requested deliverables

1. Generic Android Treble ARM64 GSI.
2. Separate OnePlus Nord CE 3 Lite 5G (`larry`) hardware target for investigation
   on devices currently running OxygenOS 15.
3. Dark GrapheneOS-inspired appearance and Android privacy settings access.
4. Approved GhostWire APK bundled with its original signature and protocols.
5. Android compilation CI and release verification, with actual build evidence.

## Saved implementation

- `device/ghostos/gsi`: derived `ghostos_arm64` product; explicitly inherits
  `gsi_release.mk` because upstream gates it on the original product name.
- `device/ghostos/larry`: `ghostos_larry` product composition using the existing
  LineageOS device/common/kernel foundation; it is not a complete pure-AOSP port.
- `vendor/ghostos`: common product, default dark-mode resource overlay, and an
  initial conditional GhostWire inclusion hook. No APK is present.
- `packages/apps/GhostOSPrivacyCenter`: Android Java app, Soong definition,
  charcoal theme, and links to existing Android privacy/security settings.
  It requests no permissions and does not implement new enforcement controls.

The build driver now stages `device/ghostos`, `vendor/ghostos`, and
`packages/apps/GhostOSPrivacyCenter` into a separate synchronized Android
checkout and selects `ghostos_larry-userdebug` rather than the upstream
LineageOS product. Mock bootstrap CI verifies those copy paths and the selected
product. This is staging/selection evidence only: it has not passed a real
`repo sync`, Soong, Android resource compilation, or device runtime test. The
Privacy Center and Quick Settings Java sources have now passed `javac` against
the Android API 36 platform in GitHub Actions; this does not replace a Soong
module/resource build. The existing upstream manifests remain unchanged.

## CI verification update — 2026-10-30

The first API-36 CI attempt failed before compilation because
`android-actions/setup-android@v3` installs the removed SDK package `tools` by
default. The workflow now explicitly installs only `platform-tools`, then
installs `platforms;android-36` in its dedicated step. Both the push and PR
runs passed on commit `a3feeaa063a09db212c218dddf8606d171ad06dd`:

- [PR validation run 41028709151](https://github.com/GhostWebEnterprise/GhostOS/actions/runs/41028709151)
- [Push validation run 41028702731](https://github.com/GhostWebEnterprise/GhostOS/actions/runs/41028702731)

The successful jobs include Android SDK setup, API 36 platform installation,
Java compilation of `PrivacyActivity.java` and `GhostModeQuickSettingsTile.java`,
XML validation, upstream larry foundation checks, bootstrap tests, and all
GhostMode manager/rollback/adapter/Privacy Center/SystemUI/Quick Settings tests.
No AOSP `repo sync`, Soong product build, image build, flash, or device boot is
claimed.

## Repository evidence inspected

GhostOS base commit: `35303013e753886a3d7269eb233c0471f26cf727`.
GhostWire inspected develop commit: `c54f8eca0d0f7359fa5e28a75a80fb980cad59bd`.
GhostWire README requires JDK 21, Android SDK/NDK and recursive submodules.
Do not rename Wire/Kalium namespaces, backend endpoints, URI schemes, crypto,
protocol identifiers, or internal package names.

Proposed GSI bring-up baseline: AOSP `android-15.0.0_r36`. Its
`target/product/aosp_arm64.mk` and `gsi_release.mk` were inspected in the
[AOSP mirror](https://github.com/aosp-mirror/platform_build/tree/android-15.0.0_r36/target/product).
This is a reproducible initial baseline, not a claim of current security support.
Select an appropriately patched baseline before considering any release.

Android 15 larry foundation branches exist. Observed `lineage-22.2` heads:

| Repository under LineageOS | Commit |
| --- | --- |
| android_device_oneplus_larry | c3c40ed1ab9c0cf0cdc2f19a02d29de21e8cfe63 |
| android_device_oneplus_sm6375-common | 148bb11d4d4fa67891e6f45f282dc6a9026cb91f |
| android_kernel_oneplus_sm6375 | fcaa4e32d76dd1b41cf8a5b8be235be8df573f01 |
| android_hardware_oplus | 3a766a4f4cfa0a02351d1fc261521c23763a2297 |

Pin and validate the full dependency closure, not just these four trees.

## Current Android 16 larry manifest — checked 2026-10-30

The active `scripts/ghostos-build.sh` uses `manifests/ghostos-23.2.xml`
(LineageOS `lineage-23.2`, Android 16). The separate `scripts/init-android17.sh`
only initializes/syncs an AOSP Android 17 source tree; it does not yet configure
or build the larry product. The current LineageOS branch heads observed through
GitHub on 2026-10-30 are:

| Manifest project | Observed `lineage-23.2` commit |
| --- | --- |
| `android_device_oneplus_larry` | `3a6d07128372207cf26dda1bf3fa0bde5f7b500b` |
| `android_device_oneplus_sm6375-common` | `a9451bc04291f13dbb8c262d4d81d6db38796aaa` |
| `android_kernel_oneplus_sm6375` | `5aee535b13d813b7ef8af9247f3fa0a3cd68047b` |

The manifest still tracks branch names rather than pinning these commits; these
SHAs are observations, not a resolved `repo manifest -r` build lock.

## Important compatibility findings

- The current [larry proprietary list](https://github.com/LineageOS/android_device_oneplus_larry/blob/lineage-23.2/proprietary-files.txt)
  identifies **CPH2467_15.0.0.1910(EX01)** as the default blob source (unless an
  entry is pinned). This supersedes the older `lineage-22.2` /
  `CPH2467_14.0.0.1800(EX01)` reference below for the active Android 16 tree.
  It still does not prove compatibility with the exact phone: the model,
  region and installed OxygenOS build must match. The current manifest does
  not fetch proprietary vendor blobs, and `scripts/ghostos-build.sh` does not
  run the upstream `extract-files.py`. `device.mk` includes
  `vendor/oneplus/larry/larry-vendor.mk` only if it exists. A larry image must
  not be treated as hardware-ready until the matching stock firmware/device
  dump is lawfully extracted and every required blob is verified and hashed.
- The upstream common BoardConfig defines ARM64 plus ARM32 support, A/B OTA,
  dynamic partitions, separate vendor_boot and vendor_dlkm, and boot header v3.
  Preserve matching kernel, DTBO, module ABI, firmware and vendor HAL dependencies.
- The current [lineage-23.2 common BoardConfig](https://github.com/LineageOS/android_device_oneplus_sm6375-common/blob/lineage-23.2/BoardConfigCommon.mk)
  enables AVB but sets both hashtree-disabled and verification-disabled flags,
  and uses upstream test RSA-4096 keys. It is not evidence of a secure
  production boot chain. Do not relock a device using these experimental
  images. Production keys alone do not establish that the bootloader supports
  a custom trust root or safe relocking.
- GSI replaces the system side; it does not supply or fix the phone's vendor
  firmware, kernel or bootloader. Android version in the UI is not a vendor
  compatibility test. Check ABI/Binder, Treble, launch API/vendor API, VNDK
  snapshots (including Android 15 VNDK changes), VINTF matrices, kernel version
  and page size, encryption and KeyMint/Keymaster behavior on the real device.
- Confirm OEM unlocking eligibility for the exact variant. Unlocking erases
  user data. Establish a verified stock recovery path and backups before any
  flashing. Inspect slot state, pending snapshots, logical partition layout,
  super capacity and AVB chain. Dynamic partitions generally require fastbootd;
  no generic destructive flashing command sequence is approved here.
- Never delete product/vendor logical partitions, downgrade firmware, disable
  AVB, or alter rollback indices just to bypass a failed compatibility check.
- GrapheneOS-inspired styling does not port GrapheneOS hardware hardening,
  verified boot, attestation, exploit protections or update guarantees.
  Keep SELinux enforcing and preserve upstream security mechanisms.

## Build environment

This session runs on Windows. WSL reports that it is not installed. Hardware
inspection found 8,259,321,856 bytes of RAM (about 8 GB), 12 logical CPUs and a
hypervisor. No AOSP source tree or Linux Android builder is available.
Google's [AOSP requirements](https://source.android.com/docs/setup/start)
specify at least 64 GB RAM and 400 GB free disk. Free disk was not established.

The user asked to create a builder, then explicitly requested saving the build
for later. **No VM, paid cloud resource, runner or WSL installation was created.**
Cloud provider/account access and a spending limit remain pending. Do not treat
the earlier request as approval for an unspecified paid subscription or budget.

## Resume in this order

1. Obtain the cloud provider and approved budget, then provision a Linux x86_64
   builder with sufficient storage/RAM. Keep the checkout on a Linux filesystem.
   Use isolated build workers; do not run untrusted PR code on a persistent
   privileged self-hosted runner or expose release keys to PR jobs.
2. Validate the corrected `scripts/ghostos-build.sh` bootstrap in a clean
   Linux source checkout. Keep GSI AOSP and larry LineageOS checkouts separate;
   do not mix Android 15 and existing 23.2 manifests. Preserve unrelated local
   manifests.
3. Complete pinned dependency manifests and validate the target-specific build
   driver against the full source closure. The current driver stages the larry
   product inputs and selects `ghostos_larry-userdebug`; it does not yet prove
   product discovery or a full build. GSI uses the generic_arm64 BoardConfig;
   larry uses upstream larry BoardConfig. Do not invent a BoardConfig with
   guessed partition geometry or pretend proprietary blobs are present.
4. Obtain exact phone/firmware evidence and use the upstream extraction tools
   for both larry and sm6375-common. Record lawful blob provenance and hashes.
   Resolve OxygenOS 15 firmware requirements before approving a hardware image.
5. Obtain an approved release-signed GhostWire APK with source revision,
   package ID, version, SHA-256, signer certificate fingerprint and redistribution
   license/source notices. Verify using apksigner/aapt2, import as a presigned
   ordinary app without privileged grants or platform re-signing, and check
   bundled native ABIs. Add a base-image-only development mode if needed, but
   fail release verification when GhostWire is absent. The current conditional
   hook alone is not a bundling implementation.
6. Compile the Privacy Center/resources, then an actual GSI system image.
   Add fast hosted static checks and full Linux Android CI separately. Save
   resolved `repo manifest -r`, logs, commit IDs, tools and image hashes even
   when a build fails. A static CI pass is not an Android build pass.
7. Compile larry target-files/images after dependencies and blobs are ready.
   Audit inherited AVB flags, test keys, vendor SPL and SELinux. Do not fabricate
   a newer vendor security patch level or copy a stock build fingerprint.
8. Verify packaged apps/theme, image contents, VINTF, SELinux enforcing, signing
   metadata, partition sizes and target-files before lab flashing. Run CTS/VTS
   as applicable, boot/suspend/encryption tests, camera/mic/location privacy
   checks, radio/VoLTE/5G, Wi-Fi/Bluetooth, camera/audio, fingerprint/GNSS,
   charging and recovery/OTA rollback tests on the exact supported variant.
   Validate GhostWire login, messaging, calls, notifications and updates against
   existing Wire services without changing protocols.
9. Require independent review and signing/release evidence before publication.
   Keep GSI and device compatibility matrices explicit. No universal bootability
   claim and no GrapheneOS-equivalent security claim.

Useful primary references:
[AOSP GSI](https://source.android.com/docs/core/tests/vts/gsi),
[developer GSI compatibility](https://developer.android.com/topic/generic-system-image),
[Android VNDK](https://source.android.com/docs/core/architecture/vndk),
[larry upstream](https://github.com/LineageOS/android_device_oneplus_larry/tree/lineage-23.2).
