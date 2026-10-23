"""Host-side GhostMode rollback and recovery regression tests; no device assertions."""
import json
import tempfile
import unittest
from pathlib import Path

from ghostos.ghostmode.android_adapters import AndroidPolicyAdapter
from ghostos.ghostmode.manager import AdapterError, GhostModeManager
from ghostos.ghostmode.privacy_center import GhostModePrivacyCenter
from ghostos.ghostmode.systemui import GhostModeSystemUI


class Backend:
    def __init__(self, name, history, fail_apply=False, fail_restore=0):
        self.name = name
        self.history = history
        self.value = "default"
        self.fail_apply = fail_apply
        self.fail_restore = fail_restore

    def read(self):
        return self.value

    def apply(self, policy):
        self.value = policy["state"]
        self.history.append("apply:" + self.name)
        if self.fail_apply:
            raise RuntimeError("partial apply")

    def restore(self, snapshot):
        self.history.append("restore:" + self.name)
        if self.fail_restore:
            self.fail_restore -= 1
            raise RuntimeError("restore failed")
        self.value = snapshot


class RollbackRecoveryTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.path = Path(self.temp.name) / "presets.json"
        self.path.write_text(json.dumps({"presets": [
            {"id": "private", **{n: {"state": "restricted"} for n in ("privacy", "device", "network")}},
            {"id": "standard", **{n: {"state": "default"} for n in ("privacy", "device", "network")}}
        ]}))
        self.history = []
        self.backends = {n: Backend(n, self.history) for n in ("privacy", "device", "network")}
        self.manager = GhostModeManager(self.path, {
            n: AndroidPolicyAdapter(n, backend) for n, backend in self.backends.items()
        })

    def test_deactivation_restores_in_reverse_order(self):
        self.manager.activate("private")
        self.manager.deactivate()
        self.assertEqual(self.history, [
            "apply:privacy", "apply:device", "apply:network",
            "restore:network", "restore:device", "restore:privacy"
        ])
        self.assertTrue(all(b.value == "default" for b in self.backends.values()))

    def test_partial_apply_restores_prior_adapters(self):
        self.backends["network"].fail_apply = True
        with self.assertRaises(AdapterError):
            self.manager.activate("private")
        self.assertTrue(all(b.value == "default" for b in self.backends.values()))
        self.assertIsNone(self.manager.get_active_policy())

    def test_failed_rollback_is_visible_and_recoverable(self):
        self.backends["device"].fail_restore = 1
        self.manager.activate("private")
        with self.assertRaises(AdapterError):
            self.manager.deactivate()
        self.assertTrue(self.manager.get_recovery_required())
        self.assertTrue(GhostModePrivacyCenter(self.manager).card_state()["recovery_required"])
        self.assertTrue(GhostModeSystemUI(self.manager).tile_state()["degraded"])
        self.manager.deactivate()
        self.assertFalse(self.manager.get_recovery_required())
        self.assertTrue(all(b.value == "default" for b in self.backends.values()))

    def test_partial_apply_unrestored_snapshot_can_retry(self):
        self.backends["network"].fail_apply = True
        self.backends["network"].fail_restore = 2
        self.backends["device"].fail_restore = 1
        with self.assertRaises(AdapterError):
            self.manager.activate("private")
        self.assertTrue(self.manager.get_recovery_required())
        self.manager.deactivate()
        self.assertFalse(self.manager.get_recovery_required())
        self.assertTrue(all(b.value == "default" for b in self.backends.values()))

    def test_switching_active_presets_is_refused(self):
        self.manager.activate("private")
        with self.assertRaises(AdapterError):
            self.manager.activate("standard")
        self.manager.deactivate()
        self.manager.activate("standard")
        self.assertEqual(self.manager.get_active_policy()["id"], "standard")


if __name__ == "__main__":
    unittest.main()
