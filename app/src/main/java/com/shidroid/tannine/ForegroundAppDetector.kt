package com.shidroid.tannine

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.app.AppOpsManager
import android.content.Context
import android.os.Process

/** Tracks the foreground package by replaying UsageStats events incrementally. */
class ForegroundAppDetector(private val context: Context) {
    private val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private var lastQuery = 0L
    var current: String? = null
        private set

    fun poll(): String? {
        val now = System.currentTimeMillis()
        val from = if (lastQuery == 0L) now - INITIAL_WINDOW else lastQuery
        lastQuery = now
        val events = usm.queryEvents(from, now)
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            when (e.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> current = e.packageName
                UsageEvents.Event.MOVE_TO_BACKGROUND -> if (current == e.packageName) current = null
            }
        }
        return current
    }

    companion object {
        private const val INITIAL_WINDOW = 2 * 60 * 60 * 1000L

        fun hasAccess(context: Context): Boolean {
            val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            return ops.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
            ) == AppOpsManager.MODE_ALLOWED
        }
    }
}
