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

    def test_repository_presets_route_to_available_android_adapter_contracts(self):
        presets_path = Path(__file__).with_name("presets.json")
        network = Adapter()
        privacy = Adapter()
        device = Adapter()
        manager = GhostModeManager(presets_path, {
            "network": network,
            "privacy": privacy,
            "device": device,
        })

        policy = manager.activate("private")

        self.assertEqual(network.applied, [policy["network"]])
        self.assertEqual(privacy.applied, [{
            "sensors": policy["sensors"],
            "clipboard": policy["clipboard"],
        }])
        self.assertEqual(device.applied, [{
            "usb": policy["usb"],
            "notifications": policy["notifications"],
        }])
        manager.deactivate()
        self.assertIsNone(manager.get_active_policy())

    def test_unknown_preset_is_rejected(self):
        with self.assertRaises(PolicyError):
            GhostModeManager(self.path).preview_policy("missing")

    def test_activation_without_adapters_fails_closed(self):
        manager = GhostModeManager(self.path)
        with self.assertRaisesRegex(AdapterError, "no platform adapters configured"):
            manager.activate("standard")
        self.assertIsNone(manager.get_active_policy())
        self.assertFalse(manager.get_recovery_required())

    def test_activation_with_unrelated_adapter_fails_closed(self):
        unrelated = Adapter()
        manager = GhostModeManager(self.path, {"notifications": unrelated})
        with self.assertRaisesRegex(AdapterError, "no matching platform adapters configured"):
            manager.activate("standard")
        self.assertEqual(unrelated.applied, [])
        self.assertIsNone(manager.get_active_policy())
        self.assertFalse(manager.get_recovery_required())

    def test_partial_adapter_set_does_not_claim_full_policy_active(self):
        network = Adapter()
        manager = GhostModeManager(self.path, {"network": network})
        with self.assertRaisesRegex(AdapterError, "missing or unsupported platform adapters"):
            manager.activate("private")
        self.assertEqual(network.applied, [])
        self.assertIsNone(manager.get_active_policy())
        self.assertFalse(manager.get_recovery_required())

    def test_unsupported_adapter_blocks_before_any_platform_changes(self):
        network = Adapter()
        usb = Adapter()
        usb.supported = False
        manager = GhostModeManager(self.path, {"network": network, "usb": usb})
        with self.assertRaisesRegex(AdapterError, "missing or unsupported platform adapters"):
            manager.activate("standard")
        self.assertEqual(network.applied, [])
        self.assertEqual(usb.applied, [])

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


    def test_missing_adapter_categories_are_reported_as_degraded(self):
        manager = GhostModeManager(Path(__file__).with_name("presets.json"))
        self.assertEqual(manager.get_capabilities(), {
            "device": False,
            "network": False,
            "privacy": False,
        })
        self.assertEqual(manager.get_degraded_controls(), ["device", "network", "privacy"])

    def test_deactivation_restores_adapters_in_reverse_order(self):
        order = []

        class TrackedAdapter(Adapter):
            def __init__(self, name):
                super().__init__()
                self.name = name

            def rollback(self, token):
                order.append(self.name)
                super().rollback(token)

        network = TrackedAdapter("network")
        usb = TrackedAdapter("usb")
        manager = GhostModeManager(self.path, {"network": network, "usb": usb})
        manager.activate("standard")
        manager.deactivate()
        self.assertEqual(order, ["usb", "network"])
        self.assertEqual(len(network.rolled_back), 1)
        self.assertEqual(len(usb.rolled_back), 1)
        self.assertIsNone(manager.get_active_policy())

    def test_failed_deactivation_keeps_active_state_and_can_retry(self):
        class FlakyAdapter(Adapter):
            fail_rollback = True

            def rollback(self, token):
                if self.fail_rollback:
                    raise RuntimeError("restore unavailable")
                super().rollback(token)

        network = Adapter()
        usb = FlakyAdapter()
        manager = GhostModeManager(self.path, {"network": network, "usb": usb})
        manager.activate("standard")
        with self.assertRaises(AdapterError):
            manager.deactivate()
        self.assertIsNotNone(manager.get_active_policy())
        self.assertEqual(len(network.rolled_back), 1)
        usb.fail_rollback = False
        manager.deactivate()
        self.assertEqual(len(usb.rolled_back), 1)
        self.assertEqual(len(network.rolled_back), 1)
        self.assertIsNone(manager.get_active_policy())

    def test_active_preset_cannot_discard_rollback_snapshots(self):
        manager = GhostModeManager(self.path, {"network": Adapter(), "usb": Adapter()})
        manager.activate("standard")
        with self.assertRaises(AdapterError):
            manager.activate("private")
        self.assertEqual(manager.get_active_policy()["id"], "standard")
        manager.deactivate()
        self.assertIsNone(manager.get_active_policy())


if __name__ == "__main__":
    unittest.main()
