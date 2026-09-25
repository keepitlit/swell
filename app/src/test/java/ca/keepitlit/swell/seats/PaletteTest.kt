package ca.keepitlit.swell.seats

import ca.keepitlit.swell.theme.Role
import ca.keepitlit.swell.theme.ThemeId
import ca.keepitlit.swell.theme.Themes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PaletteTest {
    @Test
    fun `mono tile is the accent mixed a third into the shell, as the prototype renders it`() {
        val p = Themes.resolve(ThemeId.MONO, null, emptyMap())
        // color-mix(in srgb, #b5e84d 34%, #080a0e)
        assertEquals("#435523", Themes.hex(p.tile).lowercase())
        assertEquals(0xFFB5E84D.toInt(), p.accent)
    }

    @Test
    fun `an override wins over the accent rewrite`() {
        val p = Themes.resolve(ThemeId.MONO, 0xFFFF0000.toInt(), mapOf(Role.TILE to 0xFF123456.toInt()))
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
