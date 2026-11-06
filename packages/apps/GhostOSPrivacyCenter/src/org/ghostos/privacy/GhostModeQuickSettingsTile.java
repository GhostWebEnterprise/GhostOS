// SPDX-License-Identifier: Apache-2.0
package org.ghostos.privacy;

import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/**
 * Non-toggleable Quick Settings entry point for GhostOS Privacy Center.
 *
 * The tile reflects only the Device Owner policy state independently verified
 * by GhostModeAndroidPolicyManager. It never applies policy itself; clicking
 * opens the Privacy Center for explicit controls and recovery.
 */
public final class GhostModeQuickSettingsTile extends TileService {
    @Override
    public void onStartListening() {
        super.onStartListening();
        publishVerifiedState();
    }

    @Override
    public void onClick() {
        super.onClick();
        Intent intent = new Intent(this, PrivacyActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        if (Build.VERSION.SDK_INT >= 34) {
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            startActivityAndCollapse(pendingIntent);
        } else {
            startActivityAndCollapse(intent);
        }
    }

    private void publishVerifiedState() {
        Tile tile = getQsTile();
        if (tile == null) {
            return;
        }

        String subtitle;
        tile.setState(Tile.STATE_INACTIVE);
        try {
            GhostModeDevicePolicyAdapter adapter = new GhostModeDevicePolicyAdapter(this);
            GhostModeAndroidPolicyManager manager =
                    new GhostModeAndroidPolicyManager(this, adapter);
            GhostModeAndroidPolicyManager.State state = manager.getState();

            if (state == GhostModeAndroidPolicyManager.State.ACTIVE) {
                tile.setState(Tile.STATE_ACTIVE);
                subtitle = getString(R.string.ghostmode_tile_active_subtitle);
            } else if (state == GhostModeAndroidPolicyManager.State.RECOVERY_REQUIRED) {
                subtitle = getString(R.string.ghostmode_tile_recovery_subtitle);
            } else if (state == GhostModeAndroidPolicyManager.State.UNAVAILABLE) {
                subtitle = getString(R.string.ghostmode_tile_unavailable_subtitle);
            } else {
                subtitle = getString(R.string.ghostmode_tile_subtitle);
            }
        } catch (RuntimeException unavailable) {
            // Keep the shortcut clickable and never claim protection on read failure.
            subtitle = getString(R.string.ghostmode_tile_unavailable_subtitle);
        }

        tile.setLabel(getString(R.string.ghostmode_tile_label));
        tile.setSubtitle(subtitle);
        tile.setContentDescription(getString(R.string.ghostmode_tile_description));
        tile.setIcon(Icon.createWithResource(this, android.R.drawable.ic_lock_lock));
        tile.updateTile();
    }
}
