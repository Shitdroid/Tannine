package com.shidroid.tannine

import android.content.Context
import androidx.core.content.edit

class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("tannine", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = sp.getBoolean("enabled", false)
        set(v) = sp.edit { putBoolean("enabled", v) }

    var universalMode: Boolean
        get() = sp.getBoolean("universal_mode", sp.getBoolean("universal_enabled", false))
        set(v) = sp.edit { putBoolean("universal_mode", v) }

    var triggerApps: Set<String>
        get() = sp.getStringSet("trigger_apps", emptySet()) ?: emptySet()
        set(v) = sp.edit { putStringSet("trigger_apps", v.toSet()) }

    /** 0 = follow system, 1 = light, 2 = dark. */
    var themeMode: Int
        get() = sp.getInt("theme_mode", 0)
        set(v) = sp.edit { putInt("theme_mode", v) }

    var palette: Int
        get() = sp.getInt("palette", 0)
        set(v) = sp.edit { putInt("palette", v) }

    var dynamicColor: Boolean
        get() = sp.getBoolean("dynamic_color", false)
        set(v) = sp.edit { putBoolean("dynamic_color", v) }

    /** Timeout to restore, or -1 when we have not overridden the system value. */
    var savedTimeout: Int
        get() = sp.getInt("saved_timeout", -1)
        set(v) = sp.edit { putInt("saved_timeout", v) }
}
