"""Host-testable GhostMode policy manager foundation.

Android adapters will implement the same apply/rollback contract in the framework layer.
This module deliberately contains no VPN integration.
"""
from __future__ import annotations

import copy
import json
from pathlib import Path
from typing import Any, Mapping


class PolicyError(ValueError):
    pass


class AdapterError(RuntimeError):
    pass


class GhostModeManager:
    def __init__(self, presets_path: str | Path, adapters: Mapping[str, Any] | None = None):
        raw = json.loads(Path(presets_path).read_text(encoding="utf-8"))
        self._presets = self._validate_presets(raw)
        self._adapters = dict(adapters or {})
        self._active: dict[str, Any] | None = None
        self._snapshot: dict[str, Any] | None = None

    @staticmethod
    def _validate_presets(raw: Any) -> dict[str, dict[str, Any]]:
        if not isinstance(raw, dict):
            raise PolicyError("preset document must be an object")
        presets = raw.get("presets")
        if not isinstance(presets, list) or not presets:
            raise PolicyError("presets must be a non-empty list")
        result: dict[str, dict[str, Any]] = {}
        for preset in presets:
            if not isinstance(preset, dict):
                raise PolicyError("each preset must be an object")
            name = preset.get("id") or preset.get("name")
            if not isinstance(name, str) or not name.strip():
                raise PolicyError("each preset requires id or name")
            key = name.strip().lower()
            if key in result:
                raise PolicyError(f"duplicate preset: {name}")
            result[key] = copy.deepcopy(preset)
        return result

    def get_presets(self) -> list[dict[str, Any]]:
        return copy.deepcopy(list(self._presets.values()))

    def get_active_policy(self) -> dict[str, Any] | None:
        return copy.deepcopy(self._active)

    def preview_policy(self, preset: str, overrides: Mapping[str, Any] | None = None) -> dict[str, Any]:
        key = preset.strip().lower()
        if key not in self._presets:
            raise PolicyError(f"unknown preset: {preset}")
        effective = copy.deepcopy(self._presets[key])
        if overrides:
            self._deep_merge(effective, overrides)
        return effective

    def activate(self, preset: str, overrides: Mapping[str, Any] | None = None) -> dict[str, Any]:
        target = self.preview_policy(preset, overrides)
        previous = copy.deepcopy(self._active)
        applied: list[tuple[Any, Any]] = []
        try:
            for name, adapter in self._adapters.items():
                section = target.get(name)
                if section is None:
                    continue
                token = adapter.apply(copy.deepcopy(section))
                applied.append((adapter, token))
        except Exception as exc:
            for adapter, token in reversed(applied):
                adapter.rollback(token)
            raise AdapterError("GhostMode transition rolled back") from exc
        self._snapshot = previous
        self._active = target
        return copy.deepcopy(target)

    def deactivate(self) -> None:
        self._active = copy.deepcopy(self._snapshot)
        self._snapshot = None

    def get_capabilities(self) -> dict[str, bool]:
        return {name: bool(getattr(adapter, "supported", True)) for name, adapter in self._adapters.items()}

    def get_degraded_controls(self) -> list[str]:
        return [name for name, supported in self.get_capabilities().items() if not supported]

    @classmethod
    def _deep_merge(cls, target: dict[str, Any], overrides: Mapping[str, Any]) -> None:
        for key, value in overrides.items():
            if isinstance(value, Mapping) and isinstance(target.get(key), dict):
                cls._deep_merge(target[key], value)
            else:
                target[key] = copy.deepcopy(value)
