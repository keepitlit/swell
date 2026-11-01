package ca.keepitlit.swell.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import ca.keepitlit.swell.data.AppEntry
import ca.keepitlit.swell.data.AppRepository
import ca.keepitlit.swell.helper.IconCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One app's icon, loaded off the main thread through [IconCache] and, in a monochrome look,
 * remapped to a single ink: luminance drives brightness, the ink supplies the hue, and a floor
 * keeps dark icon backgrounds from vanishing into the tile.
 */
@Composable
fun AppIcon(
    entry: AppEntry,
    repo: AppRepository,
    size: Dp,
    mono: Boolean,
    ink: Color,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    var bitmap by remember(entry.iconKey) { mutableStateOf(IconCache.peek(entry.iconKey)) }
    LaunchedEffect(entry.iconKey) {
        if (bitmap == null) {
            bitmap = withContext(Dispatchers.IO) { IconCache.load(context, entry, px, repo.activityInfo(entry), repo.shortcutInfo(entry)) }
        }
    }
    val filter = if (mono) remember(ink) { monoFilter(ink) } else null
    Box(modifier.size(size)) {
        bitmap?.let {
            Image(bitmap = it.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), colorFilter = filter)
        }
    }
}

/** A library glyph on a tile of [colour], as the icon picker shows it. */
@Composable
fun GlyphIcon(id: String, colour: Int, size: Dp, mono: Boolean, ink: Color, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    var bitmap by remember(id, colour, px) { mutableStateOf(IconCache.peekGlyph(id, colour, px)) }
    LaunchedEffect(id, colour, px) {
        if (bitmap == null) bitmap = withContext(Dispatchers.IO) { IconCache.loadGlyph(context, id, colour, px) }
    }
    val filter = if (mono) remember(ink) { monoFilter(ink) } else null
    Box(modifier.size(size)) {
        bitmap?.let {
            Image(bitmap = it.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), colorFilter = filter)
        }
    }
}

/** out = ink * (0.3 + 0.7 * luminance). Alpha untouched. */
fun monoFilter(ink: Color): ColorFilter {
    val r = ink.red; val g = ink.green; val b = ink.blue
    val k = 0.7f
    val f = 0.3f * 255f
    return ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                k * 0.213f * r, k * 0.715f * r, k * 0.072f * r, 0f, f * r,
                k * 0.213f * g, k * 0.715f * g, k * 0.072f * g, 0f, f * g,
                k * 0.213f * b, k * 0.715f * b, k * 0.072f * b, 0f, f * b,
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
    )
}
