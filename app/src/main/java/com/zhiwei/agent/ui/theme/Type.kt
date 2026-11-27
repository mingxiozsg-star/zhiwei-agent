package com.zhiwei.agent.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// 统一走系统默认字体，中文不额外加字距；整套字号/行高对齐，避免参差。
private val Base = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Normal,
    letterSpacing = 0.sp
)

val Typography = Typography(
    displayLarge = Base.copy(fontSize = 32.sp, lineHeight = 40.sp),
    displayMedium = Base.copy(fontSize = 28.sp, lineHeight = 36.sp),
    displaySmall = Base.copy(fontSize = 24.sp, lineHeight = 32.sp),

    headlineLarge = Base.copy(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    headlineMedium = Base.copy(fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = Base.copy(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),

    titleLarge = Base.copy(fontSize = 19.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium),
    titleMedium = Base.copy(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
    titleSmall = Base.copy(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),

    bodyLarge = Base.copy(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = Base.copy(fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = Base.copy(fontSize = 12.sp, lineHeight = 18.sp),

    labelLarge = Base.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = Base.copy(fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium),
    labelSmall = Base.copy(fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium)
)