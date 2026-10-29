package com.shidroid.tannine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = Prefs(context)
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && prefs.enabled &&
            (prefs.universalMode || ForegroundAppDetector.hasAccess(context))
        ) {
            WatcherService.start(context)
        }
    }
}
