package com.example.mobilefinanceappapplication32.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val Light = lightColorScheme(
    primary = AkibaGreenDark,
    onPrimary = Color.White,
    primaryContainer = AkibaMint,
    onPrimaryContainer = AkibaInk,
    secondary = AkibaGreen,
    background = AkibaCanvas,
    surface = Color.White,
    onSurface = AkibaInk,
    onSurfaceVariant = AkibaMuted,
    error = AkibaRed
)

private val Dark = darkColorScheme(
    primary = Color(0xFF8DDBAE),
    onPrimary = Color(0xFF103927),
    primaryContainer = Color(0xFF24583E),
    background = Color(0xFF102018),
    surface = Color(0xFF1A2D22),
    onSurface = Color(0xFFEAF4ED),
    onSurfaceVariant = Color(0xFFB7C9BC)
)

@Composable
fun MobileFinanceAppApplication32Theme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        shapes = Shapes(
            small = RoundedCornerShape(11.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(22.dp)
        ),
        typography = Typography,
        content = content
    )
}
