package ca.keepitlit.swell.ui

import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import kotlin.math.roundToInt
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clipToBounds
import kotlinx.coroutines.launch
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
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

/** Side margin of each home page, inside the safe drawing area. */
private val PAGE_PAD = 12.dp

/** The whole launcher. One screen, three overlays (drawer, app menu, settings). */
@Composable
fun SwellApp(model: SwellModel, widgets: Widgets, homeSignal: Int, openDrawerSignal: Int = 0) {
    val look = remember(model.appearance) { Look(model.appearance) }
    val context = LocalContext.current

    var drawerOpen by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<Pair<AppEntry, Offset>?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }
    var editFor by remember { mutableStateOf<String?>(null) }

    // Page 0 is the widget panel; page 1 is the ranked list.
    val pageCount = 2
    val pager = rememberPagerState(initialPage = 1) { pageCount }
    val stepState = remember { StepState() }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(homeSignal) {
        // HOME pressed while already home: overlays close, first screen, top of the list.
        drawerOpen = false
        menuFor = null
        settingsOpen = false
        editFor = null
        if (homeSignal > 0) {
            stepState.first = 0
            pager.animateScrollToPage(1)
        }
    }
    LaunchedEffect(openDrawerSignal) { if (openDrawerSignal > 0) drawerOpen = true }
    BackHandler(enabled = drawerOpen || menuFor != null || settingsOpen || editFor != null) {
        when {
            menuFor != null -> menuFor = null
            editFor != null -> editFor = null
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
            target != null -> try { context.startActivity(intent) } catch (_: Exception) { model.showToast("Couldn't open the clock") }
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
                        var armed = false
                        detectVerticalDragGestures(
                            onDragStart = { travel = 0f; armed = false },
                            onDragEnd = { if (travel < -60.dp.toPx()) drawerOpen = true },
                        ) { _, dy ->
                            travel += dy
                            // A bump as the swipe crosses the line where letting go opens all apps.
                            val past = travel < -60.dp.toPx()
                            if (past && !armed) haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                            armed = past
                        }
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
                        WidgetPanel(widgets, Modifier.fillMaxSize().padding(horizontal = PAGE_PAD))
                    } else {
                        SeatSteps(
                            model = model,
                            state = stepState,
                            // Clipped to the page, so the dock's bleed never shows on the widgets page.
                            modifier = Modifier.fillMaxSize().clipToBounds().padding(horizontal = PAGE_PAD),
                            onOpen = open,
                            onHold = { entry, at -> menuFor = entry to at },
                            // Dragging a held dock icon takes it out of the menu and moves it.
                            onDrag = { menuFor = null },
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
                    onEdit = { menuFor = null; editFor = entry.key },
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

            EditAppPage(key = editFor, model = model, onClose = { editFor = null })

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
    onDrag: () -> Unit,
) {
    val look = LocalLook.current
    val keys = listKeys(model)
    // Where this column sits in the window, for the dock, which is placed against the screen.
    var placed by remember { mutableStateOf<LayoutCoordinates?>(null) }
    BoxWithConstraints(modifier.onPlaced { placed = it }) {
        val pitch = linePitch(look)
        // Lines wholly clear of both bands; the list stops when its last app reaches the last of them.
        val clear = ((maxHeight - EDGE_BAND * 2) / pitch).toInt().coerceAtLeast(1)
        val dockRoom = ((maxHeight - DOCK_PAD * 2 + DOCK_GAP) / (dockIcon(look) + DOCK_GAP)).toInt()
        SideEffect { state.dockRoom = dockRoom }
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
                .lineSteps(pitchPx, onStart = { state.first = state.first.coerceIn(0, maxFirst) }) { d ->
                    val next = state.first + d
                    if (next < 0 || next > maxFirst) false else { state.first = next; true }
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
        if (gutter > 0.dp) Dock(model, room = dockRoom, column = { placed }, onOpen = onOpen, onHold = onHold, onDrag = onDrag)
    }
}

/** A docked icon's edge: smaller than the list's, never larger. */
private fun dockIcon(look: Look): Dp = minOf(44.dp, look.iconSize)

private val DOCK_PAD = 8.dp
private val DOCK_GAP = 12.dp
/** How far the dock runs past the screen's left edge, taking its left border with it. */
private val DOCK_BLEED = 24.dp

/**
 * The apps kept in a fixed place: one column against the screen's left edge, centred on the
 * screen's height and kept inside the list's [column]. Its capsule runs off the edge, so only the
 * rounded right end shows. Holds up to [room] apps.
 *
 * Tap opens; hold opens the app menu; hold and drag moves the app up or down the dock. One
 * gesture reader on the whole column does all three, because it does not move while an icon
 * does: a reader on the icon itself would see its own reordering as finger travel.
 */
@Composable
private fun Dock(
    model: SwellModel,
    room: Int,
    column: () -> LayoutCoordinates?,
    onOpen: (AppEntry, Rect?) -> Unit,
    onHold: (AppEntry, Offset) -> Unit,
    onDrag: () -> Unit,
) {
    val look = LocalLook.current
    val keys = dockKeys(model).take(room)
    if (keys.isEmpty()) return
    val icon = dockIcon(look)
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    // From the column's left to past the screen's edge: the page margin, the safe-area inset, the bleed.
    val toEdgePx = with(density) { (PAGE_PAD + DOCK_BLEED).roundToPx() } +
        WindowInsets.safeDrawing.getLeft(density, LayoutDirection.Ltr)
    // Concentric with the icons inside: their corner radius plus the padding around them.
    val corner = icon * ca.keepitlit.swell.helper.IconCache.cornerFraction + DOCK_PAD
    val shape = RoundedCornerShape(topEnd = corner, bottomEnd = corner)
    val padPx = with(density) { DOCK_PAD.toPx() }
    val pitchPx = with(density) { (icon + DOCK_GAP).toPx() }
    // Top first, as drawn: the first docked sits lowest, nearest the thumb.
    val drawn = keys.asReversed()
    // While a drag is under way: the order as it stands, which icon is moving, and how far it
    // sits from its current place.
    var order by remember { mutableStateOf<List<String>?>(null) }
    var moving by remember { mutableStateOf<String?>(null) }
    var lift by remember { mutableStateOf(0f) }
    val shown = order ?: drawn
    val bounds = remember { HashMap<String, LayoutCoordinates>() }
    fun rectOf(k: String): Rect? = bounds[k]?.takeIf { it.isAttached }?.boundsInWindow()
        ?.let { Rect(it.left.toInt(), it.top.toInt(), it.right.toInt(), it.bottom.toInt()) }
    val latestDrawn by androidx.compose.runtime.rememberUpdatedState(drawn)

    Column(
        Modifier
            .layout { measurable, constraints ->
                val p = measurable.measure(constraints)
                layout(p.width, p.height) {
                    // Placed here, not in composition, so the column's position is read at layout time.
                    // Only its height in the window is read: across, it moves with the pager.
                    val c = column()?.takeIf { it.isAttached }
                    val y = if (c == null) 0 else {
                        val top = c.positionInRoot().y
                        val centre = c.findRootCoordinates().size.height / 2f
                        (centre - top - p.height / 2f).roundToInt().coerceIn(0, (c.size.height - p.height).coerceAtLeast(0))
                    }
                    p.place(-toEdgePx, y)
                }
            }
            .clip(shape)
            .background(look.paper.copy(alpha = 0.10f))
            .border(1.dp, look.rule2, shape)
            .pointerInput(pitchPx, padPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val list = latestDrawn
                    val at = ((down.position.y - padPx) / pitchPx).toInt().coerceIn(0, list.size - 1)
                    val k = list[at]
                    val entry = model.appsByKey[k] ?: return@awaitEachGesture
                    val held = awaitLongPressOrCancellation(down.id)
                    if (held == null) {
                        // Lifted before the hold: a tap. Moved instead: not ours (the pager's, say).
                        val up = currentEvent.changes.firstOrNull { it.id == down.id }
                        if (up != null && up.changedToUp() && !up.isConsumed) {
                            up.consume()
                            onOpen(entry, rectOf(k))
                        }
                        return@awaitEachGesture
                    }
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onHold(entry, bounds[k]?.takeIf { it.isAttached }?.boundsInWindow()?.center ?: Offset.Zero)
                    var travel = 0f
                    var dragging = false
                    var slot = at
                    var now = list
                    while (true) {
                        val event = awaitPointerEvent()
                        val c = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (c.changedToUpIgnoreConsumed()) { c.consume(); break }
                        val dy = c.positionChange().y
                        c.consume()
                        travel += dy
                        if (!dragging) {
                            if (kotlin.math.abs(travel) < viewConfiguration.touchSlop) continue
                            dragging = true
                            onDrag()
                            moving = k
                            order = now
                            lift = travel
                            continue
                        }
                        lift += dy
                        // Past half a place, trade places with the neighbour; the icon stays under the finger.
                        val from = slot
                        while (lift > pitchPx / 2 && slot < now.size - 1) {
                            now = now.toMutableList().apply { this[slot] = this[slot + 1]; this[slot + 1] = k }
                            slot++; lift -= pitchPx
                        }
                        while (lift < -pitchPx / 2 && slot > 0) {
                            now = now.toMutableList().apply { this[slot] = this[slot - 1]; this[slot - 1] = k }
                            slot--; lift += pitchPx
                        }
                        if (slot != from) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        order = now
                    }
                    if (dragging && now != list) {
                        haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
                        model.orderDock(now.asReversed())
                    }
                    order = null
                    moving = null
                    lift = 0f
                }
            }
            .padding(start = DOCK_BLEED + 10.dp, end = DOCK_PAD, top = DOCK_PAD, bottom = DOCK_PAD),
        verticalArrangement = Arrangement.spacedBy(DOCK_GAP),
    ) {
        for (k in shown) {
            val entry = model.appsByKey[k] ?: continue
            key(k) {
                val lifted = moving == k
                Box(
                    Modifier
                        .onPlaced { bounds[k] = it }
                        .zIndex(if (lifted) 1f else 0f)
                        .graphicsLayer {
                            if (lifted) {
                                translationY = lift
                                scaleX = 1.12f
                                scaleY = 1.12f
                            }
                        }
                        .size(icon)
                        .semantics(mergeDescendants = true) {
                            contentDescription = entry.label
                            onClick { onOpen(entry, rectOf(k)); true }
                            onLongClick { onHold(entry, bounds[k]?.takeIf { it.isAttached }?.boundsInWindow()?.center ?: Offset.Zero); true }
                        },
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
