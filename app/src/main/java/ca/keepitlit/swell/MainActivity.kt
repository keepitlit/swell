package ca.keepitlit.swell

import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.WindowManager
import ca.keepitlit.swell.theme.Appearance
import ca.keepitlit.swell.theme.ClockPos
import ca.keepitlit.swell.theme.ThemeId
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import ca.keepitlit.swell.helper.EarlyReveal
import ca.keepitlit.swell.ui.SwellApp

/**
 * The HOME activity. Manifest carries the launcher lifecycle (singleTask, empty taskAffinity,
 * excluded from recents) carried over from two earlier launchers of mine; it is the part that is
 * easy to get subtly wrong and expensive to debug.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        const val TAG = "Swell"
        /** Startup timing, debug builds only; release logcat stays quiet. */
        private fun timing(msg: () -> String) { if (BuildConfig.DEBUG) Log.i(TAG, msg()) }
    }

    private lateinit var model: SwellModel
    private lateinit var widgets: Widgets
    private lateinit var reveal: EarlyReveal

    /** Bumped on every HOME press that re-enters this instance; overlays close, pager recentres. */
    private var homeSignal by mutableIntStateOf(0)

    /** adb only: bumped to open the all-apps sheet for a screenshot. */
    private var openDrawerSignal by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // The surface is translucent from the start. The hardware renderer decides once, when it is
        // created, whether it must clear each frame; a window that turns translucent later (the
        // wallpaper toggle) would keep every previous frame's pixels under the new one.
        window.setFormat(PixelFormat.TRANSLUCENT)

        model = SwellModel(applicationContext)
        widgets = Widgets(this)
        reveal = EarlyReveal(this)
        // The window background is the one colour the theme owns; the persisted shell colour
        // (or the wallpaper) is applied before the first frame so a cold start never flashes the XML default.
        applyBackdrop(model.appearance)
        model.start()

        setContent {
            SwellApp(model = model, widgets = widgets, homeSignal = homeSignal, openDrawerSignal = openDrawerSignal)
            val a = model.appearance
            LaunchedEffect(a.wallpaper, a.palette.shell) { applyBackdrop(a) }
            // First frame after each (re)start: the number that answers "how long until home is visible".
            LaunchedEffect(startSerial) {
                withFrameNanos { }
                timing { "frame after start #$startSerial: +${SystemClock.elapsedRealtime() - startedAt}ms" }
            }
        }
    }

    /** The window's own backdrop: the shell colour, or the system wallpaper showing through. */
    private fun applyBackdrop(a: Appearance) {
        if (a.wallpaper) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            window.setBackgroundDrawable(ColorDrawable(a.palette.shell))
        }
    }

    private var startSerial by mutableIntStateOf(0)
    private var startedAt = 0L

    override fun onStart() {
        startedAt = SystemClock.elapsedRealtime()
        startSerial++
        super.onStart()
        val t = SystemClock.elapsedRealtime()
        widgets.startListening()
        timing { "onStart #$startSerial: super=${t - startedAt}ms widgets=${SystemClock.elapsedRealtime() - t}ms" }
    }

    override fun onStop() {
        reveal.onStop()
        widgets.stopListening()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        reveal.onResume()
        val t = SystemClock.elapsedRealtime()
        model.onResume()
        timing { "onResume: +${t - startedAt}ms since start, model=${SystemClock.elapsedRealtime() - t}ms" }
    }

    override fun onPause() {
        reveal.onPause()
        super.onPause()
    }

    override fun onTopResumedActivityChanged(isTopResumedActivity: Boolean) {
        super.onTopResumedActivityChanged(isTopResumedActivity)
        timing { "topResumed=$isTopResumedActivity focus=${hasWindowFocus()} +${SystemClock.elapsedRealtime() - startedAt}ms" }
        reveal.onTopResumed(isTopResumedActivity)
    }

    override fun onEnterAnimationComplete() {
        super.onEnterAnimationComplete()
        timing { "enterAnimationComplete +${SystemClock.elapsedRealtime() - startedAt}ms" }
        reveal.onEnterAnimationComplete()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        timing { "focus=$hasFocus +${SystemClock.elapsedRealtime() - startedAt}ms" }
        reveal.onWindowFocus(hasFocus)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        homeSignal++
        debugHooks(intent)
    }

    /**
     * adb-driven hooks, so the phone can be driven without a finger:
     *   am start -n ca.keepitlit.swell/.MainActivity --es launch pkg/.Activity
     *   am start -n ca.keepitlit.swell/.MainActivity --es appearance "home=list,shelf=off,clock=bottom,wallpaper=on,theme=mono"
     *
     * Debug builds only. MainActivity is exported (it has to be, as HOME), so in a release build
     * any app could otherwise hand the launcher an intent that opens an arbitrary package.
     */
    private fun debugHooks(intent: Intent) {
        if (!BuildConfig.DEBUG) return
        intent.getStringExtra("appearance")?.let { spec ->
            model.updateAppearance { start ->
                var a = start
                for (kv in spec.split(',')) {
                    val parts = kv.split('=')
                    if (parts.size != 2) continue
                    val v = parts[1].trim().lowercase()
                    a = when (parts[0].trim().lowercase()) {
                        "shelf" -> a.copy(showShelf = v == "on")
                        "home" -> a.copy(list = v == "list")
                        "scroll" -> a.copy(stepScroll = v == "step")
                        "clock" -> a.copy(clock = ClockPos.entries.firstOrNull { it.name.lowercase() == v } ?: a.clock)
                        "wallpaper" -> a.copy(wallpaper = v == "on")
                        "theme" -> a.copy(theme = ThemeId.of(v.uppercase()))
                        "cols" -> a.copy(cols = v.toIntOrNull()?.coerceIn(3, 8) ?: a.cols)
                        "rows" -> a.copy(rows = v.toIntOrNull()?.coerceIn(2, 10) ?: a.rows)
                        "icon" -> a.copy(iconSize = v.toIntOrNull()?.coerceIn(ca.keepitlit.swell.theme.AppearancePrefs.ICON_MIN, ca.keepitlit.swell.theme.AppearancePrefs.ICON_MAX) ?: a.iconSize)
                        "outline" -> a.copy(outline = v.toIntOrNull()?.coerceIn(ca.keepitlit.swell.theme.AppearancePrefs.OUTLINE_MIN, ca.keepitlit.swell.theme.AppearancePrefs.OUTLINE_MAX) ?: a.outline)
                        "drawer" -> a.also { if (v == "open") openDrawerSignal++ }
                        else -> a
                    }
                }
                a
            }
            Log.i(TAG, "debug appearance: $spec -> ${model.appearance}")
        }
        intent.getStringExtra("launch")?.let { flat ->
            val c = android.content.ComponentName.unflattenFromString(flat) ?: return@let
            val entry = model.appsByKey.values.firstOrNull {
                it.packageName == c.packageName && it.activityClassName == c.className
            }
            if (entry == null) Log.w(TAG, "debug launch: no entry for $flat")
            else Log.i(TAG, "debug launch $flat -> ${model.launch(entry, null)}")
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (widgets.onActivityResult(requestCode, resultCode, data)) return
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onDestroy() {
        model.stop()
        super.onDestroy()
    }
}
