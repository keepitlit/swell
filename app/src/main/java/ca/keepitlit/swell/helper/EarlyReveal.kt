package ca.keepitlit.swell.helper

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.Choreographer
import android.view.Gravity
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.View
import android.view.WindowManager
import android.widget.ImageView

/**
 * Puts Swell on screen the moment it is resumed, instead of when the system launcher's
 * fallback recents animation gets around to revealing it.
 *
 * On Quickstep-based launchers (the OnePlus one included) the home gesture or button from a
 * third-party HOME goes through the launcher's own RecentsActivity, which shows bare wallpaper
 * until its recents view finishes a fixed page snap. Swell's window is resumed, drawn and
 * top-resumed some 700 ms before it is let onto the screen, and nothing an app can do to its
 * tasks shortens that without costing phone features (home-type tasks are hidden from Recents
 * and cannot split). So Swell covers the wait instead: when it becomes the top resumed activity
 * while its window still lacks focus, it copies its own freshly committed frame into an
 * application-overlay window, which composites above the launcher's, keeps that copy in step
 * with every later frame, forwards touches to the real window so the page is live from the first
 * frame, and drops the copy one frame after the real window gains focus.
 *
 * Needs "display over other apps" (SYSTEM_ALERT_WINDOW); without it this is a no-op.
 */
class EarlyReveal(private val activity: Activity) {

    companion object {
        private const val TAG = "Swell"
        /** Upper bound on a cover, in case the focus signal never comes. */
        private const val SAFETY_MS = 2500L
        private const val FADE_MS = 90L
    }

    private val wm = activity.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var cover: ImageView? = null
    private var bitmap: Bitmap? = null
    /** True from top-resumed-without-focus until focus (or a lifecycle exit). */
    private var waiting = false
    private var since = 0L
    private var serial = 0
    private var copies = 0
    private val safety = Runnable { hide("safety") }
    /** Set by onPause, cleared by onResume: the launcher re-delivers HOME as pause+resume in one go. */
    private val pauseHide = Runnable { if (!resumed) hide("pause") }
    private var resumed = true

    val available: Boolean get() = Settings.canDrawOverlays(activity)

    fun onTopResumed(top: Boolean) {
        if (!top) { waiting = false; return }
        if (activity.hasWindowFocus() || activity.isFinishing || !available) return
        waiting = true
        since = SystemClock.elapsedRealtime()
        copies = 0
        val s = ++serial
        armCopy(s)
        activity.window.decorView.invalidate()
    }

    /**
     * The transition that brought Swell forward has ended: the launcher's window is gone and the
     * real one is composited. Window focus is not the signal: it can arrive while the launcher
     * still covers the screen. One more frame, then the cover goes.
     */
    fun onEnterAnimationComplete() {
        waiting = false
        if (cover == null) return
        Log.i(TAG, "reveal: enter animation complete +${SystemClock.elapsedRealtime() - since}ms")
        Choreographer.getInstance().postFrameCallback { handler.post { hide("entered") } }
    }

    fun onWindowFocus(has: Boolean) {
        if (has && cover != null) Log.i(TAG, "reveal: focus +${SystemClock.elapsedRealtime() - since}ms")
    }

    /**
     * A pause immediately followed by a resume (the launcher's second HOME delivery to the
     * singleTask instance) keeps the cover: tearing it down and re-adding it lands the new window
     * inside whatever transition is collecting at that instant, and the launcher then holds it
     * back until its own animation ends. Any other pause takes the cover down on the next turn of
     * the main loop, well before another app's window could appear.
     */
    fun onPause() {
        resumed = false
        waiting = false
        handler.post(pauseHide)
    }

    fun onResume() {
        resumed = true
        handler.removeCallbacks(pauseHide)
    }

    fun onStop() { waiting = false; hide("stop") }

    /** Copy the next committed frame of the real window into the cover; re-arm while covering. */
    private fun armCopy(s: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        activity.window.decorView.viewTreeObserver.registerFrameCommitCallback {
            if (s == serial && (waiting || cover != null)) handler.post { copy(s) }
        }
    }

    private fun copy(s: Int) {
        if (s != serial || !(waiting || cover != null)) return
        val win = activity.window
        val decor = win.decorView
        val w = decor.width
        val h = decor.height
        if (w <= 0 || h <= 0) { armCopy(s); return }
        val bmp = bitmap?.takeIf { it.width == w && it.height == h }
            ?: Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bitmap = it }
        val t = SystemClock.elapsedRealtime()
        try {
            PixelCopy.request(win, bmp, { result ->
                if (s != serial) return@request
                if (result != PixelCopy.SUCCESS) {
                    Log.w(TAG, "reveal: copy result=$result")
                    return@request
                }
                copies++
                val c = cover
                if (c == null) {
                    if (waiting) show(bmp, SystemClock.elapsedRealtime() - t)
                } else {
                    c.invalidate()
                }
                if (cover != null) armCopy(s)
            }, handler)
        } catch (e: IllegalArgumentException) {
            // No backing surface yet: try again on the next frame.
            Log.w(TAG, "reveal: copy skipped: ${e.message}")
            armCopy(s)
        }
    }

    private fun show(bmp: Bitmap, copyMs: Long) {
        val view = object : ImageView(activity) {
            override fun onTouchEvent(event: MotionEvent): Boolean =
                activity.window.decorView.dispatchTouchEvent(event)
        }
        view.setImageBitmap(bmp)
        view.scaleType = ImageView.ScaleType.FIT_XY
        // A short dissolve over whatever the launcher was drawing (the app mid-shrink, or wallpaper).
        view.alpha = 0f
        view.animate().alpha(1f).setDuration(FADE_MS).start()
        // With the system wallpaper behind Swell, the copy is transparent where the wallpaper shows;
        // asking for the wallpaper here makes this window its target, so it is drawn right under the
        // cover rather than under the launcher's animation.
        val wallpaper = activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or wallpaper,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            title = "Swell reveal"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) setFitInsetsTypes(0)
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        try {
            wm.addView(view, lp)
        } catch (e: RuntimeException) {
            Log.w(TAG, "reveal: overlay refused", e)
            return
        }
        cover = view
        handler.postDelayed(safety, SAFETY_MS)
        Log.i(TAG, "reveal: cover up +${SystemClock.elapsedRealtime() - since}ms (copy ${copyMs}ms)")
    }

    private fun hide(why: String) {
        handler.removeCallbacks(safety)
        val v = cover ?: return
        cover = null
        try { wm.removeViewImmediate(v) } catch (e: RuntimeException) { Log.w(TAG, "reveal: remove", e) }
        Log.i(TAG, "reveal: cover down ($why) +${SystemClock.elapsedRealtime() - since}ms, $copies copies")
    }
}
