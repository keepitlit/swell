package ca.keepitlit.swell.ui

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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.keepitlit.swell.SwellModel
import ca.keepitlit.swell.data.CustomIcon
import ca.keepitlit.swell.helper.IconLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One app's name and icon, changed in place: every change shows on home as it is made. Laid out
 * like settings: one column, split at the centre line. The icons come from the bundled
 * [IconLibrary], each on a tile of the chosen colour, with the ones matching the app's name first.
 */
@Composable
fun EditAppPage(key: String?, model: SwellModel, onClose: () -> Unit) {
    val look = LocalLook.current
    // Kept through the closing animation, after the caller has let go of the key.
    var shown by remember { mutableStateOf(key) }
    if (key != null) shown = key
    AnimatedVisibility(
        key != null,
        enter = fadeIn(tween(200)) + slideInVertically(tween(260)) { it / 12 },
        exit = fadeOut(tween(180)) + slideOutVertically(tween(180)) { it / 12 },
    ) {
        val k = shown ?: return@AnimatedVisibility
        Box(
            Modifier
                .fillMaxSize()
                .background(look.shell)
                // Swallow taps so nothing reaches home underneath.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
        ) {
            Wall()
            EditColumn(k, model, onClose)
        }
    }
}

@Composable
private fun EditColumn(key: String, model: SwellModel, onClose: () -> Unit) {
    val look = LocalLook.current
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val entry = model.appsByKey[key]
    val own = model.own(key)
    if (entry == null || own == null) {
        // Uninstalled while open.
        LaunchedEffect(key) { onClose() }
        return
    }
    val edit = model.edits[key]
    val chosen = edit?.icon
    var name by remember(key) { mutableStateOf(entry.label) }
    var query by remember(key) { mutableStateOf("") }
    var colour by remember(key) { mutableStateOf(chosen?.colour ?: IconLibrary.COLOURS.first().second) }
    var coloursOpen by remember(key) { mutableStateOf(false) }
    val library by produceState(emptyList<IconLibrary.Glyph>()) {
        value = withContext(Dispatchers.IO) { IconLibrary.all(context) }
    }
    val glyphs = remember(library, query, own.label) { IconLibrary.search(library, query, own.label) }
    val list = rememberLazyListState()

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
        // The app as it will look on home, with its name as the field that changes it.
        Column(Modifier.fillMaxWidth().padding(top = 24.dp, start = 24.dp, end = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            AppIcon(entry, model.repo, 64.dp, look.iconsMono, look.paper)
            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (name.isEmpty()) Text(own.label, color = look.paper3, fontSize = 28.sp, fontFamily = look.bodyFont, textAlign = TextAlign.Center)
                BasicTextField(
                    value = name,
                    onValueChange = { name = it; model.rename(key, it) },
                    singleLine = true,
                    textStyle = TextStyle(color = look.paper, fontSize = 28.sp, fontFamily = look.bodyFont, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center),
                    cursorBrush = SolidColor(look.accent),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Box(Modifier.padding(top = 6.dp).width(160.dp).height(1.dp).background(look.rule2))
            if (edit?.name != null) {
                Text(
                    "Use “${own.label}”",
                    color = look.accent, fontSize = 15.sp, fontFamily = look.bodyFont,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { name = own.label; model.rename(key, own.label) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
            } else {
                Spacer(Modifier.height(10.dp))
            }
        }

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().edgeBlurFade(EDGE_BAND),
            state = list,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = EDGE_BAND / 2, bottom = EDGE_BAND),
        ) {
            item(key = "colour") {
                val colourName = IconLibrary.COLOURS.firstOrNull { it.second == colour }?.first ?: "Custom"
                SplitLine(
                    left = { Text("Colour", color = look.paper, fontSize = 17.sp, fontFamily = look.bodyFont) },
                    right = {
                        Box(Modifier.size(14.dp).clip(CircleShape).background(Color(colour)).border(1.dp, look.rule2, CircleShape))
                        Spacer(Modifier.width(8.dp))
                        Text(colourName.lowercase(), color = look.paper2, fontSize = 17.sp, fontFamily = look.bodyFont)
                    },
                    onClick = { coloursOpen = !coloursOpen },
                )
            }
            if (coloursOpen) items(IconLibrary.COLOURS, key = { "c" + it.second }) { (label, c) ->
                SplitLine(
                    left = { if (c == colour) Box(Modifier.width(18.dp).height(2.dp).background(look.accent)) },
                    right = {
                        Box(Modifier.size(18.dp).clip(CircleShape).background(Color(c)).border(1.dp, look.rule2, CircleShape))
                        Spacer(Modifier.width(10.dp))
                        Text(label.lowercase(), color = if (c == colour) look.paper else look.paper2, fontSize = 16.sp, fontFamily = look.bodyFont)
                    },
                    onClick = {
                        colour = c
                        coloursOpen = false
                        // A glyph already chosen takes the new colour straight away.
                        chosen?.let { model.setIcon(key, it.copy(colour = c)) }
                    },
                    minHeight = 46.dp,
                )
            }
            item(key = "search") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp, bottom = 8.dp)
                        .clip(RoundedCornerShape(look.fieldRadius))
                        .background(look.search)
                        .border(1.dp, look.rule, RoundedCornerShape(look.fieldRadius))
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("⌕", color = look.paper3, fontSize = 15.sp)
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) Text("Search icons", color = look.paper3, fontSize = 15.sp, fontFamily = look.bodyFont)
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = TextStyle(color = look.paper, fontSize = 15.sp, fontFamily = look.bodyFont),
                            cursorBrush = SolidColor(look.accent),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            if (query.isBlank()) item(key = "own") {
                IconLine(
                    icon = { AppIcon(own, model.repo, it, look.iconsMono, look.paper) },
                    label = "Its own icon",
                    selected = chosen == null,
                    onClick = { model.setIcon(key, null) },
                )
            }
            items(glyphs, key = { it.id }) { g ->
                IconLine(
                    icon = { GlyphIcon(g.id, colour, it, look.iconsMono, look.paper) },
                    label = g.name,
                    selected = chosen?.glyph == g.id,
                    onClick = { focus.clearFocus(); model.setIcon(key, CustomIcon(g.id, colour)) },
                )
            }
            if (library.isNotEmpty() && glyphs.isEmpty()) item(key = "none") {
                Text(
                    "No icon called “${query.trim()}”",
                    color = look.paper3, fontSize = 14.sp, fontFamily = look.bodyFont, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                )
            }
        }

        Text(
            "Done",
            fontFamily = look.bodyFont, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = look.accent,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(CircleShape)
                .clickable { focus.clearFocus(); onClose() }
                .padding(vertical = 14.dp),
        )
    }
}

/** A line split at the centre: [left] ends at the centre line, [right] starts just past it. */
@Composable
private fun SplitLine(
    left: @Composable () -> Unit,
    right: @Composable () -> Unit,
    onClick: () -> Unit,
    minHeight: Dp = 52.dp,
) {
    val look = LocalLook.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .clip(RoundedCornerShape(look.cellRadius))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).padding(end = 10.dp), contentAlignment = Alignment.CenterEnd) { left() }
        Row(Modifier.weight(1f).padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) { right() }
    }
}

/** One icon to choose, drawn as home draws a line: the icon left of the centre line, its name right of it. */
@Composable
private fun IconLine(icon: @Composable (Dp) -> Unit, label: String, selected: Boolean, onClick: () -> Unit) {
    val look = LocalLook.current
    SplitLine(
        left = {
            Box(
                Modifier
                    .then(if (selected) Modifier.border(2.dp, look.accent, RoundedCornerShape(percent = 30)) else Modifier)
                    .padding(3.dp),
            ) { icon(40.dp) }
        },
        right = {
            Text(
                label,
                color = if (selected) look.accent else look.paper,
                fontSize = 17.sp,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                fontFamily = look.bodyFont,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        onClick = onClick,
        minHeight = 56.dp,
    )
}
