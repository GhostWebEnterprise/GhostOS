from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "packages/apps/GhostOSSecurityCenter"


class SecurityCenterAndroidModuleTests(unittest.TestCase):
    def test_soong_module_is_declared_for_system_ext(self):
        bp = (APP / "Android.bp").read_text(encoding="utf-8")
        self.assertIn('name: "GhostOSSecurityCenter"', bp)
        self.assertIn('system_ext_specific: true', bp)

    def test_manifest_declares_only_required_network_permission(self):
        manifest = (APP / "AndroidManifest.xml").read_text(encoding="utf-8")
        self.assertIn('package="org.ghostos.securitycenter"', manifest)
        self.assertIn('android.permission.ACCESS_NETWORK_STATE', manifest)
        self.assertNotIn('BIND_VPN_SERVICE', manifest)

    def test_vpn_state_is_platform_observed_and_provider_unverified(self):
        source = (APP / "src/org/ghostos/securitycenter/MainActivity.java").read_text(encoding="utf-8")
        self.assertIn("NetworkCapabilities.TRANSPORT_VPN", source)
        self.assertIn("provider unverified", source)
        self.assertIn("Settings.ACTION_VPN_SETTINGS", source)
        self.assertNotIn("VpnService", source)

    def test_product_config_installs_security_center(self):
        config = (ROOT / "vendor/ghostos/config.mk").read_text(encoding="utf-8")
        script = (ROOT / "scripts/integrate-ghostos-apps.sh").read_text(encoding="utf-8")
        self.assertIn("GhostOSSecurityCenter", config)
        self.assertIn("vendor/ghostos/config.mk", script)


if __name__ == "__main__":
    unittest.main()
