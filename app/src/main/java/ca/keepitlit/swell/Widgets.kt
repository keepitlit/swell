package ca.keepitlit.swell

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * System widgets, hosted on their own panel to the left of the home screens.
 *
 * The three-step dance every launcher does: allocate an id, **bind** it to a provider (silently
 * if the user has allowed this launcher before, else through the system's bind dialog), then
 * run the provider's **configure** activity if it has one. Both dialogs come back through
 * [onActivityResult], so this lives with the Activity rather than the model.
 */
class Widgets(private val activity: Activity) {

    class Slot(val id: Int, var rows: Int)

    val host = AppWidgetHost(activity, HOST_ID)
    val manager: AppWidgetManager = AppWidgetManager.getInstance(activity)
    private val sp = activity.getSharedPreferences("widgets", Context.MODE_PRIVATE)

    var slots by mutableStateOf<List<Slot>>(load())
        private set

    /** The id a dialog is currently out for, so a cancel with no data still frees it. */
    private var pending = -1

    /** Bumped whenever a slot's size changes, so hosted views re-measure. */
    var version by mutableStateOf(0)
        private set

    fun startListening() = host.startListening()
    fun stopListening() = try { host.stopListening() } catch (e: Exception) { Log.d(TAG, "stopListening: $e") }

    fun providers(): List<AppWidgetProviderInfo> = try {
        manager.installedProviders.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.loadLabel(activity.packageManager) })
    } catch (e: Exception) {
        Log.w(TAG, "installedProviders: $e"); emptyList()
    }

    fun info(id: Int): AppWidgetProviderInfo? = manager.getAppWidgetInfo(id)

    /** Begin adding [provider]. Finishes synchronously when no dialog is needed. */
    fun add(provider: AppWidgetProviderInfo) {
        val id = host.allocateAppWidgetId()
        pending = id
        val bound = try {
            manager.bindAppWidgetIdIfAllowed(id, provider.profile, provider.provider, null)
        } catch (e: Exception) {
            Log.w(TAG, "bind: $e"); false
        }
        if (bound) {
            configureOrFinish(id)
        } else {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.provider)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, provider.profile)
            @Suppress("DEPRECATION")
            activity.startActivityForResult(intent, REQ_BIND)
        }
    }

    private fun configureOrFinish(id: Int) {
        val info = manager.getAppWidgetInfo(id)
        if (info?.configure != null) {
            try {
                host.startAppWidgetConfigureActivityForResult(activity, id, 0, REQ_CONFIGURE, null)
                return
            } catch (e: Exception) {
                Log.w(TAG, "configure: $e")
            }
        }
        finish(id)
    }

    private fun finish(id: Int) {
        pending = -1
        val info = manager.getAppWidgetInfo(id)
        if (info == null) { host.deleteAppWidgetId(id); return }
        val density = activity.resources.displayMetrics.density
        val rows = Math.ceil((info.minHeight / density) / ROW_DP.toDouble()).toInt().coerceIn(1, 6)
        slots = slots + Slot(id, rows)
        persist()
    }

    /** Both dialogs return here. Returns true if the result was ours. */
    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != REQ_BIND && requestCode != REQ_CONFIGURE) return false
        val id = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1).takeIf { it != null && it != -1 } ?: pending
        pending = -1
        if (resultCode != Activity.RESULT_OK) {
            if (id != -1 && slots.none { it.id == id }) host.deleteAppWidgetId(id)
            return true
        }
        if (id == -1) return true
        if (requestCode == REQ_BIND) configureOrFinish(id) else finish(id)
        return true
    }

    fun resize(id: Int, rows: Int) {
        slots.firstOrNull { it.id == id }?.rows = rows.coerceIn(1, 8)
        version++
        persist()
    }

    fun move(id: Int, delta: Int) {
        val list = slots.toMutableList()
        val i = list.indexOfFirst { it.id == id }
        val j = i + delta
        if (i == -1 || j !in list.indices) return
        list[i] = list[j].also { list[j] = list[i] }
        slots = list
        persist()
    }

    fun remove(id: Int) {
        slots = slots.filter { it.id != id }
        host.deleteAppWidgetId(id)
        persist()
    }

    private fun load(): List<Slot> {
        val raw = sp.getString(KEY, "") ?: ""
        val bound = try { host.appWidgetIds.toHashSet() } catch (_: Exception) { null }
        return raw.split(';').mapNotNull { part ->
            val bits = part.split(':')
            val id = bits.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
            if (bound != null && id !in bound) return@mapNotNull null
            Slot(id, bits.getOrNull(1)?.toIntOrNull() ?: 2)
        }
    }

    private fun persist() {
        sp.edit().putString(KEY, slots.joinToString(";") { "${it.id}:${it.rows}" }).apply()
    }

    companion object {
        const val TAG = "Swell"
        const val HOST_ID = 0x0F7E
        const val REQ_BIND = 0x0F01
        const val REQ_CONFIGURE = 0x0F02
        /** One widget row, in dp. */
        const val ROW_DP = 88
        const val KEY = "slots"
    }
}
