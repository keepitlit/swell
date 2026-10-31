package ca.keepitlit.swell.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import ca.keepitlit.swell.SwellModel
import ca.keepitlit.swell.seats.SeatBook
import ca.keepitlit.swell.theme.Appearance
import ca.keepitlit.swell.theme.AppearancePrefs
import ca.keepitlit.swell.theme.ClockPos
import ca.keepitlit.swell.theme.Role
import ca.keepitlit.swell.theme.ThemeId
import ca.keepitlit.swell.theme.Themes
import ca.keepitlit.swell.theme.Tri
import ca.keepitlit.swell.wallpaper.SwellWallpaper

/** Which line has its choices open. One at a time, so the column never grows two lists at once. */
private enum class Open { STYLE, ACCENT, PICKER, COLOURS, BACKDROP, CLOCK, MONO, START_OVER, RESET_LOOK }

/**
 * Settings, live: every change applies to home as it is made. Laid out like home: one column,
 * each line split at the screen's centre: the setting's name ends at the centre line, its value
 * starts just past it. Choices open as more lines under the one tapped, never as a row of buttons.
 *
 * Only what home's list uses is here: the look (style, accent, colours, backdrop), the clock, the
 * icons, coming home, the least used apps, and the ranking.
 *
 * Material's slider is used for its touch and accessibility behaviour, with its colours set
 * explicitly: left to defaults it arrives in Material purple.
 */
@Composable
fun SettingsPage(open: Boolean, model: SwellModel, onClose: () -> Unit) {
    val look = LocalLook.current
    var leastUsedOpen by remember { mutableStateOf(false) }
    LaunchedEffect(open) { if (!open) leastUsedOpen = false }
    AnimatedVisibility(
        open,
        enter = fadeIn(tween(200)) + slideInVertically(tween(260)) { it / 12 },
        exit = fadeOut(tween(180)) + slideOutVertically(tween(180)) { it / 12 },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(look.shell)
                // Swallow taps so nothing reaches home underneath.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
        ) {
            Wall()
            SettingsColumn(model, onClose, onLeastUsed = { leastUsedOpen = true })
            LeastUsedPage(open = leastUsedOpen, model = model, onClose = { leastUsedOpen = false })
        }
    }
}

