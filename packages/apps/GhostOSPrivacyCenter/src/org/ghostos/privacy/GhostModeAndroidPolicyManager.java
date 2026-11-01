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
 * This is not the full cross-adapter GhostMode preset service. Unsupported
 * network, sensor, clipboard and notification policies remain inactive.
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
            return adapter.availability() == GhostModeDevicePolicyAdapter.Availability.READY
                    ? State.ACTIVE
                    : State.RECOVERY_REQUIRED;
        }
        return adapter.availability() == GhostModeDevicePolicyAdapter.Availability.READY
                ? State.INACTIVE
                : State.UNAVAILABLE;
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
            if (!markActive()) {
                markRecoveryRequired();
                throw new IllegalStateException(
                        "Policy was applied but its state could not be finalized; restore is required");
            }
            return;
        }

        try {
            adapter.apply(Collections.singletonMap(control, enabled));
        } catch (RuntimeException failure) {
            if (failure.getSuppressed().length > 0) {
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
        if (!preferences.contains(KEY_CAMERA_DISABLED)) {
            markRecoveryRequired();
            throw new IllegalStateException("GhostMode rollback snapshot is incomplete");
        }
        if (adapter.availability() != GhostModeDevicePolicyAdapter.Availability.READY) {
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
                .putBoolean(KEY_MICROPHONE_MUTED, snapshot.microphoneMuted());

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
        return new GhostModeDevicePolicyAdapter.Snapshot(
                preferences.getBoolean(KEY_CAMERA_DISABLED, false),
                preferences.getBoolean(KEY_SCREEN_CAPTURE_DISABLED, false),
                preferences.getBoolean(KEY_MICROPHONE_MUTED, false),
                locationEnabled,
                usbDataEnabled);
    }

    private boolean markActive() {
        return preferences.edit()
                .putBoolean(KEY_ACTIVE, true)
                .putBoolean(KEY_PENDING, false)
                .putBoolean(KEY_RECOVERY_REQUIRED, false)
                .commit();
    }

    private void markRecoveryRequired() {
        preferences.edit().putBoolean(KEY_RECOVERY_REQUIRED, true).commit();
    }

    private boolean clearSnapshot() {
        return preferences.edit().clear().commit();
    }
}
