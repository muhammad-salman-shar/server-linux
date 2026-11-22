package neurasamu.build.samulab.linux

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color

enum class ThemeMode(val label: String) {
    LIGHT("Light"),
    DARK("Dark"),
    SYSTEM("System");

    companion object {
        fun fromName(name: String?): ThemeMode =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: DARK
    }
}

@Immutable
data class SaMuPalette(
    val isDark: Boolean,
    val primary: Color,
    val onPrimary: Color,
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val surfaceHover: Color,
    val onBackground: Color,
    val onSurface: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val divider: Color,
    val userBubble: Color,
    val userBubbleBorder: Color,
    val assistantBubble: Color,
    val assistantBubbleBorder: Color,
    val error: Color,
    val errorBg: Color,
    val success: Color,
    val warning: Color,
    val codeBg: Color,
    val codeText: Color,
    val linkColor: Color
)

val DarkSaMuPalette = SaMuPalette(
    isDark = true,
    primary = Color(0xFFFF7A45),
    onPrimary = Color(0xFFFFFFFF),
    background = Color(0xFF0F1115),
    surface = Color(0xFF1A1D24),
    surfaceVariant = Color(0xFF252932),
    surfaceHover = Color(0xFF2F333D),
    onBackground = Color(0xFFE2E8F0),
    onSurface = Color(0xFFE2E8F0),
    textSecondary = Color(0xFF94A3B8),
    textTertiary = Color(0xFF64748B),
    divider = Color(0xFF2A2E36),
    userBubble = Color(0x30FF7A45),
    userBubbleBorder = Color(0x4DFF7A45),
    assistantBubble = Color(0xFF1A1D24),
    assistantBubbleBorder = Color(0xFF252932),
    error = Color(0xFFEF4444),
    errorBg = Color(0xFF3B1212),
    success = Color(0xFF4ADE80),
    warning = Color(0xFFF59E0B),
    codeBg = Color(0xFF0A0C10),
    codeText = Color(0xFFE2E8F0),
    linkColor = Color(0xFFFF7A45)
)

val LightSaMuPalette = SaMuPalette(
    isDark = false,
    primary = Color(0xFFFF6B35),
    onPrimary = Color(0xFFFFFFFF),
    background = Color(0xFFFAFAFA),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF1F5F9),
    surfaceHover = Color(0xFFE2E8F0),
    onBackground = Color(0xFF1A1A1A),
    onSurface = Color(0xFF1A1A1A),
    textSecondary = Color(0xFF64748B),
    textTertiary = Color(0xFF94A3B8),
    divider = Color(0xFFE2E8F0),
    userBubble = Color(0x33FF7A45),
    userBubbleBorder = Color(0x55FF6B35),
    assistantBubble = Color(0xFFFFFFFF),
    assistantBubbleBorder = Color(0xFFE2E8F0),
    error = Color(0xFFDC2626),
    errorBg = Color(0xFFFEE2E2),
    success = Color(0xFF16A34A),
    warning = Color(0xFFD97706),
    codeBg = Color(0xFF1E293B),
    codeText = Color(0xFFF8FAFC),
    linkColor = Color(0xFFFF6B35)
)

val LocalSaMuColors = compositionLocalOf { DarkSaMuPalette }
val LocalFontScale = compositionLocalOf { 1.0f }

@Composable
fun SaMuTheme(
    themeMode: ThemeMode,
    fontScale: Float = 1.0f,
    content: @Composable () -> Unit
) {
    val useDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val target = if (useDark) DarkSaMuPalette else LightSaMuPalette
    val anim = tween<Color>(durationMillis = 250)

    val primary by animateColorAsState(target.primary, anim)
    val background by animateColorAsState(target.background, anim)
    val surface by animateColorAsState(target.surface, anim)
    val surfaceVariant by animateColorAsState(target.surfaceVariant, anim)
    val onBackground by animateColorAsState(target.onBackground, anim)
    val onSurface by animateColorAsState(target.onSurface, anim)
    val divider by animateColorAsState(target.divider, anim)

    val palette = target.copy(
        primary = primary,
        background = background,
        surface = surface,
        surfaceVariant = surfaceVariant,
        onBackground = onBackground,
        onSurface = onSurface,
        divider = divider
    )

    val m3Scheme = if (useDark) {
        darkColorScheme(
            primary = palette.primary,
            onPrimary = palette.onPrimary,
            background = palette.background,
            onBackground = palette.onBackground,
            surface = palette.surface,
            onSurface = palette.onSurface,
            surfaceVariant = palette.surfaceVariant,
            onSurfaceVariant = palette.textSecondary,
            error = palette.error,
            outline = palette.divider
        )
    } else {
        lightColorScheme(
            primary = palette.primary,
            onPrimary = palette.onPrimary,
            background = palette.background,
            onBackground = palette.onBackground,
            surface = palette.surface,
            onSurface = palette.onSurface,
            surfaceVariant = palette.surfaceVariant,
            onSurfaceVariant = palette.textSecondary,
            error = palette.error,
            outline = palette.divider
        )
    }

    CompositionLocalProvider(
        LocalSaMuColors provides palette,
        LocalFontScale provides fontScale.coerceIn(0.8f, 1.4f)
    ) {
        MaterialTheme(
            colorScheme = m3Scheme,
            content = content
        )
    }
}
