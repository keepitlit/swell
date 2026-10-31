package ca.keepitlit.swell.helper

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * How to fit a non-adaptive icon into the system icon shape, read from its pixels alone.
 *
 * Adaptive icons already come masked to the phone's shape at full size. Everything else arrives
 * as a picture with its own outline and padding: a square, a circle, a tile drawn small inside
 * transparent margins, or a bare logo. Left alone, those are the icons that look out of place.
 *
 * - A **tile** (most of its bounding box opaque) is cropped to that box and scaled to fill the
 *   shape; whatever the shape uncovers, such as a circle's corners, is painted the colour of the
 *   tile's own edge so the seam disappears.
 * - A **glyph** (a logo with no tile behind it) is centred on a white tile, as Pixel does.
 *
 * Pure Kotlin so the reading is unit-tested; [IconCache] does the drawing.
 */
internal object IconShape {

    /** A glyph's share of the shape's width: inside the shape's corners at any common mask. */
    const val GLYPH_SCALE = 0.68f

    const val GLYPH_FILL = 0xFFFFFFFF.toInt()

    /** Opaque enough to count as part of the icon; soft drop shadows fall below it. */
    private const val SOLID_ALPHA = 128

    /** A circle covers 79 % of its box, a rounded square more; a logo far less. */
    private const val TILE_COVERAGE = 0.6f

    /** The edge colour is read on a ring just inside the tile's outline, at this share of its side. */
    private const val RING = 0.42f
    private const val RING_SAMPLES = 32

    /**
     * Where the icon sits in its bitmap and how to draw it: [cx], [cy] and [side] are the square
     * to scale to the shape (in source pixels), [fill] the colour behind it.
     */
    data class Fit(val cx: Float, val cy: Float, val side: Float, val tile: Boolean, val fill: Int?)

    /** [argb] is row-major, `width * height` long. Null for an icon with nothing opaque in it. */
    fun read(width: Int, height: Int, argb: IntArray): Fit? {
        var left = width
        var top = height
        var right = -1
        var bottom = -1
        var solid = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (alpha(argb[y * width + x]) >= SOLID_ALPHA) {
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                    solid++
                }
            }
        }
        if (right < 0) return null
        val boxW = right - left + 1
        val boxH = bottom - top + 1
        val cx = left + boxW / 2f
        val cy = top + boxH / 2f
        val side = max(boxW, boxH).toFloat()
        val tile = solid >= TILE_COVERAGE * boxW * boxH
        if (!tile) return Fit(cx, cy, side, tile = false, fill = GLYPH_FILL)
        return Fit(cx, cy, side, tile = true, fill = ringColour(width, height, argb, cx, cy, side))
    }

    /** The average opaque colour on the ring, or null when the ring is mostly see-through. */
    private fun ringColour(width: Int, height: Int, argb: IntArray, cx: Float, cy: Float, side: Float): Int? {
        var r = 0
        var g = 0
        var b = 0
        var n = 0
        val radius = RING * side
        for (i in 0 until RING_SAMPLES) {
            val a = 2 * Math.PI * i / RING_SAMPLES
            val x = (cx + radius * cos(a)).roundToInt().coerceIn(0, width - 1)
            val y = (cy + radius * sin(a)).roundToInt().coerceIn(0, height - 1)
            val p = argb[y * width + x]
            if (alpha(p) < SOLID_ALPHA) continue
            r += (p shr 16) and 0xFF
            g += (p shr 8) and 0xFF
            b += p and 0xFF
            n++
        }
        if (n < RING_SAMPLES / 2) return null
        return (0xFF shl 24) or ((r / n) shl 16) or ((g / n) shl 8) or (b / n)
    }

    private fun alpha(p: Int) = (p ushr 24) and 0xFF
}
