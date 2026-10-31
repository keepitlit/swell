package ca.keepitlit.swell.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import ca.keepitlit.swell.theme.Themes

/** The prototype's candy `--device-wall`, tinted by the live accent: two soft blobs on a violet slope. */
@Composable
fun Wall(modifier: Modifier = Modifier) {
    val look = LocalLook.current
    Canvas(modifier.fillMaxSize()) { drawWall(look) }
}

/**
 * The wall itself, into any [DrawScope]: the home screen's canvas above, and the wallpaper
 * service's surface, so the phone's wallpaper is the same pixels as the launcher's backdrop.
 */
fun DrawScope.drawWall(look: Look) {
    val w = size.width
    val h = size.height
    drawRect(look.shell)
    val top = Color(Themes.mix(look.palette.accent, look.palette.shell, 0.28f))
    val bottom = Color(Themes.mix(look.palette.shell, 0xFF000000.toInt(), 0.55f))
    drawRect(Brush.linearGradient(listOf(top, bottom), start = Offset(0f, 0f), end = Offset(w * 0.4f, h)))
    drawCircle(
        Brush.radialGradient(listOf(look.ember.copy(alpha = 0.34f), Color.Transparent), center = Offset(w * 0.18f, h * 0.18f), radius = w * 0.55f),
        radius = w * 0.55f, center = Offset(w * 0.18f, h * 0.18f),
    )
    drawCircle(
        Brush.radialGradient(listOf(look.accent2.copy(alpha = 0.26f), Color.Transparent), center = Offset(w * 0.9f, h * 0.78f), radius = w * 0.65f),
        radius = w * 0.65f, center = Offset(w * 0.9f, h * 0.78f),
    )
}
