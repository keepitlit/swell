package ca.keepitlit.swell.theme

import kotlin.math.roundToInt

/**
 * The three styles from the prototype, and the accent arithmetic that sits on top of them.
 *
 * Colours are ARGB ints so this file has no Compose on its classpath and the derivation can be
 * unit-tested. The prototype's rule set, kept exactly:
 *
 * - a theme is a fixed base palette (`--device-*` in the CSS);
 * - an **accent** is always applied (the theme's own by default, the user's if chosen) and it
 *   rewrites `accent`, `accent2 = mix(accent 52%, white)`, `ember = accent`,
 *   `ember2 = mix(accent 72%, white)`, `tile = mix(accent 34%, shell)` and tints the wall;
 * - on top of that, any single **role** can be overridden outright. That is the "thorough" half of
 *   the colour tools: a swatch for the quick case, a role table for the rest.
 */
enum class ThemeId(val label: String, val blurb: String) {
    MONO("Mono", "Monospace, hard corners, acid on graphite."),
    ATELIER("Atelier", "Serif clock, patina and ember on dark linen."),
    CANDY("Candy", "Round everything, violet and pink."),
    ;

    companion object {
        val DEFAULT = MONO
        fun of(name: String?): ThemeId = entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: DEFAULT
    }
}

/** Every colour the launcher draws, by name. The order is the order the role table lists them. */
enum class Role(val label: String, val hint: String) {
    SHELL("Background", "Behind everything"),
    PAPER("Text", "Clock, names, primary ink"),
    PAPER2("Text · soft", "Date, labels, meta"),
    PAPER3("Text · faint", "Hints and counts"),
    ACCENT("Accent", "Pins, focus, active states"),
    ACCENT2("Accent · light", "Pin dots and highlights"),
    EMBER("Alert", "Just-moved ring"),
    EMBER2("Alert · light", "Alert text"),
    TILE("Tile", "Icon backgrounds"),
    DRAWER("Drawer", "The all-apps sheet"),
    SEARCH("Search field", "Input background"),
    RULE("Hairline", "Dividers"),
    RULE2("Hairline · strong", "Handles and borders"),
}

class Palette(private val values: IntArray) {
    operator fun get(role: Role): Int = values[role.ordinal]

    val shell get() = this[Role.SHELL]
    val paper get() = this[Role.PAPER]
    val paper2 get() = this[Role.PAPER2]
    val paper3 get() = this[Role.PAPER3]
    val accent get() = this[Role.ACCENT]
    val accent2 get() = this[Role.ACCENT2]
    val ember get() = this[Role.EMBER]
    val ember2 get() = this[Role.EMBER2]
    val tile get() = this[Role.TILE]
    val drawer get() = this[Role.DRAWER]
    val search get() = this[Role.SEARCH]
    val rule get() = this[Role.RULE]
    val rule2 get() = this[Role.RULE2]

    fun with(role: Role, colour: Int): Palette = Palette(values.copyOf().also { it[role.ordinal] = colour })

    companion object {
        fun of(vararg pairs: Pair<Role, Int>): Palette {
            val arr = IntArray(Role.entries.size)
            for ((r, c) in pairs) arr[r.ordinal] = c
            return Palette(arr)
        }
    }
}

object Themes {

    /** The five swatches the prototype offers. The first is mono's own accent. */
    val SWATCHES: List<Pair<String, Int>> = listOf(
        "Acid green" to 0xFFB5E84D.toInt(),
        "Mint" to 0xFF65D6C2.toInt(),
        "Coral" to 0xFFFF765C.toInt(),
        "Electric blue" to 0xFF7F8CFF.toInt(),
        "Signal yellow" to 0xFFF3C969.toInt(),
    )

    fun defaultAccent(id: ThemeId): Int = base(id).accent

