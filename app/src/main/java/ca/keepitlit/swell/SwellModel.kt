package ca.keepitlit.swell

import android.content.Context
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ca.keepitlit.swell.data.AppEdit
import ca.keepitlit.swell.data.AppEdits
import ca.keepitlit.swell.data.AppEntry
import ca.keepitlit.swell.data.AppRepository
import ca.keepitlit.swell.data.CustomIcon
import ca.keepitlit.swell.data.SeatStore
import ca.keepitlit.swell.helper.AppLaunch
import ca.keepitlit.swell.seats.SeatBook
import ca.keepitlit.swell.theme.Appearance
import ca.keepitlit.swell.theme.AppearancePrefs
import java.util.concurrent.Executors
import kotlin.math.min

/**
 * The one object the UI reads. Owns the seat book, the app list and the appearance, and is the
 * only thing that mutates any of them, always on the main thread. Persistence goes to a single
 * IO thread with a document that was serialised on the main thread, so the writer never sees the
 * book mid-mutation.
 */
class SwellModel(private val context: Context) {

    val repo = AppRepository(context)
    private val store = SeatStore(context)
    private val appearancePrefs = AppearancePrefs(context)
    private val editStore = AppEdits(context)
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    var appearance by mutableStateOf(appearancePrefs.load())
        private set

    val book: SeatBook = store.load()

    var apps by mutableStateOf<List<AppEntry>>(emptyList())
        private set

    var appsByKey: Map<String, AppEntry> = emptyMap()
        private set

    /** Each app as the phone names and draws it, before [edits]. */
    private var ownApps: Map<String, AppEntry> = emptyMap()

    /** The names and icons people chose, by key. */
    var edits by mutableStateOf(editStore.load())
        private set

    /** Bumped on every change to the book. Composables read it to subscribe. */
    var version by mutableIntStateOf(0)
        private set

    var toast by mutableStateOf<String?>(null)
        private set
    private var toastSerial = 0

    fun start() {
        live = this
        repo.onChanged = { onApps(it) }
        repo.start()
        io.execute {
            val list = repo.sweep()
            main.post { onApps(list) }
        }
    }

    fun stop() {
        if (live === this) live = null
        repo.stop()
    }

    private fun onApps(list: List<AppEntry>) {
        ownApps = list.associateBy { it.key }
        applyEdits()
        // Every installed app has a seat; the drawer is only a different order of the same list.
        val pruned = book.prune(appsByKey.keys)
        val seated = book.seatAll(apps.map { it.key })
        if (pruned || seated) save()
        version++
    }

    /** Rebuild [apps] from the phone's list with each chosen name and icon in place. */
    private fun applyEdits() {
        val list = ownApps.values.map { e ->
            val edit = edits[e.key] ?: return@map e
            e.copy(label = edit.name ?: e.label, customIcon = edit.icon)
        }
        apps = list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
        appsByKey = list.associateBy { it.key }
    }

    /** The app as the phone has it, before any edit: its own name and icon. */
    fun own(key: String): AppEntry? = ownApps[key]

    /** A blank or unchanged [name] goes back to the app's own. */
    fun rename(key: String, name: String) {
        val own = ownApps[key]?.label
        val n = name.trim().takeIf { it.isNotEmpty() && it != own }
        edit(key) { it.copy(name = n) }
    }

    /** A null [icon] goes back to the app's own. */
    fun setIcon(key: String, icon: CustomIcon?) = edit(key) { it.copy(icon = icon) }

    private fun edit(key: String, change: (AppEdit) -> AppEdit) {
        val next = change(edits[key] ?: AppEdit())
        edits = if (next.isEmpty) edits - key else edits + (key to next)
        val json = editStore.serialize(edits)
        io.execute { editStore.write(json) }
        applyEdits()
        version++
    }

    fun resweep() {
        io.execute { val list = repo.sweep(); main.post { onApps(list) } }
    }

