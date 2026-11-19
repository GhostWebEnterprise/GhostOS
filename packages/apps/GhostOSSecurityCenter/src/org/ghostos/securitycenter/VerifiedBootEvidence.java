package org.ghostos.securitycenter;

import java.util.Locale;

/**
 * Conservative interpretation of Android's platform-reported Verified Boot properties.
 * These values are observations, not independent AVB or vbmeta cryptographic verification.
 */
public final class VerifiedBootEvidence {
    public enum State { OBSERVED, WARNING, FAIL, UNKNOWN }

    public final State state;
    public final String label;
    public final String limitation;

    private VerifiedBootEvidence(State state, String label, String limitation) {
        this.state = state;
        this.label = label;
        this.limitation = limitation;
    }

    private static VerifiedBootEvidence of(State state, String label, String limitation) {
        return new VerifiedBootEvidence(state, label, limitation);
    }

    public static VerifiedBootEvidence classify(String stateValue, String lockedValue) {
        String state = clean(stateValue).toLowerCase(Locale.ROOT);
        String locked = clean(lockedValue);

        if ("red".equals(state)) {
            return of(State.FAIL, "Platform reports Verified Boot: red",
                    "Platform-reported boot property only; inspect boot logs and vbmeta separately.");
        }

        // Conflicting boot-state and lock properties are inconclusive, not a normal warning.
        if (("green".equals(state) && "0".equals(locked))
                || ("orange".equals(state) && "1".equals(locked))
                || ("yellow".equals(state) && "0".equals(locked))) {
            return of(State.UNKNOWN, "Inconsistent boot-state properties · Verified Boot: unknown",
                    "The boot-state and bootloader-lock properties conflict; no trust conclusion is made.");
        }

        if ("green".equals(state) && "1".equals(locked)) {
            return of(State.OBSERVED,
                    "Platform reports green state · bootloader locked · AVB not independently verified",
                    "Platform-reported properties only; no independent AVB/vbmeta signature verification was performed.");
        }
        if ("orange".equals(state)) {
            return of(State.WARNING, "Platform reports unlocked boot state",
                    "Platform property evidence only; the boot chain has not been independently verified.");
        }
        if ("yellow".equals(state)) {
            return of(State.WARNING, "Platform reports yellow state · custom trust root",
                    "Platform property evidence only; the custom trust root has not been independently validated.");
        }
        if ("0".equals(locked)) {
            return of(State.WARNING, "Bootloader reports unlocked · Verified Boot state unknown",
                    "Only the lock property was conclusive; verified-boot state was unavailable or unrecognized.");
        }
        return of(State.UNKNOWN, "Verified Boot: unknown",
                "Required boot properties were missing, inconsistent or inaccessible.");
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
