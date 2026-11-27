package com.abdullah.carbrowser.phone.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abdullah.carbrowser.browser.Prefs

/** Bumped whenever the car browser or a setting changes; composables read it to refresh. */
object UiState {
    val version = mutableIntStateOf(0)
    fun bump() { version.intValue++ }
}

@Composable
fun watch(): Int = UiState.version.intValue

@Composable
fun RemoteTheme(content: @Composable () -> Unit) {
    watch()
    val accent = Color(Prefs.accent)
    val black = Prefs.amoled
    val bg = if (black) Color.Black else Color(0xFF111216)
    val scheme = darkColorScheme(
        primary = accent,
        onPrimary = Color(0xFF0E1014),
        primaryContainer = lerp(bg, accent, 0.22f),
        onPrimaryContainer = lerp(Color.White, accent, 0.35f),
        secondaryContainer = lerp(bg, accent, 0.18f),
        onSecondaryContainer = Color(0xFFE6E8EE),
        background = bg,
        onBackground = Color(0xFFE6E8EE),
        surface = bg,
        onSurface = Color(0xFFE6E8EE),
        onSurfaceVariant = Color(0xFF9AA0AC),
        surfaceContainerLowest = if (black) Color.Black else Color(0xFF0C0D10),
        surfaceContainerLow = if (black) Color(0xFF0A0A0C) else Color(0xFF16171C),
        surfaceContainer = if (black) Color(0xFF111114) else Color(0xFF1B1C22),
        surfaceContainerHigh = if (black) Color(0xFF18181C) else Color(0xFF22242B),
        surfaceContainerHighest = if (black) Color(0xFF202026) else Color(0xFF2A2C34),
        outline = Color(0xFF3A3D47),
        outlineVariant = Color(0xFF2A2C34),
        error = Color(0xFFFF8A9A),
    )
    val type = Typography(
        titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
        labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    )
    val shapes = Shapes(
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(18.dp),
        large = RoundedCornerShape(26.dp),
    )
    MaterialTheme(colorScheme = scheme, typography = type, shapes = shapes, content = content)
}
