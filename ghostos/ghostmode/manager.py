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
        self._applied: list[tuple[Any, Any]] = []
        self._recovery_required = False

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

    def get_recovery_required(self) -> bool:
        return self._recovery_required

    def preview_policy(self, preset: str, overrides: Mapping[str, Any] | None = None) -> dict[str, Any]:
        key = preset.strip().lower()
        if key not in self._presets:
            raise PolicyError(f"unknown preset: {preset}")
        effective = copy.deepcopy(self._presets[key])
        if overrides:
            self._deep_merge(effective, overrides)
        return effective

    def _adapter_policies(self, policy: Mapping[str, Any]) -> dict[str, dict[str, Any]]:
        """Route the public preset schema to the available policy adapters.

        Production presets keep controls grouped as network/sensors/clipboard/
        usb/notifications. The Android adapter contract groups sensors and
        clipboard under privacy, and USB/notifications under device. Existing
        direct adapter sections remain supported for focused host tests.
        """
        sections = {
            name: value
            for name, value in policy.items()
            if isinstance(value, dict) and value
        }
        if "privacy" in sections and any(name in sections for name in ("sensors", "clipboard")):
            raise AdapterError("ambiguous GhostMode privacy sections")
        if "device" in sections and any(name in sections for name in ("usb", "notifications")):
            raise AdapterError("ambiguous GhostMode device sections")

        routed: dict[str, dict[str, Any]] = {}
        for name, section in sections.items():
            if name in self._adapters:
                routed[name] = copy.deepcopy(section)
            elif name in ("sensors", "clipboard"):
                routed.setdefault("privacy", {})[name] = copy.deepcopy(section)
            elif name in ("usb", "notifications"):
                routed.setdefault("device", {})[name] = copy.deepcopy(section)
            elif name in ("network", "privacy", "device"):
                routed[name] = copy.deepcopy(section)
            else:
                raise AdapterError(f"no adapter mapping for policy section: {name}")
        return routed

    def activate(self, preset: str, overrides: Mapping[str, Any] | None = None) -> dict[str, Any]:
        if self._active is not None:
            raise AdapterError("deactivate GhostMode before changing presets")
        target = self.preview_policy(preset, overrides)
        if not self._adapters:
            raise AdapterError("no platform adapters configured; policy was not applied")
        adapter_policies = self._adapter_policies(target)
        required = set(adapter_policies)
        if not any(name in self._adapters for name in required):
            raise AdapterError("no matching platform adapters configured; policy was not applied")
        # Preflight every adapter required by the real preset schema. A partial
        # adapter set must never report the full policy as active.
        unavailable = sorted(
            name for name in required
            if name not in self._adapters or not bool(getattr(self._adapters[name], "supported", True))
        )
        if unavailable:
            raise AdapterError(
                f"missing or unsupported platform adapters for: {', '.join(unavailable)}; policy was not applied"
            )
        applied: list[tuple[Any, Any]] = []
        try:
            for name, adapter in self._adapters.items():
                section = adapter_policies.get(name)
                if section is None:
                    continue
                try:
                    token = adapter.apply(copy.deepcopy(section))
                except Exception as exc:
                    if getattr(exc, "rollback_required", False):
                        applied.append((adapter, exc.rollback_token))
                    raise
                applied.append((adapter, token))
        except Exception as exc:
            failed = self._rollback(applied)
            if failed:
                self._active = target  # conservative: platform may still be modified
                self._applied = failed
                self._recovery_required = True
                raise AdapterError("GhostMode activation rollback incomplete") from exc
            raise AdapterError("GhostMode transition rolled back") from exc
        self._applied = applied
        self._active = target
        self._recovery_required = False
        return copy.deepcopy(target)

    @staticmethod
    def _rollback(applied: list[tuple[Any, Any]]) -> list[tuple[Any, Any]]:
        failed: list[tuple[Any, Any]] = []
        for adapter, token in reversed(applied):
            try:
                adapter.rollback(token)
            except Exception:
                failed.append((adapter, token))
        return list(reversed(failed))

    def deactivate(self) -> None:
        if self._active is None:
            return
        failed = self._rollback(self._applied)
        if failed:
            self._applied = failed
            self._recovery_required = True
            raise AdapterError("GhostMode deactivation rollback incomplete")
        self._applied = []
        self._active = None
        self._recovery_required = False

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
