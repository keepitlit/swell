package ca.keepitlit.swell.seats

import ca.keepitlit.swell.theme.Role
import ca.keepitlit.swell.theme.Themes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PaletteTest {
    @Test
    fun `the tile is the accent mixed a third into the shell, as the prototype renders it`() {
        val p = Themes.resolve(null, emptyMap())
        // color-mix(in srgb, #f3c969 34%, #202f50)
        assertEquals("#686359", Themes.hex(p.tile).lowercase())
        assertEquals(0xFFF3C969.toInt(), p.accent)
    }

    @Test
    fun `the default look has no purple in it`() {
        val p = Themes.resolve(null, emptyMap())
        for (role in Role.entries) {
            val c = p[role]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            // Purple: red and blue both above green. The old drawer #302647 and search #251E39 fail.
            val purple = b > g && r > g
            assertEquals("${role.name} is ${Themes.hex(c)}", false, purple)
        }
    }

    @Test
    fun `the base already holds what the accent rewrite draws`() {
        val base = Themes.BASE
        val drawn = Themes.resolve(null, emptyMap())
        for (role in Role.entries) assertEquals(role.name, Themes.hex(base[role]), Themes.hex(drawn[role]))
    }

    @Test
    fun `an override wins over the accent rewrite`() {
        val p = Themes.resolve(0xFFFF0000.toInt(), mapOf(Role.TILE to 0xFF123456.toInt()))
        assertEquals(0xFF123456.toInt(), p.tile)
        assertEquals(0xFFFF0000.toInt(), p.accent)
    }

    @Test
    fun `hex parsing accepts with and without the hash and rejects junk`() {
        assertEquals(0xFFB5E84D.toInt(), Themes.parseHex("#b5e84d"))
        assertEquals(0xFFB5E84D.toInt(), Themes.parseHex("B5E84D"))
        assertNull(Themes.parseHex("#b5e8"))
        assertNull(Themes.parseHex("zzzzzz"))
    }
}
