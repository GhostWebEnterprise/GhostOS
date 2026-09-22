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
        return GhostModeManager(PRESETS, {"host": MemoryAdapter()})

    def test_tile_reflects_activation_and_deactivation(self):
        manager = self.manager()
        ui = GhostModeSystemUI(manager, default_preset="standard")
        self.assertFalse(ui.tile_state()["active"])
        activated = ui.click()
        self.assertTrue(activated["active"])
        self.assertEqual("standard", activated["preset"])
        deactivated = ui.click()
        self.assertFalse(deactivated["active"])

    def test_long_click_routes_to_privacy_center(self):
        ui = GhostModeSystemUI(self.manager())
        self.assertEqual("ghostos://privacy-center/ghostmode", ui.long_click_target())


if __name__ == "__main__":
    unittest.main()
