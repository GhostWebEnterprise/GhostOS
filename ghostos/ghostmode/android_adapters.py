"""Android-facing GhostMode policy adapter contracts.

These adapters intentionally expose capability state and fail closed when no
platform backend is available. Real framework integrations can inject backend
implementations without the policy manager fabricating device success.
"""
from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Protocol


class PlatformBackend(Protocol):
    def read(self) -> Any: ...
    def apply(self, policy: dict[str, Any]) -> None: ...
    def restore(self, snapshot: Any) -> None: ...


class PartialPolicyApplyError(RuntimeError):
    """A backend changed state but could not restore its pre-apply snapshot."""
    rollback_required = True

    def __init__(self, name: str, rollback_token: Any):
        super().__init__(f"{name} partial apply could not be restored")
        self.rollback_token = rollback_token


@dataclass
class AndroidPolicyAdapter:
    name: str
    backend: PlatformBackend | None = None

    @property
    def supported(self) -> bool:
        return self.backend is not None

    def apply(self, policy: dict[str, Any]) -> Any:
        if self.backend is None:
            raise RuntimeError(f"{self.name} backend unavailable")
        snapshot = self.backend.read()
        try:
            self.backend.apply(policy)
        except Exception:
            try:
                self.backend.restore(snapshot)
            except Exception as restore_exc:
                raise PartialPolicyApplyError(self.name, snapshot) from restore_exc
            raise
        return snapshot

    def rollback(self, snapshot: Any) -> None:
        if self.backend is None:
            raise RuntimeError(f"{self.name} backend unavailable")
        self.backend.restore(snapshot)


class NetworkPolicyAdapter(AndroidPolicyAdapter):
    """Firewall/LAN policy adapter. VPN routing is intentionally excluded."""

    def __init__(self, backend: PlatformBackend | None = None):
        super().__init__("network", backend)


class PrivacyPolicyAdapter(AndroidPolicyAdapter):
    """Camera, microphone, location and clipboard policy adapter."""

    def __init__(self, backend: PlatformBackend | None = None):
        super().__init__("privacy", backend)


class DevicePolicyAdapter(AndroidPolicyAdapter):
    """USB locked-mode and sensitive lock-screen notification adapter."""

    def __init__(self, backend: PlatformBackend | None = None):
        super().__init__("device", backend)