    /** Apply the calendar. Called on every resume; cheap when nothing has changed. */
    fun onResume() {
        val today = System.currentTimeMillis() / DAY_MS
        when {
            store.lastEpochDay == 0L -> { store.lastEpochDay = today; save() }
            today > store.lastEpochDay -> {
                val n = min(today - store.lastEpochDay, 30L).toInt()
                repeat(n) { book.tickDay() }
                store.lastEpochDay = today
                version++
                save()
                Log.i(TAG, "ticked $n day(s)")
            }
        }
    }

    /** Open an app. The vote is only cast if the launch actually went through. */
    fun launch(entry: AppEntry, bounds: Rect?): Boolean {
        return when (val r = AppLaunch.start(context, entry, bounds)) {
            AppLaunch.Result.Ok -> {
                book.launch(entry.key)
                version++
                save()
                true
            }
            AppLaunch.Result.NotFound -> {
                showToast("${entry.label} is gone")
                resweep()
                false
            }
            is AppLaunch.Result.Refused -> {
                showToast("Couldn't open ${entry.label}")
                false
            }
        }
    }

    /**
     * Unpin a shortcut and let it leave the book.
     *
     * The re-sweep is what actually removes it: once the system stops returning it as pinned,
     * `onApps` prunes its seat like any uninstalled app, so there is one removal path rather than
     * two that could disagree.
     */
    fun removeShortcut(entry: AppEntry) {
        if (repo.unpinShortcut(entry)) {
            showToast("${entry.label} removed")
            resweep()
        } else {
            showToast("Couldn't remove ${entry.label}")
        }
    }

    fun toggleDock(key: String) {
        showToast(if (book.toggleDock(key)) "${label(key)} is in the dock" else "${label(key)} is back in the list")
        version++
        save()
    }

    /** Swap a docked app with its neighbour above ([up]) or below. [shown] is the dock as drawn, bottom first. */
    fun moveDock(key: String, up: Boolean, shown: List<String>) {
        val i = shown.indexOf(key)
        val j = if (up) i + 1 else i - 1
        if (i < 0 || j !in shown.indices) return
        orderDock(shown.toMutableList().apply { this[i] = this[j]; this[j] = key })
    }

    /** [order] is the dock as drawn, bottom first. */
    fun orderDock(order: List<String>) {
        book.orderDock(order)
        version++
        save()
    }

    fun setHysteresis(value: Double) {
        book.hysteresis = value
        version++
        save()
    }

    fun resetPage() {
        book.reset()
        book.seatAll(apps.map { it.key })
        book.note("Started over")
        version++
        save()
    }

    fun updateAppearance(change: (Appearance) -> Appearance) {
        appearance = change(appearance)
        appearancePrefs.save(appearance)
    }

    fun label(key: String): String =
        appsByKey[key]?.label ?: key.substringBefore('|').substringAfterLast('.')

    fun showToast(text: String) {
        toast = text
        val serial = ++toastSerial
        main.postDelayed({ if (toastSerial == serial) toast = null }, 1600)
    }

    private fun save() {
        val json = store.serialize(book)
        io.execute { store.write(json) }
    }

    companion object {
        const val TAG = "Swell"
        const val DAY_MS = 24L * 60 * 60 * 1000

        /**
         * The running model, for the one caller that is not the UI: [ca.keepitlit.swell.PinShortcutActivity].
         *
         * Pinning a shortcut installs no package, so it fires none of the `LauncherApps` callbacks
         * that normally drive a re-sweep; without a nudge a newly pinned icon would not appear
         * until something else changed. The activity that accepts the pin is a separate, momentary
         * activity, so it needs a way to reach whatever model is live.
         *
         * Safe to hold statically: the model is built with the **application** context, so this
         * pins no activity; there is exactly one, because HOME is `singleTask`; and it is null
         * whenever Swell is not running, which is the case [start] already handles by sweeping.
         */
        @Volatile
        var live: SwellModel? = null
            private set
    }
}
