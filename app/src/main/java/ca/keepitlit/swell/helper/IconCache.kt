package ca.keepitlit.swell.helper

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.LauncherActivityInfo
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Process
import android.util.Log
import android.util.LruCache
import ca.keepitlit.swell.data.AppEntry
import kotlin.math.max

/**
 * App icons, rasterised once and kept, plus each icon's dominant colour.
 *
 * Adapted from an earlier launcher's `IconCache`. Two differences, both because the consumer is a `Canvas`
 * rather than Compose:
 *
 * - it yields `android.graphics.Bitmap`, not `ImageBitmap`, since the map blits with
 *   `Canvas.drawBitmap`;
 * - it extracts a **dominant colour** per icon. That is what the level-of-detail tiers draw when a
 *   node is too small or too far to be worth a full icon: hundreds of colour-bucketed points in a
 *   handful of batched `drawPoints` calls instead of hundreds of blits. It is also what makes the
 *   cold-start path work: after a process death the layout snapshot has the colour but the icons
 *   are not loaded yet, so frame 1 paints recognisable blobs rather than empty squares.
 *
 * ## One shape, one size
 *
 * Every icon is drawn into the system icon shape at full size: adaptive icons as they are, any
 * other picture fitted by [IconShape]. The work-profile badge goes on afterwards, through
 * `PackageManager.getUserBadgedIcon`, so reshaping never crops it; an unbadged work-profile icon
 * is a launcher lying about which user an app will open as.
 *
 * ## Threading
 *
 * [load] is blocking and belongs on `Dispatchers.IO`: `getActivityList` plus `getBadgedIcon` plus
 * a draw is tens of milliseconds per icon on a cold list. [peek] is the cheap synchronous path the
 * draw loop uses; it never blocks and never loads.
 */
object IconCache {

    private const val TAG = "Swell"

    /**
     * Bounded by count, not bytes. A few hundred icons at 48 dp on a 3x panel is ~83 KB each, so a
     * full cache is single-digit megabytes, and a count bound cannot be defeated by one oversized
     * adaptive icon.
     */
    private const val MAX_ICONS = 400

    private val bitmaps = LruCache<String, Bitmap>(MAX_ICONS)

    /**
     * Dominant colours, kept **separately and unbounded**.
     *
     * Deliberately not evicted with the bitmap: a colour is 4 bytes, there are at most a few
     * hundred, and it is what a node falls back to when its icon has been evicted or not yet
     * loaded. Tying its lifetime to the bitmap's would mean the fallback disappears at exactly the
     * moment it is needed.
     */
    private val colours = HashMap<String, Int>()

    fun peek(key: String): Bitmap? = bitmaps.get(key)

    fun peekColour(key: String): Int? = colours[key]

    fun load(
        context: Context,
        app: AppEntry,
        sizePx: Int,
        activityInfo: LauncherActivityInfo? = null,
        shortcutInfo: ShortcutInfo? = null,
    ): Bitmap? {
        bitmaps.get(app.key)?.let { return it }

        val drawable =
            if (app.shortcutId != null) shortcutIcon(context, app, shortcutInfo)
            else launcherIcon(context, app, activityInfo)
        if (drawable == null) return null
        val shaped = drawable.shaped(sizePx) ?: return null
        val bitmap = if (app.shortcutId == null) badged(context, app, shaped, sizePx) else shaped
        bitmaps.put(app.key, bitmap)
        colours[app.key] = dominantColourOf(bitmap)
        return bitmap
    }

    /** Drop rasterised icons on a package change or a density change. Colours survive. */
    fun clearBitmaps() = bitmaps.evictAll()

    /**
     * A pinned shortcut's icon.
     *
     * `getShortcutIconDrawable` already composites the host app's badge onto the shortcut's own
     * image, which is the whole reason a shortcut is recognisable at icon size: the glyph says
     * *which* conversation or page, the badge says which app opens it.
     *
     * It needs the live [ShortcutInfo], and unlike an activity there is no cheap way to re-find
     * one from a key alone (a second `getShortcuts` call is another cross-profile system query on
     * the icon path), so a missing one falls back to the host app's plain launcher icon rather
     * than to nothing. A slightly wrong icon beats an empty seat.
     */
    private fun shortcutIcon(context: Context, app: AppEntry, shortcutInfo: ShortcutInfo?): Drawable? {
        val launcherApps =
            context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps ?: return null
        val density = context.resources.displayMetrics.densityDpi
        if (shortcutInfo != null) {
            try {
                launcherApps.getShortcutIconDrawable(shortcutInfo, density)?.let { return it }
            } catch (e: Exception) {
                Log.d(TAG, "no shortcut icon for ${app.packageName}/${app.shortcutId}: $e")
            }
        }
        return try {
            launcherApps.getActivityList(app.packageName, app.user)
                ?.firstOrNull()
                ?.getIcon(density)
        } catch (e: Exception) {
            Log.d(TAG, "no fallback icon for ${app.packageName}: $e")
            null
        }
    }

