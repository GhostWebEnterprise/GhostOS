# GhostOS Security Center Android app

This is the first native Android UI shell for the GhostOS Security Center. It uses public Android APIs only and is deliberately conservative:

- VPN transport is read from Android NetworkCapabilities; an active tunnel is labelled provider-unverified unless a trusted platform identity provider is added.
- Proton VPN is not bundled or controlled by this app. The button opens Android's VPN settings.
- Verified Boot state is read from Android boot properties; SELinux mode is read from the platform API. Both remain unknown when the platform does not expose reliable values.
- Detailed permission auditing remains unverified until its framework/Auditor provider is implemented.
- Screen-lock and ADB state are read from Android public APIs.

The Soong module is declared for the system_ext partition and is included in `vendor/ghostos/config/common.mk` as `GhostOSSecurityCenter`. CI links its manifest/resources and compiles the Java sources against the Android 17 SDK, but a full AOSP/Soong product build, image inspection, and physical Larry device test have not yet been completed.
