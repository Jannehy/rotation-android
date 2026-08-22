package org.rotationmusic.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * The colours Rotation can wear. Ready-made palettes rather than a free
 * colour picker: an accent has to carry text on it and stand apart from the
 * surface behind it, and a free choice loses that faster than it looks.
 */
enum class Palette(
    val id: String,
    val accent: Color,
    val second: Color,
    val deep: Color,
    val ink: Color,
) {
    SUNSET("sunset", Color(0xFFFF5A3C), Color(0xFF7C6CFF), Color(0xFFB3234A), Color(0xFF1A0800)),
    MINT("mint", Color(0xFF2AD4A4), Color(0xFF4AA8FF), Color(0xFF0F766E), Color(0xFF04241C)),
    VIOLET("violet", Color(0xFFA06BFF), Color(0xFFFF6BD6), Color(0xFF6C2BD9), Color(0xFF150526)),
    OCEAN("ocean", Color(0xFF3F9DFF), Color(0xFF22D3EE), Color(0xFF1D4ED8), Color(0xFF03192E)),
    GOLD("gold", Color(0xFFF0B429), Color(0xFFFF7A45), Color(0xFFB45309), Color(0xFF241703)),
    ROSE("rose", Color(0xFFFF5D8F), Color(0xFFFFA26B), Color(0xFFC2185B), Color(0xFF2A0512));

    companion object {
        fun of(id: String?) = entries.firstOrNull { it.id == id } ?: SUNSET
    }
}

/**
 * The palette in force. Read as a plain value so every composable that draws
 * with it redraws when it changes – [RotationTheme] keeps it up to date.
 */
var palette by mutableStateOf(Palette.SUNSET)
    private set

fun usePalette(id: String?) { palette = Palette.of(id) }

val Accent: Color get() = palette.accent
val AccentAlt: Color get() = palette.second
val Deep: Color get() = palette.deep

private fun dark(theme: Palette) = darkColorScheme(
    primary = theme.accent,
    onPrimary = theme.ink,
    secondary = theme.second,
    background = Color(0xFF0A0A0F),
    onBackground = Color(0xFFECEAF2),
    surface = Color(0xFF14141D),
    onSurface = Color(0xFFECEAF2),
    surfaceVariant = Color(0xFF1B1B26),
    onSurfaceVariant = Color(0xFF9291A6),
    outline = Color(0xFF262634),
)

private fun light(theme: Palette) = lightColorScheme(
    primary = theme.accent,
    onPrimary = Color.White,
    secondary = theme.second,
    background = Color(0xFFF6F5F8),
    onBackground = Color(0xFF16151D),
    surface = Color.White,
    onSurface = Color(0xFF16151D),
    surfaceVariant = Color(0xFFF2F1F6),
    onSurfaceVariant = Color(0xFF6B697D),
    outline = Color(0xFFE3E1EA),
)

@Composable
fun RotationTheme(accent: String? = null, content: @Composable () -> Unit) {
    if (accent != null) usePalette(accent)
    val theme = palette
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) dark(theme) else light(theme),
        content = content,
    )
}
