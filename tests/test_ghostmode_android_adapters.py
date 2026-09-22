import unittest

from ghostos.ghostmode.android_adapters import (
    DevicePolicyAdapter,
    NetworkPolicyAdapter,
    PrivacyPolicyAdapter,
)


class FakeBackend:
    def __init__(self):
        self.state = {"enabled": False}

    def read(self):
        return dict(self.state)

    def apply(self, policy):
        self.state = dict(policy)

    def restore(self, snapshot):
        self.state = dict(snapshot)


class AndroidAdapterTests(unittest.TestCase):
    def test_unwired_adapters_report_unsupported(self):
        self.assertFalse(NetworkPolicyAdapter().supported)
        self.assertFalse(PrivacyPolicyAdapter().supported)
        self.assertFalse(DevicePolicyAdapter().supported)

    def test_unwired_adapter_fails_instead_of_fabricating_success(self):
        with self.assertRaises(RuntimeError):
            PrivacyPolicyAdapter().apply({"camera": "blocked"})

    def test_apply_returns_snapshot_and_rollback_restores_it(self):
        backend = FakeBackend()
        adapter = DevicePolicyAdapter(backend)
        snapshot = adapter.apply({"usb": "locked-only"})
        self.assertEqual(backend.state, {"usb": "locked-only"})
        adapter.rollback(snapshot)
        self.assertEqual(backend.state, {"enabled": False})


if __name__ == "__main__":
    unittest.main()
