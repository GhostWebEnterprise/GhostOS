from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "packages/apps/GhostOSSecurityCenter/src/org/ghostos/securitycenter/MainActivity.java"


class SecurityCenterVpnStatusWordingTests(unittest.TestCase):
    def test_vpn_transport_is_not_reported_as_device_wide_protection(self):
        source = SOURCE.read_text(encoding="utf-8")
        self.assertIn(
            'return "VPN transport detected · provider/coverage unverified";',
            source,
        )
        self.assertIn(
            "does not prove the default route, all-app coverage or lockdown is active",
            source,
        )
        self.assertNotIn(
            'return "VPN active · provider unverified";',
            source,
        )


if __name__ == "__main__":
    unittest.main()
