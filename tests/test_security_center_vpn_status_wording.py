from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "packages/apps/GhostOSSecurityCenter/src/org/ghostos/securitycenter/MainActivity.java"


class SecurityCenterVpnStatusWordingTests(unittest.TestCase):
    def test_vpn_status_reports_only_the_active_default_network(self):
        source = SOURCE.read_text(encoding="utf-8")
        self.assertIn("manager.getActiveNetwork()", source)
        self.assertIn("manager.getNetworkCapabilities(activeNetwork)", source)
        self.assertNotIn("manager.getAllNetworks()", source)
        self.assertIn(
            'return "Default network uses VPN transport · provider/coverage unverified";',
            source,
        )
        self.assertIn(
            'return "Default network does not report VPN transport";',
            source,
        )
        self.assertIn(
            "does not prove all-app coverage, provider identity or lockdown is active",
            source,
        )
        self.assertNotIn(
            'return "VPN active · provider unverified";',
            source,
        )


    def test_boot_and_selinux_evidence_is_scoped(self):
        source = SOURCE.read_text(encoding="utf-8")
        self.assertIn(
            "Platform-reported boot properties only. Green/locked is not independent vbmeta signature verification.",
            source,
        )
        self.assertIn(
            "Platform reports green state · bootloader locked · AVB not independently verified",
            source,
        )
        self.assertIn(
            "Enforcement mode from Android SELinux framework; policy quality and rule coverage are not assessed here.",
            source,
        )
        self.assertNotIn("Verified Boot: green · bootloader locked", source)
        self.assertNotIn("Read from the Android SELinux framework API.", source)


if __name__ == "__main__":
    unittest.main()
