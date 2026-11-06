// SPDX-License-Identifier: Apache-2.0
package org.ghostos.privacy;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collections;
import java.util.Objects;

/**
 * Coordinates explicit Device Owner controls and persists their original state
 * so the Privacy Center can restore it after Activity or process recreation.
 *
 * This is not the full cross-adapter GhostMode preset service. Network, sensor,
 * clipboard and full notification policies remain inactive; secure keyguard
 * notification redaction is supported as an explicit Device Owner control.
 */
public final class GhostModeAndroidPolicyManager {
    public enum State {
        INACTIVE,
        ACTIVE,
        UNAVAILABLE,
        RECOVERY_REQUIRED
    }

    private static final String PREFERENCES = "ghostmode_device_policy";
    private static final String KEY_ACTIVE = "active";
    private static final String KEY_PENDING = "pending";
    private static final String KEY_RECOVERY_REQUIRED = "recoveryRequired";
    private static final String KEY_CAMERA_DISABLED = "cameraDisabled";
    private static final String KEY_SCREEN_CAPTURE_DISABLED = "screenCaptureDisabled";
    private static final String KEY_MICROPHONE_MUTED = "microphoneMuted";
    private static final String KEY_LOCATION_ENABLED = "locationEnabled";
    private static final String KEY_USB_DATA_ENABLED = "usbDataSignalingEnabled";
    private static final String KEY_KEYGUARD_DISABLED_FEATURES = "keyguardDisabledFeatures";
    private static final String KEY_EXPECTED_CAMERA = "expectedCameraDisabled";
    private static final String KEY_EXPECTED_SCREEN_CAPTURE = "expectedScreenCaptureDisabled";
    private static final String KEY_EXPECTED_MICROPHONE = "expectedMicrophoneMuted";
    private static final String KEY_EXPECTED_LOCATION = "expectedLocationEnabled";
    private static final String KEY_EXPECTED_USB_DATA = "expectedUsbDataSignalingEnabled";
    private static final String KEY_EXPECTED_NOTIFICATIONS = "expectedHideSensitiveOnLockscreen";

    private final GhostModeDevicePolicyAdapter adapter;
    private final SharedPreferences preferences;

