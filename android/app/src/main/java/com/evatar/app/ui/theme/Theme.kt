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

// iOS-inspired color palette
object EvatarColors {
    // Light
    val LightBackground = Color(0xFFF2F2F7)      // iOS system grouped background
    val LightSurface = Color(0xFFFFFFFF)
    val LightSurfaceVariant = Color(0xFFF2F2F7)
    val LightOnSurface = Color(0xFF1C1C1E)        // iOS label
    val LightOnSurfaceVariant = Color(0xFF8E8E93)  // iOS secondary label
    val LightSeparator = Color(0xFFC6C6C8)         // iOS separator
    val LightPrimary = Color(0xFF007AFF)            // iOS blue
    val LightError = Color(0xFFFF3B30)              // iOS red
    val LightSuccess = Color(0xFF34C759)            // iOS green
    val LightWarning = Color(0xFFFF9500)            // iOS orange

    // Dark
    val DarkBackground = Color(0xFF000000)
    val DarkSurface = Color(0xFF1C1C1E)
    val DarkSurfaceVariant = Color(0xFF2C2C2E)
    val DarkOnSurface = Color(0xFFF2F2F7)
    val DarkOnSurfaceVariant = Color(0xFF8E8E93)
    val DarkSeparator = Color(0xFF38383A)
    val DarkPrimary = Color(0xFF0A84FF)             // iOS dark blue
    val DarkError = Color(0xFFFF453A)
    val DarkSuccess = Color(0xFF30D158)
    val DarkWarning = Color(0xFFFF9F0A)
}

private val LightColorScheme = lightColorScheme(
    primary = EvatarColors.LightPrimary,
    onPrimary = Color.White,
    background = EvatarColors.LightBackground,
    surface = EvatarColors.LightSurface,
    surfaceVariant = EvatarColors.LightSurfaceVariant,
    onSurface = EvatarColors.LightOnSurface,
    onSurfaceVariant = EvatarColors.LightOnSurfaceVariant,
    error = EvatarColors.LightError,
    outline = EvatarColors.LightSeparator,
    outlineVariant = EvatarColors.LightSeparator.copy(alpha = 0.5f),
)

private val DarkColorScheme = darkColorScheme(
    primary = EvatarColors.DarkPrimary,
    onPrimary = Color.White,
    background = EvatarColors.DarkBackground,
    surface = EvatarColors.DarkSurface,
    surfaceVariant = EvatarColors.DarkSurfaceVariant,
    onSurface = EvatarColors.DarkOnSurface,
    onSurfaceVariant = EvatarColors.DarkOnSurfaceVariant,
    error = EvatarColors.DarkError,
    outline = EvatarColors.DarkSeparator,
    outlineVariant = EvatarColors.DarkSeparator.copy(alpha = 0.5f),
)

@Composable
fun EvatarTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

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

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

// iOS-like text styles
object EvatarTypography {
    val largeTitle = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 41.sp,
        letterSpacing = 0.37.sp,
    )
    val title1 = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = 0.36.sp,
    )
    val title2 = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.35.sp,
    )
    val title3 = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 25.sp,
        letterSpacing = 0.38.sp,
    )
    val headline = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.41).sp,
    )
    val body = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.41).sp,
    )
    val callout = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 21.sp,
        letterSpacing = (-0.32).sp,
    )
    val subheadline = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.24).sp,
    )
    val footnote = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = (-0.08).sp,
    )
    val caption1 = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    )
    val caption2 = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 13.sp,
        letterSpacing = 0.07.sp,
    )
}
