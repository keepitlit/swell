package ca.keepitlit.swell.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.keepitlit.swell.SwellModel
import ca.keepitlit.swell.data.AppEntry

/**
 * The long-press menu, anchored to the icon that was held. The prototype's long-press simply
 * toggles a pin; on a phone the same gesture is also where app info and uninstall have to live,
 * so it becomes a short menu with Pin at the top.
 */
@Composable
fun AppMenu(
    model: SwellModel,
    entry: AppEntry,
    anchor: Offset,
    onOpen: () -> Unit,
    onAppInfo: () -> Unit,
    onUninstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    val look = LocalLook.current
    val version = model.version
    val app = remember(version) { model.book.app(entry.key) }
    val density = LocalDensity.current
    var menuHeight by remember { mutableFloatStateOf(0f) }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color(0x8C000000))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val menuW = with(density) { 240.dp.toPx() }
        val margin = with(density) { 12.dp.toPx() }
        val gap = with(density) { 40.dp.toPx() }
        val below = anchor.y < heightPx * 0.52f
        val x = (anchor.x - menuW / 2f).coerceIn(margin, (widthPx - menuW - margin).coerceAtLeast(margin))
        val y = (if (below) anchor.y + gap else anchor.y - gap - menuHeight)
            .coerceIn(margin, (heightPx - menuHeight - margin).coerceAtLeast(margin))

        Column(
            Modifier
                .offset { IntOffset(x.toInt(), y.toInt()) }
                .width(240.dp)
                .onSizeChanged { menuHeight = it.height.toFloat() }
                .clip(RoundedCornerShape(look.fieldRadius))
                .background(look.drawer),
        ) {
            Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(entry, model.repo, 26.dp, look.iconsMono, look.paper, Modifier.clip(RoundedCornerShape(6.dp)))
                Column(Modifier.padding(start = 10.dp)) {
                    Text(entry.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = look.paper, fontFamily = look.bodyFont, maxLines = 1)
                    val n = app?.opens ?: 0
                    Text("$n ${if (n == 1) "open" else "opens"}", fontSize = 11.sp, color = look.paper3, fontFamily = look.monoFont, maxLines = 1)
                }
            }
            Rule()
            Item("Open", onOpen)
            Item(
                if (app?.pinned == true) "Unpin" else "Pin in place",
                { model.togglePin(entry.key); onDismiss() },
            )
            Item("App info", onAppInfo)
            // A pinned shortcut has no package of its own: "Uninstall" here would remove the app
            // that hosts it: remove a web page from home and lose the whole browser.
            Item(
                if (entry.isShortcut) "Remove" else "Uninstall",
                onUninstall,
                tint = look.ember2,
            )
        }
    }
}

@Composable
private fun Rule() {
    val look = LocalLook.current
    Box(Modifier.fillMaxWidth().height(1.dp).background(look.rule))
}

@Composable
private fun Item(label: String, onClick: () -> Unit, muted: Boolean = false, tint: Color? = null) {
    val look = LocalLook.current
    Row(
        Modifier.fillMaxWidth().height(48.dp).clickable(onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 15.sp, color = tint ?: if (muted) look.paper3 else look.paper, fontFamily = look.bodyFont, maxLines = 1)
    }
}
