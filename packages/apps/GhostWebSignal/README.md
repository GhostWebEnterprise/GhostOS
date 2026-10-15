# Optional GhostWeb Signal ROM app

Disabled by default. Build with `GHOSTOS_WITH_SIGNAL=true scripts/ghostos-build.sh`
to download and verify the exact release in `release.json` before including it.
Requires Python 3.11+, Android `apksigner` and `aapt` on PATH, or explicit
`APKSIGNER` / `AAPT` executable paths. Verification errors stop the build.
For a previously downloaded APK, run `python3 scripts/ghostos-signal.py --apk FILE`
to verify without building or installing anything.

The build script stages verified files under `vendor/ghostos/signal` in the
Android checkout and adds one product inheritance line to
`device/oneplus/$DEVICE/lineage_$DEVICE.mk` before `breakfast`. Re-running with
the default `GHOSTOS_WITH_SIGNAL=false` removes that inheritance line, so a
previous opt-in cannot silently include the app. Upstream device files must be
present after sync. No APK binary is stored in this repository.

## Identity and trust

- Source: GhostWebEnterprise/ghost-web-signal, release `v8.19.2-7`.
- Package: `org.ghostweb.signal`; version `8.19.2-7`, code `172007`.
- APK SHA-256: `4081a1a8b96f5e3c98aa1656b9b8946a754eab75e3b8c1d97a53a5efb41f47de`.
- Signing certificate SHA-256: `64bb12f28a7d39d2cf921024c6d36019ea3808481bd4471511bd83ca64f20435`.

The APK hash was checked against the GitHub release asset digest; package,
version, ARM64 support and certificate were extracted from the downloaded APK.
The dedicated CI and every opt-in build additionally run `apksigner verify`
and compare the exact signer and package pins. A certificate extracted from an
APK alone is not proof of a valid signature. This pin establishes trust in the
GhostWeb release, not in Signal's or Molly's signing keys, and does not prove
source reproducibility. Release assets are mutable: replacement bytes fail the
hash check. Updating any pin requires explicit review and verification.

The newer `v8.79.2-10/app-prod-store-release.apk` was inspected and declares
`org.ghostweb.app`; it is intentionally not used for this dedicated package.
No latest-release URL or automatic repinning is used.

## Android security and UI

Installed as an ordinary `/product/app/GhostWebSignal` app, never `priv-app`.
It retains its upstream signature; no platform key, shared system UID,
permission allowlist, SELinux exception, accessibility/device-admin grant,
default SMS role or notification-listener privilege is added. Registration,
app unlock, encryption, backups, runtime permission prompts and protocol
behavior remain controlled by the app and Android. GhostWeb VPN is not added.

Its existing launcher activity provides the launcher entry. Standard Android
Settings > Apps provides permissions, storage, notifications and disable controls.
No custom Settings hook is necessary or claimed: this repo has no implemented
Android Settings/launcher source integration. A preinstalled app normally can
be disabled by the user; full removal of the ROM copy requires an opt-out build.
Updates must use the same package and compatible signer and a higher version
code. Do not route updates to upstream Signal/Molly packages or change pins to
accept another signer. Verify the chosen update channel on hardware before release.

The APK uses compressed JNI and explicitly enables `extractNativeLibs`.
`preprocessed: true` preserves the signed bytes. The documented
`skip_preprocessed_apk_checks` exception only avoids Soong's requirement for
uncompressed JNI; the staging verifier requires native extraction to be enabled.
APK signature verification is mandatory. No re-signing or zip mutation is allowed.

## Release blockers for larry

This change does not establish a bootable, flashable or release-ready ROM.
The repository supplies orchestration and upstream device manifests, not a
validated GhostOS device image. A Linux Android source checkout, proprietary
vendor extraction, partition/VINTF/SELinux/AVB validation, signing and recovery/
OTA integration remain prerequisites. Full Soong/product parsing and an actual
`m bacon` build must pass, including checking the final APK's hash and signer
inside the built product image.

On physical `larry`, verify boot, launcher and Settings visibility, permission
denials, disable/re-enable, registration, app unlock/biometrics, messaging,
calls, notifications after reboot/doze, same-signer updates, opt-out images,
radio/camera/audio and OTA. Neither Python tests nor APK signature checks prove
these hardware behaviors or reproducibility. No ROM/APK publication is part of
this integration workflow.
