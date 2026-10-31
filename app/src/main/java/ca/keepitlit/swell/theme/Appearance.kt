package ca.keepitlit.swell.theme

import android.content.Context

/** Where the clock sits, if anywhere. */
enum class ClockPos(val label: String) { TOP("top"), BOTTOM("bottom"), OFF("off") }

/**
 * Everything the user has said about how Swell should look. Immutable; [AppearancePrefs] hands
 * out a new one on every change so Compose sees a new object.
 */
data class Appearance(
    /** null = the base palette's own accent. */
    val accent: Int? = null,
    val overrides: Map<Role, Int> = emptyMap(),
    val monoIcons: Boolean = false,
    /** Icon edge in dp on home's lines; a line is this plus 12 dp. */
    val iconSize: Int = 68,
    val clock: ClockPos = ClockPos.TOP,
    /** The system wallpaper instead of Swell's drawn backdrop; every colour role still applies. */
    val wallpaper: Boolean = false,
) {
    val palette: Palette get() = Themes.resolve(accent, overrides)

    val effectiveAccent: Int get() = accent ?: Themes.defaultAccent()
}

class AppearancePrefs(context: Context) {

    private val sp = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Redraw-on-change for anything outside the activity (the wallpaper service). */
    fun listen(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener) =
        sp.registerOnSharedPreferenceChangeListener(listener)

    fun unlisten(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener) =
        sp.unregisterOnSharedPreferenceChangeListener(listener)

    fun load(): Appearance {
        val overrides = HashMap<Role, Int>()
        val raw = sp.getString(KEY_OVERRIDES, "") ?: ""
        for (part in raw.split(';')) {
            val eq = part.indexOf('=')
            if (eq <= 0) continue
            val role = try { Role.valueOf(part.substring(0, eq)) } catch (_: Exception) { continue }
            val colour = Themes.parseHex(part.substring(eq + 1)) ?: continue
            overrides[role] = colour
        }
        return Appearance(
            accent = if (sp.contains(KEY_ACCENT)) sp.getInt(KEY_ACCENT, 0) else null,
            overrides = overrides,
            // Stored as a string since the three-way days (AUTO/ON/OFF); AUTO meant off for this look.
            monoIcons = sp.getString(KEY_MONO_ICONS, null) == "ON",
            iconSize = sp.getInt(KEY_ICON_SIZE, 68).coerceIn(ICON_MIN, ICON_MAX),
            clock = try { ClockPos.valueOf(sp.getString(KEY_CLOCK, ClockPos.TOP.name) ?: "TOP") } catch (_: Exception) { ClockPos.TOP },
            wallpaper = sp.getBoolean(KEY_WALLPAPER, false),
        )
    }

    fun save(a: Appearance) {
        sp.edit().apply {
            remove(KEY_THEME)
            if (a.accent == null) remove(KEY_ACCENT) else putInt(KEY_ACCENT, a.accent)
            putString(KEY_OVERRIDES, a.overrides.entries.joinToString(";") { "${it.key.name}=${Themes.hex(it.value)}" })
            putString(KEY_MONO_ICONS, if (a.monoIcons) "ON" else "OFF")
            putInt(KEY_ICON_SIZE, a.iconSize)
            putString(KEY_CLOCK, a.clock.name)
            putBoolean(KEY_WALLPAPER, a.wallpaper)
        }.apply()
    }

    companion object {
        const val FILE = "appearance"
        const val ICON_MIN = 40
        const val ICON_MAX = 96
        private const val KEY_ICON_SIZE = "iconSize"
        /** Written by builds with a style choice; removed on the next save. */
        private const val KEY_THEME = "theme"
        private const val KEY_ACCENT = "accent"
        private const val KEY_OVERRIDES = "overrides"
        private const val KEY_MONO_ICONS = "monoIcons"
        private const val KEY_CLOCK = "clock"
        private const val KEY_WALLPAPER = "wallpaper"
    }
}
