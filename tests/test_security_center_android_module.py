from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "packages/apps/GhostOSSecurityCenter"


class SecurityCenterAndroidModuleTests(unittest.TestCase):
    def test_soong_module_is_declared_for_system_ext(self):
        bp = (APP / "Android.bp").read_text(encoding="utf-8")
        self.assertIn('name: "GhostOSSecurityCenter"', bp)
        self.assertIn('system_ext_specific: true', bp)

    def test_manifest_declares_required_permission_and_narrow_package_visibility(self):
        manifest = (APP / "AndroidManifest.xml").read_text(encoding="utf-8")
        self.assertIn('package="org.ghostos.securitycenter"', manifest)
        self.assertIn('android.permission.ACCESS_NETWORK_STATE', manifest)
        self.assertIn('<package android:name="ch.protonvpn.android" />', manifest)
        self.assertNotIn('QUERY_ALL_PACKAGES', manifest)
        self.assertNotIn('BIND_VPN_SERVICE', manifest)

    def test_device_capabilities_use_package_manager_feature_flags(self):
        source = (APP / "src/org/ghostos/securitycenter/MainActivity.java").read_text(encoding="utf-8")
        self.assertIn("getPackageManager()", source)
        self.assertIn("manager.hasSystemFeature(feature)", source)
        for feature in (
            "FEATURE_FINGERPRINT",
            "FEATURE_FACE",
            "FEATURE_CAMERA_ANY",
            "FEATURE_MICROPHONE",
            "FEATURE_LOCATION_GPS",
            "FEATURE_WIFI",
            "FEATURE_BLUETOOTH",
            "FEATURE_NFC",
            "FEATURE_TELEPHONY",
            "FEATURE_USB_HOST",
        ):
            self.assertIn("PackageManager." + feature, source)
        self.assertIn("Presence does not prove runtime health or secure configuration", source)

    def test_vpn_lockdown_is_read_only_platform_configuration(self):
        source = (APP / "src/org/ghostos/securitycenter/MainActivity.java").read_text(encoding="utf-8")
        self.assertIn("Settings.Secure.getString(", source)
        self.assertIn("SETTING_ALWAYS_ON_VPN_APP", source)
        self.assertIn("SETTING_ALWAYS_ON_VPN_LOCKDOWN", source)
        self.assertIn('"1".equals(lockdown)', source)
        self.assertIn('"0".equals(lockdown)', source)
        self.assertIn("lockdown allowlist contents", source)
        self.assertNotIn("setAlwaysOnVpnPackage(", source)

    def test_vpn_state_is_platform_observed_and_provider_unverified(self):
        source = (APP / "src/org/ghostos/securitycenter/MainActivity.java").read_text(encoding="utf-8")
        self.assertIn("NetworkCapabilities.TRANSPORT_VPN", source)
        self.assertIn('readSystemProperty("ro.boot.verifiedbootstate"', source)
        self.assertIn('readSelinuxFlag("isSELinuxEnforced")', source)
        self.assertIn('Class.forName("android.os.SystemProperties")', source)
        self.assertIn('Class.forName("android.os.SELinux")', source)
        self.assertIn("provider/coverage unverified", source)
        self.assertIn("Settings.ACTION_VPN_SETTINGS", source)
        self.assertIn('getLaunchIntentForPackage("ch.protonvpn.android")', source)
        self.assertIn("https://github.com/ProtonVPN/android-app/releases", source)
        self.assertNotIn("VpnService", source)
        for java_file in APP.rglob("*.java"):
            module_source = java_file.read_text(encoding="utf-8")
            self.assertNotIn("VpnService", module_source)
            self.assertNotIn("VpnHood", module_source)
            self.assertNotIn("ghost-web-vpn", module_source)

    def test_product_config_installs_security_center(self):
        config = (ROOT / "vendor/ghostos/config.mk").read_text(encoding="utf-8")
        script = (ROOT / "scripts/integrate-ghostos-apps.sh").read_text(encoding="utf-8")
        self.assertIn("GhostOSSecurityCenter", config)
        self.assertIn("vendor/ghostos/config.mk", script)


if __name__ == "__main__":
    unittest.main()
