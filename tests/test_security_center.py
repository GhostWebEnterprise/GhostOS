import unittest

from ghostos.security_center.status import ControlStatus, EvidenceState, SecurityCenterSnapshot, VpnObservation, VpnState, classify_vpn


class SecurityCenterStatusTests(unittest.TestCase):
    def test_missing_control_is_unknown_not_secure(self):
        self.assertEqual(SecurityCenterSnapshot().control("verified_boot").state, EvidenceState.UNKNOWN)

    def test_stale_enabled_observation_is_downgraded_to_unknown(self):
        status = ControlStatus(state=EvidenceState.ENABLED, source="android", stale=True)
        self.assertEqual(status.state, EvidenceState.UNKNOWN)

    def test_active_transport_without_verified_provider_is_not_attributed(self):
        self.assertEqual(classify_vpn(VpnObservation(transport_active=True)), VpnState.ACTIVE_PROVIDER_UNVERIFIED)

    def test_active_transport_with_trusted_provider_evidence(self):
        self.assertEqual(classify_vpn(VpnObservation(transport_active=True, provider_identity_verified=True)), VpnState.ACTIVE_VERIFIED)

    def test_connecting_and_disconnected_are_distinct(self):
        self.assertEqual(classify_vpn(VpnObservation(transport_active=False, connecting=True)), VpnState.CONNECTING)
        self.assertEqual(classify_vpn(VpnObservation(transport_active=False)), VpnState.DISCONNECTED)

    def test_lockdown_blocked_is_distinct(self):
        observation = VpnObservation(transport_active=False, lockdown_enabled=True, blocked_by_lockdown=True)
        self.assertEqual(classify_vpn(observation), VpnState.BLOCKED_BY_LOCKDOWN)

    def test_stale_vpn_observation_is_unknown(self):
        self.assertEqual(classify_vpn(VpnObservation(transport_active=True, stale=True)), VpnState.UNKNOWN)

    def test_unknown_transport_is_not_assumed_disconnected(self):
        self.assertEqual(classify_vpn(VpnObservation(transport_active=None)), VpnState.UNKNOWN)


if __name__ == "__main__":
    unittest.main()
