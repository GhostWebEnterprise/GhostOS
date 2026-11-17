package org.ghostos.securitycenter;

public final class VpnTransportEvidenceSelfTest {
    private static void expect(
            VpnTransportEvidence.State expected,
            VpnTransportEvidence actual,
            String scenario) {
        if (actual.state != expected) {
            throw new AssertionError(
                    scenario + ": expected " + expected + " but got " + actual.state);
        }
    }

    public static void main(String[] args) {
        VpnTransportEvidence noDefaultNetwork =
                VpnTransportEvidence.classify(false, false, false);
        expect(VpnTransportEvidence.State.UNKNOWN, noDefaultNetwork, "no active default network");
        if (!noDefaultNetwork.label.contains("unknown")) {
            throw new AssertionError("missing active network must be reported as unknown");
        }

        VpnTransportEvidence missingCapabilities =
                VpnTransportEvidence.classify(true, false, false);
        expect(VpnTransportEvidence.State.UNKNOWN, missingCapabilities, "missing capabilities");

        VpnTransportEvidence vpn =
                VpnTransportEvidence.classify(true, true, true);
        expect(VpnTransportEvidence.State.VPN_TRANSPORT_OBSERVED, vpn, "VPN transport present");
        if (!vpn.limitation.contains("per-app coverage")
                || !vpn.limitation.contains("lockdown")) {
            throw new AssertionError("VPN observation must retain provider/coverage limitations");
        }

        VpnTransportEvidence noVpn =
                VpnTransportEvidence.classify(true, true, false);
        expect(VpnTransportEvidence.State.NO_VPN_TRANSPORT_OBSERVED, noVpn, "VPN transport absent");
        if (!noVpn.label.contains("other protection not assessed")) {
            throw new AssertionError("absence of VPN transport must not imply absence of other protection");
        }

        VpnTransportEvidence unavailable =
                VpnTransportEvidence.unavailable("VPN status: unavailable");
        expect(VpnTransportEvidence.State.UNKNOWN, unavailable, "runtime read failure");
        if (!unavailable.limitation.contains("do not infer")) {
            throw new AssertionError("runtime failures must remain explicitly unknown");
        }

        System.out.println("GhostOS VPN transport evidence classification: PASS");
    }
}
