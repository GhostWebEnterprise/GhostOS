# GhostOS Auditor

Native, read-only Android system application for platform-backed device observations.

## Implemented observations

- Verified Boot state and bootloader-lock properties, labelled as platform-reported rather than independently attested.
- SELinux enabled/enforcing state through guarded Android framework access.
- Secure screen-lock presence through KeyguardManager.
- ADB state through Settings.Global.
- Android release, API level, device codename and security patch metadata.

Each finding carries a state, source and limitation. Missing or inaccessible evidence remains UNKNOWN; the app does not calculate an overall "secure" score.

## Safety boundary

- No policy writes, root shell, bootloader changes, VPN control, network client, server, credentials or updater actions.
- No Proton client code or signing material.
- No GhostWeb VPN / VpnHood dependency or product integration.
- Verified Boot properties are observations, not independent AVB/vbmeta cryptographic verification.
- This app is not a replacement for hardware-backed remote attestation or a full device audit.

The app is included in the larry product package list and linked from GhostOS Security Center. Device runtime behavior remains unverified until tested on the larry handset.
