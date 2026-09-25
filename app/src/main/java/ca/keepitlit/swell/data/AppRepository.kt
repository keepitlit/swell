package ca.keepitlit.swell.data

import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import ca.keepitlit.swell.BuildConfig

/**
 * The live list of launchable activities.
 *
 * Adapted from an earlier launcher's `MainViewModel` sweep, which is the part of that file worth keeping: it
 * enumerates **every profile**, not just the current user, so work-profile and Private Space apps
 * appear and can be launched as the right user.
 *
 * ### Coalescing, and why 150 ms
 *
 * A single install fires `onPackageAdded`, then `onPackagesAvailable`, then often `onPackageChanged`
 * in quick succession. Sweeping on each is three full `getActivityList` passes across every profile
 * for one event, and on this map each sweep also re-files, re-rasterises labels and reconciles the
 * simulation. The settling window swallows the burst.
 *
 * The **first** sweep skips the delay: there is nothing to coalesce before the list has ever been
 * filled, and 150 ms on the cold-start path is 150 ms of empty map.
 *
 * ### Pinned shortcuts
 *
 * The sweep also collects the shortcuts **this launcher has pinned** ([sweepShortcuts]). The
 * system is the record, not a file of our own: accepting a `PinItemRequest` is what makes a
 * shortcut pinned-by-Swell, and `getShortcuts(FLAG_MATCH_PINNED)` reads that back. So a shortcut
 * survives reboots and process deaths for free, and one whose host app was uninstalled simply
 * stops being returned, which [ca.keepitlit.swell.seats.SeatBook.prune] then treats exactly like
 * an uninstalled app.
 */
class AppRepository(private val context: Context) {

    private val launcherApps =
        context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps

    private val userManager =
        context.getSystemService(Context.USER_SERVICE) as UserManager

    private val handler = Handler(Looper.getMainLooper())

    /** Called on the main thread whenever the app list changes. */
    var onChanged: ((List<AppEntry>) -> Unit)? = null

    @Volatile
    var apps: List<AppEntry> = emptyList()
        private set

    @Volatile
    private var activityInfoByKey: Map<String, LauncherActivityInfo> = emptyMap()

    @Volatile
    private var shortcutInfoByKey: Map<String, ShortcutInfo> = emptyMap()

    private val pending = Runnable { sweepAndPublish() }

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageAdded(packageName: String?, user: UserHandle?) = schedule()
        override fun onPackageRemoved(packageName: String?, user: UserHandle?) = schedule()
        override fun onPackageChanged(packageName: String?, user: UserHandle?) = schedule()
        override fun onPackagesAvailable(
            packageNames: Array<out String>?,
            user: UserHandle?,
            replacing: Boolean,
        ) = schedule()