    private fun launcherIcon(
        context: Context,
        app: AppEntry,
        activityInfo: LauncherActivityInfo?,
    ): Drawable? {
        activityInfo?.let {
            return try {
                it.getIcon(context.resources.displayMetrics.densityDpi)
            } catch (e: Exception) {
                Log.d(TAG, "no icon for ${app.packageName}/${app.user.hashCode()}: $e")
                null
            }
        }
        val launcherApps =
            context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps ?: return null
        val component = ComponentName(app.packageName, app.activityClassName)
        return try {
            launcherApps.getActivityList(app.packageName, app.user)
                ?.firstOrNull { it.componentName == component }
                ?.getIcon(context.resources.displayMetrics.densityDpi)
        } catch (e: Exception) {
            // A profile going away mid-sweep throws rather than returning empty. Not worth a
            // warning per app; the node falls back to its dominant colour.
            Log.d(TAG, "no icon for ${app.packageName}/${app.user.hashCode()}: $e")
            null
        }
    }

    /** The work-profile (or Private Space) badge on a shaped icon; personal icons pass through. */
    private fun badged(context: Context, app: AppEntry, shaped: Bitmap, sizePx: Int): Bitmap {
        if (app.user == Process.myUserHandle()) return shaped
        return try {
            context.packageManager
                .getUserBadgedIcon(BitmapDrawable(context.resources, shaped), app.user)
                .rasterise(sizePx) ?: shaped
        } catch (e: Exception) {
            Log.d(TAG, "no badge for ${app.packageName}/${app.user.hashCode()}: $e")
            shaped
        }
    }

    /** This icon in the system icon shape, filling [sizePx]. */
    private fun Drawable.shaped(sizePx: Int): Bitmap? {
        if (sizePx <= 0) return null
        if (this is AdaptiveIconDrawable) return rasterise(sizePx)
        val source = (this as? BitmapDrawable)?.bitmap?.takeIf { !it.isRecycled }
            ?: rasterise(if (intrinsicWidth > 0 && intrinsicHeight > 0) max(intrinsicWidth, intrinsicHeight) else sizePx)
            ?: return null
        val pixels = IntArray(source.width * source.height)
        source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
        val fit = IconShape.read(source.width, source.height, pixels) ?: return null
        return try {
            val out = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            // An empty adaptive icon's mask is the system shape, scaled to its bounds.
            val mask = AdaptiveIconDrawable(ColorDrawable(0), ColorDrawable(0))
                .apply { setBounds(0, 0, sizePx, sizePx) }
                .iconMask
            canvas.clipPath(mask)
            fit.fill?.let { canvas.drawColor(it) }
            val target = if (fit.tile) sizePx.toFloat() else sizePx * IconShape.GLYPH_SCALE
            val matrix = Matrix().apply {
                postTranslate(-fit.cx, -fit.cy)
                postScale(target / fit.side, target / fit.side)
                postTranslate(sizePx / 2f, sizePx / 2f)
            }
            canvas.drawBitmap(source, matrix, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
            out
        } catch (e: Exception) {
            Log.w(TAG, "icon reshape failed: $e")
            null
        }
    }

    private fun Drawable.rasterise(sizePx: Int): Bitmap? {
        if (sizePx <= 0) return null
        (this as? BitmapDrawable)?.bitmap?.let { source ->
            if (source.width == sizePx && source.height == sizePx && !source.isRecycled) return source
        }
        return try {
            val out = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            setBounds(0, 0, sizePx, sizePx)
            draw(Canvas(out))
            out
        } catch (e: Exception) {
            Log.w(TAG, "icon rasterise failed: $e")
            null
        }
    }

    /**
     * The average of an icon's opaque pixels, sampled on an 8x8 grid.
     *
     * Sampled rather than averaged over every pixel: 64 reads is enough to characterise an icon and
     * costs nothing, where a full scan of a 144x144 bitmap is 20,000 reads per app.
     *
     * Nearly-transparent pixels are skipped: adaptive icons are mostly padding, and including it
     * drags every colour toward whatever the background happens to be, which would make the
     * level-of-detail blobs a field of identical grey dots.
     */
    private fun dominantColourOf(bitmap: Bitmap): Int {
        val steps = 8
        var r = 0L
        var g = 0L
        var b = 0L
        var count = 0
        val stepX = (bitmap.width / steps).coerceAtLeast(1)
        val stepY = (bitmap.height / steps).coerceAtLeast(1)
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val p = bitmap.getPixel(x, y)
                val a = (p ushr 24) and 0xFF
                if (a > 128) {
                    r += (p shr 16) and 0xFF
                    g += (p shr 8) and 0xFF
                    b += p and 0xFF
                    count++
                }
                x += stepX
            }
            y += stepY
        }
        if (count == 0) return 0xFF888888.toInt()
        return (0xFF shl 24) or
            ((r / count).toInt() shl 16) or
            ((g / count).toInt() shl 8) or
            (b / count).toInt()
    }
}
