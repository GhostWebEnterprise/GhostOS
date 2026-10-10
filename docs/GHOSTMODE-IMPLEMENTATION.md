# GhostMode implementation plan

## Phase 1 — foundation

- Versioned preset schema: Standard, Private, Maximum, Custom.
- Effective-policy model: preset + user overrides + device capabilities.
- Reversible state snapshot contract.
- Capability/degraded-state reporting.

## Phase 2 — Android service

Create a privileged GhostMode manager in the GhostOS framework layer. The service owns policy transitions; UI surfaces request transitions rather than directly modifying low-level settings.

Required interfaces:

- `getPresets()`
- `getActivePolicy()`
- `previewPolicy(preset, overrides)`
- `activate(preset, overrides)`
- `deactivate()`
- `getCapabilities()`
- `getDegradedControls()`

Transitions must be serialized, locally audited, and rollback reversible when an adapter fails.

## Phase 3 — policy adapters

- Network: firewall, optional Tor routing, kill switch for supported local policies, LAN policy. GhostWeb VPN integration is explicitly out of scope.
- Privacy: background camera/microphone/location, clipboard notification and expiry.
- Device: USB locked-mode and sensitive lock-screen notifications.
- Profiles: optional profile switching/isolation hooks after the profile layer exists.

Each adapter reports supported/unsupported/degraded and must never fabricate success.

## Phase 4 — UI

Privacy Center receives a GhostMode card with active state, four preset choices, policy preview and degraded-control warnings. SystemUI receives a Quick Settings tile and compact status indicator using the GhostOS Photon-style design language.

## Phase 5 — verification

- Schema validation in CI.
- Unit tests for policy resolution and rollback.
- Integration tests for each adapter.
- Confirm supported firewall/Tor routing and kill-switch behavior with network tests; do not include GhostWeb VPN integration.
- Confirm USB policy across locked/unlocked transitions.
- Confirm state restoration after reboot and deactivation.
- Run device smoke tests on `larry`.

GhostMode is experimental until all relevant tests pass on a real supported device.
