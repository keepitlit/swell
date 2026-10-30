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
import ca.keepitlit.swell.data.AppEntry
import ca.keepitlit.swell.data.AppRepository
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
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    var appearance by mutableStateOf(appearancePrefs.load())
        private set

    // Home is one list, so the book's pages are only its capacity: no shelf, plain 4 x 4 chunks.
    val book: SeatBook = store.load().also { it.configure(4, 4, shelf = false) }

    var apps by mutableStateOf<List<AppEntry>>(emptyList())
        private set

    var appsByKey: Map<String, AppEntry> = emptyMap()
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
        apps = list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
        appsByKey = list.associateBy { it.key }
        // Every installed app has a seat; the drawer is only a different order of the same list.
        val pruned = book.prune(appsByKey.keys)
        val seated = book.seatAll(apps.map { it.key })
        if (pruned || seated) save()
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

    fun togglePin(key: String) {
        val label = label(key)
        when (book.togglePin(key)) {
            null -> showToast("Pin from the page: earn a seat first")
            true -> showToast("$label is pinned")
            false -> showToast("$label can move again")
        }
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
        book.note("Started over. The list reorders as you open apps.")
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
