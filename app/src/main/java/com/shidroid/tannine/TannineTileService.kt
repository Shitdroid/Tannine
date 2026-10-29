package com.shidroid.tannine

import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class TannineTileService : TileService() {
    private lateinit var prefs: Prefs

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        if (!TimeoutController(this).canWrite() ||
            (!prefs.universalMode && !ForegroundAppDetector.hasAccess(this))
        ) {
            startActivityAndCollapse(
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }

        prefs.enabled = !prefs.enabled
        updateService()
        updateTile()
    }

    private fun updateService() {
        val selectedModeReady = prefs.enabled &&
            ForegroundAppDetector.hasAccess(this) &&
            TimeoutController(this).canWrite()
        if (prefs.enabled && (prefs.universalMode || selectedModeReady)) {
            WatcherService.start(this)
        } else {
            WatcherService.stop(this)
        }
    }

    private fun updateTile() {
        qsTile?.let { tile ->
            tile.icon = Icon.createWithResource(this, R.drawable.ic_stat_tea)
            tile.label = getString(R.string.quick_settings_tile_label)
            tile.state = if (prefs.enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            tile.updateTile()
        }
    }

    companion object {
        fun requestRefresh(context: android.content.Context) {
            requestListeningState(
                context,
                ComponentName(context, TannineTileService::class.java)
            )
        }
    }
}
