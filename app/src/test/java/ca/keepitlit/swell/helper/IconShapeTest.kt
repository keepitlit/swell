package ca.keepitlit.swell.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IconShapeTest {
    private val red = 0xFFE53935.toInt()
    private val clear = 0

    private fun icon(size: Int, paint: (x: Int, y: Int) -> Int) =
        IntArray(size * size) { paint(it % size, it / size) }

    @Test
    fun `a full square fills the shape with its own colour behind it`() {
        val fit = IconShape.read(100, 100, icon(100) { _, _ -> red })!!
        assertTrue(fit.tile)
        assertEquals(100f, fit.side)
        assertEquals(red, fit.fill)
    }

    @Test
    fun `a circle is a tile, so its corners take the edge colour`() {
        val fit = IconShape.read(100, 100, icon(100) { x, y ->
            val dx = x - 49.5; val dy = y - 49.5
            if (dx * dx + dy * dy <= 50.0 * 50.0) red else clear
        })!!
        assertTrue(fit.tile)
        assertEquals(red, fit.fill)
    }

    @Test
    fun `a tile drawn small inside margins is cropped to its box`() {
        val fit = IconShape.read(100, 100, icon(100) { x, y -> if (x in 20..79 && y in 30..89) red else clear })!!
        assertTrue(fit.tile)
        assertEquals(60f, fit.side)
        assertEquals(50f, fit.cx)
        assertEquals(60f, fit.cy)
    }

    @Test
    fun `a bare logo goes on a white tile`() {
        // A thin plus sign: opaque along two bars, transparent everywhere else.
        val fit = IconShape.read(100, 100, icon(100) { x, y -> if (x in 45..54 || y in 45..54) red else clear })!!
        assertFalse(fit.tile)
        assertEquals(IconShape.GLYPH_FILL, fit.fill)
    }

    @Test
    fun `a soft shadow does not count toward the box`() {
        val shadow = 0x40000000
        val fit = IconShape.read(100, 100, icon(100) { x, y -> if (x in 10..89 && y in 10..89) red else shadow })!!
        assertEquals(80f, fit.side)
    }

    @Test
    fun `an icon with nothing opaque is unreadable`() {
        assertNull(IconShape.read(10, 10, IntArray(100)))
    }
}
