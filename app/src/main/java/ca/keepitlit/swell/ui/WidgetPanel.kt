package ca.keepitlit.swell.ui

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import ca.keepitlit.swell.Widgets

/**
 * The widget panel: a column of hosted system widgets, each as tall as it asked for (in rows
 * of [Widgets.ROW_DP] dp), with a long-press menu to resize, reorder or remove.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WidgetPanel(widgets: Widgets, modifier: Modifier = Modifier) {
    val look = LocalLook.current
    val slots = widgets.slots
    val version = widgets.version
    var picking by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<Int?>(null) }
    val shape = RoundedCornerShape(look.cellRadius)

    LazyColumn(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(slots, key = { it.id }) { slot ->
            val info = remember(slot.id) { widgets.info(slot.id) }
            val rowsNow = remember(version, slot.id) { slot.rows }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height((Widgets.ROW_DP * rowsNow).dp)
                    .clip(shape)
                    .background(look.tile.copy(alpha = 0.35f))
                    .border(1.dp, look.rule, shape),
            ) {
                if (info != null) {
                    HostedWidget(widgets, slot.id, info, rowsNow, Modifier.fillMaxSize())
                } else {
                    Text("Widget is gone", color = look.paper3, fontSize = 12.sp, fontFamily = look.bodyFont, modifier = Modifier.align(Alignment.Center))
                }
                // A thin hold-handle along the top edge: long-press here for the menu, since the
                // widget itself owns its own touches.
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(look.shell.copy(alpha = 0.55f))
                        .combinedClickable(onClick = { menuFor = slot.id }, onLongClick = { menuFor = slot.id })
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text("···", color = look.paper2, fontSize = 11.sp, fontFamily = look.monoFont)
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (slots.isEmpty()) {
                    Text("Widgets", color = look.paper2, fontSize = 15.sp, fontWeight = FontWeight.Medium, fontFamily = look.bodyFont)
                    Text("System widgets live here, to the left of your apps.", color = look.paper3, fontSize = 12.sp, fontFamily = look.bodyFont, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
                }
                Pill("Add a widget", onClick = { picking = true }, primary = slots.isEmpty())
            }
        }
    }

    menuFor?.let { id ->
        val slot = slots.firstOrNull { it.id == id }
        if (slot == null) { menuFor = null } else {
            SmallSheet(onDismiss = { menuFor = null }) {
                val info = widgets.info(id)
                val context = LocalContext.current
                Text(info?.loadLabel(context.packageManager) ?: "Widget", color = look.paper, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = look.bodyFont)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Shorter", onClick = { widgets.resize(id, slot.rows - 1) })
                    Pill("Taller", onClick = { widgets.resize(id, slot.rows + 1) })
                    Pill("Up", onClick = { widgets.move(id, -1) })
                    Pill("Down", onClick = { widgets.move(id, +1) })
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Remove", danger = true, onClick = { widgets.remove(id); menuFor = null })
                    Pill("Done", primary = true, onClick = { menuFor = null })
                }
            }
        }
    }

    if (picking) {
        WidgetPicker(widgets, onDismiss = { picking = false })
    }
}

@Composable
private fun HostedWidget(widgets: Widgets, id: Int, info: AppWidgetProviderInfo, rows: Int, modifier: Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            // The application context, not the activity's: an AppCompat activity's layout
            // inflater swaps every ImageView for an AppCompatImageView, and RemoteViews refuses
            // to drive those ("can't use method with RemoteViews: setImageResource"), so every
            // widget with an image came up as "Couldn't add widget."
            widgets.host.createView(ctx.applicationContext, id, info)
        },
        update = { view: AppWidgetHostView ->
            val density = view.resources.displayMetrics.density
            val wDp = (view.width / density).toInt().coerceAtLeast(200)
            val hDp = Widgets.ROW_DP * rows
            @Suppress("DEPRECATION")
            view.updateAppWidgetSize(null, wDp, hDp, wDp, hDp)
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WidgetPicker(widgets: Widgets, onDismiss: () -> Unit) {
    val look = LocalLook.current
    val context = LocalContext.current
    val pm = context.packageManager
    val providers = remember { widgets.providers() }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = look.drawer,
        contentColor = look.paper,
        scrimColor = Color(0x99000000),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text("Add a widget", fontFamily = look.clockFont, fontSize = 24.sp, fontWeight = FontWeight.Medium, color = look.paper)
            Text("${providers.size} available", color = look.paper3, fontSize = 12.sp, fontFamily = look.monoFont, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
            LazyColumn(Modifier.fillMaxWidth().height(520.dp)) {
                items(providers, key = { it.provider.flattenToString() + it.profile.hashCode() }) { p ->
                    val appLabel = remember(p) {
                        try { pm.getApplicationLabel(pm.getApplicationInfo(p.provider.packageName, 0)).toString() } catch (_: Exception) { p.provider.packageName }
                    }
                    val label = remember(p) { p.loadLabel(pm) }
                    val density = context.resources.displayMetrics.density
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(look.fieldRadius))
                            .clickable { onDismiss(); widgets.add(p) }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(label, color = look.paper, fontSize = 15.sp, fontWeight = FontWeight.Medium, fontFamily = look.bodyFont, maxLines = 1)
                            Text(appLabel, color = look.paper3, fontSize = 11.sp, fontFamily = look.bodyFont, maxLines = 1)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("${(p.minWidth / density).toInt()}×${(p.minHeight / density).toInt()}", color = look.paper3, fontSize = 11.sp, fontFamily = look.monoFont)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SmallSheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val look = LocalLook.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = look.drawer,
        contentColor = look.paper,
        scrimColor = Color(0x99000000),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp)) { content() }
    }
}
