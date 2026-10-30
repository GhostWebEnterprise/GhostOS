// SPDX-License-Identifier: Apache-2.0
package org.ghostos.privacy;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Collections;

/** Explicit Device Owner controls; unsupported GhostMode categories remain inactive. */
public final class PrivacyActivity extends Activity {
    private LinearLayout content;
    private GhostModeDevicePolicyAdapter adapter;
    private TextView readiness;
    private Switch cameraSwitch;
    private Switch screenCaptureSwitch;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        adapter = new GhostModeDevicePolicyAdapter(this);

        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        content.setPadding(padding, padding, padding, padding);
        scroll.addView(content);
        setContentView(scroll);

        text(R.string.app_name, 28);
        text(R.string.intro, 17);
        readiness = text(R.string.ghostmode_policy_unavailable, 15);
        text(R.string.ghostmode_policy_scope, 15);
        text(R.string.ghostmode_policy_controls_title, 18);

        cameraSwitch = addPolicySwitch(R.string.ghostmode_camera_control, "camera");
        screenCaptureSwitch = addPolicySwitch(
                R.string.ghostmode_screen_capture_control, "screenCapture");
        refreshPolicyControls();

        link(R.string.permissions, Settings.ACTION_PRIVACY_SETTINGS);
        link(R.string.location, Settings.ACTION_LOCATION_SOURCE_SETTINGS);
        link(R.string.vpn, Settings.ACTION_VPN_SETTINGS);
        link(R.string.network, Settings.ACTION_WIRELESS_SETTINGS);
        link(R.string.apps, Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS);
        link(R.string.security, Settings.ACTION_SECURITY_SETTINGS);
        text(R.string.scope, 15);
    }

    @Override protected void onResume() {
        super.onResume();
        refreshPolicyControls();
    }

    private Switch addPolicySwitch(int label, String control) {
        Switch policySwitch = new Switch(this);
        policySwitch.setText(label);
        policySwitch.setPadding(0, 16, 0, 16);
        content.addView(policySwitch);
        bindPolicySwitch(policySwitch, control);
        return policySwitch;
    }

    private void bindPolicySwitch(Switch policySwitch, String control) {
        policySwitch.setOnCheckedChangeListener(
                (button, enabled) -> applyPolicy(control, enabled));
    }

    private void refreshPolicyControls() {
        GhostModeDevicePolicyAdapter.Availability availability;
        try {
            availability = adapter.availability();
        } catch (RuntimeException unavailable) {
            availability = GhostModeDevicePolicyAdapter.Availability.POLICY_SERVICE_UNAVAILABLE;
        }

        showPolicyReadiness(availability);
        boolean ready = availability == GhostModeDevicePolicyAdapter.Availability.READY;
        cameraSwitch.setVisibility(ready ? View.VISIBLE : View.GONE);
        screenCaptureSwitch.setVisibility(ready ? View.VISIBLE : View.GONE);
        cameraSwitch.setEnabled(ready);
        screenCaptureSwitch.setEnabled(ready);
        if (!ready) {
            return;
        }

        try {
            setCheckedWithoutApplying(cameraSwitch, adapter.isCameraDisabled(), "camera");
            setCheckedWithoutApplying(
                    screenCaptureSwitch, adapter.isScreenCaptureDisabled(), "screenCapture");
        } catch (RuntimeException unavailable) {
            cameraSwitch.setVisibility(View.GONE);
            screenCaptureSwitch.setVisibility(View.GONE);
            readiness.setText(R.string.ghostmode_policy_unavailable);
        }
    }

    private void setCheckedWithoutApplying(Switch policySwitch, boolean checked, String control) {
        policySwitch.setOnCheckedChangeListener(null);
        policySwitch.setChecked(checked);
        bindPolicySwitch(policySwitch, control);
    }

    private void showPolicyReadiness(GhostModeDevicePolicyAdapter.Availability availability) {
        int message;
        switch (availability) {
            case READY:
                message = R.string.ghostmode_policy_ready;
                break;
            case DEVICE_OWNER_REQUIRED:
                message = R.string.ghostmode_device_owner_required;
                break;
            case ADMIN_INACTIVE:
                message = R.string.ghostmode_admin_inactive;
                break;
            default:
                message = R.string.ghostmode_policy_unavailable;
                break;
        }
        readiness.setText(message);
    }

    /** Called only by an explicit user change to one of the supported switches. */
    private void applyPolicy(String control, boolean enabled) {
        cameraSwitch.setEnabled(false);
        screenCaptureSwitch.setEnabled(false);
        try {
            if (adapter.availability() != GhostModeDevicePolicyAdapter.Availability.READY) {
                Toast.makeText(this, R.string.ghostmode_policy_unavailable, Toast.LENGTH_LONG).show();
                return;
            }

            adapter.apply(Collections.singletonMap(control, enabled));
            int message;
            if ("camera".equals(control)) {
                message = enabled
                        ? R.string.ghostmode_camera_disabled
                        : R.string.ghostmode_camera_enabled;
            } else {
                message = enabled
                        ? R.string.ghostmode_screen_capture_disabled
                        : R.string.ghostmode_screen_capture_enabled;
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        } catch (RuntimeException failure) {
            int message = failure.getSuppressed().length > 0
                    ? R.string.ghostmode_policy_rollback_incomplete
                    : R.string.ghostmode_policy_change_failed;
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        } finally {
            refreshPolicyControls();
        }
    }

    private TextView text(int label, int size) {
        TextView view = new TextView(this);
        view.setText(label);
        view.setTextSize(size);
        view.setPadding(0, 16, 0, 24);
        content.addView(view);
        return view;
    }

    private void link(int label, String action) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setOnClickListener(v -> {
            try {
                startActivity(new Intent(action));
            } catch (ActivityNotFoundException | SecurityException unavailable) {
                Toast.makeText(this, R.string.unavailable, Toast.LENGTH_LONG).show();
            }
        });
        content.addView(button);
    }
}
