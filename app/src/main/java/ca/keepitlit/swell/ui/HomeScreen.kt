package ca.keepitlit.swell.ui

import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

/** Touch cushion around a tile, roughly 10-15 px on the 12R: the tap target, not the whole cell. */
private val TAP_PAD = 5.dp

/** The whole launcher. One screen, three overlays (drawer, app menu, appearance sheet). */
@Composable
fun SwellApp(model: SwellModel, widgets: Widgets, homeSignal: Int, openDrawerSignal: Int = 0) {
    val look = remember(model.appearance) { Look(model.appearance) }
    val context = LocalContext.current

    var drawerOpen by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<Pair<AppEntry, Offset>?>(null) }
    var sheetOpen by remember { mutableStateOf(false) }

    // Page 0 is the widget panel; pages 1.. are the ranked home screens, or the one ranked list.
    val version = model.version
    val pageCount = 1 + if (look.list) 1 else model.book.pages
    val pager = rememberPagerState(initialPage = 1) { pageCount }
    val listState = rememberLazyListState()
    val stepState = remember { StepState() }
    LaunchedEffect(homeSignal) {
        // HOME pressed while already home: overlays close, first screen, top of the list.
        drawerOpen = false
        menuFor = null
        sheetOpen = false
        if (homeSignal > 0) {
            pager.animateScrollToPage(1)
            // Only the list on screen: an unattached LazyListState never finishes scrolling.
            if (look.stepScroll) stepState.first = 0 else listState.animateScrollToItem(0)
        }
    }
    LaunchedEffect(openDrawerSignal) { if (openDrawerSignal > 0) drawerOpen = true }
    BackHandler(enabled = drawerOpen || menuFor != null) {
        when {
            menuFor != null -> menuFor = null
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
                        // Swipe up anywhere that is not a tile: the prototype's shelf handle, as a gesture.
                        var travel = 0f
                        detectVerticalDragGestures(
                            onDragStart = { travel = 0f },
                            onDragEnd = { if (travel < -60.dp.toPx()) drawerOpen = true },
                        ) { _, dy -> travel += dy }
                    },
            ) {
                if (look.clock == ClockPos.TOP) HeroTime(onTap = openClock, onLongPress = { sheetOpen = true }) else Spacer(Modifier.height(12.dp))
                HorizontalPager(
                    state = pager,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    beyondViewportPageCount = 1,
                    key = { it },
                ) { page ->
                    if (page == 0) {
                        WidgetPanel(widgets, Modifier.fillMaxSize().padding(horizontal = 12.dp))
                    } else if (look.list && look.stepScroll) {
                        SeatSteps(
                            model = model,
                            state = stepState,
                            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                            onOpen = open,
                            onHold = { entry, at -> menuFor = entry to at },
                        )
                    } else if (look.list) {
                        SeatList(
                            model = model,
                            state = listState,
                            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                            onOpen = open,
                            onHold = { entry, at -> menuFor = entry to at },
                        )
                    } else {
                        SeatPage(
                            model = model,
                            page = page - 1,
                            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                            onOpen = open,
                            onHold = { entry, at -> menuFor = entry to at },
                        )
                    }
                }
                Dots(count = pageCount, current = pager.currentPage)
                if (look.showShelf) Shelf(
                    model = model,
                    modifier = Modifier.padding(horizontal = 12.dp),
                    onOpen = open,
                    onHold = { entry, at -> menuFor = entry to at },
                )
                if (look.clock == ClockPos.BOTTOM) HeroTime(onTap = openClock, onLongPress = { sheetOpen = true }, bottom = true)
                HandleRow(onOpen = { drawerOpen = true }, onHold = { sheetOpen = true })
            }

            Drawer(
                open = drawerOpen,
                model = model,
                onClose = { drawerOpen = false },
                onOpen = open,
                onHold = { entry, at -> menuFor = entry to at },
                onAppearance = { sheetOpen = true },
            )

            menuFor?.let { (entry, at) ->
                AppMenu(
                    model = model,
                    entry = entry,
                    anchor = at,
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

            if (sheetOpen) AppearanceSheet(model = model, onDismiss = { sheetOpen = false })

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
 * One home screen: [SeatBook.pageSeats] seats in reading order: the hottest seat top-left, then
 * across, then down, the coldest bottom-right (the prototype was floor-up; the phone wants rank one at
 * screen one, row one, column one). Cells are absolutely placed so a seat change animates (the
 * prototype's FLIP, as animated offsets keyed by app).
 */
@Composable
private fun SeatPage(
    model: SwellModel,
    page: Int,
    modifier: Modifier,
    onOpen: (AppEntry, Rect?) -> Unit,
    onHold: (AppEntry, Offset) -> Unit,
) {
    val version = model.version
    val book = model.book
    val cols = book.cols
    val rows = book.rows
    val keys = remember(version, page, cols, rows) { book.pageSeats(page) }

    BoxWithConstraints(modifier) {
        val cellW = maxWidth / cols
        val rowH = maxHeight / rows
        // The vertical gap between tiles is made equal to the horizontal gap: the row pitch is the
        // column pitch (cellW), not the stretched rowH. The label lives inside that gap and is not
        // counted. Row 0 keeps its place (the cells still bottom-align within rowH); the rows below
        // move up, and the slack collects at the bottom, off the page dots. Never taller than rowH.
        val pitchV = minOf(cellW, rowH)
        fun slot(i: Int): Pair<Dp, Dp> {
            val row = i / cols
            val col = i % cols
            return cellW * col to pitchV * row
        }
        for (i in keys.indices) {
            if (keys[i] != null) continue
            val (x, y) = slot(i)
            EmptyCell(Modifier.offset(x, y).size(cellW, rowH), cellW, rowH)
        }
        for (i in keys.indices) {
            val k = keys[i] ?: continue
            val entry = model.appsByKey[k] ?: continue
            val app = book.app(k) ?: continue
            val (x, y) = slot(i)
            key(k) {
                SeatCell(
                    model = model, entry = entry, pinned = app.pinned, movedAt = book.lastMoved[k] ?: 0L,
                    hot = false, x = x, y = y, width = cellW, height = rowH, onOpen = onOpen, onHold = onHold,
                )
            }
        }
    }
}

/** Which seat heads the stepped list. Hoisted so HOME can send it back to the top. */
class StepState {
    var first by mutableIntStateOf(0)
}

/** The seats home lists: everything after the shelf, in rank order. */
@Composable
private fun listKeys(model: SwellModel): List<String> {
    val version = model.version
    val book = model.book
    return remember(version, book.dockSeats) {
        book.seats.drop(book.dockSeats).filterNotNull().filter { model.appsByKey.containsKey(it) }
    }
}

/** The stepped list composes lines in blocks of this many, so most steps compose nothing. */
private const val WINDOW_STEP = 4

/** A line's height: the icon plus its 6 dp above and below. */
private fun linePitch(look: Look): Dp = look.iconSize + 12.dp

/**
 * Home as one column, scrolling smoothly: every seat after the shelf, in rank order, an app to a
 * line, the same order the grid reads in, top to bottom. A rank change slides the line to its
 * new place (the grid's FLIP, as the list's own item animation). The top and bottom edges blur and
 * fade ([edgeBlurFade]); the content padding keeps the first line clear of the band at rest.
 */
@Composable
private fun SeatList(
    model: SwellModel,
    state: LazyListState,
    modifier: Modifier,
    onOpen: (AppEntry, Rect?) -> Unit,
    onHold: (AppEntry, Offset) -> Unit,
) {
    val keys = listKeys(model)
    LazyColumn(
        modifier.edgeBlurFade(EDGE_BAND),
        state = state,
        contentPadding = PaddingValues(top = EDGE_BAND, bottom = EDGE_BAND),
    ) {
        items(keys, key = { it }) { k ->
            SeatLine(
                model, k,
                Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null, placementSpec = tween(480, easing = EASE)),
                onOpen, onHold,
            )
        }
    }
}

/**
 * Home as one column of stationary lines: a drag or fling never moves the lines, it moves the apps
 * through them, one line per [linePitch] of travel. The same ground as [SeatList] covers, with
 * nothing on screen sliding. One line peeks into each blurred edge band, as the smooth list does
 * at rest.
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
    BoxWithConstraints(modifier.clipToBounds().edgeBlurFade(EDGE_BAND)) {
        val pitch = linePitch(look)
        // Lines wholly clear of both bands; the list stops when its last app reaches the last of them.
        val clear = ((maxHeight - EDGE_BAND * 2) / pitch).toInt().coerceAtLeast(1)
        val maxFirst = (keys.size - clear).coerceAtLeast(0)
        val pitchPx = with(LocalDensity.current) { pitch.toPx() }
        val height = maxHeight
        Box(
            Modifier
                .fillMaxSize()
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
                    )
                }
            }
        }
    }
}

/**
 * One app's line: the icon bare (no tile), right-aligned to the screen's centre line, and its name
 * starting just past it, so the divide between icons and names runs down the middle. The whole
 * line is the tap target.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SeatLine(
    model: SwellModel,
    k: String,
    modifier: Modifier,
    onOpen: (AppEntry, Rect?) -> Unit,
    onHold: (AppEntry, Offset) -> Unit,
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
    // Read lazily, on tap, like the grid's cells: the icon's bounds seed the launch animation.
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val iconSize = look.iconSize
    Row(
        modifier
            .fillMaxWidth()
            .height(linePitch(look))
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
        // Two equal halves: the icon ends 8 dp short of the centre line, the name starts 8 dp past it.
        Box(Modifier.weight(1f).padding(end = 8.dp), contentAlignment = Alignment.CenterEnd) {
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

/** Height of the blurred, fading band at the list's top and bottom edges. */
private val EDGE_BAND = 40.dp

/**
 * Blurs and fades the content toward its top and bottom edges. The content is recorded once into
 * a layer; the middle is drawn straight from it, and each edge band composites a sharp copy and a
 * band-sized blurred copy through gradient masks. Across a band, measured from the edge as
 * t = 0..1, the total alpha is t, of which the blurred share is 1 - t: sharp alpha t², blurred
 * alpha t(1 - t). Only the two bands are blurred and only they go offscreen; nothing is composed
 * twice.
 */
private fun Modifier.edgeBlurFade(band: Dp): Modifier = composed {
    val content = rememberGraphicsLayer()
    val topBlur = rememberGraphicsLayer()
    val bottomBlur = rememberGraphicsLayer()
    val radius = with(LocalDensity.current) { 10.dp.toPx() }
    LaunchedEffect(radius) {
        topBlur.renderEffect = BlurEffect(radius, radius, TileMode.Decal)
        bottomBlur.renderEffect = BlurEffect(radius, radius, TileMode.Decal)
    }
    drawWithContent {
        val b = band.toPx().coerceAtMost(size.height / 2)
        if (b <= 0f) { drawContent(); return@drawWithContent }
        val h = size.height
        val strip = IntSize(size.width.roundToInt(), kotlin.math.ceil(b).toInt())
        content.record { this@drawWithContent.drawContent() }
        topBlur.record(strip) { drawLayer(content) }
        bottomBlur.record(strip) { translate(top = -(h - b)) { drawLayer(content) } }

        clipRect(top = b, bottom = h - b) { drawLayer(content) }
        // Stops at t = 0, 1/4, 1/2, 3/4, 1 from the edge, laid from y = edge to y = edge ± b.
        val ts = listOf(0f, 0.25f, 0.5f, 0.75f, 1f)
        fun mask(edge: Float, inward: Float, alpha: (Float) -> Float): Brush {
            val stops = ts.map { t -> t to Color.Black.copy(alpha = alpha(t)) }
            return Brush.verticalGradient(*stops.toTypedArray(), startY = edge, endY = edge + inward)
        }
        val sharp = { t: Float -> t * t }
        val blurred = { t: Float -> t * (1f - t) }
        drawBand(content, 0f, b, mask(0f, b, sharp), mask(0f, b, blurred), topBlur)
        drawBand(content, h - b, h, mask(h, -b, sharp), mask(h, -b, blurred), bottomBlur)
    }
}

/** One edge band, [top]..[bottom]: [sharp] through [sharpMask] plus [blur] (a strip drawn at [top]) through [blurMask]. */
private fun DrawScope.drawBand(
    sharp: GraphicsLayer, top: Float, bottom: Float,
    sharpMask: Brush, blurMask: Brush, blur: GraphicsLayer,
) {
    val bounds = androidx.compose.ui.geometry.Rect(0f, top, size.width, bottom)
    val canvas = drawContext.canvas
    canvas.saveLayer(bounds, Paint())
    drawLayer(sharp)
    drawRect(sharpMask, topLeft = bounds.topLeft, size = bounds.size, blendMode = BlendMode.DstIn)
    canvas.restore()
    canvas.saveLayer(bounds, Paint())
    translate(top = top) { drawLayer(blur) }
    drawRect(blurMask, topLeft = bounds.topLeft, size = bounds.size, blendMode = BlendMode.DstIn)
    canvas.restore()
}

/** The shelf: the hottest [SeatBook.dockSeats], fixed under every screen. */
@Composable
private fun Shelf(
    model: SwellModel,
    modifier: Modifier,
    onOpen: (AppEntry, Rect?) -> Unit,
    onHold: (AppEntry, Offset) -> Unit,
) {
    val look = LocalLook.current
    val version = model.version
    val book = model.book
    val cols = book.cols
    val shelfH = 104.dp
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(shelfH)
            .background(Brush.verticalGradient(0f to look.paper.copy(alpha = 0.04f), 0.7f to Color.Transparent)),
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(look.rule))
        val cellW = maxWidth / cols
        for (seat in 0 until book.dockSeats) {
            val k = book.seats.getOrNull(seat) ?: continue
            val entry = model.appsByKey[k] ?: continue
            val app = book.app(k) ?: continue
            key(k) {
                SeatCell(
                    model = model, entry = entry, pinned = app.pinned, movedAt = book.lastMoved[k] ?: 0L,
                    hot = true, x = cellW * seat, y = 0.dp, width = cellW, height = shelfH, onOpen = onOpen, onHold = onHold,
                )
            }
        }
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

@Composable
private fun EmptyCell(modifier: Modifier, width: Dp, height: Dp) {
    val look = LocalLook.current
    Column(
        modifier.padding(TAP_PAD),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
    ) {
        Box(
            Modifier
                .size(tileFor(width, height, false, look))
                .border(1.dp, look.paper.copy(alpha = 0.10f), RoundedCornerShape(look.tileRadius)),
        )
        Spacer(Modifier.height(6.dp))
        Text(" ", fontSize = 11.sp, lineHeight = 12.sp)
        Spacer(Modifier.height(2.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SeatCell(
    model: SwellModel,
    entry: AppEntry,
    pinned: Boolean,
    movedAt: Long,
    hot: Boolean,
    x: Dp, y: Dp,
    width: Dp, height: Dp,
    onOpen: (AppEntry, Rect?) -> Unit,
    onHold: (AppEntry, Offset) -> Unit,
) {
    val look = LocalLook.current
    val density = LocalDensity.current
    val target = with(density) { IntOffset(x.roundToPx(), y.roundToPx()) }
    // Animate the FLIP only when this app actually changed rank (a recent launch). A grid reflow or
    // a page scrolling into view retargets every tile at once; those must snap, or hundreds of tiles
    // animate together for 480 ms and the whole launcher stalls until it settles.
    val anim = remember { Animatable(target, IntOffset.VectorConverter) }
    LaunchedEffect(target) {
        val recentlyMoved = movedAt > 0 && System.currentTimeMillis() - movedAt < 1600
        if (recentlyMoved && anim.value != target) anim.animateTo(target, tween(480, easing = EASE))
        else anim.snapTo(target)
    }
    val offset = anim.value

    var justMoved by remember { mutableStateOf(false) }
    LaunchedEffect(movedAt) {
        val left = 1600 - (System.currentTimeMillis() - movedAt)
        if (movedAt > 0 && left > 0) { justMoved = true; delay(left); justMoved = false } else justMoved = false
    }

    // The tap target's window bounds are read lazily, only when the cell is opened, from the live
    // layout coordinates. Reading them on every placement meant a boundsInWindow tree-walk per tile
    // on every pager frame: the swipe stutter that grew with the number of tiles on a page.
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val tileSize = tileFor(width, height, hot, look)

    Box(
        Modifier
            .offset { offset }
            .size(width, height),
        contentAlignment = Alignment.BottomCenter,
    ) {
      Column(
        Modifier
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
            )
            .onPlaced { coords = it }
            // Fixed to the tile plus a small cushion, so the tap target hugs the icon (about
            // 10-15 px beyond it) and the rest of the cell is dead space between neighbours. The
            // label no longer stretches the target; it wraps to the tile's width.
            .width(tileSize + TAP_PAD * 2)
            .padding(TAP_PAD),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(tileSize)
                .clip(RoundedCornerShape(look.tileRadius))
                .background(look.tile)
                .then(if (justMoved) Modifier.border(2.dp, look.ember, RoundedCornerShape(look.tileRadius)) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(entry, model.repo, look.iconIn(tileSize), look.iconsMono, look.paper)
            if (pinned) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 2.dp, y = (-2).dp)
                        .size(10.dp)
                        .background(look.shell, CircleShape)
                        .padding(2.dp)
                        .background(look.accent2, CircleShape),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (look.showLabels) entry.label else " ",
            fontFamily = look.bodyFont,
            fontSize = if (tileSize < 60.dp) 9.sp else 11.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.Medium,
            color = look.paper2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(if (hot) 6.dp else 2.dp))
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
        Box(Modifier.width(42.dp).height(5.dp).background(look.paper2, CircleShape))
        Text(
            "all apps",
            fontFamily = look.monoFont,
            fontSize = 10.sp,
            letterSpacing = 1.sp,
            color = look.paper3,
            modifier = Modifier.padding(top = 8.dp),
        )
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

/**
 * Tile edge for a cell of [width] x [height]: as large as the cell allows, leaving room for the
 * label, capped at the look's icon size so a sparse grid does not turn into a wall of posters.
 * The fixed row's tiles run a touch larger, as in the prototype.
 */
private fun tileFor(width: Dp, height: Dp, hot: Boolean, look: Look): Dp {
    val cap = if (hot) look.iconSize + 4.dp else look.iconSize
    val byWidth = width - 8.dp
    val byHeight = height - (if (look.showLabels) 30.dp else 10.dp)
    return minOf(byWidth, byHeight, cap).coerceAtLeast(28.dp)
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
