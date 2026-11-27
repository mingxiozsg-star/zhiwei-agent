package com.zhiwei.agent.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// 浅色：樱粉主色 × 冰蓝辅助，背景带极淡粉调，中性色统一灰蓝，不再混入 M3 默认紫。
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFFD63A78),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFD9E5),
    onPrimaryContainer = Color(0xFF3E001E),

    secondary = Color(0xFF1E6FB0),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD1E9FF),
    onSecondaryContainer = Color(0xFF00213C),

    tertiary = Color(0xFF7A4FB0),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFEDDDFF),
    onTertiaryContainer = Color(0xFF2A0054),

    background = Color(0xFFFFF8FA),
    onBackground = Color(0xFF201A1D),
    surface = Color(0xFFFFF8FA),
    onSurface = Color(0xFF201A1D),
    surfaceVariant = Color(0xFFF2E2EA),
    onSurfaceVariant = Color(0xFF51454B),

    outline = Color(0xFF83737B),
    outlineVariant = Color(0xFFD5C2CB),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF)
)

// 深色：主色转亮粉，背景深灰，中性色统一，和浅色一一对应。
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFFFB1D0),
    onPrimary = Color(0xFF5F0F35),
    primaryContainer = Color(0xFF7C2A4C),
    onPrimaryContainer = Color(0xFFFFD9E5),

    secondary = Color(0xFF9BD4FF),
    onSecondary = Color(0xFF003352),
    secondaryContainer = Color(0xFF0B5C8A),
    onSecondaryContainer = Color(0xFFD1E9FF),

    tertiary = Color(0xFFD8B6FF),
    onTertiary = Color(0xFF3F1D6E),
    tertiaryContainer = Color(0xFF563B87),
    onTertiaryContainer = Color(0xFFEDDDFF),

    background = Color(0xFF161217),
    onBackground = Color(0xFFECE0E6),
    surface = Color(0xFF161217),
    onSurface = Color(0xFFECE0E6),
    surfaceVariant = Color(0xFF51454B),
    onSurfaceVariant = Color(0xFFD5C2CB),

    outline = Color(0xFF9E8F97),
    outlineVariant = Color(0xFF51454B),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

@Composable
fun ZhiweiAgentTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}