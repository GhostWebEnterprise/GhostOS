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
        self.assertEqual(application.attrib.get(ANDROID + "allowBackup"), "false")
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

    def test_admin_metadata_declares_camera_and_keyguard_policies(self):
        policy = ET.parse(
            ROOT / "packages/apps/GhostOSPrivacyCenter/res/xml/device_admin.xml"
        ).getroot()
        declared = [child.tag for child in policy.find("uses-policies")]
        self.assertEqual(declared, ["disable-camera", "disable-keyguard-features"])

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
        self.assertIn("KEYGUARD_DISABLE_UNREDACTED_NOTIFICATIONS", source)
        self.assertIn("setKeyguardDisabledFeatures", source)
        self.assertIn("getKeyguardDisabledFeatures", source)
        self.assertIn('"hideSensitiveOnLockscreen"', source)
        self.assertIn("current | redaction", source)
        self.assertIn("current & ~redaction", source)
        self.assertIn("snapshot.keyguardDisabledFeatures() != null", source)
        self.assertIn("Build.VERSION_CODES.S", source)
        self.assertIn('"usbDataSignaling"', source)
        self.assertIn("Snapshot before = snapshot()", source)
        self.assertIn("restore(before)", source)
        self.assertIn("public void rollback(Snapshot snapshot)", source)
        self.assertIn("public Snapshot captureSnapshot()", source)
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
        self.assertIn("notificationsSwitch = addPolicySwitch", activity)
        self.assertIn("adapter::isSensitiveNotificationsHidden", activity)
        self.assertIn("adapter.isLocationControlSupported()", activity)
        self.assertIn("adapter::isMicrophoneMuted", activity)
        self.assertIn("adapter::isLocationEnabled", activity)
        self.assertIn("policySwitch.setOnCheckedChangeListener", activity)
        self.assertIn("bindPolicySwitch(policySwitch, control)", activity)
        self.assertIn("adapter.availability()", activity)
        self.assertIn("policyManager = new GhostModeAndroidPolicyManager(this, adapter)", activity)
        self.assertIn("policyManager.applyControl(control, enabled)", activity)
        self.assertIn("policyManager.restorePreviousPolicy()", activity)
        self.assertIn("restoreButton", activity)
        self.assertIn("boolean recoveryRequired = managerState", activity)
        self.assertIn("boolean controlsReady = ready && !recoveryRequired", activity)
        self.assertIn("cameraSwitch.setEnabled(controlsReady)", activity)
        self.assertIn("restoreButton.setEnabled(ready && hasSavedSnapshot)", activity)
        self.assertNotIn("adapter.apply(Collections.singletonMap(control, enabled))", activity)
        self.assertIn("onResume()", activity)

        tile = (
            ROOT
            / "packages/apps/GhostOSPrivacyCenter/src/org/ghostos/privacy/"
            "GhostModeQuickSettingsTile.java"
        ).read_text(encoding="utf-8")
        self.assertIn("Tile.STATE_INACTIVE", tile)
        self.assertIn("manager.getState()", tile)
        self.assertIn("GhostModeAndroidPolicyManager.State.ACTIVE", tile)
        self.assertIn("tile.setState(Tile.STATE_ACTIVE)", tile)
        self.assertNotIn("applyControl(", tile)


    def test_android_policy_manager_persists_and_restores_original_device_state(self):
        manager = (
            ROOT
            / "packages/apps/GhostOSPrivacyCenter/src/org/ghostos/privacy/"
            "GhostModeAndroidPolicyManager.java"
        ).read_text(encoding="utf-8")
        self.assertIn("SharedPreferences", manager)
        self.assertIn("adapter.captureSnapshot()", manager)
        self.assertIn("persistSnapshot(baseline)", manager)
        self.assertIn("adapter.apply(Collections.singletonMap(control, enabled))", manager)
        self.assertIn("adapter.rollback(baseline)", manager)
        self.assertIn("KEY_PENDING", manager)
        self.assertIn("KEY_RECOVERY_REQUIRED", manager)
        self.assertIn("verifyExpectedState()", manager)
        self.assertIn("adapter.isCameraDisabled()", manager)
        self.assertIn("adapter.isScreenCaptureDisabled()", manager)
        self.assertIn("adapter.isMicrophoneMuted()", manager)
        self.assertIn("adapter.isLocationEnabled()", manager)
        self.assertIn("adapter.isUsbDataSignalingEnabled()", manager)
        self.assertIn("adapter.isSensitiveNotificationsHidden()", manager)
        self.assertIn("KEY_KEYGUARD_DISABLED_FEATURES", manager)
        self.assertIn("KEY_EXPECTED_NOTIFICATIONS", manager)
        self.assertIn("!preferences.contains(KEY_EXPECTED_NOTIFICATIONS)", manager)
        self.assertIn("private boolean adapterReady()", manager)
        self.assertIn("recordExpected(expectedKey, enabled)", manager)
        self.assertIn(".remove(KEY_EXPECTED_CAMERA)", manager)
        self.assertIn(".remove(KEY_EXPECTED_LOCATION)", manager)
        self.assertIn(".remove(KEY_EXPECTED_USB_DATA)", manager)
        self.assertIn("clearSnapshot()", manager)
        self.assertIn("commit()", manager)
        self.assertNotIn("VpnHood", manager)

    def test_subsequent_policy_changes_are_journaled_before_android_mutation(self):
        manager = (
            ROOT
            / "packages/apps/GhostOSPrivacyCenter/src/org/ghostos/privacy/"
            "GhostModeAndroidPolicyManager.java"
        ).read_text(encoding="utf-8")
        active_change = manager.split(
            "            return;\n        }\n\n", 1
        )[1].split("\n    }\n\n    /** Restores", 1)[0]

        begin = active_change.index("if (!beginPendingChange())")
        apply = active_change.index(
            "adapter.apply(Collections.singletonMap(control, enabled))"
        )
        record = active_change.index("recordExpected(expectedKey, enabled)")
        finish = active_change.index("if (!finishPendingChange())")

        self.assertLess(begin, apply)
        self.assertLess(apply, record)
        self.assertLess(record, finish)
        self.assertIn("|| preferences.getBoolean(KEY_RECOVERY_REQUIRED, false)", active_change)
        self.assertIn("boolean policyApplied = false;", active_change)
        self.assertIn("policyApplied = true;", active_change)
        self.assertIn("if (policyApplied", active_change)
        self.assertIn("private boolean beginPendingChange()", manager)
        self.assertIn("private boolean finishPendingChange()", manager)

    def test_incomplete_rollback_snapshot_is_rejected_before_loading_defaults(self):
        manager = (
            ROOT
            / "packages/apps/GhostOSPrivacyCenter/src/org/ghostos/privacy/"
            "GhostModeAndroidPolicyManager.java"
        ).read_text(encoding="utf-8")
        restore = manager.split(chr(10) + "    public void restorePreviousPolicy() {", 1)[1].split(
            chr(10) + "    private boolean persistSnapshot", 1
        )[0]
        state = manager.split(chr(10) + "    public State getState() {", 1)[1].split(
            chr(10) + "    public boolean hasSavedSnapshot()", 1
        )[0]
        completeness = manager.split("private boolean hasCompleteSnapshot() {", 1)[1].split(
            chr(10) + "    private void markRecoveryRequired()", 1
        )[0]

        self.assertIn("if (!hasCompleteSnapshot())", restore)
        self.assertLess(restore.index("if (!hasCompleteSnapshot())"), restore.index("= loadSnapshot()"))
        self.assertIn("!hasCompleteSnapshot()", state)

        for key in (
            "KEY_CAMERA_DISABLED",
            "KEY_SCREEN_CAPTURE_DISABLED",
            "KEY_MICROPHONE_MUTED",
            "KEY_KEYGUARD_DISABLED_FEATURES",
        ):
            self.assertIn(f"preferences.contains({key})", completeness)

        for expected_key, snapshot_key in (
            ("KEY_EXPECTED_LOCATION", "KEY_LOCATION_ENABLED"),
            ("KEY_EXPECTED_USB_DATA", "KEY_USB_DATA_ENABLED"),
        ):
            self.assertIn(f"!preferences.contains({expected_key})", completeness)
            self.assertIn(f"preferences.contains({snapshot_key})", completeness)

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
