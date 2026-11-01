package ca.keepitlit.swell.helper

import android.content.Context
import android.util.Log

/**
 * The icons a person can give any app, bundled so nothing has to be fetched or copied in.
 *
 * `assets/icons.tsv` holds one glyph per line: id, name, and its SVG path on a 24-unit square.
 * The glyphs are from Material Design Icons by Pictogrammers (Apache 2.0), generic ones only, no
 * brand marks.
 */
object IconLibrary {

    data class Glyph(val id: String, val name: String, val path: String)

    /** The tile colours offered for a glyph, in the order they are listed; the first is the default. */
    val COLOURS: List<Pair<String, Int>> = listOf(
        // White first: it matches the phone's own icons, most of which sit on white.
        "White" to 0xFFF4F1EA.toInt(),
        "Graphite" to 0xFF2B2E34.toInt(),
        "Navy" to 0xFF24345C.toInt(),
        "Acid green" to 0xFFB5E84D.toInt(),
        "Mint" to 0xFF65D6C2.toInt(),
        "Coral" to 0xFFFF765C.toInt(),
        "Red" to 0xFFE5484D.toInt(),
        "Electric blue" to 0xFF7F8CFF.toInt(),
        "Signal yellow" to 0xFFF3C969.toInt(),
    )

    @Volatile
    private var glyphs: List<Glyph>? = null
    @Volatile
    private var byId: Map<String, Glyph> = emptyMap()

    /** Every glyph, in the file's order. Reads the asset once; call off the main thread the first time. */
    fun all(context: Context): List<Glyph> {
        glyphs?.let { return it }
        val list = try {
            context.assets.open("icons.tsv").bufferedReader().useLines { lines ->
                lines.mapNotNull { line ->
                    val parts = line.split('\t')
                    if (parts.size == 3) Glyph(parts[0], parts[1], parts[2]) else null
                }.toList()
            }
        } catch (e: Exception) {
            Log.w("Swell", "icon library unreadable: $e")
            emptyList()
        }
        byId = list.associateBy { it.id }
        glyphs = list
        return list
    }

    fun find(context: Context, id: String): Glyph? {
        all(context)
        return byId[id]
    }

    /**
     * Glyphs for [query], best first: names that share a word with it, then names or ids that
     * contain it. An empty query lists every glyph, with those matching [hint] (the app's name)
     * first.
     */
    fun search(all: List<Glyph>, query: String, hint: String): List<Glyph> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) {
            val words = words(hint)
            if (words.isEmpty()) return all
            val (near, rest) = all.partition { g -> words(g.name).any { it in words } || words.any { w -> g.id.contains(w) } }
            return near + rest
        }
        return all.filter { it.name.lowercase().contains(q) || it.id.contains(q) }
            .sortedBy { if (it.name.lowercase().startsWith(q)) 0 else 1 }
    }

    private fun words(s: String): Set<String> =
        s.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length >= 3 }.toSet()
}
