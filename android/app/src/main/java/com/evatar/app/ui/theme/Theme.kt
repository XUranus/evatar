package com.evatar.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// Observatory color palette — shared with frontend
object EvatarColors {
    // Dark (default)
    val DarkVoid = Color(0xFF0D0D1A)
    val DarkBackground = Color(0xFF141425)
    val DarkSurface = Color(0xFF1C1C32)
    val DarkSurfaceVariant = Color(0xFF252542)
    val DarkOnSurface = Color(0xFFE8E0D4)
    val DarkOnSurfaceVariant = Color(0xFFA09888)
    val DarkMuted = Color(0xFF6A6258)
    val DarkBorder = Color(0x0FFFFFFF) // 6% white
    val DarkPrimary = Color(0xFFF0A500)        // amber
    val DarkPrimaryDim = Color(0x26F0A500)     // amber 15%
    val DarkError = Color(0xFFE85D75)
    val DarkSuccess = Color(0xFF00D9A6)
    val DarkWarning = Color(0xFFF0A500)
    val DarkBlue = Color(0xFF5B8DEF)

    // Light
    val LightVoid = Color(0xFFF5F0EB)
    val LightBackground = Color(0xFFFAF7F2)
    val LightSurface = Color(0xFFFFFFFF)
    val LightSurfaceVariant = Color(0xFFF0EBE4)
    val LightOnSurface = Color(0xFF1A1510)
    val LightOnSurfaceVariant = Color(0xFF6B5E50)
    val LightMuted = Color(0xFFA09080)
    val LightBorder = Color(0x14000000) // 8% black
    val LightPrimary = Color(0xFFC88500)
    val LightPrimaryDim = Color(0x1AC88500)
    val LightError = Color(0xFFD44459)
    val LightSuccess = Color(0xFF00B88C)
    val LightBlue = Color(0xFF4A7BD4)
}

private val DarkColorScheme = darkColorScheme(
    primary = EvatarColors.DarkPrimary,
    onPrimary = EvatarColors.DarkVoid,
    background = EvatarColors.DarkBackground,
    surface = EvatarColors.DarkSurface,
    surfaceVariant = EvatarColors.DarkSurfaceVariant,
    onSurface = EvatarColors.DarkOnSurface,
    onSurfaceVariant = EvatarColors.DarkOnSurfaceVariant,
    error = EvatarColors.DarkError,
    outline = EvatarColors.DarkBorder,
    outlineVariant = EvatarColors.DarkBorder,
)

private val LightColorScheme = lightColorScheme(
    primary = EvatarColors.LightPrimary,
    onPrimary = Color.White,
    background = EvatarColors.LightBackground,
    surface = EvatarColors.LightSurface,
    surfaceVariant = EvatarColors.LightSurfaceVariant,
    onSurface = EvatarColors.LightOnSurface,
    onSurfaceVariant = EvatarColors.LightOnSurfaceVariant,
    error = EvatarColors.LightError,
    outline = EvatarColors.LightBorder,
    outlineVariant = EvatarColors.LightBorder,
)

@Composable
fun EvatarTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            window?.let {
                it.statusBarColor = colorScheme.background.toArgb()
                WindowCompat.getInsetsController(it, view).isAppearanceLightStatusBars = !darkTheme
            }
        }
    }

    val typography = Typography(
        displayLarge = EvatarTypography.largeTitle,
        headlineLarge = EvatarTypography.title1,
        headlineMedium = EvatarTypography.title2,
        headlineSmall = EvatarTypography.title3,
        titleLarge = EvatarTypography.headline,
        bodyLarge = EvatarTypography.body,
        bodyMedium = EvatarTypography.callout,
        bodySmall = EvatarTypography.subheadline,
        labelLarge = EvatarTypography.footnote,
        labelMedium = EvatarTypography.caption1,
        labelSmall = EvatarTypography.caption2,
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        content = content
    )
}

object EvatarTypography {
    val largeTitle = TextStyle(fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 41.sp)
    val title1 = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp)
    val title2 = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp)
    val title3 = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 25.sp)
    val headline = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp)
    val body = TextStyle(fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 22.sp)
    val callout = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 21.sp)
    val subheadline = TextStyle(fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 20.sp)
    val footnote = TextStyle(fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp)
    val caption1 = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp)
    val caption2 = TextStyle(fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 13.sp)
}
