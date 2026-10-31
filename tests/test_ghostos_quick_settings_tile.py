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

    def test_tile_never_claims_ghostmode_is_active(self):
        source = (
            ROOT
            / "packages/apps/GhostOSPrivacyCenter/src/org/ghostos/privacy/"
            "GhostModeQuickSettingsTile.java"
        ).read_text(encoding="utf-8")
        strings = (
            ROOT
            / "packages/apps/GhostOSPrivacyCenter/res/values/strings.xml"
        ).read_text(encoding="utf-8")

        self.assertIn("Tile.STATE_INACTIVE", source)
        self.assertNotIn("Tile.STATE_ACTIVE", source)
        self.assertIn("ghostmode_tile_subtitle", source)
        self.assertIn("Setup required", strings)
        self.assertIn("Remaining GhostMode controls are not connected", strings)
        self.assertIn("startActivityAndCollapse", source)


if __name__ == "__main__":
    unittest.main()
