package ca.keepitlit.swell.ui

import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import kotlin.math.roundToInt
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlinx.coroutines.launch
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.keepitlit.swell.SwellModel
import ca.keepitlit.swell.Widgets
import ca.keepitlit.swell.data.AppEntry
import ca.keepitlit.swell.helper.AppLaunch
import ca.keepitlit.swell.theme.ClockPos
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val EASE = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/** The whole launcher. One screen, three overlays (drawer, app menu, settings). */
@Composable
fun SwellApp(model: SwellModel, widgets: Widgets, homeSignal: Int, openDrawerSignal: Int = 0) {
    val look = remember(model.appearance) { Look(model.appearance) }
    val context = LocalContext.current

    var drawerOpen by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<Pair<AppEntry, Offset>?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }

    // Page 0 is the widget panel; page 1 is the ranked list.
    val pageCount = 2
    val pager = rememberPagerState(initialPage = 1) { pageCount }
    val stepState = remember { StepState() }
    LaunchedEffect(homeSignal) {
        // HOME pressed while already home: overlays close, first screen, top of the list.
        drawerOpen = false
        menuFor = null
        settingsOpen = false
        if (homeSignal > 0) {
            stepState.first = 0
            pager.animateScrollToPage(1)
        }
    }
    LaunchedEffect(openDrawerSignal) { if (openDrawerSignal > 0) drawerOpen = true }
    BackHandler(enabled = drawerOpen || menuFor != null || settingsOpen) {
        when {
            menuFor != null -> menuFor = null
            settingsOpen -> settingsOpen = false
            else -> drawerOpen = false
        }
    }

    val open: (AppEntry, Rect?) -> Unit = { entry, bounds ->
        menuFor = null
        if (model.launch(entry, bounds)) drawerOpen = false
    }
    // A tap on the clock opens the phone's clock app: whatever answers the alarms intent, opened
    // as a seated app (so it votes) when it is one, else as a plain intent.
    val openClock: () -> Unit = {
        val intent = Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val target = try { intent.resolveActivity(context.packageManager) } catch (_: Exception) { null }
        val entry = target?.let { c -> model.apps.firstOrNull { it.packageName == c.packageName && it.user == android.os.Process.myUserHandle() } }
        when {
            entry != null -> open(entry, null)
            target != null -> try { context.startActivity(intent) } catch (_: Exception) { model.showToast("No clock app") }
            else -> model.showToast("No clock app")
        }
    }

    CompositionLocalProvider(LocalLook provides look) {
        // With the system wallpaper on, the window itself is transparent and nothing is painted behind the page.
        Box(Modifier.fillMaxSize().then(if (look.wallpaper) Modifier else Modifier.background(look.shell))) {
            if (!look.wallpaper) Wall()
            Column(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .pointerInput(Unit) {
                        // Swipe up anywhere that is not an app line opens all apps.
                        var travel = 0f
                        detectVerticalDragGestures(
                            onDragStart = { travel = 0f },
                            onDragEnd = { if (travel < -60.dp.toPx()) drawerOpen = true },
                        ) { _, dy -> travel += dy }
                    },
            ) {
                if (look.clock == ClockPos.TOP) HeroTime(onTap = openClock, onLongPress = { settingsOpen = true }) else Spacer(Modifier.height(12.dp))
                HorizontalPager(
                    state = pager,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    beyondViewportPageCount = 1,
                    key = { it },
                ) { page ->
                    if (page == 0) {
                        WidgetPanel(widgets, Modifier.fillMaxSize().padding(horizontal = 12.dp))
                    } else {
                        SeatSteps(
                            model = model,
                            state = stepState,
                            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                            onOpen = open,
                            onHold = { entry, at -> menuFor = entry to at },
                        )
                    }
                }
                Dots(count = pageCount, current = pager.currentPage)
                if (look.clock == ClockPos.BOTTOM) HeroTime(onTap = openClock, onLongPress = { settingsOpen = true }, bottom = true)
                HandleRow(onOpen = { drawerOpen = true }, onHold = { settingsOpen = true })
            }

            Drawer(
                open = drawerOpen,
                model = model,
                onClose = { drawerOpen = false },
                onOpen = open,
                onHold = { entry, at -> menuFor = entry to at },
                onSettings = { settingsOpen = true },
            )

            menuFor?.let { (entry, at) ->
                AppMenu(
                    model = model,
                    entry = entry,
                    anchor = at,
                    dockRoom = stepState.dockRoom,
                    onOpen = { open(entry, null) },
                    onAppInfo = { menuFor = null; AppLaunch.appInfo(context, entry) },
                    onUninstall = {
                        menuFor = null
                        if (entry.isShortcut) {
                            model.removeShortcut(entry)
                        } else {
                            val intent = Intent(Intent.ACTION_DELETE, Uri.fromParts("package", entry.packageName, null))
                                .putExtra(Intent.EXTRA_USER, entry.user)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            try { context.startActivity(intent) } catch (_: Exception) { model.showToast("Can't uninstall ${entry.label}") }
                        }
                    },
                    onDismiss = { menuFor = null },
                )
            }

            SettingsPage(open = settingsOpen, model = model, onClose = { settingsOpen = false })

            Toast(model.toast, Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HeroTime(onTap: () -> Unit, onLongPress: () -> Unit, bottom: Boolean = false) {
    val look = LocalLook.current
    val context = LocalContext.current
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            val ms = System.currentTimeMillis()
            delay(60_000 - ms % 60_000 + 20)
        }
    }
    val is24 = DateFormat.is24HourFormat(context)
    val clockFmt = remember(is24) { SimpleDateFormat(if (is24) "H:mm" else "h:mm", Locale.getDefault()) }
    val dateFmt = remember { SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()) }
    Column(
        Modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTap,
                onLongClick = onLongPress,
            )
            .padding(top = if (bottom) 8.dp else 22.dp, bottom = if (bottom) 2.dp else 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            clockFmt.format(now),
            fontFamily = look.clockFont,
            fontSize = 72.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = (-2).sp,
            lineHeight = 66.sp,
            color = look.paper,
        )
        Text(
            dateFmt.format(now),
            fontFamily = look.bodyFont,
            fontSize = 14.sp,
            letterSpacing = 0.3.sp,
            color = look.paper2,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/**
 * Which seat heads the stepped list, hoisted so HOME can send it back to the top, and how many
 * apps the dock has room for, which the app menu needs.
 */
class StepState {
    var first by mutableIntStateOf(0)
    var dockRoom by mutableIntStateOf(0)
}

/** Every seated app not in the dock, in rank order. */
@Composable
private fun listKeys(model: SwellModel): List<String> {
    val version = model.version
    val book = model.book
    return remember(version) {
        book.seats.filterNotNull().filter { model.appsByKey.containsKey(it) && it !in book.dock }
    }
}

/** The dock's apps that are installed, bottom first. */
@Composable
private fun dockKeys(model: SwellModel): List<String> {
    val version = model.version
    val book = model.book
    return remember(version) { book.dock.filter { model.appsByKey.containsKey(it) } }
}

/** The stepped list composes lines in blocks of this many, so most steps compose nothing. */
private const val WINDOW_STEP = 4

/** A line's height: the icon plus its 6 dp above and below. */
private fun linePitch(look: Look): Dp = look.iconSize + 12.dp

/**
 * Home: one column of stationary lines, every seated app in rank order. A drag or fling never
 * moves the lines; it moves the apps through them, one line per [linePitch] of travel, so nothing
 * on screen slides. One line peeks into each blurred, fading edge band ([edgeBlurFade]). The
 * [Dock] stands in the empty half left of the icons and does not move.
 */
@Composable
private fun SeatSteps(
    model: SwellModel,
    state: StepState,
    modifier: Modifier,
    onOpen: (AppEntry, Rect?) -> Unit,
    onHold: (AppEntry, Offset) -> Unit,
) {
    val look = LocalLook.current
    val keys = listKeys(model)
    val decay = rememberSplineBasedDecay<Float>()
    val scope = rememberCoroutineScope()
    val fling = remember { Animatable(0f) }
    BoxWithConstraints(modifier) {
        val pitch = linePitch(look)
        // Lines wholly clear of both bands; the list stops when its last app reaches the last of them.
        val clear = ((maxHeight - EDGE_BAND * 2) / pitch).toInt().coerceAtLeast(1)
        SideEffect { state.dockRoom = clear }
        val maxFirst = (keys.size - clear).coerceAtLeast(0)
        val pitchPx = with(LocalDensity.current) { pitch.toPx() }
        val height = maxHeight
        // Empty width left of the list's icons; the dock stands in it.
        val gutter = maxWidth / 2 - 8.dp - look.iconSize
        Box(
            Modifier
                .fillMaxSize()
                .clipToBounds()
                .edgeBlurFade(EDGE_BAND)
                .pointerInput(maxFirst, pitchPx) {
                    var travel = 0f
                    // Spend drag travel a line at a time; false once an end is reached.
                    fun spend(dy: Float): Boolean {
                        travel += dy
                        while (travel <= -pitchPx) {
                            if (state.first >= maxFirst) { travel = 0f; return false }
                            state.first++
                            travel += pitchPx
                        }
                        while (travel >= pitchPx) {
                            if (state.first <= 0) { travel = 0f; return false }
                            state.first--
                            travel -= pitchPx
                        }
                        return true
                    }
                    val tracker = VelocityTracker()
                    detectVerticalDragGestures(
                        onDragStart = {
                            scope.launch { fling.stop() }
                            state.first = state.first.coerceIn(0, maxFirst)
                            travel = 0f
                            tracker.resetTracking()
                        },
                        onDragEnd = {
                            val v = tracker.calculateVelocity().y
                            scope.launch {
                                fling.snapTo(0f)
                                var last = 0f
                                fling.animateDecay(v, decay) {
                                    if (!spend(value - last)) scope.launch { fling.stop() }
                                    last = value
                                }
                            }
                        },
                    ) { change, dy ->
                        tracker.addPosition(change.uptimeMillis, change.position)
                        spend(dy)
                    }
                },
        ) {
            // A step is a layout change only: each line reads the head in its offset lambda, so a
            // step re-places the lines without composing anything. Composition runs only when the
            // window of composed lines moves, every WINDOW_STEP lines; the window keeps a spare
            // WINDOW_STEP either side of the screen. Slot -1 peeks into the top band.
            val bandPx = with(LocalDensity.current) { EDGE_BAND.toPx() }
            val onScreen = ((height - EDGE_BAND) / pitch).toInt() + 2
            val windowStart by remember(maxFirst) {
                derivedStateOf { state.first.coerceIn(0, maxFirst) / WINDOW_STEP * WINDOW_STEP }
            }
            val from = (windowStart - 1 - WINDOW_STEP).coerceAtLeast(0)
            val to = (windowStart + onScreen + WINDOW_STEP * 2).coerceAtMost(keys.size)
            for (i in from until to) {
                val k = keys[i]
                key(k) {
                    SeatLine(
                        model, k,
                        Modifier.offset { IntOffset(0, (bandPx + (i - state.first.coerceIn(0, maxFirst)) * pitchPx).roundToInt()) },
                        onOpen, onHold,
                        inset = (gutter - 8.dp).coerceAtLeast(0.dp),
                    )
                }
            }
        }
        Dock(model, slots = clear, gutter = gutter, onOpen = onOpen, onHold = onHold)
    }
}

/** A docked icon's edge: smaller than the list's, never larger. */
private val DOCK_ICON = 44.dp

/**
 * The apps kept in a fixed place: one column centred in the [gutter] left of the list's icons,
 * each icon on one of the [slots] clear lines, filling from the bottom line up. One app or a
 * full column, it sits in a single capsule.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Dock(
    model: SwellModel,
    slots: Int,
    gutter: Dp,
    onOpen: (AppEntry, Rect?) -> Unit,
    onHold: (AppEntry, Offset) -> Unit,
) {
    val look = LocalLook.current
    val keys = dockKeys(model).take(slots)
    if (keys.isEmpty() || gutter <= 0.dp) return
    val pitch = linePitch(look)
    val icon = minOf(DOCK_ICON, look.iconSize)
    val pad = 8.dp
    // Line slot j's icon centre is EDGE_BAND + j * pitch + pitch / 2; the dock takes the last keys.size slots.
    val top = EDGE_BAND + pitch * (slots - keys.size) + pitch / 2 - icon / 2 - pad
    Column(
        Modifier
            .offset(x = (gutter - icon) / 2 - pad, y = top)
            .clip(CircleShape)
            .background(look.paper.copy(alpha = 0.10f))
            .border(1.dp, look.rule2, CircleShape)
            .padding(pad),
        verticalArrangement = Arrangement.spacedBy(pitch - icon),
    ) {
        // First docked sits lowest, nearest the thumb.
        for (k in keys.asReversed()) {
            val entry = model.appsByKey[k] ?: continue
            key(k) {
                var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
                Box(
                    Modifier
                        .onPlaced { coords = it }
                        .size(icon)
                        .clip(RoundedCornerShape(percent = 30))
                        .combinedClickable(
                            onClick = {
                                val b = coords?.takeIf { it.isAttached }?.boundsInWindow()
                                onOpen(entry, b?.let { Rect(it.left.toInt(), it.top.toInt(), it.right.toInt(), it.bottom.toInt()) })
                            },
                            onLongClick = {
                                val c = coords?.takeIf { it.isAttached }?.boundsInWindow()?.center ?: Offset.Zero
                                onHold(entry, c)
                            },
                        ),
                ) {
                    AppIcon(entry, model.repo, icon, look.iconsMono, look.paper)
                }
            }
        }
    }
}

/**
 * One app's line: the icon bare (no tile), right-aligned to the screen's centre line, and its name
 * starting just past it, so the divide between icons and names runs down the middle. The tap
 * target starts [inset] in, 8 dp left of the icon, so it never reaches the dock.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SeatLine(
    model: SwellModel,
    k: String,
    modifier: Modifier,
    onOpen: (AppEntry, Rect?) -> Unit,
    onHold: (AppEntry, Offset) -> Unit,
    inset: Dp,
) {
    val look = LocalLook.current
    val book = model.book
    val entry = model.appsByKey[k] ?: return
    val movedAt = book.lastMoved[k] ?: 0L
    var justMoved by remember { mutableStateOf(false) }
    LaunchedEffect(movedAt) {
        val left = 1600 - (System.currentTimeMillis() - movedAt)
        if (movedAt > 0 && left > 0) { justMoved = true; delay(left); justMoved = false } else justMoved = false
    }
    // Read lazily, on tap: the icon's bounds seed the launch animation.
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val iconSize = look.iconSize
    Row(
        modifier
            .fillMaxWidth()
            .height(linePitch(look))
            .padding(start = inset)
            .clip(RoundedCornerShape(look.cellRadius))
            .combinedClickable(
                onClick = {
                    val b = coords?.takeIf { it.isAttached }?.boundsInWindow()
                    onOpen(entry, b?.let { Rect(it.left.toInt(), it.top.toInt(), it.right.toInt(), it.bottom.toInt()) })
                },
                onLongClick = {
                    val c = coords?.takeIf { it.isAttached }?.boundsInWindow()?.center ?: Offset.Zero
                    onHold(entry, c)
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The icon ends 8 dp short of the centre line and the name starts 8 dp past it.
        Box(Modifier.padding(horizontal = 8.dp)) {
            Box(
                Modifier
                    .onPlaced { coords = it }
                    .size(iconSize)
                    .then(if (justMoved) Modifier.border(2.dp, look.ember, RoundedCornerShape(percent = 30)) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                AppIcon(entry, model.repo, iconSize, look.iconsMono, look.paper)
                if (book.app(k)?.pinned == true) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .size(10.dp)
                            .background(look.shell, CircleShape)
                            .padding(2.dp)
                            .background(look.accent2, CircleShape),
                    )
                }
            }
        }
        Text(
            entry.label,
            fontFamily = look.bodyFont,
            fontSize = 20.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Medium,
            color = look.paper,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 8.dp),
        )
    }
}

@Composable
private fun Dots(count: Int, current: Int) {
    val look = LocalLook.current
    Row(Modifier.fillMaxWidth().height(14.dp).wrapContentSize(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (i in 0 until count) {
            val on = i == current
            Box(
                Modifier
                    .size(width = if (i == 0) 10.dp else 6.dp, height = 6.dp)
                    .background(if (on) look.accent else look.rule2, if (i == 0) RoundedCornerShape(2.dp) else CircleShape),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HandleRow(onOpen: () -> Unit, onHold: () -> Unit) {
    val look = LocalLook.current
    Column(
        Modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onOpen,
                onLongClick = onHold,
            )
            .padding(top = 8.dp, bottom = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // One pill with the words inside it: the handle and its name are the same thing.
        Box(
            Modifier
                .clip(CircleShape)
                .background(look.paper.copy(alpha = 0.10f))
                .border(1.dp, look.rule2, CircleShape)
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Text(
                "all apps",
                fontFamily = look.monoFont,
                fontSize = 12.sp,
                letterSpacing = 1.sp,
                color = look.paper2,
            )
        }
    }
}

@Composable
private fun Toast(text: String?, modifier: Modifier) {
    val look = LocalLook.current
    var last by remember { mutableStateOf("") }
    if (text != null) last = text
    AnimatedVisibility(visible = text != null, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Box(
            Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(look.tile)
                .border(1.dp, look.rule2, RoundedCornerShape(999.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(last, fontFamily = look.bodyFont, fontSize = 12.sp, color = look.paper, maxLines = 1)
        }
    }
}

/** Shared: a small pill button in the look's shape. */
@Composable
fun Pill(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false, danger: Boolean = false) {
    val look = LocalLook.current
    val shape = RoundedCornerShape(look.buttonRadius)
    Box(
        modifier
            .clip(shape)
            .background(if (primary) look.accent else look.shell.copy(alpha = 0.6f))
            .border(1.dp, if (primary) Color.Transparent else look.rule2, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        val ink = when {
            primary -> if (ca.keepitlit.swell.theme.Themes.luminance(look.palette.accent) > 0.4f) Color(0xFF0B0D10) else Color.White
            danger -> look.ember2
            else -> look.paper
        }
        Text(label, fontFamily = look.bodyFont, fontSize = 12.sp, color = ink, maxLines = 1)
    }
}
