package ca.keepitlit.swell.helper

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import kotlin.math.roundToInt

/**
 * The detent under a stepping list: one short tick per line, full strength when lines go by
 * slowly and fainter the faster they go. Compose's haptics have no strength, so this drives the
 * vibrator itself, as touch feedback: it follows the system's touch-feedback switch and, from
 * Android 13, its touch intensity.
 */
class Ticker(context: Context) {
    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= 31) context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else context.getSystemService(Vibrator::class.java)
    private val resolver = context.contentResolver
    private val primitive = Build.VERSION.SDK_INT >= 30 &&
        vibrator?.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_TICK) == true
    private var last = 0L

    fun tick() {
        val v = vibrator?.takeIf { it.hasVibrator() } ?: return
        val now = SystemClock.uptimeMillis()
        val gap = now - last
        // Closer than this, the motor cannot tell two ticks apart.
        if (gap < MIN_GAP_MS) return
        last = now
        if (Settings.System.getInt(resolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) == 0) return
        val strength = (gap.toFloat() / SLOW_MS).coerceIn(FAINT, 1f)
        val effect = when {
            primitive -> VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, strength).compose()
            v.hasAmplitudeControl() -> VibrationEffect.createOneShot(ONE_SHOT_MS, (strength * 255).roundToInt().coerceIn(1, 255))
            Build.VERSION.SDK_INT >= 29 -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
            else -> VibrationEffect.createOneShot(ONE_SHOT_MS, VibrationEffect.DEFAULT_AMPLITUDE)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).build())
        }
    }

    private companion object {
        const val MIN_GAP_MS = 12L
        /** A line this far apart or slower ticks at full strength. */
        const val SLOW_MS = 160f
        /** The faintest tick, at the fastest lines. */
        const val FAINT = 0.3f
        /** Under one swing of a phone's motor (about 6 ms), so it lands as a tap, not a buzz. */
        const val ONE_SHOT_MS = 3L
    }
}
