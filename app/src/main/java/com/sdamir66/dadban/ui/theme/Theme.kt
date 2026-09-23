package com.sdamir66.dadban.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ═══ رنگ‌های Light Theme ═══
private val LightColors = lightColorScheme(
    primary = HeaderBlue,
    onPrimary = Color.White,
    primaryContainer = HeaderBlue.copy(alpha = 0.15f),
    onPrimaryContainer = HeaderBlue,
    
    secondary = HeaderBlue,
    onSecondary = Color.White,
    
    tertiary = HeaderBlue,
    onTertiary = Color.White,
    
    // ✅ رنگ پس‌زمینه و سطح
    background = BgLight,
    onBackground = Color(0xFF1B1B1F),     // ← مشکی واضح
    surface = Color.White,
    onSurface = Color(0xFF1B1B1F),        // ← مشکی واضح
    surfaceVariant = Color(0xFFF0F1F7),
    onSurfaceVariant = Color(0xFF5C5D72), // ← خاکستری متوسط
    
    // ✅ رنگ متن‌های ثانویه
    outline = Color(0xFF9E9E9E),
    outlineVariant = Color(0xFFCCCCCC),
    
    // ✅ رنگ خطا
    error = DebitRed,
    onError = Color.White,
    errorContainer = Color(0xFFFFEBEE),
    onErrorContainer = DebitRed
)

// ═══ رنگ‌های Dark Theme (اگه خواستی) ═══
private val DarkColors = darkColorScheme(
    primary = HeaderBlue,
    onPrimary = Color.White,
    background = Color(0xFF1E1F25),
    onBackground = Color.White,
    surface = Color(0xFF2A2B33),
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFFB0B1BC),
    error = DebitRed,
    onError = Color.White
)

@Composable
fun MaliManagerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
