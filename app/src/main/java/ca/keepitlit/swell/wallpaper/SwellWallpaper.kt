package ca.keepitlit.swell.wallpaper

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.os.Build
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.SurfaceHolder
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import ca.keepitlit.swell.theme.AppearancePrefs
import ca.keepitlit.swell.ui.Look
import ca.keepitlit.swell.ui.drawWall

/**
 * Swell's backdrop as the phone's wallpaper: the same [drawWall] the home screen paints, on the
 * lock screen, behind the app switcher, and in the moment the system launcher shows wallpaper
 * while handing over to Swell. It follows Swell's look (any accent or colour change
 * redraws it) and tells the system its colours so status-bar icons and system theming follow.
 *
 * It never animates: a frame is drawn when the surface appears or resizes, when the system asks
 * for one, and when the appearance changes. Between those it costs nothing.
 *
 * A third-party app cannot make itself the live wallpaper silently; [setIntent] opens the
 * system's preview, where the user confirms and picks home, or home and lock.
 */
class SwellWallpaper : WallpaperService() {

    override fun onCreateEngine(): Engine = WallEngine()

    inner class WallEngine : Engine(), SharedPreferences.OnSharedPreferenceChangeListener {

        private val prefs = AppearancePrefs(this@SwellWallpaper)
        private var look = Look(prefs.load())
        private val scope = CanvasDrawScope()

        override fun onCreate(holder: SurfaceHolder) {
            super.onCreate(holder)
            setOffsetNotificationsEnabled(false)
            prefs.listen(this)
        }

        override fun onDestroy() {
            prefs.unlisten(this)
            super.onDestroy()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            draw()
        }

        override fun onSurfaceRedrawNeeded(holder: SurfaceHolder) {
            super.onSurfaceRedrawNeeded(holder)
            draw()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            if (visible) draw()
        }

        override fun onSharedPreferenceChanged(sp: SharedPreferences, key: String?) {
            look = Look(prefs.load())
            draw()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) notifyColorsChanged()
        }

        override fun onComputeColors(): WallpaperColors? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return null
            // A small render, so the system derives its hints (dark text or not) the way it would
            // from any picture.
            val bmp = Bitmap.createBitmap(144, 320, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bmp)
            scope.draw(Density(0.5f), LayoutDirection.Ltr, Canvas(canvas), Size(bmp.width.toFloat(), bmp.height.toFloat())) { drawWall(look) }
            return WallpaperColors.fromBitmap(bmp).also { bmp.recycle() }
        }

        private fun draw() {
            val holder = surfaceHolder ?: return
            val frame = holder.surfaceFrame
            if (frame.width() <= 0 || frame.height() <= 0) return
            val canvas = try { holder.lockHardwareCanvas() } catch (e: Exception) { null } ?: holder.lockCanvas() ?: return
            try {
                scope.draw(
                    Density(resources.displayMetrics.density),
                    LayoutDirection.Ltr,
                    Canvas(canvas),
                    Size(frame.width().toFloat(), frame.height().toFloat()),
                ) { drawWall(look) }
            } finally {
                try { holder.unlockCanvasAndPost(canvas) } catch (e: Exception) { Log.w(TAG, "wallpaper post", e) }
            }
        }
    }

    companion object {
        private const val TAG = "Swell"

        fun component(context: Context) = ComponentName(context, SwellWallpaper::class.java)

        /** Is Swell's backdrop the phone's wallpaper (home; and lock where the platform can tell)? */
        fun active(context: Context): Boolean {
            val wm = WallpaperManager.getInstance(context)
            val home = wm.wallpaperInfo?.component == component(context)
            return home
        }

        /** Whether the lock screen shows it too; null when the platform cannot say. */
        fun activeOnLock(context: Context): Boolean? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return null
            val wm = WallpaperManager.getInstance(context)
            val lock = wm.getWallpaperInfo(WallpaperManager.FLAG_LOCK)
            // Lock unset means the lock screen mirrors home.
            return lock == null && active(context) || lock?.component == component(context)
        }

        /** The system's live-wallpaper preview for Swell, or the general chooser if a ROM lacks it. */
        fun setIntent(context: Context): Intent {
            val preview = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component(context))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (context.packageManager.resolveActivity(preview, 0) != null) return preview
            return Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
