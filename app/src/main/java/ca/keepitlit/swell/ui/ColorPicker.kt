package ca.keepitlit.swell.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.keepitlit.swell.theme.Themes

/**
 * A full picker: a saturation/value plane under a hue strip, with the hex both readable and
 * typeable. Emits an ARGB int on every change so the page behind the sheet repaints live.
 */
@Composable
fun ColorPicker(colour: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val look = LocalLook.current
    var hsv by remember { mutableStateOf(toHsv(colour)) }
    var hexText by remember { mutableStateOf(Themes.hex(colour)) }
    // External changes (a swatch tapped, a reset) resync the local state; local edits do not loop.
    LaunchedEffect(colour) {
        if (fromHsv(hsv) != colour) hsv = toHsv(colour)
        if (Themes.parseHex(hexText) != colour) hexText = Themes.hex(colour)
    }
    fun emit(h: Float, s: Float, v: Float) {
        hsv = floatArrayOf(h, s, v)
        val c = fromHsv(hsv)
        hexText = Themes.hex(c)
        onChange(c)
    }
    val hueColour = fromHsv(floatArrayOf(hsv[0], 1f, 1f))

    Column(modifier.fillMaxWidth()) {
        // Saturation / value plane.
        Box(
            Modifier
                .fillMaxWidth()
                .height(170.dp)
                .clip(RoundedCornerShape(look.fieldRadius))
                .pointerInput(Unit) {
                    detectTapGestures { p -> emit(hsv[0], (p.x / size.width).coerceIn(0f, 1f), (1f - p.y / size.height).coerceIn(0f, 1f)) }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        val p = change.position
                        emit(hsv[0], (p.x / size.width).coerceIn(0f, 1f), (1f - p.y / size.height).coerceIn(0f, 1f))
                    }
                },
        ) {
            Canvas(Modifier.fillMaxWidth().height(170.dp)) {
                drawRect(Brush.horizontalGradient(listOf(Color.White, Color(hueColour))))
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                val x = hsv[1] * size.width
                val y = (1f - hsv[2]) * size.height
                drawCircle(Color.White, radius = 9.dp.toPx(), center = Offset(x, y), style = Stroke(2.dp.toPx()))
                drawCircle(Color.Black.copy(alpha = 0.5f), radius = 10.5f.dp.toPx(), center = Offset(x, y), style = Stroke(1.dp.toPx()))
            }
        }
        Spacer(Modifier.height(10.dp))
        // Hue strip.
        Box(
            Modifier
                .fillMaxWidth()
                .height(22.dp)
                .clip(RoundedCornerShape(999.dp))
                .pointerInput(Unit) { detectTapGestures { p -> emit((p.x / size.width * 360f).coerceIn(0f, 359.99f), hsv[1], hsv[2]) } }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        emit((change.position.x / size.width * 360f).coerceIn(0f, 359.99f), hsv[1], hsv[2])
                    }
                },
        ) {
            Canvas(Modifier.fillMaxWidth().height(22.dp)) {
                val stops = (0..12).map { i -> Color(fromHsv(floatArrayOf(i * 30f % 360f, 1f, 1f))) }
                drawRect(Brush.horizontalGradient(stops))
                val x = hsv[0] / 360f * size.width
                drawCircle(Color.White, radius = 8.dp.toPx(), center = Offset(x, size.height / 2f), style = Stroke(2.5f.dp.toPx()))
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(30.dp).clip(CircleShape).background(Color(fromHsv(hsv))).border(1.dp, look.rule2, CircleShape))
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .width(120.dp)
                    .clip(RoundedCornerShape(look.fieldRadius))
                    .background(look.search)
                    .border(1.dp, look.rule, RoundedCornerShape(look.fieldRadius))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                BasicTextField(
                    value = hexText,
                    onValueChange = { t ->
                        val cleaned = "#" + t.removePrefix("#").filter { it.isLetterOrDigit() }.take(6).uppercase()
                        hexText = cleaned
                        Themes.parseHex(cleaned)?.let { c -> hsv = toHsv(c); onChange(c) }
                    },
                    singleLine = true,
                    textStyle = TextStyle(color = look.paper, fontSize = 14.sp, fontFamily = look.monoFont),
                    cursorBrush = SolidColor(look.accent),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                )
            }
            Spacer(Modifier.width(10.dp))
            Text("hex", color = look.paper3, fontSize = 11.sp, fontFamily = look.monoFont)
        }
    }
}

private fun toHsv(colour: Int): FloatArray {
    val out = FloatArray(3)
    android.graphics.Color.colorToHSV(colour, out)
    return out
}

private fun fromHsv(hsv: FloatArray): Int = android.graphics.Color.HSVToColor(hsv)
