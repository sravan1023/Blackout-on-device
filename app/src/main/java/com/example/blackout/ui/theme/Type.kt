package com.example.blackout.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.blackout.R

val DmSans = FontFamily(
    Font(R.font.dm_sans, weight = FontWeight.Normal),
    Font(R.font.dm_sans, weight = FontWeight.Medium),
    Font(R.font.dm_sans, weight = FontWeight.SemiBold),
    Font(R.font.dm_sans, weight = FontWeight.Bold),
)

val BlackoutTypography = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 28.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 22.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 18.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp),
    bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.8.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 11.sp, letterSpacing = 0.8.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 10.sp, letterSpacing = 0.8.sp),
)
