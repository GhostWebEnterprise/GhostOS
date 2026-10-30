// SPDX-License-Identifier: Apache-2.0
package org.ghostos.privacy;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;

import java.util.Map;
import java.util.Objects;

/**
 * Narrow Device Owner adapter for Android-enforced camera and screen-capture
 * restrictions. It never requests or applies policies during construction.
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

        private Snapshot(boolean cameraDisabled, boolean screenCaptureDisabled) {
            this.cameraDisabled = cameraDisabled;
            this.screenCaptureDisabled = screenCaptureDisabled;
        }

        public boolean cameraDisabled() {
            return cameraDisabled;
        }

        public boolean screenCaptureDisabled() {
            return screenCaptureDisabled;
        }
    }

    private final Context context;
    private final DevicePolicyManager manager;
    private final ComponentName admin;

    public GhostModeDevicePolicyAdapter(Context context) {
        this.context = Objects.requireNonNull(context, "context").getApplicationContext();
        this.manager = this.context.getSystemService(DevicePolicyManager.class);
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
            if (!"camera".equals(entry.getKey()) && !"screenCapture".equals(entry.getKey())) {
                throw new IllegalArgumentException("Unsupported Device Owner control: " + entry.getKey());
            }
            Objects.requireNonNull(entry.getValue(), "Null policy value: " + entry.getKey());
        }

        Snapshot before = snapshot();
        try {
            if (requested.containsKey("camera")) {
                manager.setCameraDisabled(admin, requested.get("camera"));
            }
            if (requested.containsKey("screenCapture")) {
                manager.setScreenCaptureDisabled(admin, requested.get("screenCapture"));
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

    private Snapshot snapshot() {
        return new Snapshot(
                manager.getCameraDisabled(admin),
                manager.getScreenCaptureDisabled(admin));
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
            if (failure == null) {
                failure = error;
            } else {
                failure.addSuppressed(error);
            }
        }
        if (failure != null) {
            throw failure;
        }
        if (manager.getCameraDisabled(admin) != snapshot.cameraDisabled()
                || manager.getScreenCaptureDisabled(admin) != snapshot.screenCaptureDisabled()) {
            throw new IllegalStateException("Android policy rollback did not restore the previous state");
        }
    }

    private void requireReady() {
        Availability state = availability();
        if (state != Availability.READY) {
            throw new SecurityException("GhostMode Device Owner policy adapter unavailable: " + state);
        }
    }
}
