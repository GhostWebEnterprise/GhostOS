import re
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
        self.assertIn("canUsbDataSignalingBeDisabled", source)
        self.assertIn("setUsbDataSignalingEnabled", source)
        self.assertIn("isUsbDataSignalingEnabled", source)
        self.assertIn("Build.VERSION_CODES.S", source)
        self.assertIn('"usbDataSignaling"', source)
        self.assertIn("Snapshot before = snapshot()", source)
        self.assertIn("restore(before)", source)
        self.assertIn("public void rollback(Snapshot snapshot)", source)
        self.assertIn("Unsupported Device Owner control", source)

    def test_microphone_and_location_controls_use_supported_device_owner_apis(self):
        source = (
            ROOT
            / "packages/apps/GhostOSPrivacyCenter/src/org/ghostos/privacy/"
            "GhostModeDevicePolicyAdapter.java"
        ).read_text(encoding="utf-8")
        self.assertIn("UserManager.DISALLOW_UNMUTE_MICROPHONE", source)
        self.assertIn("manager.addUserRestriction(admin", source)
        self.assertIn("manager.clearUserRestriction(admin", source)
        self.assertIn("manager.getUserRestrictions(admin)", source)
        self.assertIn("manager.setLocationEnabled(admin", source)
        self.assertIn("locationManager.isLocationEnabled()", source)
        self.assertIn("Build.VERSION_CODES.R", source)
        self.assertIn("microphoneMuted", source)
        self.assertIn("locationEnabled", source)
        self.assertIn("locationEnabled()", source)

    def test_privacy_center_applies_only_explicit_device_owner_switch_changes(self):
        activity = (
            ROOT
            / "packages/apps/GhostOSPrivacyCenter/src/org/ghostos/privacy/"
            "PrivacyActivity.java"
        ).read_text(encoding="utf-8")
        on_create = activity.split("@Override protected void onResume()", 1)[0]
        self.assertNotIn("adapter.apply(", on_create)
        self.assertIn("cameraSwitch = addPolicySwitch", activity)
        self.assertIn("screenCaptureSwitch = addPolicySwitch", activity)
        self.assertIn("microphoneSwitch = addPolicySwitch", activity)
        self.assertIn("locationSwitch = addPolicySwitch", activity)
        self.assertIn("usbDataSwitch = addPolicySwitch", activity)
        self.assertIn("adapter.isLocationControlSupported()", activity)
        self.assertIn("adapter.isMicrophoneMuted()", activity)
        self.assertIn("adapter.isLocationEnabled()", activity)
        self.assertIn("policySwitch.setOnCheckedChangeListener", activity)
        self.assertIn("bindPolicySwitch(policySwitch, control)", activity)
        self.assertIn("adapter.availability()", activity)
        self.assertIn("adapter.apply(Collections.singletonMap(control, enabled))", activity)
        self.assertIn("failure.getSuppressed().length > 0", activity)
        self.assertIn("onResume()", activity)

        tile = (
            ROOT
            / "packages/apps/GhostOSPrivacyCenter/src/org/ghostos/privacy/"
            "GhostModeQuickSettingsTile.java"
        ).read_text(encoding="utf-8")
        self.assertIn("Tile.STATE_INACTIVE", tile)
        self.assertNotIn("Tile.STATE_ACTIVE", tile)

    def test_all_android_string_references_exist(self):
        strings_path = ROOT / "packages/apps/GhostOSPrivacyCenter/res/values/strings.xml"
        strings = ET.parse(strings_path).getroot()
        defined = {item.attrib["name"] for item in strings.findall("string")}

        source_paths = [
            ROOT / "packages/apps/GhostOSPrivacyCenter/AndroidManifest.xml",
            ROOT / "packages/apps/GhostOSPrivacyCenter/res/xml/device_admin.xml",
        ]
        source_paths.extend(
            (ROOT / "packages/apps/GhostOSPrivacyCenter/src").rglob("*.java")
        )
        combined = "\n".join(path.read_text(encoding="utf-8") for path in source_paths)
        references = set(re.findall(r"@string/([A-Za-z0-9_]+)", combined))
        references.update(re.findall(r"R\.string\.([A-Za-z0-9_]+)", combined))
        self.assertEqual(references - defined, set(), "Undefined Android string resources")


if __name__ == "__main__":
    unittest.main()
