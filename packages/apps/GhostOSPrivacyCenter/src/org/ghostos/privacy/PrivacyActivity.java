// SPDX-License-Identifier: Apache-2.0
package org.ghostos.privacy;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** No permissions, background services, telemetry, or hidden API access. */
public final class PrivacyActivity extends Activity {
    private LinearLayout content;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        content.setPadding(padding, padding, padding, padding);
        scroll.addView(content);
        setContentView(scroll);
        text(R.string.app_name, 28);
        text(R.string.intro, 17);
        link(R.string.permissions, Settings.ACTION_PRIVACY_SETTINGS);
        link(R.string.location, Settings.ACTION_LOCATION_SOURCE_SETTINGS);
        link(R.string.vpn, Settings.ACTION_VPN_SETTINGS);
        link(R.string.network, Settings.ACTION_WIRELESS_SETTINGS);
        link(R.string.apps, Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS);
        link(R.string.security, Settings.ACTION_SECURITY_SETTINGS);
        text(R.string.scope, 15);
    }

    private void text(int label, int size) {
        TextView view = new TextView(this);
        view.setText(label);
        view.setTextSize(size);
        view.setPadding(0, 16, 0, 24);
        content.addView(view);
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
