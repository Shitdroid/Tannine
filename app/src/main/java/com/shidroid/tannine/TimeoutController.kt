package com.shidroid.tannine

import android.content.Context
import android.provider.Settings

/** Switches the system screen timeout to "never" and back, remembering the original value. */
class TimeoutController(private val context: Context) {
    private val prefs = Prefs(context)
    private val resolver get() = context.contentResolver

    fun canWrite() = Settings.System.canWrite(context)

    val isOverridden get() = prefs.savedTimeout != -1

    fun applyNever() {
        if (!canWrite()) return
        val current = Settings.System.getInt(resolver, Settings.System.SCREEN_OFF_TIMEOUT, DEFAULT_TIMEOUT)
        if (current == NEVER) return
        if (!isOverridden) prefs.savedTimeout = current
        Settings.System.putInt(resolver, Settings.System.SCREEN_OFF_TIMEOUT, NEVER)
    }

    fun restore() {
        if (!isOverridden) return
        if (canWrite()) {
            Settings.System.putInt(resolver, Settings.System.SCREEN_OFF_TIMEOUT, prefs.savedTimeout)
        }
        prefs.savedTimeout = -1
    }

    companion object {
        const val NEVER = Int.MAX_VALUE
        private const val DEFAULT_TIMEOUT = 60_000
    }
}
