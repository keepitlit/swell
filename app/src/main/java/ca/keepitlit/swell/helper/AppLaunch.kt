package ca.keepitlit.swell.helper

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.os.UserHandle
import android.util.Log
import ca.keepitlit.swell.data.AppEntry

/**
 * Starting an app, through `LauncherApps` rather than a plain `Intent`.
 *
 * `startMainActivity(component, user, sourceBounds, opts)` is the only route that launches **as the
 * right user**. An `Intent` with `FLAG_ACTIVITY_NEW_TASK` opens the personal copy regardless of
 * which profile the icon came from, which on a device with a work profile means the launcher
 * quietly opens the wrong app.
 *
 * Adapted from an earlier launcher's `AppLaunch`, minus the toast plumbing: the caller decides what a
 * failure looks like on the map.
 */
object AppLaunch {

    private const val TAG = "Swell"

    sealed interface Result {
        object Ok : Result

        /** The activity is gone: uninstalled, or disabled, since the list was built. */
        object NotFound : Result

        data class Refused(val reason: String) : Result
    }

    /**
     * @param sourceBounds where on screen the icon was, so the system can animate from it
     */
    fun start(
        context: Context,
        app: AppEntry,
        sourceBounds: Rect? = null,
        options: android.os.Bundle? = null,
    ): Result =
        if (app.shortcutId != null) {
            startShortcut(context, app, sourceBounds, options)
        } else {
            start(context, app.packageName, app.activityClassName, app.user, sourceBounds, options)
        }

    /**
     * Open a pinned shortcut.
     *
     * `startShortcut` is granted to the **default home app** only, which is the same condition
     * that let the shortcut be swept in the first place; if the role was lost between the sweep
     * and the tap it throws `SecurityException` and the caller reports a refusal rather than
     * silently doing nothing.
     *
     * A shortcut whose host app has been uninstalled, or that the app itself disabled, throws
     * `IllegalStateException`. That is [Result.NotFound]: the caller re-sweeps, the shortcut stops
     * being returned, and the seat book prunes it, so a dead icon removes itself on the one tap
     * that discovers it is dead.
     */
    private fun startShortcut(
        context: Context,
        app: AppEntry,
        sourceBounds: Rect?,
        options: android.os.Bundle?,
    ): Result {
        val id = app.shortcutId ?: return Result.NotFound
        val launcher =
            context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
                ?: return Result.Refused("no LauncherApps service")
        return try {
            launcher.startShortcut(app.packageName, id, sourceBounds, options, app.user)
            Result.Ok
        } catch (e: IllegalStateException) {
            Log.w(TAG, "shortcut ${app.packageName}/$id is gone: $e")
            Result.NotFound
        } catch (e: SecurityException) {
            Log.w(TAG, "shortcut launch refused for ${app.packageName}/$id: $e")
            Result.Refused("SecurityException")
        } catch (e: Exception) {
            Log.w(TAG, "shortcut launch failed for ${app.packageName}/$id: $e")
            Result.Refused(e.javaClass.simpleName)
        }
    }

    fun start(
        context: Context,
        packageName: String,
        activityClassName: String?,
        user: UserHandle,
        sourceBounds: Rect? = null,
        options: android.os.Bundle? = null,
    ): Result {
        val launcher =
            context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
                ?: return Result.Refused("no LauncherApps service")

        val activities = try {
            launcher.getActivityList(packageName, user)
        } catch (e: Exception) {
            // A profile can go away between the sweep and the tap.
            return Result.Refused("getActivityList: ${e.javaClass.simpleName}")
        } ?: return Result.NotFound

        // Prefer the exact activity recorded at sweep time. Fall back to the package's own
        // launcher entry if that class has vanished across an update, which is common enough that
        // failing outright would read as the launcher being broken.
        val named = !activityClassName.isNullOrBlank() &&
            activities.any { it.componentName.className == activityClassName }

        val component = when {
            named -> ComponentName(packageName, activityClassName!!)
            activities.isEmpty() -> return Result.NotFound
            else -> ComponentName(packageName, activities.last().name)
        }

        return try {
            launcher.startMainActivity(component, user, sourceBounds, options)
            Result.Ok
        } catch (e: SecurityException) {
            Log.w(TAG, "launch refused for $component: $e")
            Result.Refused("SecurityException")
        } catch (e: Exception) {
            Log.w(TAG, "launch failed for $component: $e")
            Result.Refused(e.javaClass.simpleName)
        }
    }

    /**
     * Open the system's App info screen for [app].
     *
     * Through `LauncherApps` for the same reason [start] is: `ACTION_APPLICATION_DETAILS_SETTINGS`
     * with a `package:` uri always lands on the personal profile's copy, so on a device with a work
     * profile the settings screen would describe a different app than the one held.
     *
     * This has no counterpart in the prototype. On Android it is where uninstall, permissions and
     * notification settings live, and a launcher that cannot reach it sends the user off to find
     * Settings by hand, which is the one thing a home screen exists to prevent.
     */
    fun appInfo(context: Context, app: AppEntry): Result {
        val launcher =
            context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
                ?: return Result.Refused("no LauncherApps service")
        // A pinned shortcut records no activity of its own, and an empty class name is not a
        // component the details screen can resolve. Its "App info" is the host app's, so borrow
        // that package's own launcher entry.
        val className = app.activityClassName.ifBlank {
            try {
                launcher.getActivityList(app.packageName, app.user)?.firstOrNull()?.name
            } catch (e: Exception) {
                Log.d(TAG, "no activity to anchor app info for ${app.packageName}: $e")
                null
            }.orEmpty()
        }
        return try {
            launcher.startAppDetailsActivity(
                ComponentName(app.packageName, className),
                app.user,
                null,
                null,
            )
            Result.Ok
        } catch (e: Exception) {
            Log.w(TAG, "app info failed for ${app.packageName}: $e")
            Result.Refused(e.javaClass.simpleName)
        }
    }
}
