package ca.keepitlit.swell.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.keepitlit.swell.data.AppEntry
import kotlinx.coroutines.launch
import java.text.Normalizer
import kotlin.math.roundToInt

/** The letter an app files under: its first letter without accents, or '#' for anything else. */
private fun letterOf(label: String): Char {
    val c = Normalizer.normalize(label.take(1), Normalizer.Form.NFD).firstOrNull()?.uppercaseChar() ?: return '#'
    return if (c in 'A'..'Z') c else '#'
}

/** Each letter that starts an app in [rows], with the index of its first app, in list order. */
fun letterSections(rows: List<AppEntry>): List<Pair<Char, Int>> {
    val seen = LinkedHashMap<Char, Int>()
    rows.forEachIndexed { i, e -> seen.putIfAbsent(letterOf(e.label), i) }
    return seen.entries.map { it.key to it.value }
}

/**
 * The letters down the right edge of all apps. Touch or drag along it and [state] jumps to the
 * first app under the letter at the finger; a large copy of that letter rides beside the finger
 * and each new letter ticks. The letters share the rail's height evenly, so every one is reachable
 * however many there are.
 */
@Composable
fun AlphabetRail(sections: List<Pair<Char, Int>>, state: LazyListState, modifier: Modifier = Modifier) {
    val look = LocalLook.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var touching by remember { mutableStateOf<Int?>(null) }
    var fingerY by remember { mutableStateOf(0f) }
    val density = LocalDensity.current

    Box(modifier.width(RAIL_WIDTH).fillMaxHeight()) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .pointerInput(sections) {
                    if (sections.isEmpty()) return@pointerInput
                    fun pick(y: Float) {
                        fingerY = y.coerceIn(0f, size.height.toFloat())
                        val i = (fingerY / size.height * sections.size).toInt().coerceIn(0, sections.lastIndex)
                        if (i != touching) {
                            touching = i
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            scope.launch { state.scrollToItem(sections[i].second) }
                        }
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        pick(down.position.y)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            change.consume()
                            pick(change.position.y)
                        }
                        touching = null
                    }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            sections.forEachIndexed { i, (letter, _) ->
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        letter.toString(),
                        color = if (touching == i) look.accent else look.paper3,
                        fontSize = 11.sp,
                        fontWeight = if (touching == i) FontWeight.Bold else FontWeight.Medium,
                        fontFamily = look.monoFont,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
        }
        // The letter under the finger, large, just left of the rail.
        touching?.let { i ->
            val bubble = 56.dp
            val y = with(density) { (fingerY - bubble.toPx() / 2).roundToInt() }
            Box(
                Modifier
                    .offset { IntOffset(-(bubble + 12.dp).roundToPx(), y) }
                    .size(bubble)
                    .background(look.accent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    sections[i].first.toString(),
                    color = if (ca.keepitlit.swell.theme.Themes.luminance(look.palette.accent) > 0.4f) androidx.compose.ui.graphics.Color(0xFF0B0D10) else androidx.compose.ui.graphics.Color.White,
                    fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFamily = look.bodyFont,
                )
            }
        }
    }
}

private val RAIL_WIDTH = 28.dp