@Composable
private fun SettingsColumn(model: SwellModel, onClose: () -> Unit, onLeastUsed: () -> Unit) {
    val look = LocalLook.current
    val a = model.appearance
    val context = LocalContext.current
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
    var opened by remember { mutableStateOf<Open?>(null) }
    var editingRole by remember { mutableStateOf<Role?>(null) }
    fun toggle(o: Open) { opened = if (opened == o) null else o; editingRole = null }
    val scroll = rememberScrollState()
    LaunchedEffect(Unit) { scroll.scrollTo(0) }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Text(
            "Settings",
            fontFamily = look.clockFont, fontSize = 40.sp, fontWeight = FontWeight.Medium, letterSpacing = (-1).sp,
            color = look.paper, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 4.dp),
        )
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .edgeBlurFade(EDGE_BAND)
                .verticalScroll(scroll)
                .padding(horizontal = 16.dp)
                .padding(top = EDGE_BAND, bottom = EDGE_BAND),
        ) {
            // ── Look ──────────────────────────────────────────────────────────────────
            Heading("Look")
            Line("Style", a.theme.label, onClick = { toggle(Open.STYLE) })
            if (opened == Open.STYLE) for (id in ThemeId.entries) {
                Option(
                    id.label, selected = a.theme == id,
                    font = when (id) { ThemeId.MONO -> FontFamily.Monospace; ThemeId.ATELIER -> FontFamily.Serif; ThemeId.CANDY -> FontFamily.SansSerif },
                    dot = Color(Themes.defaultAccent(id)),
                ) { model.updateAppearance { it.copy(theme = id) } }
            }

            val swatchName = Themes.SWATCHES.firstOrNull { it.second == a.effectiveAccent }?.first?.lowercase()
            Line(
                "Accent", if (a.accent == null) "theme's own" else swatchName ?: Themes.hex(a.effectiveAccent),
                dot = Color(a.effectiveAccent), onClick = { toggle(Open.ACCENT) },
            )
            if (opened == Open.ACCENT || opened == Open.PICKER) {
                Option("theme's own", selected = a.accent == null, dot = Color(Themes.defaultAccent(a.theme))) {
                    model.updateAppearance { it.copy(accent = null) }
                }
                for ((name, colour) in Themes.SWATCHES) {
                    Option(name.lowercase(), selected = a.accent == colour, dot = Color(colour)) {
                        model.updateAppearance { it.copy(accent = colour) }
                    }
                }
                val custom = a.accent != null && swatchName == null
                Option(
                    "any colour", selected = custom || opened == Open.PICKER,
                    brush = if (custom) SolidColor(Color(a.effectiveAccent)) else Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)),
                ) { opened = if (opened == Open.PICKER) Open.ACCENT else Open.PICKER }
                if (opened == Open.PICKER) Picker(a.effectiveAccent) { c -> model.updateAppearance { it.copy(accent = c) } }
            }

            Line(
                "Colours", if (a.overrides.isEmpty()) "theme's own" else "${a.overrides.size} changed",
                onClick = { toggle(Open.COLOURS) },
            )
            if (opened == Open.COLOURS) {
                for (role in Role.entries) {
                    val colour = look.palette[role]
                    val changed = role in a.overrides
                    Line(
                        role.label, Themes.hex(colour), sub = role.hint, dot = Color(colour),
                        valueColour = if (changed) look.paper else look.paper3, mono = true,
                        onClick = { editingRole = if (editingRole == role) null else role },
                    )
                    if (editingRole == role) {
                        Picker(colour) { c -> model.updateAppearance { it.copy(overrides = it.overrides + (role to c)) } }
                        if (changed) Action("Use the theme's ${role.label.lowercase()}") {
                            model.updateAppearance { it.copy(overrides = it.overrides - role) }
                        }
                    }
                }
                if (a.overrides.isNotEmpty()) Action("Use the theme's colours for all ${a.overrides.size}") {
                    editingRole = null
                    model.updateAppearance { it.copy(overrides = emptyMap()) }
                }
            }

            Line("Backdrop", if (a.wallpaper) "phone's wallpaper" else "style's own", onClick = { toggle(Open.BACKDROP) })
            if (opened == Open.BACKDROP) {
                Option("style's own", selected = !a.wallpaper) { model.updateAppearance { it.copy(wallpaper = false) } }
                Option("phone's wallpaper", selected = a.wallpaper) { model.updateAppearance { it.copy(wallpaper = true) } }
                if (a.wallpaper) Action("Choose the phone's wallpaper") {
                    try {
                        context.startActivity(Intent(Intent.ACTION_SET_WALLPAPER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (_: Exception) { model.showToast("No wallpaper picker on this phone") }
                }
            }
            // The live wallpaper paints this backdrop wherever the phone shows its wallpaper.
            if (!a.wallpaper) Line(
                "Phone wallpaper",
                when {
                    wallActive && wallOnLock == true -> "this, lock screen too"
                    wallActive -> "this"
                    else -> "Use this"
                },
                valueColour = if (wallActive) look.paper2 else look.accent,
                onClick = {
                    try { context.startActivity(SwellWallpaper.setIntent(context)) } catch (_: Exception) { model.showToast("This phone has no live wallpaper picker") }
                },
            )

            // ── Home ──────────────────────────────────────────────────────────────────
            Heading("Home")
            Line("Clock", a.clock.label, onClick = { toggle(Open.CLOCK) })
            if (opened == Open.CLOCK) for (p in ClockPos.entries) {
                Option(p.label, selected = a.clock == p) { model.updateAppearance { it.copy(clock = p) } }
            }
            // With no clock to hold, the handle is the only way back here.
            if (a.clock == ClockPos.OFF) Note("Hold the handle under the list for settings.")

            Line("Icon size", "${a.iconSize} dp", onClick = null)
            Slider(
                value = a.iconSize.toFloat(),
                onValueChange = { v -> model.updateAppearance { it.copy(iconSize = v.toInt()) } },
                valueRange = AppearancePrefs.ICON_MIN.toFloat()..AppearancePrefs.ICON_MAX.toFloat(),
                colors = sliderColours(look),
                modifier = Modifier.padding(horizontal = 8.dp),
            )

            Line(
                "Monochrome icons",
                when (a.monoIcons) { Tri.AUTO -> "theme's (${if (a.iconsMono) "on" else "off"})"; Tri.ON -> "on"; Tri.OFF -> "off" },
                onClick = { toggle(Open.MONO) },
            )
            if (opened == Open.MONO) for (t in Tri.entries) {
                val label = when (t) { Tri.AUTO -> "theme's"; Tri.ON -> "on"; Tri.OFF -> "off" }
                Option(label, selected = a.monoIcons == t) { model.updateAppearance { it.copy(monoIcons = t) } }
            }

            Line(
                "Appear instantly", if (canCover) "on" else "Allow",
                valueColour = if (canCover) look.paper2 else look.accent,
                onClick = if (canCover) null else ({
                    context.startActivity(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context.packageName))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }),
            )
            // Without "display over other apps" the system launcher holds Swell back about a second.
            if (!canCover) Note("Off: leaving an app shows bare wallpaper for about a second.")

            // ── Apps ──────────────────────────────────────────────────────────────────
            Heading("Apps")
            val unopened = remember(model.apps, model.version) { leastUsed(model).count { it.use.opens == 0 } }
            Line("Least used", if (unopened > 0) "$unopened never opened" else "Review", onClick = onLeastUsed)

            // ── Order ─────────────────────────────────────────────────────────────────
            Heading("Order")
            val version = model.version
            val hyst = remember(version) { model.book.hysteresis.toFloat() }
            Line("Reordering", when { hyst < 0.75f -> "loose"; hyst > 1.4f -> "locked"; else -> "natural" }, onClick = null)
            Slider(
                value = hyst,
                onValueChange = { model.setHysteresis(it.toDouble()) },
                valueRange = 0.4f..2.2f,
                colors = sliderColours(look),
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("moves often", color = look.paper3, fontSize = 12.sp, fontFamily = look.bodyFont)
                Text("holds still", color = look.paper3, fontSize = 12.sp, fontFamily = look.bodyFont)
            }
            Spacer(Modifier.height(10.dp))
            val events = model.book.log.take(6)
            for (e in events) {
                when (e.kind) {
                    SeatBook.Event.Kind.NOTE -> Note(e.note ?: "")
                    else -> Line(
                        model.label(e.key),
                        when (e.kind) {
                            SeatBook.Event.Kind.EARNED -> "joined the list"
                            SeatBook.Event.Kind.TOOK_LAST -> "replaced ${model.label(e.other ?: "")}"
                            else -> "moved above ${model.label(e.other ?: "")}"
                        },
                        valueColour = look.paper3, size = 14, onClick = null,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Action("Start over", colour = look.ember2) { toggle(Open.START_OVER) }
            if (opened == Open.START_OVER) {
                Option("forget the order", selected = false, colour = look.ember2) { opened = null; model.resetPage() }
                Option("keep it", selected = false) { opened = null }
            }
            Action("Reset the look", colour = look.ember2) { toggle(Open.RESET_LOOK) }
            if (opened == Open.RESET_LOOK) {
                Option("reset style, colours, clock, icons", selected = false, colour = look.ember2) {
                    opened = null
                    model.updateAppearance { Appearance() }
                }
                Option("keep it", selected = false) { opened = null }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "Done",
                fontFamily = look.bodyFont, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = look.accent,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CircleShape)
                    .clickable(onClick = onClose)
                    .padding(vertical = 14.dp),
            )
        }
    }
}

/** A section title on the centre line, between two hairlines. */
@Composable
private fun Heading(text: String) {
    val look = LocalLook.current
    Row(Modifier.fillMaxWidth().padding(top = 26.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(1.dp).background(look.rule))
        Text(
            text.uppercase(), color = look.paper3, fontSize = 11.sp, letterSpacing = 2.4.sp, fontFamily = look.monoFont,
            modifier = Modifier.padding(horizontal = 14.dp),
        )
        Box(Modifier.weight(1f).height(1.dp).background(look.rule))
    }
}

/**
 * One setting: [label] right-aligned to the centre line, [value] starting just past it, with an
 * optional colour [dot] before the value. A null [onClick] makes a plain read-out.
 */
@Composable
private fun Line(
    label: String,
    value: String,
    onClick: (() -> Unit)?,
    sub: String? = null,
    dot: Color? = null,
    valueColour: Color? = null,
    mono: Boolean = false,
    size: Int = 17,
) {
    val look = LocalLook.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = if (size < 17) 36.dp else 52.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(look.cellRadius))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 10.dp), horizontalAlignment = Alignment.End) {
            Text(label, color = look.paper, fontSize = size.sp, fontFamily = look.bodyFont, textAlign = TextAlign.End, maxLines = 2)
            if (sub != null) Text(sub, color = look.paper3, fontSize = 12.sp, fontFamily = look.bodyFont, textAlign = TextAlign.End)
        }
        Row(Modifier.weight(1f).padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (dot != null) {
                Box(Modifier.size(14.dp).clip(CircleShape).background(dot).border(1.dp, look.rule2, CircleShape))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                value, color = valueColour ?: look.paper2, fontSize = size.sp,
                fontFamily = if (mono) look.monoFont else look.bodyFont, maxLines = 2,
            )
        }
    }
}

