import unittest
from pathlib import Path

from ghostos.ghostmode.manager import GhostModeManager
from ghostos.ghostmode.privacy_center import GhostModePrivacyCenter


PRESETS = Path(__file__).with_name("presets.json")


class UnsupportedAdapter:
    supported = False

    def apply(self, policy):
        raise AssertionError("unsupported adapter must not be used in this test")


class PrivacyCenterTest(unittest.TestCase):
    def test_card_lists_presets_and_degraded_controls(self):
        manager = GhostModeManager(PRESETS, {"device": UnsupportedAdapter()})
        center = GhostModePrivacyCenter(manager)
        state = center.card_state()
        self.assertFalse(state["active"])
        self.assertGreaterEqual(len(state["presets"]), 4)
        self.assertIn("device", state["degraded_controls"])

    def test_preview_does_not_activate_policy(self):
        manager = GhostModeManager(PRESETS)
        center = GhostModePrivacyCenter(manager)
        preview = center.preview("private")
        self.assertIn("policy", preview)
        self.assertIsNone(manager.get_active_policy())

    def test_activate_and_deactivate_reflect_manager_state(self):
        manager = GhostModeManager(PRESETS)
        center = GhostModePrivacyCenter(manager)
        result = center.activate("standard")
        self.assertEqual(result["policy"], manager.get_active_policy())
        state = center.card_state()
        self.assertTrue(state["active"])
        state = center.deactivate()
        self.assertFalse(state["active"])


if __name__ == "__main__":
    unittest.main()
