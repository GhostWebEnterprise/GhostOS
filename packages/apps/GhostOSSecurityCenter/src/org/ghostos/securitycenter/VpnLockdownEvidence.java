package org.ghostos.securitycenter;

import java.util.regex.Pattern;
/**
 * Conservative classification of Android Always-on VPN and lockdown settings.
 * These values are configuration evidence only; they do not prove a VPN tunnel,
 * provider identity, package installation, per-app coverage, or a correct lockdown allowlist.
 */
public final class VpnLockdownEvidence {
    private static final Pattern PACKAGE_NAME = Pattern.compile(
            "[A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)*");

    public enum State {
        ALWAYS_ON_LOCKDOWN_ENABLED,
        ALWAYS_ON_LOCKDOWN_DISABLED,
        ALWAYS_ON_LOCKDOWN_UNKNOWN,
        LOCKDOWN_SETTING_WITHOUT_PACKAGE,
        NOT_CONFIGURED,
        INVALID_PACKAGE_EVIDENCE,
        UNKNOWN
    }

    public final State state;
    public final String label;
    public final String limitation;

    private VpnLockdownEvidence(State state, String label) {
        this.state = state;
        this.label = label;
        this.limitation = "Read-only settings only; tunnel, provider, package installation, per-app coverage and lockdown allowlist are not verified.";
    }

    public static VpnLockdownEvidence classify(String packageName, String lockdownValue) {
        // Do not normalize malformed platform evidence into a valid package name.
        // Null/empty means absent; any non-empty value must be valid as supplied.
        String rawPackageValue = packageName == null ? "" : packageName;
        boolean packagePresent = !rawPackageValue.isEmpty();
        String packageValue = rawPackageValue.trim();
        if (packagePresent && (!rawPackageValue.equals(packageValue)
                || !PACKAGE_NAME.matcher(packageValue).matches())) {
            return new VpnLockdownEvidence(
                    State.INVALID_PACKAGE_EVIDENCE,
                    "Always-on VPN package value invalid · lockdown state not trusted");
        }
        boolean packageConfigured = packagePresent;
        // Settings.Secure values are expected to be exactly "0" or "1".
        // Do not trim corrupted values into a security-positive/negative state.
        String value = lockdownValue;

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
