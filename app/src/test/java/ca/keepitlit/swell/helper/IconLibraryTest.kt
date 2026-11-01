package ca.keepitlit.swell.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class IconLibraryTest {

    private val library: List<IconLibrary.Glyph> =
        File("src/main/assets/icons.tsv").readLines().filter { it.isNotBlank() }.map {
            val p = it.split('\t')
            assertEquals("three fields: $it", 3, p.size)
            IconLibrary.Glyph(p[0], p[1], p[2])
        }

    @Test
    fun `every glyph has a unique id and a path`() {
        assertTrue(library.size > 100)
        assertEquals(library.size, library.map { it.id }.toSet().size)
        assertTrue(library.all { it.path.startsWith("M") })
    }

    @Test
    fun `an empty search puts glyphs matching the app's name first`() {
        val found = IconLibrary.search(library, "", "Calculator")
        assertEquals("calculator", found.first().id)
        assertEquals(library.size, found.size)
    }

    @Test
    fun `a search keeps only matches, names starting with it first`() {
        val found = IconLibrary.search(library, "cam", "Whatever")
        assertEquals("camera", found.first().id)
        assertTrue(found.all { it.name.lowercase().contains("cam") || it.id.contains("cam") })
    }
}
