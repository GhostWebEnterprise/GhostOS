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

        public String property(String key) { return properties.get(key); }
        public Boolean selinuxEnabled() { return enabled; }
        public Boolean selinuxEnforced() { return enforced; }
        public Boolean secureScreenLock() { return screenLock; }
        public Boolean adbEnabled() { return adb; }
        public String securityPatch() { return patch; }
        public String androidRelease() { return release; }
        public String device() { return device; }
        public int sdkInt() { return sdk; }
    }

    public static void main(String[] args) {
        expect(AuditSnapshot.State.PASS, AuditSnapshot.verifiedBoot("green", "1"));
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
        List<AuditSnapshot.Finding> findings = AuditSnapshot.collect(source);
        if (findings.size() != 9) throw new AssertionError("expected 9 findings, got " + findings.size());
        expect(AuditSnapshot.State.PASS, findings.get(0));
        expect(AuditSnapshot.State.PASS, findings.get(1));
        expect(AuditSnapshot.State.PASS, findings.get(2));
        expect(AuditSnapshot.State.PASS, findings.get(3));
        expect(AuditSnapshot.State.UNKNOWN, findings.get(4));
        expect(AuditSnapshot.State.OBSERVED, findings.get(5));
        expect(AuditSnapshot.State.OBSERVED, findings.get(6));
        expect(AuditSnapshot.State.OBSERVED, findings.get(7));
        expect(AuditSnapshot.State.OBSERVED, findings.get(8));

        source.properties.clear();
        source.enabled = null;
        source.enforced = null;
        source.screenLock = null;
        source.adb = null;
        for (AuditSnapshot.Finding finding : AuditSnapshot.collect(source)) {
            if (finding.state == AuditSnapshot.State.PASS || finding.state == AuditSnapshot.State.FAIL) {
                throw new AssertionError("missing evidence must not become PASS/FAIL: " + finding.title);
            }
        }
        System.out.println("GhostOS Auditor evidence classification: PASS");
    }
}
