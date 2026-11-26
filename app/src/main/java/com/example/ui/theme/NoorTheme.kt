package com.example.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.model.ShopSettings
import com.example.data.model.THEME_PRESETS

data class NoorPalette(
    val isDark: Boolean,
    val accent: Color,
    val accentHover: Color,
    val background: Color,
    val card: Color,
    val inputBg: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val statusGreen: Color = Color(0xFF10B981),
    val statusPink: Color = Color(0xFFFF1F8E),
    val statusAmber: Color = Color(0xFFFFB454),
    val statusRed: Color = Color(0xFFEF4444)
) {
    val primaryGradient: Brush
        get() = Brush.horizontalGradient(listOf(accent, accentHover))
}

val LocalNoorColors = staticCompositionLocalOf {
    NoorPalette(
        isDark = true,
        accent = Color(0xFFFF1F8E),
        accentHover = Color(0xFFC20A72),
        background = Color(0xFF000000),
        card = Color(0xFF0E0E12),
        inputBg = Color(0xFF16161C),
        border = Color(0xFF262630),
        textPrimary = Color(0xFFFFFFFF),
        textSecondary = Color(0xFFA1A1AA),
        textMuted = Color(0xFF71717A)
    )
}

fun parseHexColor(hex: String, fallback: Color): Color {
    return try {
        val clean = hex.trim().removePrefix("#")
        val value = when (clean.length) {
            6 -> 0xFF000000L or clean.toLong(16)
            8 -> clean.toLong(16)
            else -> return fallback
        }
        Color(value)
    } catch (_: Exception) {
        fallback
    }
}

@Composable
fun NoorTheme(
    settings: ShopSettings,
    content: @Composable () -> Unit
) {
    val isDark = settings.colorMode != "light"
    val preset = THEME_PRESETS.firstOrNull { it.id == settings.themePreset }
    val accent = parseHexColor(settings.accentColor, parseHexColor(preset?.accentHex ?: "#FF1F8E", Color(0xFFFF1F8E)))
    val hover = parseHexColor(preset?.hoverHex ?: "#C20A72", accent.copy(alpha = 0.85f))

    val palette = if (isDark) {
        val bg = parseHexColor(settings.bgColor, parseHexColor(preset?.bgHex ?: "#000000", Color.Black))
        val card = parseHexColor(settings.cardColor, parseHexColor(preset?.cardHex ?: "#0E0E12", Color(0xFF0E0E12)))
        NoorPalette(
            isDark = true,
            accent = accent,
            accentHover = hover,
            background = bg,
            card = card,
            inputBg = Color(0xFF17171F),
            border = Color(0xFF272733),
            textPrimary = Color(0xFFFFFFFF),
            textSecondary = Color(0xFFA1A1AA),
            textMuted = Color(0xFF71717A)
        )
    } else {
        NoorPalette(
            isDark = false,
            accent = accent,
            accentHover = hover,
            background = Color(0xFFF6F7FB),
            card = Color(0xFFFFFFFF),
            inputBg = Color(0xFFF0F1F6),
            border = Color(0xFFE2E4EC),
            textPrimary = Color(0xFF111118),
            textSecondary = Color(0xFF525260),
            textMuted = Color(0xFF8A8A98)
        )
    }

    val colorScheme = if (isDark) {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = Color.White,
            secondary = palette.accentHover,
            background = palette.background,
            onBackground = palette.textPrimary,
            surface = palette.card,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.inputBg,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.border,
            error = palette.statusRed
        )
    } else {
        lightColorScheme(
            primary = palette.accent,
            onPrimary = Color.White,
            secondary = palette.accentHover,
            background = palette.background,
            onBackground = palette.textPrimary,
            surface = palette.card,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.inputBg,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.border,
            error = palette.statusRed
        )
    }

    CompositionLocalProvider(LocalNoorColors provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

fun Modifier.noorCard(
    palette: NoorPalette,
    radius: Dp = 18.dp,
    highlighted: Boolean = false
): Modifier {
    val shape = RoundedCornerShape(radius)
    val borderColor = if (highlighted) palette.accent.copy(alpha = 0.7f) else palette.border
    val mod = if (highlighted) {
        this.shadow(
            elevation = 10.dp,
            shape = shape,
            ambientColor = palette.accent.copy(alpha = 0.38f),
            spotColor = palette.accent.copy(alpha = 0.45f)
        )
    } else this
    return mod
        .background(palette.card, shape)
        .border(1.dp, borderColor, shape)
}