        override fun onPackagesUnavailable(
            packageNames: Array<out String>?,
            user: UserHandle?,
            replacing: Boolean,
        ) = schedule()
    }

    fun start() {
        launcherApps.registerCallback(callback, handler)
    }

    fun stop() {
        handler.removeCallbacks(pending)
        try {
            launcherApps.unregisterCallback(callback)
        } catch (e: Exception) {
            Log.d(TAG, "unregister: $e")
        }
    }

    private fun schedule() {
        handler.removeCallbacks(pending)
        handler.postDelayed(pending, COALESCE_MS)
    }

    /** The metadata returned by the sweep, reused by icon loading to avoid another package query. */
    fun activityInfo(app: AppEntry): LauncherActivityInfo? = activityInfoByKey[app.key]

    /** As [activityInfo], for a pinned shortcut: what `getShortcutIconDrawable` needs. */
    fun shortcutInfo(app: AppEntry): ShortcutInfo? = shortcutInfoByKey[app.key]

    /**
     * Enumerate every launchable activity across every profile.
     *
     * Blocking, and genuinely slow (measured in hundreds of milliseconds on a device with a few
     * hundred packages), so callers run it off the main thread after the first frame.
     */
    fun sweep(): List<AppEntry> {
        val started = System.nanoTime()
        val out = ArrayList<AppEntry>(128)
        val infoByKey = HashMap<String, LauncherActivityInfo>(128)
        for (profile in userManager.userProfiles) {
            val activities = try {
                launcherApps.getActivityList(null, profile)
            } catch (e: Exception) {
                // A profile can be locked or disappearing. Skip it rather than losing every
                // other profile's apps to one exception.
                Log.d(TAG, "getActivityList failed for $profile: $e")
                continue
            } ?: continue

            for (activity in activities) {
                val pkg = activity.applicationInfo.packageName
                // Swell does not list itself. Tapping it would be a no-op at best.
                if (pkg == BuildConfig.APPLICATION_ID) continue
                val entry = AppEntry(
                    packageName = pkg,
                    activityClassName = activity.componentName.className,
                    user = profile,
                    // Resolving a label can reach the resource system; do it exactly once.
                    label = activity.label?.toString().orEmpty().ifBlank { pkg },
                )
                out.add(entry)
                infoByKey[entry.key] = activity
            }
        }
        val shortcutInfo = HashMap<String, ShortcutInfo>(8)
        val activityCount = out.size
        sweepShortcuts(out, shortcutInfo)

        apps = out
        activityInfoByKey = infoByKey
        shortcutInfoByKey = shortcutInfo
        Log.i(
            TAG,
            "list rebuilt ($activityCount activities, ${shortcutInfo.size} pinned shortcuts) " +
                "in ${(System.nanoTime() - started) / 1_000_000}ms",
        )
        return out
    }

    /**
     * Append the shortcuts this launcher has pinned, across every profile.
     *
     * `getShortcuts` is granted only to the **default home app**, and only while it holds that
     * role. Swell is normally the launcher, but it is not always: during setup, or right after the
     * user has switched away, the call throws `SecurityException`. That is an ordinary state, not
     * a fault: a launcher that crashed on it would be unopenable exactly when the user was trying
     * to make it the default again, so it is logged at debug and the sweep returns apps only.
     */
    private fun sweepShortcuts(out: MutableList<AppEntry>, infoByKey: MutableMap<String, ShortcutInfo>) {
        val query = LauncherApps.ShortcutQuery().setQueryFlags(
            LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED,
        )
        for (profile in userManager.userProfiles) {
            val shortcuts = try {
                launcherApps.getShortcuts(query, profile)
            } catch (e: SecurityException) {
                // Not the default launcher (yet). Nothing to report per profile.
                Log.d(TAG, "getShortcuts unavailable for $profile: $e")
                continue
            } catch (e: Exception) {
                Log.d(TAG, "getShortcuts failed for $profile: $e")
                continue
            } ?: continue

            for (shortcut in shortcuts) {
                if (!shortcut.isEnabled) continue
                val label = shortcut.longLabel?.toString().orEmpty()
                    .ifBlank { shortcut.shortLabel?.toString().orEmpty() }
                    .ifBlank { shortcut.`package` }
                val entry = AppEntry(
                    packageName = shortcut.`package`,
                    // A shortcut has no activity of its own; the id is its identity.
                    activityClassName = "",
                    user = shortcut.userHandle ?: profile,
                    label = label,
                    shortcutId = shortcut.id,
                )
                out.add(entry)
                infoByKey[entry.key] = shortcut
            }
        }
    }

    /**
     * Drop a pinned shortcut.
     *
     * Unpinning is "set the pinned set to everything except this one" (there is no remove call),
     * so the current set has to be re-read and filtered. Returns whether the system accepted it.
     */
    fun unpinShortcut(app: AppEntry): Boolean {
        val id = app.shortcutId ?: return false
        return try {
            val query = LauncherApps.ShortcutQuery()
                .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
                .setPackage(app.packageName)
            val keep = (launcherApps.getShortcuts(query, app.user) ?: emptyList())
                .map { it.id }
                .filter { it != id }
            launcherApps.pinShortcuts(app.packageName, keep, app.user)
            true
        } catch (e: Exception) {
            Log.w(TAG, "unpin ${app.packageName}/$id failed: $e")
            false
        }
    }

    private fun sweepAndPublish() {
        val list = sweep()
        onChanged?.invoke(list)
    }

    private companion object {
        const val TAG = "Swell"
        const val COALESCE_MS = 150L
    }
}
