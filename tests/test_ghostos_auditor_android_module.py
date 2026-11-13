from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "packages/apps/GhostOSAuditor"
SECURITY_APP = ROOT / "packages/apps/GhostOSSecurityCenter"


class GhostOSAuditorAndroidModuleTests(unittest.TestCase):
    def test_soong_module_is_a_platform_signed_system_ext_app(self):
        bp = (APP / "Android.bp").read_text(encoding="utf-8")
        self.assertIn('name: "GhostOSAuditor"', bp)
        self.assertIn("platform_apis: true", bp)
        self.assertIn('certificate: "platform"', bp)
        self.assertIn("system_ext_specific: true", bp)

    def test_manifest_has_launcher_and_only_read_only_update_permission(self):
        manifest = (APP / "AndroidManifest.xml").read_text(encoding="utf-8")
        self.assertIn('package="org.ghostos.auditor"', manifest)
        self.assertIn('android:name=".MainActivity"', manifest)
        self.assertIn('android:exported="true"', manifest)
        self.assertIn("android.permission.READ_SYSTEM_UPDATE_INFO", manifest)
        self.assertNotIn("android.permission.RECOVERY", manifest)
        self.assertNotIn("android.permission.INSTALL_PACKAGES", manifest)
        self.assertNotIn("android.permission.UPDATE_CONFIG", manifest)
        self.assertNotIn("QUERY_ALL_PACKAGES", manifest)

    def test_platform_observations_and_limitations_are_explicit(self):
        source = (APP / "src/org/ghostos/auditor/MainActivity.java").read_text(encoding="utf-8")
        model = (APP / "src/org/ghostos/auditor/AuditSnapshot.java").read_text(encoding="utf-8")
        for item in ("android.os.SystemProperties", "android.os.SELinux", "KeyguardManager",
                     "Settings.Global.ADB_ENABLED"):
            self.assertIn(item, source)
        self.assertIn("PackageManager", source)
        self.assertIn("hasSystemFeature", source)
        self.assertIn("Feature declaration only", model)
        self.assertIn("does not independently prove payload signature verification", model)
        self.assertIn('getSystemService("system_update")', source)
        self.assertIn("retrieveSystemUpdateInfo", source)
        self.assertIn(r'detail.append("\\n")', source)
        self.assertNotIn("updateSystemUpdateInfo", source)
        self.assertNotIn("applyPayload", source)
        self.assertNotIn("resetStatus", source)
        for item in ("ro.boot.verifiedbootstate", "ro.boot.flash.locked",
                     "independent AVB/vbmeta verification", "ro.boot.vbmeta.digest",
                     "ro.boot.vbmeta.avb_version", "ro.boot.slot_suffix",
                     "SystemUpdateManager.retrieveSystemUpdateInfo", "State.UNKNOWN"):
            self.assertIn(item, model)

    def test_missing_evidence_is_not_promoted_to_pass_or_fail(self):
        test = (ROOT / "tests/java/org/ghostos/auditor/AuditSnapshotSelfTest.java").read_text(encoding="utf-8")
        self.assertIn("missing evidence must not become PASS/FAIL", test)
        self.assertIn('verifiedBoot("", "")', test)
        self.assertIn("selinux(true, null)", test)

    def test_security_center_links_to_auditor_with_visibility(self):
        manifest = (SECURITY_APP / "AndroidManifest.xml").read_text(encoding="utf-8")
        source = (SECURITY_APP / "src/org/ghostos/securitycenter/MainActivity.java").read_text(encoding="utf-8")
        self.assertIn('<package android:name="org.ghostos.auditor" />', manifest)
        self.assertIn('getLaunchIntentForPackage("org.ghostos.auditor")', source)
        self.assertIn("Auditor is not installed", source)

    def test_larry_product_overlay_copies_and_installs_auditor(self):
        config = (ROOT / "vendor/ghostos/config.mk").read_text(encoding="utf-8")
        script = (ROOT / "scripts/integrate-ghostos-apps.sh").read_text(encoding="utf-8")
        self.assertIn("GhostOSAuditor", config)
        self.assertIn("packages/apps/GhostOSAuditor", script)

    def test_auditor_has_no_vpn_or_network_client(self):
        for path in APP.rglob("*.java"):
            source = path.read_text(encoding="utf-8")
            for forbidden in ("VpnService", "VpnHood", "ghost-web-vpn", "ch.protonvpn.android",
                              "java.net.", "android.net.ConnectivityManager"):
                self.assertNotIn(forbidden, source)


if __name__ == "__main__":
    unittest.main()
