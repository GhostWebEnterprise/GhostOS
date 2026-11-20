package org.ghostos.securitycenter;

/**
 * Conservative classification of Android Always-on VPN and lockdown settings.
 * These values are configuration evidence only; they do not prove a VPN tunnel,
 * provider identity, per-app coverage, or a correct lockdown allowlist.
 */
public final class VpnLockdownEvidence {
    public enum State {
        ALWAYS_ON_LOCKDOWN_ENABLED,
        ALWAYS_ON_LOCKDOWN_DISABLED,
        ALWAYS_ON_LOCKDOWN_UNKNOWN,
        LOCKDOWN_SETTING_WITHOUT_PACKAGE,
        NOT_CONFIGURED,
        UNKNOWN
    }

    public final State state;
    public final String label;
    public final String limitation;

    private VpnLockdownEvidence(State state, String label) {
        this.state = state;
        this.label = label;
        this.limitation = "Read-only settings only; tunnel, provider, per-app coverage and lockdown allowlist are not verified.";
    }

    public static VpnLockdownEvidence classify(String packageName, String lockdownValue) {
        boolean packageConfigured = packageName != null && !packageName.trim().isEmpty();
        String value = lockdownValue == null ? null : lockdownValue.trim();

        if ("1".equals(value)) {
            if (packageConfigured) {
                return new VpnLockdownEvidence(
                        State.ALWAYS_ON_LOCKDOWN_ENABLED,
                        "Always-on VPN configured · lockdown setting enabled");
            }
            return new VpnLockdownEvidence(
                    State.LOCKDOWN_SETTING_WITHOUT_PACKAGE,
                    "Lockdown setting present · always-on package unknown");
        }
        if ("0".equals(value)) {
            if (packageConfigured) {
                return new VpnLockdownEvidence(
                        State.ALWAYS_ON_LOCKDOWN_DISABLED,
                        "Always-on VPN configured · lockdown disabled");
            }
            return new VpnLockdownEvidence(
                    State.NOT_CONFIGURED,
                    "Always-on VPN package: not configured");
        }
        if (packageConfigured) {
            return new VpnLockdownEvidence(
                    State.ALWAYS_ON_LOCKDOWN_UNKNOWN,
                    "Always-on VPN configured · lockdown unknown");
        }
        return new VpnLockdownEvidence(State.UNKNOWN, "Always-on VPN / lockdown: unknown");
    }
}
