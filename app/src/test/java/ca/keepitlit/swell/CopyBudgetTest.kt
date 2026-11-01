package ca.keepitlit.swell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Keeps explainers from growing back onto Swell's screens (words-and-paths standard,
 * declutter-2026-10-12). Every string literal in the app's sources that a person can read is
 * checked, except those inside a log call, an exception or an annotation:
 *
 *  - one sentence at most: a second sentence is an explainer;
 *  - at most [MAX_WORDS] words, a template counting as one;
 *  - none of "shelf", "page" or "drawer", per Alex's 2026-09-04 ruling on Swell's vocabulary.
 *
 * A string that must break a rule goes in [EXCEPTIONS] with a citation of an accepted decision
 * or a dated quote from Alex, never the agent's own judgement.
 */
class CopyBudgetTest {

    @Test
    fun `every on-screen string keeps to the budget`() {
        val found = onScreen()
        // A scan that finds nothing passes everything; these must be seen.
        for (known in listOf("Add to dock", "Edit name and icon", "Search apps", "Swell")) {
            assertTrue("scan missed \"$known\"", found.any { it.text == known })
        }
        val offenders = found.filter { it.text !in EXCEPTIONS }.mapNotNull { s -> breach(s.text)?.let { "${s.where}: \"${s.text}\" ($it)" } }
        assertEquals(offenders.joinToString("\n"), 0, offenders.size)
    }

    @Test
    fun `the rules catch an explainer and let a log line through`() {
        val src = """
            fun f() {
                Log.i(TAG, "a log line can say as much as it likes about what happened and why it did")
                // Text("Started over. The list reorders as you open apps.")
                Text("Started over. The list reorders as you open apps.")
                Text("${'$'}{entry.label} is on the shelf")
            }
        """.trimIndent()
        val texts = literals(src, "f.kt").map { it.text }
        assertEquals(listOf("Started over. The list reorders as you open apps.", "{} is on the shelf"), texts)
        assertTrue(texts.all { breach(it) != null })
    }

    private data class Found(val text: String, val where: String)

    private fun breach(text: String): String? {
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        return when {
            Regex("[.!?:]\\s+[A-Z]").containsMatchIn(text) -> "more than one sentence"
            words.size > MAX_WORDS -> "${words.size} words, the cap is $MAX_WORDS"
            Regex("\\b(shelf|shelves|page|pages|drawer|drawers)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) -> "a word Alex ruled out"
            else -> null
        }
    }

    private fun onScreen(): List<Found> {
        val kotlin = File("src/main/java").walkTopDown().filter { it.extension == "kt" }
            .flatMap { literals(it.readText(), it.path).asSequence() }
        val xml = File("src/main/res").walkTopDown().filter { it.extension == "xml" }.flatMap { f ->
            Regex("<string[^>]*>([^<]*)</string>").findAll(f.readText())
                .map { Found(it.groupValues[1].replace("\\'", "'").replace(Regex("%\\d\\$[sd]"), "{}"), f.path) }
        }
        // Prose only: keys, ids and formats have no space and start lower case.
        return (kotlin + xml).filter { it.text.contains(' ') || it.text.firstOrNull()?.isUpperCase() == true }.toList()
    }

    /**
     * The string literals in a Kotlin source, outside comments and outside any call to a log,
     * an exception or an annotation. Templates read as "{}".
     */
    private fun literals(src: String, path: String): List<Found> {
        val out = ArrayList<Found>()
        val calls = ArrayList<Boolean>() // per open paren: inside a machine call?
        var i = 0
        var line = 1
        fun machine() = calls.any { it }
        // Reads a "..." literal starting after the quote; returns its text with templates as {}.
        fun string(raw: Boolean): String {
            val sb = StringBuilder()
            while (i < src.length) {
                if (raw && src.startsWith("\"\"\"", i)) { i += 3; return sb.toString() }
                val c = src[i]
                if (!raw && c == '"') { i++; return sb.toString() }
                if (c == '\n') line++
                if (!raw && c == '\\') { sb.append(src.getOrElse(i + 1) { ' ' }); i += 2; continue }
                if (c == '$' && src.getOrNull(i + 1) == '{') {
                    var depth = 0
                    while (i < src.length) {
                        val d = src[i]
                        if (d == '{') depth++
                        if (d == '}') { depth--; if (depth == 0) { i++; break } }
                        if (d == '"') { i++; string(false); continue }
                        i++
                    }
                    sb.append("{}"); continue
                }
                if (c == '$' && src.getOrNull(i + 1)?.isLetter() == true) {
                    i++
                    while (i < src.length && (src[i].isLetterOrDigit() || src[i] == '_')) i++
                    sb.append("{}"); continue
                }
                sb.append(c); i++
            }
            return sb.toString()
        }
        while (i < src.length) {
            val c = src[i]
            when {
                c == '\n' -> { line++; i++ }
                src.startsWith("//", i) -> { while (i < src.length && src[i] != '\n') i++ }
                src.startsWith("/*", i) -> {
                    val end = src.indexOf("*/", i + 2).let { if (it < 0) src.length else it + 2 }
                    line += src.substring(i, end).count { it == '\n' }; i = end
                }
                c == '\'' -> { i++; if (src.getOrNull(i) == '\\') i++; i += 2 }
                src.startsWith("\"\"\"", i) -> { val at = line; i += 3; val s = string(true); if (!machine()) out += Found(s, "$path:$at") }
                c == '"' -> { val at = line; i++; val s = string(false); if (!machine()) out += Found(s, "$path:$at") }
                c == '(' -> {
                    val callee = Regex("(@?[A-Za-z_][\\w.]*)\\s*$").find(src.substring(maxOf(0, i - 60), i))?.groupValues?.get(1) ?: ""
                    calls += MACHINE.matches(callee)
                    i++
                }
                c == ')' -> { if (calls.isNotEmpty()) calls.removeAt(calls.size - 1); i++ }
                else -> i++
            }
        }
        return out
    }

    private companion object {
        const val MAX_WORDS = 8
        val MACHINE = Regex("Log\\.\\w+|error|require|check|requireNotNull|checkNotNull|TODO|\\w*Exception|@\\w+|Trace\\.\\w+")
        /** literal → citation of an accepted decision or a dated Alex quote. */
        val EXCEPTIONS: Map<String, String> = emptyMap()
    }
}
