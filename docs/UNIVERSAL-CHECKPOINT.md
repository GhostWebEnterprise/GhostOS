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

## Previous Android 16 larry manifest baseline — checked 2026-10-31

At the 2026-10-31 checkpoint, `scripts/ghostos-build.sh` used
`manifests/ghostos-23.2.xml` (LineageOS `lineage-23.2`, Android 16), and
`scripts/init-android17.sh` only initialized/synced an AOSP Android 17 source
tree. That historical state is superseded by the pinned LineageOS 24.0 / Android
17 larry manifest and bootstrap described in the final section below. The
LineageOS 23.2 branch heads observed through GitHub on 2026-10-30 were:

| Manifest project | Observed `lineage-23.2` commit |
| --- | --- |
| `android_device_oneplus_larry` | `3a6d07128372207cf26dda1bf3fa0bde5f7b500b` |
| `android_device_oneplus_sm6375-common` | `a9451bc04291f13dbb8c262d4d81d6db38796aaa` |
| `android_kernel_oneplus_sm6375` | `5aee535b13d813b7ef8af9247f3fa0a3cd68047b` |
| `android_hardware_oplus` | `b1dc1db2df55601e4125abfdc3bc3f273f3ecb2e` |
| `proprietary_vendor_oneplus_larry` (TheMuppets) | `6c1018818ebd3f0ada02709ee8c58e42121d9e7f` |
| `proprietary_vendor_oneplus_sm6375-common` (TheMuppets) | `c1e5323598c20d241bc9d7c4c3762dd01d07d26d` |

The manifest tracks branch names rather than pinning these commits; these SHAs
are observations, not a resolved `repo manifest -r` build lock.

## Important compatibility findings

- The current [larry proprietary list](https://github.com/LineageOS/android_device_oneplus_larry/blob/lineage-23.2/proprietary-files.txt)
  identifies **CPH2467_15.0.0.1910(EX01)** as the default blob source (unless an
  entry is pinned). This supersedes the older `lineage-22.2` /
  `CPH2467_14.0.0.1800(EX01)` reference below for the active Android 16 tree.
  The 2026-10-31 manifest correction now syncs `hardware/oplus`,
  `vendor/oneplus/larry`, and `vendor/oneplus/sm6375-common` from their
  `lineage-23.2` branches. A path audit matched all 1,066 larry and 1,101
  SM6375-common proprietary-file entries to source/target paths in those
  vendor trees. This is path-coverage only, not binary hash or firmware
  provenance verification. The exact handset model, region and installed
  OxygenOS build must still match; otherwise use the upstream extraction tools
  on a lawful matching firmware/device dump and verify every required blob.
  `scripts/ghostos-build.sh` still has not run a full `repo sync` or build.
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
2. Validate the LineageOS 24.0 / Android 17 `scripts/ghostos-build.sh`
   bootstrap in a clean Linux source checkout. Keep the GSI AOSP and larry
   LineageOS checkouts separate; do not mix the Android 16 23.2 and Android 17
   24.0 manifests. Preserve unrelated local manifests.
3. The new `manifests/ghostos-24.0.xml` pins larry, SM6375-common, OPlus
   hardware, kernel and both proprietary vendor trees to exact commits. Validate
   the full source closure during a real `repo sync`; the current driver stages
   the larry product inputs and selects `ghostos_larry-userdebug`, but does not
   yet prove product discovery or a full build. GSI uses the generic_arm64
   BoardConfig; larry uses upstream larry BoardConfig. Do not invent a
   BoardConfig with guessed partition geometry or pretend proprietary blobs
   are present.
4. Confirm the exact phone model, region and installed OxygenOS 15 build against
   the LineageOS 24.0 vendor source `CPH2467_15.0.0.2000(EX01)`. The previous
   23.2 tree used `CPH2467_15.0.0.1910(EX01)`; these blob sets must not be
   assumed interchangeable. Compare vendor blob hashes/provenance; if the
   synced TheMuppets branches do not match the handset, use the upstream
   extraction tools on a lawful matching firmware/device dump. Do not approve
   a hardware image from path coverage alone.
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

## Android 17 / LineageOS 24.0 device foundation — checked 2026-11-01

A current upstream device closure is now available for larry on LineageOS
24.0. The LineageOS 24.0 top-level manifest explicitly pins the AOSP base to
android-17.0.0_r1; Android 17 is API level 37. The new
manifests/ghostos-24.0.xml records exact Git commit SHAs for all six
device/common/kernel/hardware/vendor projects rather than relying on moving
branch names.

| Manifest project | Pinned commit |
| --- | --- |
| LineageOS/android_device_oneplus_larry | c61f542b529d561e8b4e1d2dcf33027e437c3740 |
| LineageOS/android_device_oneplus_sm6375-common | 16a0967e6b5a4a23aa35e1bf857e84c543733db7 |
| LineageOS/android_hardware_oplus | c78b87c9efb89049b35a184d60095f26c7cb29aa |
| LineageOS/android_kernel_oneplus_sm6375 | 410239fbcdd647b66eca7011b9c3053e52cf1d9d |
| TheMuppets/proprietary_vendor_oneplus_larry | 491fcb0a609f4e165dfdac41cbff9630d871f226 |
| TheMuppets/proprietary_vendor_oneplus_sm6375-common | 306f82d86c7be9f899603b6e3b47a08544c6276c |

The larry, common and vendor branch heads report source firmware
CPH2467_15.0.0.2000(EX01). The earlier 23.2 manifest used
CPH2467_15.0.0.1910(EX01). These are not interchangeable assumptions:
confirm the exact handset region and installed firmware, or extract and verify
matching proprietary blobs from a lawful device/firmware source before any
device boot test. The manifest is a pinned source closure, not proof that the
local handset matches it.

The upstream sm6375-common/BoardConfigCommon.mk on LineageOS 24.0 enables
AVB but also sets --set_hashtree_disabled_flag and
--set_verification_disabled_flag, and uses the upstream test RSA-4096 key.
This is acceptable only as an explicitly experimental bring-up baseline. It
does not establish verified boot or production security. Do not flash a
resulting image to a primary device, relock the bootloader, or claim secure
AVB until GhostOS has a separately reviewed AVB configuration, production
keys, matching firmware and a validated recovery path.

The active bootstrap selects LineageOS 24.0, applies the pinned manifest
before repo sync, stages the GhostOS product/Privacy Center, and selects
ghostos_larry-userdebug. GitHub Actions is configured to install Android
platform package platforms;android-37.0 and Build Tools 37.0.0, then validate
API 37 resource/source compilation, manifest commit existence, script syntax
and offline bootstrap regressions. This CI is not a full multi-hundred-gigabyte
Soong product build. No full repo sync, flashable image, physical boot,
hardware, modem, camera, fingerprint, OTA or AVB production verification is
claimed.
