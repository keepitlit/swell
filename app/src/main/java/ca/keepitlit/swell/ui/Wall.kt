package ca.keepitlit.swell.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import ca.keepitlit.swell.theme.ThemeId
import ca.keepitlit.swell.theme.Themes

/**
 * The prototype's `--device-wall`, per theme, tinted by the live accent:
 * mono is hairline diagonals on graphite, atelier a low glow with ruled lines, candy two soft
 * blobs on a violet slope.
 */
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
    val stepPx = 16.dp.toPx()
    val rulePx = 64.dp.toPx()
    val hair = 1.dp.toPx()
    val shell = look.shell
    val accent = look.accent
    drawRect(shell)
    val w = size.width
    val h = size.height
    when (look.theme) {
        ThemeId.MONO -> {
            drawRect(Brush.verticalGradient(0f to Color.Transparent, 1f to accent.copy(alpha = 0.06f)))
            val line = accent.copy(alpha = 0.08f)
            var x = 0f
            while (x < w + h) {
                drawLine(line, Offset(x, 0f), Offset(x - h, h), strokeWidth = hair)
                x += stepPx
            }
        }
        ThemeId.ATELIER -> {
            val glow = Color(Themes.mix(look.palette.accent, look.palette.shell, 0.35f))
            val deep = Color(Themes.mix(look.palette.shell, 0xFF000000.toInt(), 0.6f))
            drawRect(
                Brush.radialGradient(
                    0f to glow, 0.52f to shell, 1f to deep,
                    center = Offset(w / 2f, h * 1.1f),
                    radius = w * 1.25f,
                ),
            )
            val line = look.paper.copy(alpha = 0.045f)
            var y = rulePx
            while (y < h) { drawLine(line, Offset(0f, y), Offset(w, y), strokeWidth = hair); y += rulePx }
            drawRect(Brush.verticalGradient(0f to Color.Transparent, 0.62f to look.accent2.copy(alpha = 0.10f), 1f to look.ember.copy(alpha = 0.10f)))
        }
        ThemeId.CANDY -> {
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
    }
}
