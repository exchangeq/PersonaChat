package com.wunding.personachat.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

val AccentGreen = Color(0xFF2FD6B0)
val AccentAmber = Color(0xFFF0B35A)
val BgDark = Color(0xFF0B0D0E)
val SurfaceDark = Color(0xFF14181A)
val SurfaceDark2 = Color(0xFF1B2124)

val LocalAccent = staticCompositionLocalOf { AccentGreen }

@Composable
fun PersonaTheme(
    dark: Boolean = isSystemInDarkTheme(),
    accent: Color = AccentGreen,
    content: @Composable () -> Unit,
) {
    val scheme = if (dark) {
        darkColorScheme(
            primary = accent,
            onPrimary = Color(0xFF04110D),
            primaryContainer = accent.copy(alpha = 0.18f),
            onPrimaryContainer = accent,
            secondary = accent,
            background = BgDark,
            onBackground = Color(0xFFE8ECEB),
            surface = SurfaceDark,
            onSurface = Color(0xFFE8ECEB),
            surfaceVariant = SurfaceDark2,
            onSurfaceVariant = Color(0xFF9BA6A3),
            error = Color(0xFFFF7A7A),
            outline = Color(0xFF2A3235),
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = Color.White,
            primaryContainer = accent.copy(alpha = 0.18f),
            onPrimaryContainer = Color(0xFF0B3A30),
            background = Color(0xFFF6F7F6),
            onBackground = Color(0xFF171A19),
            surface = Color.White,
            onSurface = Color(0xFF171A19),
            surfaceVariant = Color(0xFFEDEFEE),
            onSurfaceVariant = Color(0xFF5A6360),
            error = Color(0xFFB3271E),
            outline = Color(0xFFD5D9D7),
        )
    }
    val type = Typography(
        bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
        titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp),
        bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    )
    CompositionLocalProvider(LocalAccent provides accent) {
        MaterialTheme(colorScheme = scheme, typography = type, content = content)
    }
}
