import unittest
import xml.etree.ElementTree as ET
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
ANDROID = "{http://schemas.android.com/apk/res/android}"


class GhostModeQuickSettingsTileTests(unittest.TestCase):
    def test_tile_is_declared_as_non_toggleable_quick_settings_service(self):
        manifest = ET.parse(
            ROOT / "packages/apps/GhostOSPrivacyCenter/AndroidManifest.xml"
        ).getroot()
        application = manifest.find("application")
        self.assertIsNotNone(application)

        service = application.find("service")
        self.assertIsNotNone(service)
        self.assertEqual(
            service.attrib.get(ANDROID + "name"),
            ".GhostModeQuickSettingsTile",
        )
        self.assertEqual(
            service.attrib.get(ANDROID + "permission"),
            "android.permission.BIND_QUICK_SETTINGS_TILE",
        )
        self.assertEqual(service.attrib.get(ANDROID + "exported"), "true")

        actions = [
            action.attrib.get(ANDROID + "name")
            for action in service.findall(".//action")
        ]
        self.assertIn(
            "android.service.quicksettings.action.QS_TILE",
            actions,
        )

        toggleable = service.find("meta-data")
        self.assertIsNotNone(toggleable)
        self.assertEqual(
            toggleable.attrib.get(ANDROID + "value"),
            "false",
        )

    def test_tile_reflects_only_verified_policy_state_without_mutating_policy(self):
        source = (
            ROOT
            / "packages/apps/GhostOSPrivacyCenter/src/org/ghostos/privacy/"
            "GhostModeQuickSettingsTile.java"
        ).read_text(encoding="utf-8")
        strings = (
            ROOT
            / "packages/apps/GhostOSPrivacyCenter/res/values/strings.xml"
        ).read_text(encoding="utf-8")

        self.assertIn("new GhostModeDevicePolicyAdapter(this)", source)
        self.assertIn("new GhostModeAndroidPolicyManager(this, adapter)", source)
        self.assertIn("manager.getState()", source)
        self.assertIn("GhostModeAndroidPolicyManager.State.ACTIVE", source)
        self.assertIn("GhostModeAndroidPolicyManager.State.RECOVERY_REQUIRED", source)
        self.assertIn("GhostModeAndroidPolicyManager.State.UNAVAILABLE", source)
        self.assertIn("tile.setState(Tile.STATE_ACTIVE)", source)
        self.assertIn("Tile.STATE_INACTIVE", source)
        self.assertNotIn("applyControl(", source)
        self.assertIn("ghostmode_tile_active_subtitle", source)
        self.assertIn("ghostmode_tile_recovery_subtitle", source)
        self.assertIn("ghostmode_tile_unavailable_subtitle", source)
        self.assertIn("startActivityAndCollapse(pendingIntent)", source)
        self.assertIn("@SuppressWarnings(\"deprecation\")", source)
        self.assertIn("startActivityAndCollapseLegacy(intent)", source)
        self.assertIn("startActivityAndCollapse(intent)", source)

        self.assertIn("Setup required", strings)
        self.assertIn("Restrictions active", strings)
        self.assertIn("Recovery required", strings)
        self.assertIn("Device Owner required", strings)


if __name__ == "__main__":
    unittest.main()
