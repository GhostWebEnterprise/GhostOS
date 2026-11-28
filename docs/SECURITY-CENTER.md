# GhostOS Security Center

## Purpose

A single, evidence-based control surface for device integrity, privacy and network protection. The center aggregates platform state and delegates privileged operations to the Android framework. It never reports success based only on a requested action.

## Information architecture

1. **Overview** — boot integrity, verified boot state, security patch level, encryption, screen-lock state, update state and actionable warnings.
2. **Network protection** — VPN transport state, Proton VPN app entry, Android Always-on VPN and lockdown guidance, private DNS state, firewall and LAN isolation capability.
3. **Permissions & sensors** — camera, microphone, location, nearby devices, clipboard and background access.
4. **App security** — app source, signing identity, update status, per-app network policy and supported isolation controls.
5. **System integrity** — bootloader / AVB, SELinux, kernel security state, Auditor and OTA verification.
6. **USB & lock screen** — locked-device USB policy and sensitive notification visibility.
7. **Security activity** — local, minimal audit events for security state changes; never collect message text, browsing content, credentials, tokens or clipboard contents.

## VPN integration: Proton VPN

Proton VPN is the recommended optional VPN application. Integrate it as an external Android app, not as an operating-system VPN implementation.

- Link to the official Proton VPN Android app and open it through Android package / intent resolution.
- Do not bundle a repackaged APK, fork its client, copy its signing keys, store Proton credentials, or proxy Proton account/API traffic.
- Keep app installation and updates under Proton's official distribution and signature chain.
- Read active VPN transport from Android platform APIs. App-installed or app-launched state is not proof of an active tunnel.
- If Android reports a VPN transport but the provider cannot be authenticated, display **VPN active — provider unverified**.
- Display disconnected, connecting, blocked-by-lockdown and unknown as distinct states. Never infer protection from stale status.
- Guide users to Android's Always-on VPN and lockdown settings. These settings may require user action or privileged system/device-owner access; do not silently toggle them.
- Warn before enabling lockdown that network access may be blocked whenever the VPN is unavailable, and provide a recovery route.

## Security behavior

- Capability-gate every control and disclose unsupported hardware/platform features.
- Separate observed state, configured state and desired state.
- Fail closed for explicitly enabled protections when safe rollback is possible; otherwise expose degraded state and recovery guidance.
- Never disable emergency calling or prevent device recovery.
- Keep security event logs local and data-minimal.
- No first-party VPN client, VPN servers, VPN credentials or VPN protocol engine are included in GhostOS.

## Delivery gates

1. Define typed state model: supported / unsupported / enabled / disabled / degraded / unknown.
2. Implement framework-backed read-only status providers.
3. Implement safe, user-confirmed settings deep links.
4. Build Compose Security Center screens and Quick Settings entry points.
5. Add unit tests for state mapping, stale-state handling and capability absence.
6. Add Android integration tests for VPN transport and lockdown transitions.
7. Verify Proton app launch and Android settings flow on a real device.
8. Verify no VPN implementation or secrets are bundled in the ROM.
9. Device smoke test on OnePlus Nord CE 3 Lite 5G (`larry`).

The Security Center remains experimental until platform integration and real-device tests pass.
