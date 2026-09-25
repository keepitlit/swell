package ca.keepitlit.swell.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.keepitlit.swell.SwellModel
import ca.keepitlit.swell.theme.Role
import ca.keepitlit.swell.theme.ThemeId
import ca.keepitlit.swell.theme.Themes
import ca.keepitlit.swell.theme.Tri
import ca.keepitlit.swell.theme.ClockPos
import ca.keepitlit.swell.theme.AppearancePrefs
import ca.keepitlit.swell.wallpaper.SwellWallpaper

/**
 * Appearance, live: every change applies to the page behind the sheet as it is made.
 *
 * Three layers, quick to thorough: a style, an accent (five swatches or any colour), and a
 * role-by-role table for anyone who wants the drawer one shade darker and nothing else. Below
 * that, the page's one behavioural knob, and reset.
 *
 * Material's slider and switch are used for their touch and accessibility behaviour, and their
 * colours are set explicitly: left to defaults they arrive in Material purple on a mono page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSheet(model: SwellModel, onDismiss: () -> Unit) {
    val look = LocalLook.current
    val a = model.appearance
    val context = LocalContext.current
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Facts the phone owns, re-read whenever Swell comes back from a system screen.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var canCover by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var wallActive by remember { mutableStateOf(SwellWallpaper.active(context)) }
    var wallOnLock by remember { mutableStateOf(SwellWallpaper.activeOnLock(context)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                canCover = Settings.canDrawOverlays(context)
                wallActive = SwellWallpaper.active(context)
                wallOnLock = SwellWallpaper.activeOnLock(context)
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    var pickingAccent by remember { mutableStateOf(false) }
    var editingRole by remember { mutableStateOf<Role?>(null) }
    var confirmReset by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = look.drawer,
        contentColor = look.paper,
        scrimColor = Color(0x99000000),
        dragHandle = { Box(Modifier.padding(top = 12.dp, bottom = 4.dp).width(40.dp).height(4.dp).background(look.rule2, CircleShape)) },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp),
        ) {
            Text("Appearance", fontFamily = look.clockFont, fontSize = 26.sp, fontWeight = FontWeight.Medium, color = look.paper)
            Spacer(Modifier.height(18.dp))

            // ── Style ─────────────────────────────────────────────────────────────────
            SectionLabel("Style")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (id in ThemeId.entries) {
                    ThemeCard(id, selected = a.theme == id, Modifier.weight(1f)) {
                        model.updateAppearance { it.copy(theme = id) }
                    }
                }
            }
            Text(a.theme.blurb, color = look.paper3, fontSize = 12.sp, fontFamily = look.bodyFont, modifier = Modifier.padding(top = 8.dp))
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
                        wallActive && wallOnLock == true -> "This backdrop is the phone's wallpaper, lock screen too."
                        wallActive -> "This backdrop is the phone's home wallpaper."
                        else -> "Make this backdrop the phone's wallpaper."
                    },
                    color = look.paper, fontSize = 14.sp, fontFamily = look.bodyFont, modifier = Modifier.weight(1f),
                )
                Pill(if (wallActive) "Change" else "Set", primary = !wallActive, onClick = {
                    try { context.startActivity(SwellWallpaper.setIntent(context)) } catch (_: Exception) { model.showToast("This phone has no live wallpaper picker") }
                }, modifier = Modifier.padding(start = 6.dp))
            }
            Text(
                if (wallActive) "It follows the style and colours here wherever the wallpaper shows: lock screen, app switcher, the moment you leave an app."
                else "A live wallpaper that is the same pixels as this backdrop and follows the style and colours here. The phone's preview asks whether the lock screen gets it too.",
                color = look.paper3, fontSize = 11.sp, fontFamily = look.bodyFont, modifier = Modifier.padding(top = 4.dp),
            )
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("System wallpaper behind everything", color = look.paper, fontSize = 14.sp, fontFamily = look.bodyFont, modifier = Modifier.weight(1f))
                LookSwitch(a.wallpaper) { on -> model.updateAppearance { it.copy(wallpaper = on) } }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (a.wallpaper) "The style's own backdrop steps aside. Every colour below still applies."
                    else "Off: the style paints its own backdrop in the background colour.",
                    color = look.paper3, fontSize = 11.sp, fontFamily = look.bodyFont, modifier = Modifier.weight(1f).padding(top = 4.dp),
                )
                if (a.wallpaper) Pill("Choose", onClick = {
                    try {
                        context.startActivity(Intent(Intent.ACTION_SET_WALLPAPER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (_: Exception) { model.showToast("No wallpaper picker on this phone") }
                }, modifier = Modifier.padding(start = 8.dp))
            }
            Spacer(Modifier.height(22.dp))

            // ── Accent ────────────────────────────────────────────────────────────────
            SectionLabel("Accent")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for ((name, colour) in Themes.SWATCHES) {
                    Swatch(colour, active = !pickingAccent && a.effectiveAccent == colour, name) {
                        pickingAccent = false
                        model.updateAppearance { it.copy(accent = colour) }
                    }
                }
                // The custom slot: the current accent if it is not one of the five, else a wheel.
                val custom = Themes.SWATCHES.none { it.second == a.effectiveAccent }
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(
                            if (custom) SolidBrush(Color(a.effectiveAccent))
                            else Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)),
                        )
                        .border(2.dp, if (pickingAccent || custom) look.paper else Color.Transparent, CircleShape)
                        .clickable { pickingAccent = !pickingAccent; editingRole = null },
                )
                Spacer(Modifier.weight(1f))
                Text(Themes.hex(a.effectiveAccent), color = look.paper3, fontSize = 11.sp, fontFamily = look.monoFont)
            }
            if (pickingAccent) {
                Spacer(Modifier.height(12.dp))
                ColorPicker(colour = a.effectiveAccent, onChange = { c -> model.updateAppearance { it.copy(accent = c) } })
            }
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (a.accent != null) Pill("Theme's own accent", onClick = { pickingAccent = false; model.updateAppearance { it.copy(accent = null) } })
            }
            Spacer(Modifier.height(22.dp))

            // ── Colours, role by role ─────────────────────────────────────────────────
            SectionLabel("Colours")
            Text(
                "Every colour the page uses. Tap one to change it; a dot marks the ones you have.",
                color = look.paper3, fontSize = 12.sp, fontFamily = look.bodyFont,
            )
            Spacer(Modifier.height(8.dp))
            for (role in Role.entries) {
                val colour = look.palette[role]
                val overridden = role in a.overrides
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(look.fieldRadius))
                        .clickable { editingRole = if (editingRole == role) null else role; pickingAccent = false }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(look.buttonRadius))
                            .background(Color(colour))
                            .border(1.dp, look.rule2, RoundedCornerShape(look.buttonRadius)),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(role.label, color = look.paper, fontSize = 14.sp, fontFamily = look.bodyFont)
                        Text(role.hint, color = look.paper3, fontSize = 11.sp, fontFamily = look.bodyFont)
                    }
                    if (overridden) Box(Modifier.size(7.dp).background(look.accent2, CircleShape))
                    Spacer(Modifier.width(10.dp))
                    Text(Themes.hex(colour), color = look.paper3, fontSize = 11.sp, fontFamily = look.monoFont)
                }
                if (editingRole == role) {
                    Column(Modifier.padding(start = 4.dp, end = 4.dp, bottom = 10.dp)) {
                        ColorPicker(colour = colour, onChange = { c -> model.updateAppearance { it.copy(overrides = it.overrides + (role to c)) } })
                        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (overridden) Pill("Reset", onClick = { model.updateAppearance { it.copy(overrides = it.overrides - role) } })
                            Pill("Done", onClick = { editingRole = null }, primary = true)
                        }
                    }
                }
            }
            if (a.overrides.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Pill("Clear all ${a.overrides.size} overrides", onClick = { editingRole = null; model.updateAppearance { it.copy(overrides = emptyMap()) } })
            }
            Spacer(Modifier.height(22.dp))

            // ── Grid ──────────────────────────────────────────────────────────────────
            SectionLabel("Grid")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Columns", color = look.paper, fontSize = 14.sp, fontFamily = look.bodyFont, modifier = Modifier.weight(1f))
                for (c in 4..6) Pill("$c", primary = a.cols == c, onClick = { model.updateAppearance { it.copy(cols = c) } }, modifier = Modifier.padding(start = 6.dp))
            }
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Rows", color = look.paper, fontSize = 14.sp, fontFamily = look.bodyFont, modifier = Modifier.weight(1f))
                for (r in 4..7) Pill("$r", primary = a.rows == r, onClick = { model.updateAppearance { it.copy(rows = r) } }, modifier = Modifier.padding(start = 6.dp))
            }
            Text(
                (if (a.showShelf) "${a.cols} in the fixed row, " else "") +
                    "${a.cols * a.rows} per screen, ${model.book.pages} ${if (model.book.pages == 1) "screen" else "screens"} for ${model.apps.size} apps.",
                color = look.paper3, fontSize = 11.sp, fontFamily = look.bodyFont, modifier = Modifier.padding(top = 6.dp),
            )
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Fixed row under every screen", color = look.paper, fontSize = 14.sp, fontFamily = look.bodyFont, modifier = Modifier.weight(1f))
                LookSwitch(a.showShelf) { on -> model.updateAppearance { it.copy(showShelf = on) } }
            }
            Text(
                if (a.showShelf) "Your ${a.cols} most-used apps stay put while the screens change."
                else "Off: the most-used apps head the first screen instead. Nothing is hidden.",
                color = look.paper3, fontSize = 11.sp, fontFamily = look.bodyFont, modifier = Modifier.padding(top = 4.dp),
            )
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Clock", color = look.paper, fontSize = 14.sp, fontFamily = look.bodyFont, modifier = Modifier.weight(1f))
                for (p in ClockPos.entries) {
                    Pill(p.label, primary = a.clock == p, onClick = { model.updateAppearance { it.copy(clock = p) } }, modifier = Modifier.padding(start = 6.dp))
                }
            }
            Text(
                when (a.clock) {
                    ClockPos.TOP -> "Above the screens. Tap for the clock app, hold for this sheet."
                    ClockPos.BOTTOM -> "Under the screens. Tap for the clock app, hold for this sheet."
                    ClockPos.OFF -> "No clock. Hold the handle at the bottom for this sheet."
                },
                color = look.paper3, fontSize = 11.sp, fontFamily = look.bodyFont, modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(22.dp))

            // ── Home ──────────────────────────────────────────────────────────────────
            SectionLabel("Coming home")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (canCover) "Swell appears the moment you leave an app." else "Let Swell appear the moment you leave an app.",
                    color = look.paper, fontSize = 14.sp, fontFamily = look.bodyFont, modifier = Modifier.weight(1f),
                )
                if (!canCover) Pill("Allow", primary = true, onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context.packageName))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }, modifier = Modifier.padding(start = 6.dp))
            }
            Text(
                if (canCover) "Allowed to display over other apps, which is how it gets ahead of the system launcher's own animation."
                else "Needs \"display over other apps\": the system launcher otherwise shows bare wallpaper for about a second before it lets Swell through.",
                color = look.paper3, fontSize = 11.sp, fontFamily = look.bodyFont, modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(22.dp))

            // ── Icons ─────────────────────────────────────────────────────────────────
            SectionLabel("Icons")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Monochrome icons", color = look.paper, fontSize = 14.sp, fontFamily = look.bodyFont, modifier = Modifier.weight(1f))
                for (t in Tri.entries) {
                    val label = when (t) { Tri.AUTO -> "theme"; Tri.ON -> "on"; Tri.OFF -> "off" }
                    Pill(label, primary = a.monoIcons == t, onClick = { model.updateAppearance { it.copy(monoIcons = t) } }, modifier = Modifier.padding(start = 6.dp))
                }
            }
            Text(
                if (a.monoIcons == Tri.AUTO) "Following the theme: ${if (a.iconsMono) "on" else "off"} for ${a.theme.label.lowercase()}." else "Set by hand.",
                color = look.paper3, fontSize = 11.sp, fontFamily = look.bodyFont, modifier = Modifier.padding(top = 4.dp),
            )
            Text("Icon size · ${a.iconSize} dp", color = look.paper, fontSize = 14.sp, fontFamily = look.bodyFont, modifier = Modifier.padding(top = 12.dp))
            Slider(
                value = a.iconSize.toFloat(),
                onValueChange = { v -> model.updateAppearance { it.copy(iconSize = v.toInt()) } },
                valueRange = AppearancePrefs.ICON_MIN.toFloat()..AppearancePrefs.ICON_MAX.toFloat(),
                steps = AppearancePrefs.ICON_MAX - AppearancePrefs.ICON_MIN - 1,
                colors = sliderColours(look),
            )
            Text(
                "The most a tile will draw. A dense grid stays smaller than this.",
                color = look.paper3, fontSize = 11.sp, fontFamily = look.bodyFont,
            )
            Text("Outline · ${a.outline} dp", color = look.paper, fontSize = 14.sp, fontFamily = look.bodyFont, modifier = Modifier.padding(top = 12.dp))
            Slider(
                value = a.outline.toFloat(),
                onValueChange = { v -> model.updateAppearance { it.copy(outline = v.toInt()) } },
                valueRange = AppearancePrefs.OUTLINE_MIN.toFloat()..AppearancePrefs.OUTLINE_MAX.toFloat(),
                steps = AppearancePrefs.OUTLINE_MAX - AppearancePrefs.OUTLINE_MIN - 1,
                colors = sliderColours(look),
            )
            Text(
                if (a.outline == 0) "The icon fills a square tile." else "How much tile shows around each icon, in the Tile colour above. The corners round with it.",
                color = look.paper3, fontSize = 11.sp, fontFamily = look.bodyFont,
            )
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Show names under icons", color = look.paper, fontSize = 14.sp, fontFamily = look.bodyFont, modifier = Modifier.weight(1f))
                LookSwitch(a.showLabels) { on -> model.updateAppearance { it.copy(showLabels = on) } }
            }
            Spacer(Modifier.height(22.dp))

            // ── The screens ───────────────────────────────────────────────────────────
            SectionLabel("Home screens")
            val version = model.version
            val hyst = remember(version) { model.book.hysteresis.toFloat() }
            val hystLabel = when { hyst < 0.75f -> "loose"; hyst > 1.4f -> "locked"; else -> "natural" }
            Text("How expensive a seat is to steal · $hystLabel", color = look.paper2, fontSize = 12.sp, fontFamily = look.monoFont)
            Slider(
                value = hyst,
                onValueChange = { model.setHysteresis(it.toDouble()) },
                valueRange = 0.4f..2.2f,
                colors = sliderColours(look),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("loose", color = look.paper3, fontSize = 11.sp, fontFamily = look.bodyFont)
                Text("natural", color = look.paper3, fontSize = 11.sp, fontFamily = look.bodyFont)
                Text("locked", color = look.paper3, fontSize = 11.sp, fontFamily = look.bodyFont)
            }
            Spacer(Modifier.height(10.dp))
            val st = model.book.stability()
            Text("stability $st% · day ${model.book.day} · ${model.book.totalOpens} launches", color = look.paper3, fontSize = 11.sp, fontFamily = look.monoFont)
            Spacer(Modifier.height(10.dp))
            Text("What just moved", color = look.paper2, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = look.bodyFont)
            val events = model.book.log.take(8)
            if (events.isEmpty()) {
                Text("Open something. The page only writes when you do.", color = look.paper3, fontSize = 12.sp, fontFamily = look.bodyFont, modifier = Modifier.padding(top = 4.dp))
            } else {
                for (e in events) {
                    val text = when (e.kind) {
                        ca.keepitlit.swell.seats.SeatBook.Event.Kind.EARNED -> "${model.label(e.key)} earned a seat"
                        ca.keepitlit.swell.seats.SeatBook.Event.Kind.TOOK_LAST -> "${model.label(e.key)} took the last seat from ${model.label(e.other ?: "")}"
                        ca.keepitlit.swell.seats.SeatBook.Event.Kind.CLIMBED -> "${model.label(e.key)} climbed past ${model.label(e.other ?: "")}"
                        ca.keepitlit.swell.seats.SeatBook.Event.Kind.NOTE -> e.note ?: ""
                    }
                    Row(Modifier.padding(top = 4.dp)) {
                        Text("d${e.day}", color = look.paper3, fontSize = 11.sp, fontFamily = look.monoFont, modifier = Modifier.width(40.dp))
                        Text(text, color = look.paper2, fontSize = 12.sp, fontFamily = look.bodyFont)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!confirmReset) {
                    Pill("Start over", danger = true, onClick = { confirmReset = true })
                } else {
                    Pill("Forget every rank?", danger = true, onClick = { confirmReset = false; model.resetPage() })
                    Pill("Keep it", onClick = { confirmReset = false })
                }
                Pill("Reset appearance", onClick = {
                    pickingAccent = false; editingRole = null
                    model.updateAppearance { ca.keepitlit.swell.theme.Appearance() }
                })
            }
        }
    }
}

@Composable
private fun sliderColours(look: Look) = SliderDefaults.colors(
    thumbColor = look.accent, activeTrackColor = look.accent, inactiveTrackColor = look.rule2,
    activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent,
)

/** Material's switch in the look's colours; left to defaults it arrives in purple. */
@Composable
private fun LookSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val look = LocalLook.current
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = look.shell, checkedTrackColor = look.accent,
            uncheckedThumbColor = look.paper2, uncheckedTrackColor = look.search, uncheckedBorderColor = look.rule2,
        ),
    )
}

