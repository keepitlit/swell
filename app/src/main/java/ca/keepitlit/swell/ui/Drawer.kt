package ca.keepitlit.swell.ui

import android.graphics.Rect
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.keepitlit.swell.SwellModel
import ca.keepitlit.swell.data.AppEntry

/** All apps, alphabetical, with search. Home holds the ranking; this is the index. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Drawer(
    open: Boolean,
    model: SwellModel,
    onClose: () -> Unit,
    onOpen: (AppEntry, Rect?) -> Unit,
    onHold: (AppEntry, Offset) -> Unit,
    onSettings: () -> Unit,
) {
    val look = LocalLook.current
    val version = model.version
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    // The field is NOT focused on open: the drawer is for scrolling first, and a keyboard
    // jumping up on every pull covers half the list. Tap the field to search.
    LaunchedEffect(open) {
        if (open) query = "" else focusManager.clearFocus()
    }

    // Always alphabetical: model.apps arrives sorted. Only the home screens are ranked.
    val rows = remember(model.apps, query) {
        val q = query.trim().lowercase()
        model.apps.filter { q.isEmpty() || it.label.lowercase().contains(q) }
    }

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(open, enter = fadeIn(tween(220)), exit = fadeOut(tween(220))) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0x8C080706))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
            )
        }
        AnimatedVisibility(
            open,
            enter = slideInVertically(tween(320)) { it },
            exit = slideOutVertically(tween(260)) { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            // The same backdrop as the screens, under a hairline so the sheet still reads as one.
            Box(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.8f)
                    .clip(RoundedCornerShape(topStart = look.drawerRadius, topEnd = look.drawerRadius))
                    .background(look.shell)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
            ) {
            Wall()
            Column(
                Modifier
                    .fillMaxSize()
                    .border(1.dp, look.rule, RoundedCornerShape(topStart = look.drawerRadius, topEnd = look.drawerRadius))
                    .imePadding()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 12.dp),
            ) {
                Box(Modifier.align(Alignment.CenterHorizontally).width(40.dp).height(4.dp).background(look.rule2, CircleShape))
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(look.fieldRadius))
                        .background(look.search)
                        .border(1.dp, look.rule, RoundedCornerShape(look.fieldRadius))
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("⌕", color = look.paper3, fontSize = 15.sp)
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) Text("Search apps", color = look.paper3, fontSize = 15.sp, fontFamily = look.bodyFont)
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = TextStyle(color = look.paper, fontSize = 15.sp, fontFamily = look.bodyFont),
                            cursorBrush = SolidColor(look.accent),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = { rows.firstOrNull()?.let { onOpen(it, null) } }),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                val listState = rememberLazyListState()
                LaunchedEffect(open, query) { listState.scrollToItem(0) }
                val sections = remember(rows) { letterSections(rows) }
                Row(Modifier.weight(1f)) {
                LazyColumn(Modifier.weight(1f), state = listState, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(rows, key = { it.key }) { entry ->
                        var centre by remember { mutableStateOf(Offset.Zero) }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(look.fieldRadius))
                                .combinedClickable(onClick = { onOpen(entry, null) }, onLongClick = { onHold(entry, centre) })
                                .onGloballyPositioned { centre = it.boundsInWindow().center }
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Bare, as on home: no tile behind the icon.
                            AppIcon(entry, model.repo, 40.dp, look.iconsMono, look.paper)
                            Spacer(Modifier.width(12.dp))
                            Text(entry.label, color = look.paper, fontSize = 15.sp, fontWeight = FontWeight.Medium, fontFamily = look.bodyFont, maxLines = 1, modifier = Modifier.weight(1f))
                        }
                    }
                    if (rows.isEmpty()) item { Text("Nothing matches.", color = look.paper3, fontSize = 13.sp, fontFamily = look.bodyFont, modifier = Modifier.padding(8.dp)) }
                }
                // Searching narrows the list to a few rows; the letters would only get in the way.
                if (query.isBlank() && sections.size > 1) AlphabetRail(sections, listState)
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    val st = model.book.stability()
                    Text(
                        "stability $st% · day ${model.book.day} · ${model.book.totalOpens} launches",
                        color = look.paper3, fontSize = 11.sp, fontFamily = look.monoFont, maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    Pill("Settings", onClick = onSettings)
                }
            }
            }
        }
    }
}
