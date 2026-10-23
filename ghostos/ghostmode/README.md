# GhostMode

GhostMode is the policy orchestration layer for GhostOS. It provides a single user-facing control that coordinates privacy and security subsystems without silently destroying user data.

## Presets

- **Standard** — secure daily defaults, tracker blocking, locked-screen USB charge-only, sensitive notification protection.
- **Private** — direct routing, LAN isolation, and stricter sensor/clipboard policy. GhostWeb VPN is not integrated.
- **Maximum** — Tor routing where available, kill switch, LAN isolation, shortest clipboard lifetime and strict background sensor policy.
- **Custom** — user-controlled policy assembled from the same primitives.

Preset definitions live in `presets.json` and are versioned independently from device-specific implementation.

## Architecture

```text
SystemUI / Privacy Center / Quick Settings
                 |
          GhostMode Manager
                 |
     policy validation + preview
                 |
 +---------------+----------------+
 |               |                |
Network       Privacy          Device
policy        policy           policy
 |               |                |
Firewall     Sensors          USB restricted
Tor (future) Clipboard        notifications
Kill switch  permissions      profile hooks
```

## Activation contract

1. Resolve preset and user overrides.
2. Validate required platform capabilities and any independently configured Tor provider. GhostWeb VPN is outside GhostOS.
3. Show the effective policy before first activation of a restrictive preset.
4. Snapshot reversible settings.
5. Apply policy atomically where Android APIs permit it; otherwise fail closed only for controls explicitly selected by the user.
6. Expose active state and degraded/unavailable controls in SystemUI and Privacy Center.
7. On deactivation, restore the previous reversible state rather than assuming platform defaults.

## Safety requirements

- No preset wipes user data.
- No preset changes credentials, disables emergency calling, or intentionally prevents device recovery.
- Destructive duress actions are outside GhostMode and must never be enabled implicitly.
- Unavailable Tor or other supported routing capabilities must be visible; GhostMode must not claim protected routing when the requested route is unavailable. GhostWeb VPN is not part of GhostOS.
- Device-specific hooks must be capability checked.
- Security-sensitive state transitions must be auditable locally without recording message content, browsing content, secrets, or clipboard contents.

## Integration gates

1. Policy schema + validation.
2. GhostMode Manager service/API.
3. Privacy Center preset selector and effective-policy preview.
4. SystemUI Quick Settings tile and active-state indicator.
5. Firewall and LAN-isolation policy adapters (no GhostWeb VPN integration).
6. Tor provider interface.
7. Sensor, clipboard, notification and USB policy adapters.
8. Auditor events and automated tests.
9. `larry` device smoke test before marking production-ready.
