package com.shidroid.tannine.ui.theme

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.materialkolor.rememberDynamicColorScheme

/** Selectable accent colors, named after teas. The first one is the default. */
enum class TeaPalette(val label: String, val seed: Color) {
    Oolong("Oolong", Color(0xFF00898B)),
    Matcha("Matcha", Color(0xFF5B8C3A)),
    Chai("Chai", Color(0xFFB3702A)),
    Hibiscus("Hibiscus", Color(0xFFC2395F)),
    Lavender("Lavender", Color(0xFF7E5BB0)),
    EarlGrey("Earl Grey", Color(0xFF3F6FB0)),
}

@Composable
fun TannineTheme(
    darkTheme: Boolean,
    palette: TeaPalette = TeaPalette.Oolong,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val seeded = rememberDynamicColorScheme(seedColor = palette.seed, isDark = darkTheme)
    val colorScheme: ColorScheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else seeded
    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
