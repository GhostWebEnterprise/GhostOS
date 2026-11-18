# GhostOS Privacy Center (experimental)

The Android application module lives in `packages/apps/GhostOSPrivacyCenter`.
It is an experimental, user-operated Device Owner control surface, not a
complete privacy dashboard or a production-ready ROM component.

## Implemented Android controls

The app exposes individual controls only when Android reports that the
Device Owner adapter is ready:

- Camera restriction.
- Screen-capture restriction.
- Microphone mute restriction that prevents user unmute.
- Device location enable/disable on Android 11 (API 30) and later.
- USB data-signaling enable/disable when the Android device reports support.
- Redaction of sensitive notifications on a secure lock screen.

Before the first change, `GhostModeAndroidPolicyManager` persists the original
device-policy snapshot. Further changes are journaled, the expected state is
re-read before the UI reports an active restriction, and the Restore action
returns the saved baseline. Incomplete or unverifiable state is shown as
recovery-required; the app does not assume that a failed operation succeeded.

The Quick Settings tile is deliberately non-toggleable. It reports the
verified Device Owner state and opens the Privacy Center for explicit user
action. It never activates or restores a policy itself.

## Requirements and limitations

- The app must be provisioned as the device owner and its admin must be active.
  Installing the APK alone does not grant Device Owner authority.
- The Android module has minSdk 29 (Android 10).
- Location and USB controls are hidden when their platform requirements are
  not met.
- Full GhostMode preset orchestration across Android adapters is not yet
  implemented.
- Network firewall, Tor routing, kill-switch policy, clipboard controls,
  general sensor-permission policy, app-permission inventory and a privacy-event
  history are not implemented. They must remain reported as degraded or
  unavailable.
- The VPN entry point opens Android's VPN settings only. GhostWeb VPN code,
  routing and service integration are explicitly outside GhostOS.
- The Soong module declares `certificate: "platform"`, which selects the
  platform certificate configured by the Android build tree. The actual
  certificate material depends on that build's key configuration. CI has not
  produced an APK or ROM image, so no production signing or release image is
  verified.

CI compiles and validates the Privacy Center sources against the configured
Android SDK. That is not a full LineageOS/Soong ROM build, a flashable image,
or a device-boot test. The full larry build remains experimental until the
dedicated Linux build environment and real-device verification are available.
