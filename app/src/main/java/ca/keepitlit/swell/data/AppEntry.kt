package ca.keepitlit.swell.data

import android.os.UserHandle

/**
 * One seatable thing, for one user: a launchable activity, or a pinned shortcut.
 *
 * The user is part of the identity because the same package genuinely appears twice on a device
 * with a work profile, and the two are different apps as far as launching is concerned. Getting
 * this wrong means a launcher that opens the personal Slack when you tapped the work one.
 *
 * ### Pinned shortcuts
 *
 * When [shortcutId] is non-null this is a shortcut the user pinned through some app's "add to
 * home screen": a browser's web app, a chat app's conversation, a maps app's saved place. It
 * carries no activity of its own: it is launched with `LauncherApps.startShortcut` and its icon
 * comes from `getShortcutIconDrawable`, so every consumer has to branch on it. Everything else
 * about it (a seat, a score, a pin) is identical to an app's, which is the point: the seat
 * book never learns that shortcuts exist.
 */
data class AppEntry(
    val packageName: String,
    val activityClassName: String,
    val user: UserHandle,
    val label: String,
    /** Non-null for a pinned shortcut; null for a launchable activity. */
    val shortcutId: String? = null,
) {
    val isShortcut: Boolean get() = shortcutId != null

    /**
     * `pkg|activity|user`, the same shape an earlier launcher's caches and favourites used, and for a shortcut
     * `pkg|@id|user`.
     *
     * The `@` is what keeps the two namespaces apart. An activity class name is a Java binary
     * name, so it can never begin with `@`; without a marker a shortcut whose id happened to match
     * an activity name would silently inherit that activity's seat and score.
     */
    val key: String = if (shortcutId != null) {
        "$packageName|@$shortcutId|${user.hashCode()}"
    } else {
        "$packageName|$activityClassName|${user.hashCode()}"
    }

    /**
     * A stable 64-bit identity for the simulation and the layout snapshot.
     *
     * The engine indexes nodes by `Long`, not by string, because the hot loops must not touch
     * objects. This must be stable across process deaths (it is what reconciles a restored map
     * against a fresh app list), so it is derived from the key rather than from anything
     * runtime-assigned like a list position.
     *
     * 64-bit FNV-1a rather than `String.hashCode()`: the latter is 32-bit, and a birthday
     * collision across a few hundred apps is not fanciful at that width. A collision here would
     * silently give two apps the same node.
     */
    val id: Long = fnv1a64(key)

    companion object {
        fun fnv1a64(s: String): Long {
            var hash = -0x340d631b7bdddcdbL      // 14695981039346656037
            for (element in s) {
                hash = hash xor element.code.toLong()
                hash *= 0x100000001b3L           // 1099511628211
            }
            return hash
        }
    }
}
