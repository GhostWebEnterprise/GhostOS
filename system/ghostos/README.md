# GhostOS Framework / SystemUI

GhostOS UI and framework layer.

Design principles:
- preserve Android platform compatibility
- expose privacy/security state clearly
- avoid unnecessary privileged APIs
- keep device-specific code outside the shared UI layer

Planned surfaces:
- GhostOS Control Center
- Privacy indicators and dashboard
- security status
- updater status
- device-specific capability reporting

## GhostOS Security Center

The Security Center is the single status and control surface for device security. It aggregates evidence from platform services; it does not replace them or claim a control is active without confirmation.

Initial sections:
- **Overview:** verified boot / build state, patch level, encryption, screen lock, and outstanding warnings.
- **Network protection:** Proton VPN launch/deep-link entry, Android Always-on VPN and lockdown settings, active VPN transport observation, and last-known status.
- **Privacy:** camera, microphone, location, clipboard, notification and permission controls.
- **Device integrity:** bootloader / AVB state, SELinux state, Auditor and update status.
- **App security:** install source, app updates and supported isolation controls.
- **Activity:** privacy-preserving local security events with no message, browsing, credential or clipboard contents.

### VPN provider policy

Proton VPN is the recommended external VPN client. GhostOS does not embed Proton's protocol implementation, credentials, servers, API, or signing keys. The official Proton VPN Android app remains separately installed, signed and updated by Proton.

The Security Center may open the official app and guide users to Android's system VPN settings. It may report an active VPN only from platform-observed VPN transport state; it must not infer a connected Proton session merely because the app is installed or launched. If provider identity cannot be verified, show **VPN active — provider unverified**.

Android Always-on VPN and lockdown are OS-managed settings and may require user confirmation or device-owner/system privileges. GhostOS must not silently change them. Explain that lockdown can block all network traffic when the VPN is disconnected, and provide a safe recovery path.

No GhostWeb VPN code, service, server list, protocol client, credentials or branding belongs in GhostOS.
