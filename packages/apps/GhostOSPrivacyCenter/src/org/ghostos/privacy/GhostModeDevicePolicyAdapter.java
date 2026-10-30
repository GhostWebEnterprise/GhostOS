// SPDX-License-Identifier: Apache-2.0
package org.ghostos.privacy;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.UserManager;

import java.util.Map;
import java.util.Objects;

/**
 * Narrow Device Owner adapter for Android-enforced camera, screen-capture,
 * microphone and location restrictions. It never applies policies during
 * construction.
 *
 * The caller must explicitly invoke apply() after checking availability().
 * Unsupported GhostMode categories remain degraded and must not be reported
 * as active by the manager or Quick Settings tile.
 */
public final class GhostModeDevicePolicyAdapter {
    public enum Availability {
        READY,
        POLICY_SERVICE_UNAVAILABLE,
        DEVICE_OWNER_REQUIRED,
        ADMIN_INACTIVE
    }

    public static final class Snapshot {
        private final boolean cameraDisabled;
        private final boolean screenCaptureDisabled;
        private final boolean microphoneMuted;
        private final Boolean locationEnabled;
        private final Boolean usbDataSignalingEnabled;

        private Snapshot(
                boolean cameraDisabled,
                boolean screenCaptureDisabled,
                boolean microphoneMuted,
                Boolean locationEnabled,
                Boolean usbDataSignalingEnabled) {
            this.cameraDisabled = cameraDisabled;
            this.screenCaptureDisabled = screenCaptureDisabled;
            this.microphoneMuted = microphoneMuted;
            this.locationEnabled = locationEnabled;
            this.usbDataSignalingEnabled = usbDataSignalingEnabled;
        }

        public boolean cameraDisabled() {
            return cameraDisabled;
        }

        public boolean screenCaptureDisabled() {
            return screenCaptureDisabled;
        }

        public boolean microphoneMuted() {
            return microphoneMuted;
        }

        public Boolean locationEnabled() {
            return locationEnabled;
        }

        public Boolean usbDataSignalingEnabled() {
            return usbDataSignalingEnabled;
        }
    }

    private final Context context;
    private final DevicePolicyManager manager;
    private final ComponentName admin;
    private final LocationManager locationManager;

    public GhostModeDevicePolicyAdapter(Context context) {
        this.context = Objects.requireNonNull(context, "context").getApplicationContext();
        this.manager = this.context.getSystemService(DevicePolicyManager.class);
        this.locationManager = this.context.getSystemService(LocationManager.class);
        this.admin = new ComponentName(this.context, GhostModeDeviceAdminReceiver.class);
    }

    public Availability availability() {
        if (manager == null) {
            return Availability.POLICY_SERVICE_UNAVAILABLE;
        }
        if (!manager.isDeviceOwnerApp(context.getPackageName())) {
            return Availability.DEVICE_OWNER_REQUIRED;
        }
        if (!manager.isAdminActive(admin)) {
            return Availability.ADMIN_INACTIVE;
        }
        return Availability.READY;
    }

    public Snapshot apply(Map<String, Boolean> requested) {
        requireReady();
        Objects.requireNonNull(requested, "requested");
        for (Map.Entry<String, Boolean> entry : requested.entrySet()) {
            if (!"camera".equals(entry.getKey())
                    && !"screenCapture".equals(entry.getKey())
                    && !"microphoneMuted".equals(entry.getKey())
                    && !"locationEnabled".equals(entry.getKey())
                    && !"usbDataSignaling".equals(entry.getKey())) {
                throw new IllegalArgumentException("Unsupported Device Owner control: " + entry.getKey());
            }
            Objects.requireNonNull(entry.getValue(), "Null policy value: " + entry.getKey());
        }

        if (requested.containsKey("usbDataSignaling") && !usbDataSignalingSupported()) {
            throw new UnsupportedOperationException(
                    "USB data signaling is unavailable on this Android device");
        }
        if (requested.containsKey("locationEnabled") && !locationControlSupported()) {
            throw new UnsupportedOperationException(
                    "Device Owner location control requires Android 11 or later");
        }

        Snapshot before = snapshot();
        try {
            if (requested.containsKey("camera")) {
                manager.setCameraDisabled(admin, requested.get("camera"));
            }
            if (requested.containsKey("screenCapture")) {
                manager.setScreenCaptureDisabled(admin, requested.get("screenCapture"));
            }
            if (requested.containsKey("microphoneMuted")) {
                setMicrophoneMuted(requested.get("microphoneMuted"));
            }
            if (requested.containsKey("locationEnabled")) {
                manager.setLocationEnabled(admin, requested.get("locationEnabled"));
            }
            if (requested.containsKey("usbDataSignaling")) {
                manager.setUsbDataSignalingEnabled(requested.get("usbDataSignaling"));
            }
            verify(requested);
            return before;
        } catch (RuntimeException failure) {
            try {
                restore(before);
            } catch (RuntimeException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            throw failure;
        }
    }

    public void rollback(Snapshot snapshot) {
        requireReady();
        restore(Objects.requireNonNull(snapshot, "snapshot"));
    }

    public boolean isCameraDisabled() {
        requireReady();
        return manager.getCameraDisabled(admin);
    }

    public boolean isScreenCaptureDisabled() {
        requireReady();
        return manager.getScreenCaptureDisabled(admin);
    }

    /** Returns the microphone restriction set by this GhostOS device admin. */
    public boolean isMicrophoneMuted() {
        requireReady();
        return manager.getUserRestrictions(admin)
                .getBoolean(UserManager.DISALLOW_UNMUTE_MICROPHONE, false);
    }

    public boolean isLocationControlSupported() {
        requireReady();
        return locationControlSupported();
    }

    public boolean isLocationEnabled() {
        requireReady();
        if (!locationControlSupported()) {
            throw new UnsupportedOperationException(
                    "Device Owner location control requires Android 11 or later");
        }
        return locationManager.isLocationEnabled();
    }

    public boolean isUsbDataSignalingSupported() {
        requireReady();
        return usbDataSignalingSupported();
    }

    public boolean isUsbDataSignalingEnabled() {
        requireReady();
        if (!usbDataSignalingSupported()) {
            throw new UnsupportedOperationException(
                    "USB data signaling is unavailable on this Android device");
        }
        return manager.isUsbDataSignalingEnabled();
    }

    private void setMicrophoneMuted(boolean muted) {
        if (muted) {
            manager.addUserRestriction(admin, UserManager.DISALLOW_UNMUTE_MICROPHONE);
        } else {
            manager.clearUserRestriction(admin, UserManager.DISALLOW_UNMUTE_MICROPHONE);
        }
    }

    private boolean locationControlSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && locationManager != null;
    }