    /** The `--device-*` block for each theme, verbatim. */
    fun base(id: ThemeId): Palette = when (id) {
        ThemeId.MONO -> Palette.of(
            Role.SHELL to 0xFF080A0E.toInt(),
            Role.PAPER to 0xFFF0F3EA.toInt(),
            Role.PAPER2 to 0xFFADB6B0.toInt(),
            Role.PAPER3 to 0xFF69747A.toInt(),
            Role.ACCENT to 0xFFB5E84D.toInt(),
            Role.ACCENT2 to 0xFFD3FF75.toInt(),
            Role.EMBER to 0xFFFF765C.toInt(),
            Role.EMBER2 to 0xFFFF9A84.toInt(),
            Role.TILE to 0xFF222832.toInt(),
            Role.DRAWER to 0xFF11151C.toInt(),
            Role.SEARCH to 0xFF080A0E.toInt(),
            Role.RULE to argb(0.15f, 0xF0, 0xF3, 0xEA),
            Role.RULE2 to argb(0.32f, 0xF0, 0xF3, 0xEA),
        )
        ThemeId.ATELIER -> Palette.of(
            Role.SHELL to 0xFF14221F.toInt(),
            Role.PAPER to 0xFFF2EEE2.toInt(),
            Role.PAPER2 to 0xFFBDCFC5.toInt(),
            Role.PAPER3 to 0xFF81948B.toInt(),
            Role.ACCENT to 0xFF176B60.toInt(),
            Role.ACCENT2 to 0xFF6FBCAE.toInt(),
            Role.EMBER to 0xFFCB6244.toInt(),
            Role.EMBER2 to 0xFFB95B40.toInt(),
            Role.TILE to 0xFF264B45.toInt(),
            Role.DRAWER to 0xFF172A26.toInt(),
            Role.SEARCH to 0xFF0F1D1B.toInt(),
            Role.RULE to argb(0.16f, 0xF2, 0xEE, 0xE2),
            Role.RULE2 to argb(0.30f, 0xF2, 0xEE, 0xE2),
        )
        ThemeId.CANDY -> Palette.of(
            Role.SHELL to 0xFF3D315C.toInt(),
            Role.PAPER to 0xFFFBF8FF.toInt(),
            Role.PAPER2 to 0xFFD9D1EC.toInt(),
            Role.PAPER3 to 0xFFAAA0C3.toInt(),
            Role.ACCENT to 0xFF6D5BD0.toInt(),
            Role.ACCENT2 to 0xFFAA9FFF.toInt(),
            Role.EMBER to 0xFFF0788E.toInt(),
            Role.EMBER2 to 0xFFDC637A.toInt(),
            Role.TILE to 0xFF6557B9.toInt(),
            Role.DRAWER to 0xFF302647.toInt(),
            Role.SEARCH to 0xFF251E39.toInt(),
            Role.RULE to argb(0.18f, 0xFB, 0xF8, 0xFF),
            Role.RULE2 to argb(0.34f, 0xFB, 0xF8, 0xFF),
        )
    }

    /**
     * The palette actually drawn: base, then the accent rewrite, then the user's role overrides.
     *
     * @param accent null means "the theme's own", which still goes through the rewrite; the
     *   prototype applies `data-accent` unconditionally, so mono's tile is olive, not slate.
     */
    fun resolve(id: ThemeId, accent: Int?, overrides: Map<Role, Int>): Palette {
        var p = base(id)
        val a = accent ?: p.accent
        p = p.with(Role.ACCENT, a)
            .with(Role.ACCENT2, mix(a, WHITE, 0.52f))
            .with(Role.EMBER, a)
            .with(Role.EMBER2, mix(a, WHITE, 0.72f))
            .with(Role.TILE, mix(a, p.shell, 0.34f))
        for ((role, colour) in overrides) p = p.with(role, colour)
        return p
    }

    private const val WHITE = 0xFFFFFFFF.toInt()

    /** `color-mix(in srgb, a <weight>, b)`: weight of [a], the rest [b]. Alpha from [a]. */
    fun mix(a: Int, b: Int, weight: Float): Int {
        val w = weight.coerceIn(0f, 1f)
        fun ch(shift: Int) = (((a shr shift) and 0xFF) * w + ((b shr shift) and 0xFF) * (1 - w)).roundToInt().coerceIn(0, 255)
        return (a and 0xFF000000.toInt()) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    fun withAlpha(colour: Int, alpha: Float): Int =
        ((alpha.coerceIn(0f, 1f) * 255).roundToInt() shl 24) or (colour and 0x00FFFFFF)

    fun argb(alpha: Float, r: Int, g: Int, b: Int): Int =
        ((alpha * 255).roundToInt() shl 24) or (r shl 16) or (g shl 8) or b

    fun hex(colour: Int): String = "#%06X".format(colour and 0xFFFFFF)

    fun parseHex(s: String?): Int? {
        val t = s?.trim()?.removePrefix("#") ?: return null
        if (t.length != 6 || !t.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
        return (0xFF shl 24) or t.toInt(16)
    }

    /** Relative luminance, 0..1, for choosing ink over a swatch. */
    fun luminance(colour: Int): Float {
        fun lin(c: Int): Float { val v = c / 255f; return if (v <= 0.03928f) v / 12.92f else Math.pow(((v + 0.055f) / 1.055f).toDouble(), 2.4).toFloat() }
        return 0.2126f * lin((colour shr 16) and 0xFF) + 0.7152f * lin((colour shr 8) and 0xFF) + 0.0722f * lin(colour and 0xFF)
    }
}