    public GhostModeAndroidPolicyManager(
            Context context,
            GhostModeDevicePolicyAdapter adapter) {
        Context appContext = Objects.requireNonNull(context, "context").getApplicationContext();
        this.adapter = Objects.requireNonNull(adapter, "adapter");
        this.preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    public State getState() {
        if (preferences.getBoolean(KEY_RECOVERY_REQUIRED, false)
                || preferences.getBoolean(KEY_PENDING, false)) {
            return State.RECOVERY_REQUIRED;
        }
        if (preferences.getBoolean(KEY_ACTIVE, false)) {
            if (!adapterReady() || !hasCompleteSnapshot()) {
                return State.RECOVERY_REQUIRED;
            }
            try {
                return verifyExpectedState() ? State.ACTIVE : State.RECOVERY_REQUIRED;
            } catch (RuntimeException unavailable) {
                return State.RECOVERY_REQUIRED;
            }
        }
        return adapterReady() ? State.INACTIVE : State.UNAVAILABLE;
    }

    public boolean hasSavedSnapshot() {
        return preferences.getBoolean(KEY_ACTIVE, false)
                || preferences.getBoolean(KEY_PENDING, false)
                || preferences.getBoolean(KEY_RECOVERY_REQUIRED, false);
    }

    /**
     * Applies one explicit Device Owner control. The first change stores a
     * baseline snapshot before touching Android; later changes retain that
     * original baseline until restorePreviousPolicy() succeeds.
     */
    public void applyControl(String control, boolean enabled) {
        Objects.requireNonNull(control, "control");
        if (control.trim().isEmpty()) {
            throw new IllegalArgumentException("GhostMode control name is empty");
        }
        String expectedKey = expectedKey(control);
        State current = getState();
        if (current == State.RECOVERY_REQUIRED) {
            throw new IllegalStateException(
                    "Restore the saved GhostMode device policy before applying another change");
        }
        if (current == State.UNAVAILABLE) {
            throw new SecurityException("GhostMode Device Owner policy adapter is unavailable");
        }

        if (!preferences.getBoolean(KEY_ACTIVE, false)) {
            GhostModeDevicePolicyAdapter.Snapshot baseline = adapter.captureSnapshot();
            if (!persistSnapshot(baseline)) {
                throw new IllegalStateException(
                        "Could not persist the rollback snapshot; no policy change was applied");
            }
            try {
                adapter.apply(Collections.singletonMap(control, enabled));
            } catch (RuntimeException failure) {
                if (failure.getSuppressed().length > 0) {
                    markRecoveryRequired();
                } else if (!clearSnapshot()) {
                    markRecoveryRequired();
                }
                throw failure;
            }
            if (!markActive(expectedKey, enabled)) {
                markRecoveryRequired();
                throw new IllegalStateException(
                        "Policy was applied but its state could not be finalized; restore is required");
            }
            return;
        }

        // Journal every later policy mutation before touching Android. If the
        // process dies between apply() and recordExpected(), the next launch
        // must require restoration from the original baseline snapshot.
        if (!beginPendingChange()) {
            throw new IllegalStateException(
                    "Could not persist pending policy change; no Android policy change was applied");
        }
        boolean policyApplied = false;
        try {
            adapter.apply(Collections.singletonMap(control, enabled));
            policyApplied = true;
            if (!recordExpected(expectedKey, enabled)) {
                markRecoveryRequired();
                throw new IllegalStateException(
                        "Policy was applied but its expected state could not be persisted");
            }
            if (!finishPendingChange()) {
                markRecoveryRequired();
                throw new IllegalStateException(
                        "Policy was applied but its transaction could not be finalized; restore is required");
            }
        } catch (RuntimeException failure) {
            if (policyApplied
                    || failure.getSuppressed().length > 0
                    || preferences.getBoolean(KEY_RECOVERY_REQUIRED, false)) {
                markRecoveryRequired();
            } else if (!finishPendingChange()) {
                markRecoveryRequired();
            }
            throw failure;
        }
    }

    /** Restores the exact pre-GhostMode state saved before the first change. */
    public void restorePreviousPolicy() {
        if (!hasSavedSnapshot()) {
            return;
        }
        // Never guess missing values: loadSnapshot() uses defaults for absent
        // booleans, which could overwrite a user's pre-GhostMode device state.
        if (!hasCompleteSnapshot()) {
            markRecoveryRequired();
            throw new IllegalStateException("GhostMode rollback snapshot is incomplete");
        }
        if (!adapterReady()) {
            markRecoveryRequired();
            throw new SecurityException(
                    "Device Owner access is unavailable; saved policy has not been restored");
        }

        GhostModeDevicePolicyAdapter.Snapshot baseline = loadSnapshot();
        try {
            adapter.rollback(baseline);
        } catch (RuntimeException failure) {
            markRecoveryRequired();
            throw new IllegalStateException("GhostMode policy restoration is incomplete", failure);
        }
        if (!clearSnapshot()) {
            markRecoveryRequired();
            throw new IllegalStateException(
                    "Android state was restored but recovery metadata could not be cleared");
        }
    }

    private boolean persistSnapshot(GhostModeDevicePolicyAdapter.Snapshot snapshot) {
        SharedPreferences.Editor editor = preferences.edit()
                .putBoolean(KEY_PENDING, true)
                .putBoolean(KEY_ACTIVE, false)
                .putBoolean(KEY_RECOVERY_REQUIRED, false)
                .putBoolean(KEY_CAMERA_DISABLED, snapshot.cameraDisabled())
                .putBoolean(KEY_SCREEN_CAPTURE_DISABLED, snapshot.screenCaptureDisabled())
                .putBoolean(KEY_MICROPHONE_MUTED, snapshot.microphoneMuted())
                .putInt(KEY_KEYGUARD_DISABLED_FEATURES, snapshot.keyguardDisabledFeatures())
                .remove(KEY_EXPECTED_CAMERA)
                .remove(KEY_EXPECTED_SCREEN_CAPTURE)
                .remove(KEY_EXPECTED_MICROPHONE)
                .remove(KEY_EXPECTED_LOCATION)
                .remove(KEY_EXPECTED_USB_DATA)
                .remove(KEY_EXPECTED_NOTIFICATIONS);

        if (snapshot.locationEnabled() == null) {
            editor.remove(KEY_LOCATION_ENABLED);
        } else {
            editor.putBoolean(KEY_LOCATION_ENABLED, snapshot.locationEnabled());
        }
        if (snapshot.usbDataSignalingEnabled() == null) {
            editor.remove(KEY_USB_DATA_ENABLED);
        } else {
            editor.putBoolean(KEY_USB_DATA_ENABLED, snapshot.usbDataSignalingEnabled());
        }
        return editor.commit();
    }

    private GhostModeDevicePolicyAdapter.Snapshot loadSnapshot() {
        Boolean locationEnabled = preferences.contains(KEY_LOCATION_ENABLED)
                ? preferences.getBoolean(KEY_LOCATION_ENABLED, false)
                : null;
        Boolean usbDataEnabled = preferences.contains(KEY_USB_DATA_ENABLED)
                ? preferences.getBoolean(KEY_USB_DATA_ENABLED, false)
                : null;
        Integer keyguardDisabledFeatures = preferences.contains(KEY_KEYGUARD_DISABLED_FEATURES)
                ? preferences.getInt(KEY_KEYGUARD_DISABLED_FEATURES, 0)
                : null;
        return new GhostModeDevicePolicyAdapter.Snapshot(
                preferences.getBoolean(KEY_CAMERA_DISABLED, false),
                preferences.getBoolean(KEY_SCREEN_CAPTURE_DISABLED, false),
                preferences.getBoolean(KEY_MICROPHONE_MUTED, false),
                locationEnabled,
                usbDataEnabled,
                keyguardDisabledFeatures);
    }

    private boolean markActive(String expectedKey, boolean enabled) {
        return preferences.edit()
                .putBoolean(KEY_ACTIVE, true)
                .putBoolean(KEY_PENDING, false)
                .putBoolean(KEY_RECOVERY_REQUIRED, false)
                .putBoolean(expectedKey, enabled)
                .commit();
    }

    private boolean beginPendingChange() {
        return preferences.edit().putBoolean(KEY_PENDING, true).commit();
    }

    private boolean finishPendingChange() {
        return preferences.edit().putBoolean(KEY_PENDING, false).commit();
    }

    private boolean recordExpected(String expectedKey, boolean enabled) {
        return preferences.edit().putBoolean(expectedKey, enabled).commit();
    }

    private String expectedKey(String control) {
        switch (control) {
            case "camera":
                return KEY_EXPECTED_CAMERA;
            case "screenCapture":
                return KEY_EXPECTED_SCREEN_CAPTURE;
            case "microphoneMuted":
                return KEY_EXPECTED_MICROPHONE;
            case "locationEnabled":
                return KEY_EXPECTED_LOCATION;
            case "usbDataSignaling":
                return KEY_EXPECTED_USB_DATA;
            case "hideSensitiveOnLockscreen":
                return KEY_EXPECTED_NOTIFICATIONS;
            default:
                throw new IllegalArgumentException("Unsupported GhostMode Android control: " + control);
        }
    }

    private boolean verifyExpectedState() {
        boolean found = false;
        if (preferences.contains(KEY_EXPECTED_CAMERA)) {
            found = true;
            if (adapter.isCameraDisabled() != preferences.getBoolean(KEY_EXPECTED_CAMERA, false)) {
                return false;
            }
        }
        if (preferences.contains(KEY_EXPECTED_SCREEN_CAPTURE)) {
            found = true;
            if (adapter.isScreenCaptureDisabled()
                    != preferences.getBoolean(KEY_EXPECTED_SCREEN_CAPTURE, false)) {
                return false;
            }
        }
        if (preferences.contains(KEY_EXPECTED_MICROPHONE)) {
            found = true;
            if (adapter.isMicrophoneMuted()
                    != preferences.getBoolean(KEY_EXPECTED_MICROPHONE, false)) {
                return false;
            }
        }
        if (preferences.contains(KEY_EXPECTED_LOCATION)) {
            found = true;
            if (adapter.isLocationEnabled()
                    != preferences.getBoolean(KEY_EXPECTED_LOCATION, false)) {
                return false;
            }
        }
        if (preferences.contains(KEY_EXPECTED_USB_DATA)) {
            found = true;
            if (adapter.isUsbDataSignalingEnabled()
                    != preferences.getBoolean(KEY_EXPECTED_USB_DATA, false)) {
                return false;
            }
        }
        if (preferences.contains(KEY_EXPECTED_NOTIFICATIONS)) {
            found = true;
            if (adapter.isSensitiveNotificationsHidden()
                    != preferences.getBoolean(KEY_EXPECTED_NOTIFICATIONS, false)) {
                return false;
            }
        }
        return found;
    }

    private boolean hasCompleteSnapshot() {
        return preferences.contains(KEY_CAMERA_DISABLED)
                && preferences.contains(KEY_SCREEN_CAPTURE_DISABLED)
                && preferences.contains(KEY_MICROPHONE_MUTED)
                && (!preferences.contains(KEY_EXPECTED_LOCATION)
                || preferences.contains(KEY_LOCATION_ENABLED))
                && (!preferences.contains(KEY_EXPECTED_USB_DATA)
                || preferences.contains(KEY_USB_DATA_ENABLED))
                && preferences.contains(KEY_KEYGUARD_DISABLED_FEATURES);
    }

    private void markRecoveryRequired() {
        preferences.edit().putBoolean(KEY_RECOVERY_REQUIRED, true).commit();
    }

    private boolean adapterReady() {
        try {
            return adapter.availability() == GhostModeDevicePolicyAdapter.Availability.READY;
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    private boolean clearSnapshot() {
        return preferences.edit().clear().commit();
    }
}
