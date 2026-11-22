package org.ghostos.securitycenter;

/**
 * Pure classification of Android's active default-network VPN transport evidence.
 *
 * A VPN transport on the default network does not identify the provider, prove
 * per-app coverage, or establish that lockdown is enabled. Missing network
 * evidence is UNKNOWN and must never be treated as an unprotected or protected
 * state.
 */
public final class VpnTransportEvidence {
    public enum State {
        VPN_TRANSPORT_OBSERVED,
        NO_VPN_TRANSPORT_OBSERVED,
        UNKNOWN
    }

    public final State state;
    public final String label;
    public final String limitation;

    private VpnTransportEvidence(State state, String label, String limitation) {
        this.state = state;
        this.label = label;
        this.limitation = limitation;
    }

    public static VpnTransportEvidence classify(
            boolean activeNetworkAvailable,
            boolean capabilitiesAvailable,
            boolean hasVpnTransport) {
        if (!activeNetworkAvailable) {
            return new VpnTransportEvidence(
                    State.UNKNOWN,
                    "Default network: unknown (no active network reported)",
                    "No active default-network evidence was available; VPN protection is unknown.");
        }
        if (!capabilitiesAvailable) {
            return new VpnTransportEvidence(
                    State.UNKNOWN,
                    "Default network VPN transport: unknown (capabilities unavailable)",
                    "Android did not provide capabilities for the active default network.");
        }
        if (hasVpnTransport) {
            return new VpnTransportEvidence(
                    State.VPN_TRANSPORT_OBSERVED,
                    "Default network uses VPN transport · provider/coverage unverified",
                    "This does not identify the VPN provider, prove per-app coverage, or establish lockdown.");
        }
        return new VpnTransportEvidence(
                State.NO_VPN_TRANSPORT_OBSERVED,
                "Default network does not report VPN transport · other protection not assessed",
                "No VPN transport is reported for the default network; other protection mechanisms are not assessed.");
    }

    public static VpnTransportEvidence unavailable(String label) {
        String safeLabel = label == null || label.trim().isEmpty()
                ? "VPN status: unavailable"
                : label;
        return new VpnTransportEvidence(
                State.UNKNOWN,
                safeLabel,
                "Android did not provide reliable network evidence; do not infer VPN protection state.");
    }
}
