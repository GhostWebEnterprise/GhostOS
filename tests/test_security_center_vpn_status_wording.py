from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "packages/apps/GhostOSSecurityCenter/src/org/ghostos/securitycenter/MainActivity.java"


class SecurityCenterVpnStatusWordingTests(unittest.TestCase):
    def test_vpn_status_uses_only_active_default_network_evidence(self):
        source = SOURCE.read_text(encoding="utf-8")
        evidence = (SOURCE.parent / "VpnTransportEvidence.java").read_text(encoding="utf-8")
        self.assertIn("manager.getActiveNetwork()", source)
        self.assertIn("manager.getNetworkCapabilities(activeNetwork)", source)
        self.assertNotIn("manager.getAllNetworks()", source)
        self.assertIn("NetworkCapabilities.TRANSPORT_VPN", source)
        self.assertIn("VpnTransportEvidence.classify(false, false, false)", source)
        self.assertIn("VpnTransportEvidence.classify(true, false, false)", source)
        self.assertIn("VpnTransportEvidence.unavailable(", source)
        self.assertIn("VPN_TRANSPORT_OBSERVED", evidence)
        self.assertIn("NO_VPN_TRANSPORT_OBSERVED", evidence)
        self.assertIn("State.UNKNOWN", evidence)
        self.assertIn("other protection not assessed", evidence)
        self.assertIn("provider/coverage unverified", evidence)
        self.assertIn("per-app coverage", evidence)
        self.assertIn("lockdown", evidence)
        self.assertNotIn("VpnService", source)


if __name__ == "__main__":
    unittest.main()