@Composable
private fun SectionLabel(text: String) {
    val look = LocalLook.current
    Text(text.uppercase(), color = look.paper3, fontSize = 11.sp, letterSpacing = 1.2.sp, fontFamily = look.monoFont, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun ThemeCard(id: ThemeId, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val look = LocalLook.current
    val p = Themes.resolve(id, null, emptyMap())
    val radius = when (id) { ThemeId.MONO -> 6.dp; ThemeId.ATELIER -> 14.dp; ThemeId.CANDY -> 18.dp }
    Column(
        modifier
            .clip(RoundedCornerShape(radius))
            .background(Color(p.shell))
            .border(2.dp, if (selected) look.accent else look.rule, RoundedCornerShape(radius))
            .clickable(onClick = onClick)
            .padding(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(3) { i ->
                Box(Modifier.size(14.dp).clip(RoundedCornerShape(radius / 3)).background(Color(if (i == 2) p.accent else p.tile)))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            id.label,
            color = Color(p.paper), fontSize = 12.sp, fontWeight = FontWeight.Medium,
            fontFamily = when (id) { ThemeId.MONO -> look.monoFont; ThemeId.ATELIER -> androidx.compose.ui.text.font.FontFamily.Serif; ThemeId.CANDY -> androidx.compose.ui.text.font.FontFamily.SansSerif },
        )
    }
}

@Composable
private fun Swatch(colour: Int, active: Boolean, name: String, onClick: () -> Unit) {
    val look = LocalLook.current
    Box(
        Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(Color(colour))
            .border(2.dp, if (active) look.paper else Color.Transparent, CircleShape)
            .clickable(onClick = onClick),
    )
}

private fun SolidBrush(c: Color): Brush = androidx.compose.ui.graphics.SolidColor(c)
