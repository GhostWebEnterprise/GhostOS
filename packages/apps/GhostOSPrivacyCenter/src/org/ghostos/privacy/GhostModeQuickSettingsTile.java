// SPDX-License-Identifier: Apache-2.0
package org.ghostos.privacy;

import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/**
 * Quick Settings entry point for GhostOS Privacy Center.
 *
 * This is deliberately a non-toggleable shortcut until real Android policy
 * backends are wired. It always advertises INACTIVE and explains that setup
 * is required; it never claims that GhostMode protections are applied.
 */
public final class GhostModeQuickSettingsTile extends TileService {
    @Override
    public void onStartListening() {
        super.onStartListening();
        publishSetupRequiredState();
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

    private void publishSetupRequiredState() {
        Tile tile = getQsTile();
        if (tile == null) {
            return;
        }

        tile.setState(Tile.STATE_INACTIVE);
        tile.setLabel(getString(R.string.ghostmode_tile_label));
        tile.setSubtitle(getString(R.string.ghostmode_tile_subtitle));
        tile.setContentDescription(getString(R.string.ghostmode_tile_description));
        tile.setIcon(Icon.createWithResource(this, android.R.drawable.ic_lock_lock));
        tile.updateTile();
    }
}
