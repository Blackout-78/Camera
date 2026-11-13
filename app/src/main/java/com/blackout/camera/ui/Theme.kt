package com.blackout.camera.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Design B · Clear: black, see-through controls, blue accent. */
object C {
    val Bg = Color(0xFF000000)
    val Card = Color(0xFF1C1C1C)
    val Line = Color(0xFF3A3A3A)
    val Text = Color(0xFFF2F2F2)
    val Sub = Color(0xFFA0A0A8)
    val Accent = Color(0xFF23295D)
    val AccentInk = Color(0xFF93A0F0)
    val Glass = Color(0x73000000)
    val Rec = Color(0xFFFF5A5F)
    val Warn = Color(0xFFFFB86B)
    val Good = Color(0xFF22C55E)
}

@Composable
fun CameraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.Accent, onPrimary = Color.White, background = C.Bg, onBackground = C.Text,
            surface = C.Card, onSurface = C.Text, surfaceVariant = C.Card, onSurfaceVariant = C.Sub,
            surfaceContainer = C.Card, surfaceContainerHigh = C.Card, surfaceContainerHighest = C.Card,
            outline = C.Line, outlineVariant = C.Line,
        ),
        content = content,
    )
}
