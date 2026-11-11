import unittest
from pathlib import Path

from ghostos.ghostmode.manager import AdapterError, GhostModeManager
from ghostos.ghostmode.systemui import GhostModeSystemUI


PRESETS = Path(__file__).with_name("presets.json")


class MemoryAdapter:
    supported = True

    def __init__(self):
        self.applied = []
        self.rolled_back = []

    def apply(self, policy):
        self.applied.append(policy)
        return policy

    def rollback(self, token):
        self.rolled_back.append(token)


class GhostModeSystemUITest(unittest.TestCase):
    def manager(self):
        return GhostModeManager(PRESETS, {
            name: MemoryAdapter() for name in ("network", "sensors", "clipboard", "usb", "notifications")
        })

    def test_tile_reflects_activation_and_deactivation(self):
        manager = self.manager()
        ui = GhostModeSystemUI(manager, default_preset="standard")
        self.assertFalse(ui.tile_state()["active"])
        activated = ui.click()
        self.assertTrue(activated["active"])
        self.assertEqual("standard", activated["preset"])
        deactivated = ui.click()
        self.assertFalse(deactivated["active"])

    def test_tile_reports_degraded_when_required_backends_are_unwired(self):
        manager = GhostModeManager(PRESETS)
        state = GhostModeSystemUI(manager).tile_state()
        self.assertFalse(state["active"])
        self.assertTrue(state["degraded"])
        self.assertEqual(state["degraded_controls"], ["device", "network", "privacy"])

    def test_tile_does_not_activate_when_no_adapter_matches(self):
        manager = GhostModeManager(PRESETS, {"host": MemoryAdapter()})
        ui = GhostModeSystemUI(manager, default_preset="standard")
        with self.assertRaisesRegex(AdapterError, "no matching platform adapters configured"):
            ui.click()
        self.assertFalse(ui.tile_state()["active"])

    def test_tile_stays_inactive_with_missing_policy_adapters(self):
        manager = GhostModeManager(PRESETS, {"network": MemoryAdapter()})
        ui = GhostModeSystemUI(manager, default_preset="standard")
        with self.assertRaisesRegex(AdapterError, "missing or unsupported platform adapters"):
            ui.click()
        self.assertFalse(ui.tile_state()["active"])

    def test_long_click_routes_to_privacy_center(self):
        ui = GhostModeSystemUI(self.manager())
        self.assertEqual("ghostos://privacy-center/ghostmode", ui.long_click_target())


if __name__ == "__main__":
    unittest.main()