    private boolean usbDataSignalingSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && manager.canUsbDataSignalingBeDisabled();
    }

    private Snapshot snapshot() {
        Bundle restrictions = manager.getUserRestrictions(admin);
        Boolean locationEnabled = locationControlSupported()
                ? locationManager.isLocationEnabled()
                : null;
        Boolean usbDataSignaling = usbDataSignalingSupported()
                ? manager.isUsbDataSignalingEnabled()
                : null;
        return new Snapshot(
                manager.getCameraDisabled(admin),
                manager.getScreenCaptureDisabled(admin),
                restrictions.getBoolean(UserManager.DISALLOW_UNMUTE_MICROPHONE, false),
                locationEnabled,
                usbDataSignaling);
    }

    private void verify(Map<String, Boolean> requested) {
        if (requested.containsKey("camera")
                && manager.getCameraDisabled(admin) != requested.get("camera")) {
            throw new IllegalStateException("Android did not apply the requested camera policy");
        }
        if (requested.containsKey("screenCapture")
                && manager.getScreenCaptureDisabled(admin) != requested.get("screenCapture")) {
            throw new IllegalStateException("Android did not apply the requested screen-capture policy");
        }
        if (requested.containsKey("microphoneMuted")
                && isMicrophoneMuted() != requested.get("microphoneMuted")) {
            throw new IllegalStateException("Android did not apply the requested microphone policy");
        }
        if (requested.containsKey("locationEnabled")
                && locationManager.isLocationEnabled() != requested.get("locationEnabled")) {
            throw new IllegalStateException("Android did not apply the requested location policy");
        }
        if (requested.containsKey("usbDataSignaling")
                && manager.isUsbDataSignalingEnabled() != requested.get("usbDataSignaling")) {
            throw new IllegalStateException("Android did not apply the requested USB data policy");
        }
    }

    private void restore(Snapshot snapshot) {
        RuntimeException failure = null;
        try {
            manager.setCameraDisabled(admin, snapshot.cameraDisabled());
        } catch (RuntimeException error) {
            failure = error;
        }
        try {
            manager.setScreenCaptureDisabled(admin, snapshot.screenCaptureDisabled());
        } catch (RuntimeException error) {
            failure = append(failure, error);
        }
        try {
            setMicrophoneMuted(snapshot.microphoneMuted());
        } catch (RuntimeException error) {
            failure = append(failure, error);
        }
        if (snapshot.locationEnabled() != null) {
            try {
                manager.setLocationEnabled(admin, snapshot.locationEnabled());
            } catch (RuntimeException error) {
                failure = append(failure, error);
            }
        }
        if (snapshot.usbDataSignalingEnabled() != null) {
            try {
                manager.setUsbDataSignalingEnabled(snapshot.usbDataSignalingEnabled());
            } catch (RuntimeException error) {
                failure = append(failure, error);
            }
        }
        if (failure != null) {
            throw failure;
        }
        Bundle restrictions = manager.getUserRestrictions(admin);
        if (manager.getCameraDisabled(admin) != snapshot.cameraDisabled()
                || manager.getScreenCaptureDisabled(admin) != snapshot.screenCaptureDisabled()
                || restrictions.getBoolean(UserManager.DISALLOW_UNMUTE_MICROPHONE, false)
                != snapshot.microphoneMuted()
                || (snapshot.locationEnabled() != null
                && locationManager.isLocationEnabled() != snapshot.locationEnabled())
                || (snapshot.usbDataSignalingEnabled() != null
                && manager.isUsbDataSignalingEnabled() != snapshot.usbDataSignalingEnabled())) {
            throw new IllegalStateException("Android policy rollback did not restore the previous state");
        }
    }

    private static RuntimeException append(RuntimeException current, RuntimeException next) {
        if (current == null) {
            return next;
        }
        current.addSuppressed(next);
        return current;
    }

    private void requireReady() {
        Availability state = availability();
        if (state != Availability.READY) {
            throw new SecurityException("GhostMode Device Owner policy adapter unavailable: " + state);
        }
    }
}
