package com.freedomfighter.readersattention.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.freedomfighter.readersattention.App
import com.freedomfighter.readersattention.MainActivity
import com.freedomfighter.readersattention.R
import com.freedomfighter.readersattention.ui.OPEN_SETTINGS

/** The switch among the quick settings. Without notification access, a tap opens the app's settings. */
class BatchTileService : TileService() {
    override fun onStartListening() = show()

    override fun onClick() {
        val s = (application as App).prefs.settings.value
        if (BatchService.enabled(this)) { BatchService.set(this, !s.batching); show(); return }
        val open = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(OPEN_SETTINGS, true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(open)
        }
    }

    private fun show() {
        val tile = qsTile ?: return
        val s = (application as App).prefs.settings.value
        tile.state = if (BatchService.active(this, s)) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_batch)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = if (BatchService.active(this, s)) BatchService.nextLine(this, s)?.substringAfterLast(' ') else null
        tile.updateTile()
    }
}
