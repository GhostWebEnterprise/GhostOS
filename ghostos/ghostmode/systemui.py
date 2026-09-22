"""Host-testable SystemUI/Quick Settings integration for GhostMode.

This models the state/actions that the future Android SystemUI tile binds to.
It deliberately does not claim a real device tile is installed or functional.
"""
from __future__ import annotations

from typing import Any, Mapping

from .manager import GhostModeManager


class GhostModeSystemUI:
    def __init__(self, manager: GhostModeManager, default_preset: str = "private"):
        self._manager = manager
        self._default_preset = default_preset

    def tile_state(self) -> dict[str, Any]:
        active = self._manager.get_active_policy()
        degraded = self._manager.get_degraded_controls()
        return {
            "active": active is not None,
            "label": "GhostMode",
            "preset": (active.get("id") or active.get("preset")) if active else None,
            "degraded": bool(degraded),
            "degraded_controls": degraded,
        }

    def click(self, overrides: Mapping[str, Any] | None = None) -> dict[str, Any]:
        if self._manager.get_active_policy() is None:
            self._manager.activate(self._default_preset, overrides)
        else:
            self._manager.deactivate()
        return self.tile_state()

    def long_click_target(self) -> str:
        return "ghostos://privacy-center/ghostmode"
