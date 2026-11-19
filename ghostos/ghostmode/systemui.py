"""Host-testable SystemUI/Quick Settings integration for GhostMode.

The Android Quick Settings tile is a non-toggleable shortcut to Privacy Center.
It reflects verified policy state but never applies or restores policy itself.
"""
from __future__ import annotations

from typing import Any

from .manager import GhostModeManager


class GhostModeSystemUI:
    def __init__(self, manager: GhostModeManager):
        self._manager = manager

    def tile_state(self) -> dict[str, Any]:
        active = self._manager.get_active_policy()
        degraded = self._manager.get_degraded_controls()
        return {
            "active": active is not None,
            "label": "GhostMode",
            "preset": (active.get("id") or active.get("preset")) if active else None,
            "degraded": bool(degraded) or self._manager.get_recovery_required(),
            "recovery_required": self._manager.get_recovery_required(),
            "degraded_controls": degraded,
        }

    def click(self) -> dict[str, Any]:
        """Open Privacy Center without changing any GhostMode policy."""
        return {
            **self.tile_state(),
            "action": "open_privacy_center",
            "target": self.privacy_center_target(),
        }

    @staticmethod
    def privacy_center_target() -> str:
        return "ghostos://privacy-center/ghostmode"
