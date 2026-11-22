package org.ghostos.securitycenter;

public final class VerifiedBootEvidenceSelfTest {
    private static void expect(
            VerifiedBootEvidence.State expected,
            VerifiedBootEvidence actual,
            String scenario) {
        if (actual.state != expected) {
            throw new AssertionError(
                    scenario + ": expected " + expected + " but got " + actual.state);
        }
    }

    public static void main(String[] args) {
        expect(VerifiedBootEvidence.State.FAIL,
                VerifiedBootEvidence.classify("red", "1"), "red state");

        VerifiedBootEvidence greenLocked =
                VerifiedBootEvidence.classify("green", "1");
        expect(VerifiedBootEvidence.State.OBSERVED, greenLocked, "green and locked");
        if (!greenLocked.label.contains("AVB not independently verified")) {
            throw new AssertionError("green/locked must not claim independent AVB verification");
        }

        expect(VerifiedBootEvidence.State.UNKNOWN,
                VerifiedBootEvidence.classify("green", "0"), "green but unlocked");
        expect(VerifiedBootEvidence.State.UNKNOWN,
                VerifiedBootEvidence.classify("orange", "1"), "orange but locked");
        expect(VerifiedBootEvidence.State.UNKNOWN,
                VerifiedBootEvidence.classify("yellow", "0"), "yellow but unlocked");

        expect(VerifiedBootEvidence.State.WARNING,
                VerifiedBootEvidence.classify("orange", "0"), "orange and unlocked");
        expect(VerifiedBootEvidence.State.WARNING,
                VerifiedBootEvidence.classify("yellow", "1"), "yellow and locked");
        expect(VerifiedBootEvidence.State.WARNING,
                VerifiedBootEvidence.classify("unrecognized", "0"), "unlocked with unknown state");

        expect(VerifiedBootEvidence.State.UNKNOWN,
                VerifiedBootEvidence.classify(null, null), "missing properties");
        expect(VerifiedBootEvidence.State.UNKNOWN,
                VerifiedBootEvidence.classify("green", null), "missing lock property");
        expect(VerifiedBootEvidence.State.UNKNOWN,
                VerifiedBootEvidence.classify("unrecognized", "1"), "unrecognized state");

        System.out.println("GhostOS Security Center Verified Boot evidence classification: PASS");
    }
}
