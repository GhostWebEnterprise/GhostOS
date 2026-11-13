package org.ghostos.auditor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class AuditSnapshotSelfTest {
    private static void expect(AuditSnapshot.State expected, AuditSnapshot.Finding finding) {
        if (finding.state != expected) {
            throw new AssertionError(finding.title + ": expected " + expected + " but got " + finding.state);
        }
    }

    private static final class FakeSource implements AuditSnapshot.Source {
        final Map<String, String> properties = new HashMap<>();
        Boolean enabled;
        Boolean enforced;
        Boolean screenLock;
        Boolean adb;
        String patch = "2026-11-01";
        String release = "17";
        String device = "larry";
        int sdk = 37;
        String otaStatus;
        final Map<String, Boolean> features = new HashMap<>();

        public String property(String key) { return properties.get(key); }
        public Boolean selinuxEnabled() { return enabled; }
        public Boolean selinuxEnforced() { return enforced; }
        public Boolean secureScreenLock() { return screenLock; }
        public Boolean adbEnabled() { return adb; }
        public String securityPatch() { return patch; }
        public String androidRelease() { return release; }
        public String device() { return device; }
        public int sdkInt() { return sdk; }
        public Boolean hasSystemFeature(String feature) { return features.get(feature); }
        public String otaStatus() { return otaStatus; }
    }

    private static AuditSnapshot.Finding find(List<AuditSnapshot.Finding> findings, String title) {
        for (AuditSnapshot.Finding finding : findings) {
            if (title.equals(finding.title)) return finding;
        }
        throw new AssertionError("missing finding: " + title);
    }

    public static void main(String[] args) {
        expect(AuditSnapshot.State.OBSERVED, AuditSnapshot.verifiedBoot("green", "1"));
        expect(AuditSnapshot.State.FAIL, AuditSnapshot.verifiedBoot("red", "1"));
        expect(AuditSnapshot.State.WARNING, AuditSnapshot.verifiedBoot("orange", "0"));
        expect(AuditSnapshot.State.WARNING, AuditSnapshot.verifiedBoot("yellow", "1"));
        expect(AuditSnapshot.State.WARNING, AuditSnapshot.verifiedBoot("green", "0"));
        expect(AuditSnapshot.State.UNKNOWN, AuditSnapshot.verifiedBoot("", ""));
        expect(AuditSnapshot.State.PASS, AuditSnapshot.selinux(true, true));
        expect(AuditSnapshot.State.WARNING, AuditSnapshot.selinux(true, false));
        expect(AuditSnapshot.State.FAIL, AuditSnapshot.selinux(false, false));
        expect(AuditSnapshot.State.UNKNOWN, AuditSnapshot.selinux(true, null));
        expect(AuditSnapshot.State.UNKNOWN, AuditSnapshot.selinux(null, null));
        expect(AuditSnapshot.State.PASS, AuditSnapshot.screenLock(true));
        expect(AuditSnapshot.State.WARNING, AuditSnapshot.screenLock(false));
        expect(AuditSnapshot.State.UNKNOWN, AuditSnapshot.screenLock(null));
        expect(AuditSnapshot.State.PASS, AuditSnapshot.adb(false));
        expect(AuditSnapshot.State.WARNING, AuditSnapshot.adb(true));
        expect(AuditSnapshot.State.UNKNOWN, AuditSnapshot.adb(null));

        FakeSource source = new FakeSource();
        source.properties.put("ro.boot.verifiedbootstate", "green");
        source.properties.put("ro.boot.flash.locked", "1");
        source.enabled = true;
        source.enforced = true;
        source.screenLock = true;
        source.adb = false;
        source.properties.put("ro.boot.vbmeta.digest", "test-digest");
        source.properties.put("ro.boot.vbmeta.avb_version", "1.2");
        source.properties.put("ro.boot.slot_suffix", "_a");
        source.properties.put("ro.boot.slot_successful", "1");
        source.properties.put("ro.build.fingerprint", "ghostos/larry/test");
        source.properties.put("ro.build.version.incremental", "eng.test");
        source.features.put("android.hardware.fingerprint", true);
        source.features.put("android.hardware.nfc", false);
        source.otaStatus = "System updater reports no pending update";
        List<AuditSnapshot.Finding> findings = AuditSnapshot.collect(source);
        if (findings.size() != 30) throw new AssertionError("expected 30 findings, got " + findings.size());
        expect(AuditSnapshot.State.OBSERVED, find(findings, "Verified Boot"));
        expect(AuditSnapshot.State.PASS, find(findings, "SELinux"));
        expect(AuditSnapshot.State.PASS, find(findings, "Screen lock"));
        expect(AuditSnapshot.State.PASS, find(findings, "ADB debugging"));
        expect(AuditSnapshot.State.OBSERVED, find(findings, "VBMeta digest"));
        expect(AuditSnapshot.State.OBSERVED, find(findings, "AVB version"));
        expect(AuditSnapshot.State.OBSERVED, find(findings, "Active boot slot"));
        expect(AuditSnapshot.State.OBSERVED, find(findings, "OTA updater status"));
        expect(AuditSnapshot.State.OBSERVED, find(findings, "Capability: Fingerprint"));
        expect(AuditSnapshot.State.OBSERVED, find(findings, "Capability: NFC"));
        expect(AuditSnapshot.State.UNKNOWN, find(findings, "Capability: Face biometrics"));
        if (!"Declared by PackageManager".equals(find(findings, "Capability: Fingerprint").value)) {
            throw new AssertionError("supported capability must be described as a platform declaration");
        }
        if (!"Not declared by PackageManager".equals(find(findings, "Capability: NFC").value)) {
            throw new AssertionError("unsupported capability must not be described as a failed security control");
        }

        source.properties.clear();
        source.enabled = null;
        source.enforced = null;
        source.screenLock = null;
        source.adb = null;
        source.otaStatus = null;
        source.features.clear();
        List<AuditSnapshot.Finding> unknownFindings = AuditSnapshot.collect(source);
        expect(AuditSnapshot.State.UNKNOWN, find(unknownFindings, "OTA updater status"));
        for (AuditSnapshot.Finding finding : unknownFindings) {
            if (finding.state == AuditSnapshot.State.PASS || finding.state == AuditSnapshot.State.FAIL) {
                throw new AssertionError("missing evidence must not become PASS/FAIL: " + finding.title);
            }
        }
        System.out.println("GhostOS Auditor evidence classification: PASS");
    }
}
