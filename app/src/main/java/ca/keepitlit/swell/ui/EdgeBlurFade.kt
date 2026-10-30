package ca.keepitlit.swell.ui

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Height of the blurred, fading band at a scrolling column's top and bottom edges. */
internal val EDGE_BAND = 40.dp

/**
 * Blurs and fades the content toward its top and bottom edges. The content is recorded once into
 * a layer; the middle is drawn straight from it, and each edge band composites a sharp copy and a
 * band-sized blurred copy through gradient masks. Across a band, measured from the edge as
 * t = 0..1, the total alpha is t, of which the blurred share is 1 - t: sharp alpha t², blurred
 * alpha t(1 - t). Only the two bands are blurred and only they go offscreen; nothing is composed
 * twice.
 */
internal fun Modifier.edgeBlurFade(band: Dp): Modifier = composed {
    val content = rememberGraphicsLayer()
    val topBlur = rememberGraphicsLayer()
    val bottomBlur = rememberGraphicsLayer()
    val radius = with(LocalDensity.current) { 10.dp.toPx() }
    LaunchedEffect(radius) {
        topBlur.renderEffect = BlurEffect(radius, radius, TileMode.Decal)
        bottomBlur.renderEffect = BlurEffect(radius, radius, TileMode.Decal)
    }
    drawWithContent {
        val b = band.toPx().coerceAtMost(size.height / 2)
        if (b <= 0f) { drawContent(); return@drawWithContent }
        val h = size.height
        val strip = IntSize(size.width.roundToInt(), kotlin.math.ceil(b).toInt())
        content.record { this@drawWithContent.drawContent() }
        topBlur.record(strip) { drawLayer(content) }
        bottomBlur.record(strip) { translate(top = -(h - b)) { drawLayer(content) } }

        clipRect(top = b, bottom = h - b) { drawLayer(content) }
        // Stops at t = 0, 1/4, 1/2, 3/4, 1 from the edge, laid from y = edge to y = edge ± b.
        val ts = listOf(0f, 0.25f, 0.5f, 0.75f, 1f)
        fun mask(edge: Float, inward: Float, alpha: (Float) -> Float): Brush {
            val stops = ts.map { t -> t to Color.Black.copy(alpha = alpha(t)) }
            return Brush.verticalGradient(*stops.toTypedArray(), startY = edge, endY = edge + inward)
        }
        val sharp = { t: Float -> t * t }
        val blurred = { t: Float -> t * (1f - t) }
        drawBand(content, 0f, b, mask(0f, b, sharp), mask(0f, b, blurred), topBlur)
        drawBand(content, h - b, h, mask(h, -b, sharp), mask(h, -b, blurred), bottomBlur)
    }
}

/** One edge band, [top]..[bottom]: [sharp] through [sharpMask] plus [blur] (a strip drawn at [top]) through [blurMask]. */
private fun DrawScope.drawBand(
    sharp: GraphicsLayer, top: Float, bottom: Float,
    sharpMask: Brush, blurMask: Brush, blur: GraphicsLayer,
) {
    val bounds = androidx.compose.ui.geometry.Rect(0f, top, size.width, bottom)
    val canvas = drawContext.canvas
    canvas.saveLayer(bounds, Paint())
    drawLayer(sharp)
    drawRect(sharpMask, topLeft = bounds.topLeft, size = bounds.size, blendMode = BlendMode.DstIn)
    canvas.restore()
    canvas.saveLayer(bounds, Paint())
    translate(top = top) { drawLayer(blur) }
    drawRect(blurMask, topLeft = bounds.topLeft, size = bounds.size, blendMode = BlendMode.DstIn)
    canvas.restore()
}
