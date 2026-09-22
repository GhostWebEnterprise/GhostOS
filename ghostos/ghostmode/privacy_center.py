"""Host-testable Privacy Center integration for GhostMode.

This view model exposes Manager state to the future Android Privacy Center UI.
It never reports controls as available when the backing adapter is unsupported.
"""
from __future__ import annotations

from typing import Any, Mapping

from .manager import GhostModeManager


class GhostModePrivacyCenter:
    def __init__(self, manager: GhostModeManager):
        self._manager = manager

    def card_state(self) -> dict[str, Any]:
        active = self._manager.get_active_policy()
        return {
            "active": active is not None,
            "active_policy": active,
            "presets": self._manager.get_presets(),
            "capabilities": self._manager.get_capabilities(),
            "degraded_controls": self._manager.get_degraded_controls(),
        }

    def preview(self, preset: str, overrides: Mapping[str, Any] | None = None) -> dict[str, Any]:
        policy = self._manager.preview_policy(preset, overrides)
        return {
            "policy": policy,
            "degraded_controls": self._manager.get_degraded_controls(),
        }

    def activate(self, preset: str, overrides: Mapping[str, Any] | None = None) -> dict[str, Any]:
        policy = self._manager.activate(preset, overrides)
        return {
            "policy": policy,
            "degraded_controls": self._manager.get_degraded_controls(),
        }

    def deactivate(self) -> dict[str, Any]:
        self._manager.deactivate()
        return self.card_state()
