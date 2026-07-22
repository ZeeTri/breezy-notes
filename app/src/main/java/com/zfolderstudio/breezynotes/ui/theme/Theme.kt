package com.zfolderstudio.breezynotes.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ZTapperColorScheme = darkColorScheme(
    primary = ZPrimary,
    onPrimary = ZOnPrimary,
    background = ZBackground,
    onBackground = Color.White,
    surface = ZBackground,
    onSurface = Color.White,
    surfaceVariant = ZSurface, 
    onSurfaceVariant = Color.White,
    primaryContainer = ZBackground, 
    onPrimaryContainer = Color.White,
    secondaryContainer = ZSurface,
    onSecondaryContainer = Color.White,
    secondary = ZSecondary
)

@Composable
fun MyApplicationTheme(

    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ZTapperColorScheme,
        typography = Typography,
        content = content
    )
}
