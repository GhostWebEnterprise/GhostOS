import unittest
import xml.etree.ElementTree as ET
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
ANDROID = "{http://schemas.android.com/apk/res/android}"


class GhostModeDevicePolicyAdapterTests(unittest.TestCase):
    def test_device_admin_receiver_is_explicit_and_bound_by_android(self):
        manifest = ET.parse(
            ROOT / "packages/apps/GhostOSPrivacyCenter/AndroidManifest.xml"
        ).getroot()
        application = manifest.find("application")
        receiver = application.find("receiver")
        self.assertIsNotNone(receiver)
        self.assertEqual(
            receiver.attrib.get(ANDROID + "name"),
            ".GhostModeDeviceAdminReceiver",
        )
        self.assertEqual(
            receiver.attrib.get(ANDROID + "permission"),
            "android.permission.BIND_DEVICE_ADMIN",
        )
        self.assertEqual(receiver.attrib.get(ANDROID + "exported"), "true")
        metadata = receiver.find("meta-data")
        self.assertIsNotNone(metadata)
        self.assertEqual(
            metadata.attrib.get(ANDROID + "name"),
            "android.app.device_admin",
        )
        self.assertEqual(metadata.attrib.get(ANDROID + "resource"), "@xml/device_admin")

        actions = [
            action.attrib.get(ANDROID + "name")
            for action in receiver.findall(".//action")
        ]
        self.assertIn("android.app.action.DEVICE_ADMIN_ENABLED", actions)

    def test_admin_metadata_declares_only_the_camera_policy(self):
        policy = ET.parse(
            ROOT / "packages/apps/GhostOSPrivacyCenter/res/xml/device_admin.xml"
        ).getroot()
        declared = [child.tag for child in policy.find("uses-policies")]
        self.assertEqual(declared, ["disable-camera"])

    def test_adapter_is_device_owner_gated_and_has_snapshot_rollback(self):
        source = (
            ROOT
            / "packages/apps/GhostOSPrivacyCenter/src/org/ghostos/privacy/"
            "GhostModeDevicePolicyAdapter.java"
        ).read_text(encoding="utf-8")
        self.assertIn("isDeviceOwnerApp", source)
        self.assertIn("isAdminActive", source)
        self.assertIn("setCameraDisabled", source)
        self.assertIn("setScreenCaptureDisabled", source)
        self.assertIn("Snapshot before = snapshot()", source)
        self.assertIn("restore(before)", source)
        self.assertIn("public void rollback(Snapshot snapshot)", source)
        self.assertIn("Unsupported Device Owner control", source)

    def test_privacy_center_reports_readiness_without_automatically_applying_policy(self):
        activity = (
            ROOT
            / "packages/apps/GhostOSPrivacyCenter/src/org/ghostos/privacy/"
            "PrivacyActivity.java"
        ).read_text(encoding="utf-8")
        self.assertIn("adapter.availability()", activity)
        self.assertNotIn("adapter.apply(", activity)

        tile = (
            ROOT
            / "packages/apps/GhostOSPrivacyCenter/src/org/ghostos/privacy/"
            "GhostModeQuickSettingsTile.java"
        ).read_text(encoding="utf-8")
        self.assertIn("Tile.STATE_INACTIVE", tile)
        self.assertNotIn("Tile.STATE_ACTIVE", tile)


if __name__ == "__main__":
    unittest.main()
