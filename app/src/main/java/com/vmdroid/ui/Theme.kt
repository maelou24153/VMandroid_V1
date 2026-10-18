package com.vmdroid.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun VmDroidTheme(content: @Composable () -> Unit) {
    val scheme = if (isSystemInDarkTheme()) {
        darkColorScheme(
            primary = Color(0xFF4DD0E1),
            secondary = Color(0xFF80CBC4),
            background = Color(0xFF101418),
            surface = Color(0xFF181D22)
        )
    } else {
        lightColorScheme(primary = Color(0xFF00838F), secondary = Color(0xFF00695C))
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
