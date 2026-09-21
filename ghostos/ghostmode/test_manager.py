import tempfile
import unittest
from pathlib import Path

from manager import AdapterError, GhostModeManager, PolicyError


PRESETS = '''{"schemaVersion":1,"presets":[{"id":"standard","network":{"route":"direct"},"usb":{"lockedMode":"charge_only"}},{"id":"private","network":{"route":"direct","blockLan":true},"usb":{"lockedMode":"charge_only"}}]}'''


class Adapter:
    supported = True
    def __init__(self, fail=False):
        self.fail = fail
        self.applied = []
        self.rolled_back = []
    def apply(self, policy):
        if self.fail:
            raise RuntimeError("apply failed")
        self.applied.append(policy)
        return policy
    def rollback(self, token):
        self.rolled_back.append(token)


class GhostModeManagerTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.path = Path(self.tmp.name) / "presets.json"
        self.path.write_text(PRESETS, encoding="utf-8")
    def tearDown(self):
        self.tmp.cleanup()

    def test_preview_merges_overrides_without_mutating_preset(self):
        manager = GhostModeManager(self.path)
        preview = manager.preview_policy("private", {"network": {"blockLan": False}})
        self.assertFalse(preview["network"]["blockLan"])
        self.assertTrue(manager.preview_policy("private")["network"]["blockLan"])

    def test_unknown_preset_is_rejected(self):
        with self.assertRaises(PolicyError):
            GhostModeManager(self.path).preview_policy("missing")

    def test_failed_transition_rolls_back_prior_adapters(self):
        network = Adapter()
        usb = Adapter(fail=True)
        manager = GhostModeManager(self.path, {"network": network, "usb": usb})
        with self.assertRaises(AdapterError):
            manager.activate("standard")
        self.assertEqual(len(network.rolled_back), 1)
        self.assertIsNone(manager.get_active_policy())

    def test_capability_reports_degraded_adapter(self):
        network = Adapter()
        usb = Adapter()
        usb.supported = False
        manager = GhostModeManager(self.path, {"network": network, "usb": usb})
        self.assertEqual(manager.get_degraded_controls(), ["usb"])


if __name__ == "__main__":
    unittest.main()