/**
 * One choice under an open line: the choice on the right of the centre line, a mark on the left
 * when it is the current one.
 */
@Composable
private fun Option(
    text: String,
    selected: Boolean,
    font: FontFamily? = null,
    dot: Color? = null,
    brush: Brush? = null,
    colour: Color? = null,
    onClick: () -> Unit,
) {
    val look = LocalLook.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(look.cellRadius))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).padding(end = 10.dp), contentAlignment = Alignment.CenterEnd) {
            if (selected) Box(Modifier.width(18.dp).height(2.dp).background(look.accent))
        }
        Row(Modifier.weight(1f).padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (dot != null || brush != null) {
                Box(
                    Modifier.size(18.dp).clip(CircleShape)
                        .then(if (brush != null) Modifier.background(brush) else Modifier.background(dot!!))
                        .border(1.dp, look.rule2, CircleShape),
                )
                Spacer(Modifier.width(10.dp))
            }
            Text(
                text,
                color = colour ?: if (selected) look.paper else look.paper2,
                fontSize = 16.sp, fontFamily = font ?: look.bodyFont,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            )
        }
    }
}

/** An action that belongs to the open line above it, on the value side, in the accent. */
@Composable
private fun Action(text: String, colour: Color? = null, onClick: () -> Unit) {
    val look = LocalLook.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(look.cellRadius))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.weight(1f))
        Text(text, color = colour ?: look.accent, fontSize = 15.sp, fontFamily = look.bodyFont, modifier = Modifier.weight(1f).padding(start = 10.dp))
    }
}

/** A short line of fact, centred under the line it is about. */
@Composable
private fun Note(text: String) {
    val look = LocalLook.current
    Text(
        text, color = look.paper3, fontSize = 13.sp, fontFamily = look.bodyFont, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
    )
}

/** The full colour picker, under the line it edits. */
@Composable
private fun Picker(colour: Int, onChange: (Int) -> Unit) {
    Box(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) { ColorPicker(colour = colour, onChange = onChange) }
}

@Composable
private fun sliderColours(look: Look) = SliderDefaults.colors(
    thumbColor = look.accent, activeTrackColor = look.accent, inactiveTrackColor = look.rule2,
    activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent,
)
