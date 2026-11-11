# GhostOS Security Center Android app

This is the first native Android UI shell for the GhostOS Security Center. It uses public Android APIs only and is deliberately conservative:

- VPN transport is read from Android NetworkCapabilities; an active tunnel is labelled provider-unverified unless a trusted platform identity provider is added.
- Proton VPN is not bundled or controlled by this app. The button opens Android's VPN settings.
- Verified Boot, AVB, SELinux and detailed privacy audit remain explicitly unverified until their trusted framework/Auditor providers are implemented.
- Screen-lock and ADB state are read from Android public APIs.

The Soong module is intended for the system_ext partition. It is not yet included in a device product package list, compiled in an AOSP build, or tested on larry.
