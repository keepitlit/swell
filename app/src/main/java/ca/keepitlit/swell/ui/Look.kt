package ca.keepitlit.swell.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ca.keepitlit.swell.theme.Appearance
import ca.keepitlit.swell.theme.ClockPos
import ca.keepitlit.swell.theme.Palette
import ca.keepitlit.swell.theme.ThemeId

/**
 * The resolved appearance, in Compose types: colours, corner radii and type per theme. Built
 * once per [Appearance] and provided through [LocalLook] so nothing below reads preferences.
 */
class Look(val appearance: Appearance) {
    val theme: ThemeId = appearance.theme
    val palette: Palette = appearance.palette

    val shell = Color(palette.shell)
    val paper = Color(palette.paper)
    val paper2 = Color(palette.paper2)
    val paper3 = Color(palette.paper3)
    val accent = Color(palette.accent)
    val accent2 = Color(palette.accent2)
    val ember = Color(palette.ember)
    val ember2 = Color(palette.ember2)
    val tile = Color(palette.tile)
    val drawer = Color(palette.drawer)
    val search = Color(palette.search)
    val rule = Color(palette.rule)
    val rule2 = Color(palette.rule2)

    /**
     * Tile corners follow the outline slider: the theme's own radius at the default 7 dp outline,
     * square at 0, and past a point the corners meet and the tile is a circle.
     */
    val tileRadius: Dp = when (theme) { ThemeId.MONO -> 8.dp; ThemeId.ATELIER -> 15.dp; ThemeId.CANDY -> 20.dp } * (appearance.outline / 7f)
    val cellRadius: Dp = when (theme) { ThemeId.MONO -> 8.dp; ThemeId.ATELIER -> 16.dp; ThemeId.CANDY -> 22.dp }
    val drawerRadius: Dp = when (theme) { ThemeId.MONO -> 12.dp; ThemeId.ATELIER -> 28.dp; ThemeId.CANDY -> 38.dp }
    val buttonRadius: Dp = when (theme) { ThemeId.MONO -> 4.dp; ThemeId.ATELIER -> 999.dp; ThemeId.CANDY -> 14.dp }
    val fieldRadius: Dp = when (theme) { ThemeId.MONO -> 6.dp; ThemeId.ATELIER -> 14.dp; ThemeId.CANDY -> 18.dp }

    /** The clock. Mono is all-monospace; atelier's clock is the serif; candy is the system sans. */
    val clockFont: FontFamily = when (theme) { ThemeId.MONO -> FontFamily.Monospace; ThemeId.ATELIER -> FontFamily.Serif; ThemeId.CANDY -> FontFamily.SansSerif }
    val bodyFont: FontFamily = when (theme) { ThemeId.MONO -> FontFamily.Monospace; else -> FontFamily.SansSerif }
    val monoFont: FontFamily = FontFamily.Monospace

    val iconsMono: Boolean = appearance.iconsMono
    val showLabels: Boolean = appearance.showLabels
    val showShelf: Boolean = appearance.showShelf
    val list: Boolean = appearance.list
    val stepScroll: Boolean = appearance.stepScroll
    val iconSize: Dp = appearance.iconSize.dp
    val outline: Dp = appearance.outline.dp

    /** The icon inside a tile of [tile] dp: the tile less the outline on each side, never a speck. */
    fun iconIn(tile: Dp): Dp = (tile - outline * 2).coerceAtLeast(16.dp)
    val clock: ClockPos = appearance.clock
    val wallpaper: Boolean = appearance.wallpaper
}

val LocalLook = staticCompositionLocalOf<Look> { error("No Look provided") }
