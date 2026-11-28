"""Evidence-based status model for the GhostOS Security Center.

Platform adapters must supply fresh observations. Missing evidence is UNKNOWN,
never an enabled or secure state. This module contains no VPN client code.
"""
from __future__ import annotations

from dataclasses import dataclass, field
from enum import Enum
from typing import Mapping


class EvidenceState(str, Enum):
    SUPPORTED = "supported"
    UNSUPPORTED = "unsupported"
    ENABLED = "enabled"
    DISABLED = "disabled"
    DEGRADED = "degraded"
    UNKNOWN = "unknown"


class VpnState(str, Enum):
    DISCONNECTED = "disconnected"
    CONNECTING = "connecting"
    ACTIVE_VERIFIED = "active_verified"
    ACTIVE_PROVIDER_UNVERIFIED = "active_provider_unverified"
    BLOCKED_BY_LOCKDOWN = "blocked_by_lockdown"
    UNKNOWN = "unknown"


@dataclass(frozen=True)
class ControlStatus:
    """A single control's observed state and evidence source."""
    state: EvidenceState = EvidenceState.UNKNOWN
    source: str = "unavailable"
    detail: str = ""
    stale: bool = False

    def __post_init__(self) -> None:
        if not self.source.strip():
            raise ValueError("status source must be non-empty")
        if self.stale:
            # Stale evidence must not preserve any security conclusion.
            # Capability, degraded, and disabled states can all change at runtime.
            object.__setattr__(self, "state", EvidenceState.UNKNOWN)


@dataclass(frozen=True)
class VpnObservation:
    """VPN fields populated only by an Android platform-backed observer.

    provider_identity_verified must come from a trusted platform adapter.
    An installed/launched app or user-selected label is not proof. None means
    provider identity cannot be established.
    """
    transport_active: bool | None
    provider_identity_verified: bool | None = None
    connecting: bool = False
    lockdown_enabled: bool | None = None
    blocked_by_lockdown: bool = False
    stale: bool = False


def classify_vpn(observation: VpnObservation) -> VpnState:
    """Map strictly typed platform evidence to a conservative VPN state.

    Python truthy values such as 1, "true", and non-empty strings are not
    platform booleans. Treat them as malformed evidence rather than inferring
    an active tunnel or lockdown state.
    """
    boolean_evidence = (
        observation.transport_active,
        observation.provider_identity_verified,
        observation.connecting,
        observation.lockdown_enabled,
        observation.blocked_by_lockdown,
        observation.stale,
    )
    if any(value is not None and type(value) is not bool for value in boolean_evidence):
        return VpnState.UNKNOWN
    if observation.stale:
        return VpnState.UNKNOWN
    if observation.blocked_by_lockdown:
        # Do not accept contradictory or incomplete evidence as a confirmed block.
        # The platform must report lockdown enabled and no active VPN transport.
        if observation.lockdown_enabled is True and observation.transport_active is False:
            return VpnState.BLOCKED_BY_LOCKDOWN
        return VpnState.UNKNOWN
    if observation.transport_active is None:
        return VpnState.UNKNOWN
    if observation.transport_active is False:
        return VpnState.CONNECTING if observation.connecting else VpnState.DISCONNECTED
    if observation.provider_identity_verified is True:
        return VpnState.ACTIVE_VERIFIED
    return VpnState.ACTIVE_PROVIDER_UNVERIFIED


@dataclass(frozen=True)
class SecurityCenterSnapshot:
    """Immutable snapshot; missing sections remain explicitly unknown."""
    controls: Mapping[str, ControlStatus] = field(default_factory=dict)
    vpn: VpnState = VpnState.UNKNOWN
    generated_by: str = "unavailable"

    def __post_init__(self) -> None:
        if not self.generated_by.strip():
            raise ValueError("snapshot source must be non-empty")
        if any(not key.strip() for key in self.controls):
            raise ValueError("control names must be non-empty")

    def control(self, name: str) -> ControlStatus:
        """Return UNKNOWN when a provider did not report the requested item."""
        return self.controls.get(name, ControlStatus())
