package ca.keepitlit.swell.data

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File

/** A person's own name and icon for one app. A null field keeps the app's own. */
data class AppEdit(val name: String? = null, val icon: CustomIcon? = null) {
    val isEmpty: Boolean get() = name == null && icon == null
}

/**
 * The names and icons people chose, by app key, in one small JSON file beside the seat book.
 * Kept when an app is uninstalled, so a reinstall comes back as it was.
 */
class AppEdits(private val context: Context) {

    private val file: File get() = File(context.filesDir, FILE_NAME)

    fun load(): Map<String, AppEdit> {
        val f = file
        if (!f.isFile) return emptyMap()
        return try {
            val o = JSONObject(f.readText())
            buildMap {
                for (key in o.keys()) {
                    val e = o.getJSONObject(key)
                    val name = e.optString("n").ifBlank { null }
                    val glyph = e.optString("i").ifBlank { null }
                    val icon = glyph?.let { CustomIcon(it, e.optInt("c", DEFAULT_COLOUR)) }
                    AppEdit(name, icon).takeIf { !it.isEmpty }?.let { put(key, it) }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "app edits unreadable: $e")
            emptyMap()
        }
    }

    /** Build on the main thread; [write] can then go to IO. */
    fun serialize(edits: Map<String, AppEdit>): String {
        val o = JSONObject()
        for ((key, e) in edits) {
            val j = JSONObject()
            e.name?.let { j.put("n", it) }
            e.icon?.let { j.put("i", it.glyph).put("c", it.colour) }
            o.put(key, j)
        }
        return o.toString()
    }

    fun write(json: String) {
        val target = file
        val tmp = File(target.parentFile, "$FILE_NAME.tmp")
        try {
            tmp.writeText(json)
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
        } catch (e: Exception) {
            Log.w(TAG, "app edits save failed: $e")
            tmp.delete()
        }
    }

    private companion object {
        const val TAG = "Swell"
        const val FILE_NAME = "edits.json"
        const val DEFAULT_COLOUR = 0xFFF4F1EA.toInt()
    }
}
