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

        VpnLockdownEvidence malformedLockdown =
                VpnLockdownEvidence.classify(null, "true");
        expect(VpnLockdownEvidence.State.UNKNOWN, malformedLockdown,
                "malformed lockdown setting and no package");

        VpnLockdownEvidence invalidPackage =
                VpnLockdownEvidence.classify("com..example.vpn", "0");
        expect(VpnLockdownEvidence.State.INVALID_PACKAGE_EVIDENCE, invalidPackage,
                "invalid package value must not be reported as not configured");
        if (invalidPackage.label.contains("not configured")) {
            throw new AssertionError("invalid package evidence must not be reported as absent");
        }

        VpnLockdownEvidence invalidPackageLockdownEnabled =
                VpnLockdownEvidence.classify("com.example-vpn", "1");
        expect(VpnLockdownEvidence.State.INVALID_PACKAGE_EVIDENCE,
                invalidPackageLockdownEnabled,
                "invalid package value must not be reported as lockdown enabled");

        VpnLockdownEvidence whitespacePackage =
                VpnLockdownEvidence.classify("   ", "1");
        expect(VpnLockdownEvidence.State.INVALID_PACKAGE_EVIDENCE,
                whitespacePackage,
                "whitespace-only package must not be normalized to absent");

        VpnLockdownEvidence paddedPackage =
                VpnLockdownEvidence.classify(" com.example.vpn ", "1");
        expect(VpnLockdownEvidence.State.INVALID_PACKAGE_EVIDENCE,
                paddedPackage,
                "padded package must not be normalized to valid evidence");
        if (paddedPackage.label.contains("setting enabled")) {
            throw new AssertionError("malformed package must not be reported as lockdown enabled");
        }

        System.out.println("GhostOS VPN lockdown evidence classification: PASS");
    }
}
