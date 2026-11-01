package ca.keepitlit.swell.data

import android.content.Context
import android.util.Log
import ca.keepitlit.swell.seats.SeatBook
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Persists the seat book as one small JSON file, written to a temp file and renamed so a process
 * death mid-write keeps the previous good copy.
 *
 * Also owns the calendar: [lastEpochDay] is the last day the book was ticked, so a launch after
 * a week away applies seven days of decay before anything is drawn.
 */
class SeatStore(private val context: Context) {

    private val file: File get() = File(context.filesDir, FILE_NAME)

    var lastEpochDay: Long = 0L

    fun load(): SeatBook {
        val book = SeatBook()
        val f = file
        if (!f.isFile) return book
        return try {
            val o = JSONObject(f.readText())
            val v = o.optInt("v", 1)
            if (v > VERSION) throw IllegalStateException("version $v")
            lastEpochDay = o.optLong("lastEpochDay", 0L)
            book.hysteresis = o.optDouble("hysteresis", 1.0)
            val apps = o.optJSONObject("apps") ?: JSONObject()
            for (key in apps.keys()) {
                val a = apps.getJSONObject(key)
                book.appOrNew(key).apply {
                    opens = a.optInt("o", 0)
                    ema = a.optDouble("e", 0.0)
                    tenure = a.optInt("t", 0)
                    lastDay = a.optInt("l", -1)
                }
            }
            val seatsArr = o.optJSONArray("seats") ?: JSONArray()
            val seats = ArrayList<String?>(seatsArr.length())
            for (i in 0 until seatsArr.length()) seats.add(if (seatsArr.isNull(i)) null else seatsArr.getString(i))
            val recentArr = o.optJSONArray("recent") ?: JSONArray()
            val recent = ArrayList<Int>(recentArr.length())
            for (i in 0 until recentArr.length()) recent.add(recentArr.getInt(i))
            book.restore(o.optInt("day", 0), o.optInt("totalOpens", 0), seats, recent)
            val dockArr = o.optJSONArray("dock") ?: JSONArray()
            for (i in 0 until dockArr.length()) book.dock.add(dockArr.getString(i))
            val logArr = o.optJSONArray("log") ?: JSONArray()
            for (i in logArr.length() - 1 downTo 0) {
                val e = logArr.getJSONObject(i)
                val kind = try { SeatBook.Event.Kind.valueOf(e.getString("k")) } catch (_: Exception) { continue }
                val other = e.optString("b").ifBlank { null }
                val passedArr = e.optJSONArray("p")
                val passed = if (passedArr == null) listOfNotNull(other) else List(passedArr.length()) { passedArr.getString(it) }
                val ev = SeatBook.Event(e.optInt("d", 0), kind, e.optString("a", ""), other, passed)
                ev.note = e.optString("n").ifBlank { null }
                book.log.addFirst(ev)
            }
            if (v < 2) {
                // Books saved under the per-launch climb budget hold used apps below unused ones.
                val moved = book.settle()
                book.note("Re-sorted by use")
                Log.i(TAG, "seat book v$v -> $VERSION: settle moved=$moved")
                write(serialize(book))
            }
            Log.i(TAG, "seat book loaded: ${book.allApps().size} apps, day ${book.day}")
            book
        } catch (e: Exception) {
            // Corrupt or from a future version. A home screen that refuses to open because a cache
            // file was half-written is not a home screen: start fresh.
            Log.w(TAG, "seat book unreadable, starting fresh: $e")
            f.delete()
            SeatBook()
        }
    }

    fun save(book: SeatBook) = write(serialize(book))

    /** Build the document on the thread that owns the book (the main thread); [write] can then go to IO. */
    fun serialize(book: SeatBook): String {
        val o = JSONObject()
        o.put("v", VERSION)
        o.put("day", book.day)
        o.put("totalOpens", book.totalOpens)
        o.put("hysteresis", book.hysteresis)
        o.put("lastEpochDay", lastEpochDay)
        val apps = JSONObject()
        for (a in book.allApps()) {
            apps.put(a.key, JSONObject().put("o", a.opens).put("e", a.ema).put("t", a.tenure).put("l", a.lastDay))
        }
        o.put("apps", apps)
        val seats = JSONArray()
        for (s in book.seats) seats.put(s ?: JSONObject.NULL)
        o.put("seats", seats)
        o.put("recent", JSONArray(book.recentMoves.toList()))
        o.put("dock", JSONArray(book.dock))
        val log = JSONArray()
        for (e in book.log) {
            val j = JSONObject().put("d", e.day).put("k", e.kind.name).put("a", e.key)
            e.other?.let { j.put("b", it) }
            if (e.passed.size > 1) j.put("p", JSONArray(e.passed))
            e.note?.let { j.put("n", it) }
            log.put(j)
        }
        o.put("log", log)
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
            Log.w(TAG, "seat book save failed: $e")
            tmp.delete()
        }
    }

    private companion object {
        const val TAG = "Swell"
        const val FILE_NAME = "seats.json"
        const val VERSION = 2
    }
}
