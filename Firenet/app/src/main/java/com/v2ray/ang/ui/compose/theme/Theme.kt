package com.v2ray.ang.ui.compose.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val FirenetDarkScheme = darkColorScheme(
    primary = FirenetColors.Accent,
    onPrimary = Color.White,
    primaryContainer = FirenetColors.Accent.copy(alpha = 0.22f),
    onPrimaryContainer = FirenetColors.AccentSoft,
    secondary = FirenetColors.Connected,
    onSecondary = Color(0xFF03251C),
    tertiary = FirenetColors.AccentSoft,
    background = FirenetColors.BackdropMid,
    onBackground = FirenetColors.TextPrimary,
    surface = FirenetColors.BackdropMid,
    onSurface = FirenetColors.TextPrimary,
    surfaceVariant = FirenetColors.GlassFill,
    onSurfaceVariant = FirenetColors.TextSecondary,
    outline = FirenetColors.GlassStroke,
    outlineVariant = FirenetColors.GlassStrokeSoft,
    error = FirenetColors.Blocked,
    onError = Color.White
)

/**
 * تم سراسری برنامه.
 *
 * برنامه فقط در حالت تیره ارائه می‌شود؛ طراحی شیشه‌ای روی پس‌زمینه‌ی روشن
 * کنتراست کافی ندارد و خواندن اعداد سرعت را سخت می‌کند. پارامتر [darkTheme]
 * صرفاً برای پیش‌نمایش نگه داشته شده است.
 */
@Composable
fun FirenetTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        val context = LocalContext.current
        SideEffect {
            val window = (context as? Activity)?.window ?: return@SideEffect
            WindowCompat.setDecorFitsSystemWindows(window, false)
            @Suppress("DEPRECATION")
            window.statusBarColor = Color.Transparent.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = FirenetDarkScheme,
        typography = FirenetTypography,
        content = content
    )
}
