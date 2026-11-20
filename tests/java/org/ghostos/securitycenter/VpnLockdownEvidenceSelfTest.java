package org.ghostos.securitycenter;

public final class VpnLockdownEvidenceSelfTest {
    private static void expect(
            VpnLockdownEvidence.State expected,
            VpnLockdownEvidence actual,
            String scenario) {
        if (actual.state != expected) {
            throw new AssertionError(
                    scenario + ": expected " + expected + " but got " + actual.state);
        }
    }

    public static void main(String[] args) {
        VpnLockdownEvidence enabled =
                VpnLockdownEvidence.classify("com.example.vpn", "1");
        expect(VpnLockdownEvidence.State.ALWAYS_ON_LOCKDOWN_ENABLED, enabled,
                "configured VPN with lockdown enabled");
        if (!enabled.label.contains("setting enabled")
                || !enabled.limitation.contains("tunnel")) {
            throw new AssertionError("enabled configuration must retain evidence limits");
        }

        VpnLockdownEvidence disabled =
                VpnLockdownEvidence.classify("com.example.vpn", "0");
        expect(VpnLockdownEvidence.State.ALWAYS_ON_LOCKDOWN_DISABLED, disabled,
                "configured VPN with lockdown disabled");

        VpnLockdownEvidence packageUnknown =
                VpnLockdownEvidence.classify(null, "1");
        expect(VpnLockdownEvidence.State.LOCKDOWN_SETTING_WITHOUT_PACKAGE, packageUnknown,
                "lockdown flag with package unknown");
        if (!packageUnknown.label.contains("package unknown")) {
            throw new AssertionError("must not claim an always-on package when none is reported");
        }

        VpnLockdownEvidence notConfigured =
                VpnLockdownEvidence.classify("", "0");
        expect(VpnLockdownEvidence.State.NOT_CONFIGURED, notConfigured,
                "no always-on VPN configured");

        VpnLockdownEvidence configuredUnknown =
                VpnLockdownEvidence.classify("com.example.vpn", null);
        expect(VpnLockdownEvidence.State.ALWAYS_ON_LOCKDOWN_UNKNOWN, configuredUnknown,
                "configured package with missing lockdown evidence");

        VpnLockdownEvidence missingEvidence =
                VpnLockdownEvidence.classify(null, null);
        expect(VpnLockdownEvidence.State.UNKNOWN, missingEvidence,
                "missing package and lockdown evidence");

        VpnLockdownEvidence malformed =
                VpnLockdownEvidence.classify(" ", "true");
        expect(VpnLockdownEvidence.State.UNKNOWN, malformed,
                "malformed settings and no package");

        System.out.println("GhostOS VPN lockdown evidence classification: PASS");
    }
}
