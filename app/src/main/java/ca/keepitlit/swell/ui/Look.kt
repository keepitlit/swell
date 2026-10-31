package ca.keepitlit.swell.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ca.keepitlit.swell.theme.Appearance
import ca.keepitlit.swell.theme.ClockPos
import ca.keepitlit.swell.theme.Palette

/**
 * The resolved appearance, in Compose types: colours, corner radii and type. Built once per
 * [Appearance] and provided through [LocalLook] so nothing below reads preferences.
 */
class Look(val appearance: Appearance) {
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

    val cellRadius: Dp = 22.dp
    val drawerRadius: Dp = 38.dp
    val buttonRadius: Dp = 14.dp
    val fieldRadius: Dp = 18.dp

    val clockFont: FontFamily = FontFamily.SansSerif
    val bodyFont: FontFamily = FontFamily.SansSerif
    val monoFont: FontFamily = FontFamily.Monospace

    val iconsMono: Boolean = appearance.monoIcons
    val iconSize: Dp = appearance.iconSize.dp
    val clock: ClockPos = appearance.clock
    val wallpaper: Boolean = appearance.wallpaper
}

val LocalLook = staticCompositionLocalOf<Look> { error("No Look provided") }
