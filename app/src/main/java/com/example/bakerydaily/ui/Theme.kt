package com.example.bakerydaily.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp

private val BakeryBlue = Color(0xFF2F6B8A)
private val BakeryBlueDark = Color(0xFF9CCBE3)
private val BakeryBackground = Color(0xFFF7F9FB)
private val BakerySurface = Color(0xFFFFFFFF)
private val BakerySurfaceDark = Color(0xFF172026)

private val LightColors = lightColorScheme(
    primary = BakeryBlue,
    onPrimary = Color.White,
    secondary = Color(0xFF6D8A99),
    background = BakeryBackground,
    surface = BakerySurface,
    error = Color(0xFFB3261E)
)

private val DarkColors = darkColorScheme(
    primary = BakeryBlueDark,
    onPrimary = Color(0xFF0A202B),
    secondary = Color(0xFFB5CBD6),
    background = Color(0xFF101518),
    surface = BakerySurfaceDark,
    error = Color(0xFFFFB4AB)
)

private val BakeryTypography = Typography(
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
)

@Composable
fun BakeryTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = BakeryTypography,
        shapes = Shapes(
            small = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
            medium = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
            large = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)
        ),
        content = content
    )
}
