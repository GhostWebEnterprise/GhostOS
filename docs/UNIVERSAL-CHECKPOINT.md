

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

The active bootstrap now selects LineageOS 24.0, applies the pinned manifest
before repo sync, stages the GhostOS product/Privacy Center, and selects
ghostos_larry-userdebug. GitHub Actions validates manifest commit existence,
Android API 37 resource/source compilation, script syntax and offline
bootstrap regressions. It does not run the full multi-hundred-gigabyte Soong
product build. No full repo sync, flashable image, physical boot, hardware,
modem, camera, fingerprint, OTA or AVB production verification is claimed.
