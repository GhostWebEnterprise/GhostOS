import json
import unittest
from pathlib import Path

from ghostos.ghostmode.manager import GhostModeManager, MemoryAdapter
from ghostos.ghostmode.systemui import GhostModeSystemUI


class GhostModeSystemUITest(unittest.TestCase):
    def manager(self):
        presets = json.loads(Path("ghostos/ghostmode/presets.json").read_text())
        return GhostModeManager(presets, [MemoryAdapter("host", supported=True)])

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
