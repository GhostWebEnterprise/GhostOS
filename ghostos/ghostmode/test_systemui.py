import unittest
from pathlib import Path

from ghostos.ghostmode.manager import GhostModeManager
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

    def test_tile_state_reflects_verified_activation_and_deactivation(self):
        manager = self.manager()
        ui = GhostModeSystemUI(manager)
        self.assertFalse(ui.tile_state()["active"])

        manager.activate("standard")
        self.assertTrue(ui.tile_state()["active"])
        self.assertEqual("standard", ui.tile_state()["preset"])

        manager.deactivate()
        self.assertFalse(ui.tile_state()["active"])

    def test_click_opens_privacy_center_without_mutating_policy(self):
        manager = self.manager()
        ui = GhostModeSystemUI(manager)

        result = ui.click()

        self.assertEqual("open_privacy_center", result["action"])
        self.assertEqual("ghostos://privacy-center/ghostmode", result["target"])
        self.assertFalse(result["active"])
        self.assertIsNone(manager.get_active_policy())

    def test_click_does_not_deactivate_an_active_policy(self):
        manager = self.manager()
        manager.activate("standard")
        before = manager.get_active_policy()

        result = GhostModeSystemUI(manager).click()

        self.assertTrue(result["active"])
        self.assertEqual(before, manager.get_active_policy())

    def test_tile_reports_degraded_when_required_backends_are_unwired(self):
        manager = GhostModeManager(PRESETS)
        state = GhostModeSystemUI(manager).tile_state()
        self.assertFalse(state["active"])
        self.assertTrue(state["degraded"])
        self.assertEqual(state["degraded_controls"], ["device", "network", "privacy"])

    def test_click_remains_available_when_policy_adapters_are_unwired(self):
        manager = GhostModeManager(PRESETS)

        result = GhostModeSystemUI(manager).click()

        self.assertEqual("open_privacy_center", result["action"])
        self.assertEqual("ghostos://privacy-center/ghostmode", result["target"])
        self.assertFalse(result["active"])
        self.assertTrue(result["degraded"])
        self.assertIsNone(manager.get_active_policy())


if __name__ == "__main__":
    unittest.main()
