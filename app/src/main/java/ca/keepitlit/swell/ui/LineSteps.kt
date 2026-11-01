package ca.keepitlit.swell.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import ca.keepitlit.swell.helper.Ticker
import kotlinx.coroutines.launch

/**
 * Scrolls a list a whole line at a time, home and all apps alike: drag travel and fling are spent
 * in steps of [pitchPx], and every step ticks. [step] moves one line (1 on down the list, -1 back
 * up) and returns false at an end; [onStart] runs as a drag begins.
 */
@Composable
fun Modifier.lineSteps(pitchPx: Float, onStart: () -> Unit = {}, step: (Int) -> Boolean): Modifier {
    val context = LocalContext.current
    val ticker = remember { Ticker(context.applicationContext) }
    val decay = rememberSplineBasedDecay<Float>()
    val scope = rememberCoroutineScope()
    val fling = remember { Animatable(0f) }
    val start by rememberUpdatedState(onStart)
    val move by rememberUpdatedState(step)
    return pointerInput(pitchPx) {
        var travel = 0f
        // Spend travel a line at a time; false once an end is reached.
        fun spend(dy: Float): Boolean {
            travel += dy
            while (travel <= -pitchPx) {
                if (!move(1)) { travel = 0f; return false }
                ticker.tick()
                travel += pitchPx
            }
            while (travel >= pitchPx) {
                if (!move(-1)) { travel = 0f; return false }
                ticker.tick()
                travel -= pitchPx
            }
            return true
        }
        val tracker = VelocityTracker()
        detectVerticalDragGestures(
            onDragStart = {
                scope.launch { fling.stop() }
                start()
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
    }
}
