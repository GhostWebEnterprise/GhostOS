# GhostOS Security Center Android app

This is the first native Android UI shell for the GhostOS Security Center. It uses public Android APIs only and is deliberately conservative:

- VPN transport is read from Android NetworkCapabilities; an active tunnel is labelled provider-unverified unless a trusted platform identity provider is added.
- Proton VPN is not bundled or controlled by this app. The button opens Android's VPN settings.
- Verified Boot state is read from Android boot properties; SELinux mode is read from the platform API. Both remain unknown when the platform does not expose reliable values.
- Detailed permission auditing remains unverified until its framework/Auditor provider is implemented.
- Screen-lock and ADB state are read from Android public APIs.

The Soong module is intended for the system_ext partition. It is not yet included in a device product package list, compiled in an AOSP build, or tested on larry.


## Quick Settings

The GhostMode tile is a non-toggleable shortcut to Security Center. It does not activate, deactivate or claim the state of any policy. Policy changes remain in the capability-gated GhostMode flow and are not performed by the tile.
