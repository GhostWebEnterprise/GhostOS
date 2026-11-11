import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts/integrate-ghostos-apps.sh"


class GhostOSIntegrationScriptTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.android = Path(self.temp.name)
        device = self.android / "device/oneplus/larry"
        device.mkdir(parents=True)
        (device / "device.mk").write_text("# upstream larry device config\n", encoding="utf-8")

    def tearDown(self):
        self.temp.cleanup()

    def run_script(self, device="larry"):
        env = os.environ.copy()
        env["DEVICE"] = device
        return subprocess.run(["bash", str(SCRIPT), str(self.android)], env=env, text=True, capture_output=True)

    def test_copies_app_and_installs_product_package(self):
        result = self.run_script()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertTrue((self.android / "packages/apps/GhostOSSecurityCenter/Android.bp").is_file())
        config = (self.android / "vendor/ghostos/config.mk").read_text(encoding="utf-8")
        self.assertIn("GhostOSSecurityCenter", config)
        device_mk = (self.android / "device/oneplus/larry/device.mk").read_text(encoding="utf-8")
        self.assertIn("inherit-product, vendor/ghostos/config.mk", device_mk)

    def test_integration_is_idempotent(self):
        first = self.run_script()
        second = self.run_script()
        self.assertEqual(first.returncode, 0, first.stderr)
        self.assertEqual(second.returncode, 0, second.stderr)
        device_mk = (self.android / "device/oneplus/larry/device.mk").read_text(encoding="utf-8")
        self.assertEqual(device_mk.count("inherit-product, vendor/ghostos/config.mk"), 1)

    def test_other_devices_fail_explicitly(self):
        result = self.run_script(device="oscaro")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("only for larry", result.stderr)


if __name__ == "__main__":
    unittest.main()
