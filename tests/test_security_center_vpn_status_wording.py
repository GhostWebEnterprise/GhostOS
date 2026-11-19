from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "packages/apps/GhostOSSecurityCenter"
SOURCE = APP / "src/org/ghostos/securitycenter/MainActivity.java"


class SecurityCenterEvidenceTests(unittest.TestCase):
    def test_vpn_status_uses_only_active_default_network_evidence(self):
        source = SOURCE.read_text(encoding="utf-8")
        evidence = (SOURCE.parent / "VpnTransportEvidence.java").read_text(encoding="utf-8")
        self.assertIn("manager.getActiveNetwork()", source)
        self.assertIn("manager.getNetworkCapabilities(activeNetwork)", source)
        self.assertNotIn("manager.getAllNetworks()", source)
        self.assertIn("NetworkCapabilities.TRANSPORT_VPN", source)
        self.assertIn("VpnTransportEvidence.classify(", source)
        self.assertIn("VpnTransportEvidence.unavailable(", source)
        self.assertIn("State.UNKNOWN", evidence)
        self.assertIn("provider/coverage unverified", evidence)
        self.assertIn("other protection not assessed", evidence)
        self.assertNotIn("if (capabilities != null", source)
        self.assertNotIn("VpnService", source)

    def test_boot_evidence_is_conservative_and_not_independent_avb_verification(self):
        source = SOURCE.read_text(encoding="utf-8")
        evidence = (SOURCE.parent / "VerifiedBootEvidence.java").read_text(encoding="utf-8")
        self.assertIn("VerifiedBootEvidence.classify(", source)
        self.assertIn("Inconsistent boot-state properties", evidence)
        self.assertIn("AVB not independently verified", evidence)
        self.assertIn("State.UNKNOWN", evidence)
        self.assertIn(
            "Platform-reported boot properties only. Green/locked is not independent vbmeta signature verification.",
            source,
        )

    def test_lockdown_settings_are_read_only_and_permission_is_declared(self):
        source = SOURCE.read_text(encoding="utf-8")
        manifest = (APP / "AndroidManifest.xml").read_text(encoding="utf-8")
        bp = (APP / "Android.bp").read_text(encoding="utf-8")
        self.assertIn("Settings.Secure.getString(", source)
        self.assertIn("SETTING_ALWAYS_ON_VPN_APP", source)
        self.assertIn("SETTING_ALWAYS_ON_VPN_LOCKDOWN", source)
        self.assertIn("android.permission.READ_SECURE_SETTINGS", manifest)
        self.assertIn('certificate: "platform"', bp)
        self.assertNotIn("android.permission.WRITE_SECURE_SETTINGS", manifest)
        self.assertNotIn("setAlwaysOnVpnPackage(", source)


if __name__ == "__main__":
    unittest.main()
