# GhostOS Security Center Android app

This is the first native Android UI shell for the GhostOS Security Center. It uses public Android APIs for network, lock and settings observations, plus guarded platform-only probes for boot properties and SELinux. Platform-only probes may be inaccessible at runtime and must then remain unknown:

- VPN transport is read from Android NetworkCapabilities. A detected VPN transport does not prove default-route selection, all-app coverage or lockdown; provider identity remains unverified unless a trusted platform identity provider is added.
- Proton VPN is not bundled or controlled by this app. The “Open Proton VPN” button launches the installed Proton VPN app, or opens Proton's official Android-app releases page when the app is absent. The separate “Open Android VPN settings” button opens the OS-managed VPN settings page.
- Verified Boot state is read through a guarded platform-property probe; SELinux mode uses a guarded platform API probe. Both remain unknown when access is restricted or evidence is unavailable.
- Detailed permission auditing remains unverified until its framework/Auditor provider is implemented.
- Screen-lock and ADB state are read from Android public APIs.

The Soong module is declared for the system_ext partition and is included in `vendor/ghostos/config/common.mk` as `GhostOSSecurityCenter`. CI links its manifest/resources and compiles the Java sources against the Android 17 SDK, but a full AOSP/Soong product build, image inspection, and physical Larry device test have not yet been completed.
