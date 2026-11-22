#!/usr/bin/env python3
import re
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = (ROOT / "scripts/init-android17.sh").read_text()
MANIFEST = ET.parse(ROOT / "manifests/ghostos-24.0.xml").getroot()
EXPECTED = {
    "android_device_oneplus_larry",
    "android_device_oneplus_sm6375-common",
    "android_hardware_oplus",
    "android_kernel_oneplus_sm6375",
    "proprietary_vendor_oneplus_larry",
    "proprietary_vendor_oneplus_sm6375-common",
}


class Android17BootstrapTests(unittest.TestCase):
    def test_bootstrap_uses_lineage_24_manifest_and_branch(self):
        self.assertIn('LINEAGE_BRANCH="lineage-24.0"', SCRIPT)
        self.assertIn('MANIFEST="$ROOT/manifests/ghostos-24.0.xml"', SCRIPT)
        self.assertIn('https://github.com/LineageOS/android.git', SCRIPT)
        self.assertIn('repo sync', SCRIPT)
        self.assertNotIn('-b android17-release', SCRIPT)

    def test_manifest_has_exact_device_projects_and_pinned_revisions(self):
        remotes = {r.attrib["name"]: r.attrib for r in MANIFEST.findall("remote")}
        self.assertEqual(remotes["lineage"]["revision"], "lineage-24.0")
        self.assertEqual(remotes["themuppets"]["revision"], "lineage-24.0")
        projects = MANIFEST.findall("project")
        self.assertEqual({p.attrib["name"] for p in projects}, EXPECTED)
        for project in projects:
            self.assertIn(project.attrib["remote"], remotes)
            self.assertRegex(project.attrib["revision"], re.compile(r"^[0-9a-f]{40}$"))

    def test_source_tree_safety_checks_are_present(self):
        self.assertIn('[[ "$ANDROID_DIR" != "/" ]]', SCRIPT)
        self.assertIn('"$ROOT/"*', SCRIPT)
        self.assertIn('"$ANDROID_DIR/"*', SCRIPT)


if __name__ == "__main__":
    unittest.main()
