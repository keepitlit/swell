package ca.keepitlit.swell.seats

import kotlin.math.ln1p
import kotlin.math.max

/**
 * The home list, as a book of seats: seat 0 is the top line.
 *
 * A port of the prototype's model (`index.html`, the `enterOrBubble` / `launch` / `tickDay`
 * trio), generalised from twenty seats to **one seat for every installed app**. Pure Kotlin on
 * purpose: nothing here imports Android, so the JVM tests in `app/src/test` exercise exactly the
 * arithmetic the phone runs.
 *
 * ### The rules, from the prototype
 *
 * 1. **Every launch is a vote.** An app's `ema` gains 1 per launch; every other app's decays by
 *    0.4% per launch and by 3% per day.
 * 2. **A used app is always ahead of an unused one.** Among used apps, one passes the app above
 *    it only when its score beats the occupant's by a gap that grows with the seat's prestige and
 *    with how much the book has seen (the hysteresis slider scales that gap, so close scores do
 *    not swap on every launch). There is no per-launch climb budget: one launch carries an app as
 *    far up as its score allows. A pinned occupant is never passed. (The prototype budgeted 4/2/1
 *    seats per launch; on a 140-seat book that left a freshly used app crawling up behind apps
 *    nobody had opened, which is not "sorted by use".)
 * 3. **Every app has a seat, in rank order.** Nothing waits in the drawer: the drawer is
 *    alphabetical and every app is also on home.
 *
 * ### What changed with every app seated
 *
 * - **Capacity is the app count** ([fit]): no empty seats.
 * - **Prestige is normalised** to the prototype's own range: the CSS scaled the gap by
 *   `(TOTAL - seat) * 0.035` with TOTAL = 20, i.e. 0..0.7. With 140 seats that literal formula
 *   would demand a five-launch lead to take the top, so it is `(1 - seat/total) * 0.7` here:
 *   identical at 20 seats, sane at 200.
 * - **No climb budget** (see rule 2); [settle] repairs a book saved under the old one.
 */
class SeatBook {

    /** One app's standing. Mutable in place; the book is the only writer. */
    class App(
        val key: String,
        var opens: Int = 0,
        var ema: Double = 0.0,
        var pinned: Boolean = false,
        var tenure: Int = 0,
        /** The book [day] of the last launch, or -1 if it predates the record (or never happened). */
        var lastDay: Int = -1,
    )

    /**
     * What just moved. `other` is the app that was passed or evicted, when there was one; for a
     * climb, [passed] is every app it went past, nearest last. Events saved before [passed] was
     * recorded carry `other` alone.
     */
    class Event(
        val day: Int,
        val kind: Kind,
        val key: String,
        val other: String? = null,
        val passed: List<String> = listOfNotNull(other),
    ) {
        enum class Kind { EARNED, TOOK_LAST, CLIMBED, NOTE }
        /** For [Kind.NOTE] events, the text itself. */
        var note: String? = null
    }

    private val apps = HashMap<String, App>()

    /** Seat index -> app key, top line first. A null is a hole only while a move is under way. */
    val seats: MutableList<String?> = ArrayList()

    val total: Int get() = seats.size

    var day: Int = 0
        private set
    var totalOpens: Int = 0
        private set

    /** 0.4 loose .. 1.0 natural .. 2.2 locked. How expensive a seat is to steal. */
    var hysteresis: Double = 1.0
        set(value) { field = value.coerceIn(0.2, 4.0) }

    /** 1 for a launch that moved something, 0 for one that did not; the last [RECENT_WINDOW]. */
    val recentMoves = ArrayDeque<Int>()

    /** Newest first. */
    val log = ArrayDeque<Event>()

    /** Wall-clock millis of the last move per key, for the UI's "just moved" ring. */
    val lastMoved = HashMap<String, Long>()

    init { fit() }

    // ─── reads ────────────────────────────────────────────────────────────────────────────────

    fun app(key: String): App? = apps[key]

    fun appOrNew(key: String): App = apps.getOrPut(key) { App(key) }

    fun allApps(): Collection<App> = apps.values

    fun seatOf(key: String): Int = seats.indexOf(key)

    /** Percentage of recent launches that did NOT move anything. */
    fun stability(): Int {
        if (recentMoves.isEmpty()) return 0
        return Math.round(100.0 * (1.0 - recentMoves.sum().toDouble() / recentMoves.size)).toInt()
    }

    // ─── shape ────────────────────────────────────────────────────────────────────────────────

    /**
     * Give every key a seat, alphabetically for the ones that have none, so a fresh install or a
     * freshly installed app lands at the end of the book rather than in a drawer. Returns
     * whether anything changed.
     */
    fun seatAll(keysInOrder: List<String>): Boolean {
        val seated = seats.filterNotNull().toHashSet()
        val missing = keysInOrder.filter { it !in seated }
        if (missing.isEmpty()) return false
        for (k in missing) appOrNew(k)
        fit()
        var next = 0
        for (k in missing) {
            while (next < seats.size && seats[next] != null) next++
            if (next >= seats.size) seats.add(k) else seats[next] = k
        }
        fit()
        return true
    }

    /** Capacity = one seat per app. */
    private fun fit() {
        compact()
        val want = max(apps.size, seats.count { it != null })
        while (seats.size < want) seats.add(null)
        while (seats.size > want) seats.removeAt(seats.size - 1)
    }

    private fun compact() {
        val filled = seats.filterNotNull()
        for (i in seats.indices) seats[i] = filled.getOrNull(i)
    }

    // ─── the rules ────────────────────────────────────────────────────────────────────────────

    internal fun gapNeeded(seat: Int): Double {
        val evidence = ln1p(totalOpens.toDouble())
        val base = 0.22 * hysteresis
        val prestige = (1.0 - seat.toDouble() / max(1, total)) * PROTO_SEATS * 0.035 * hysteresis
        val evid = 0.055 * evidence * hysteresis
        return base + prestige + evid
    }

    /** Rule 2: may [me] take [seat] from [above]? */
    internal fun passes(me: App, above: App, seat: Int): Boolean {
        if (above.pinned) return false
        if (above.opens == 0) return me.opens > 0
        return me.ema >= above.ema + gapNeeded(seat)
    }

    /**
     * Let the app at [from] climb as far as rule 2 allows. Returns the seat it ended in; holes on
     * the way are closed by [compact] when it returns.
     */
    private fun climb(from: Int, passed: MutableList<String>? = null): Int {
        val key = seats[from] ?: return from
        val me = apps[key] ?: return from
        var idx = from
        while (idx > 0) {
            val aboveKey = seats[idx - 1]
            val above = aboveKey?.let { apps[it] }
            if (above == null) {
                seats[idx - 1] = key
                seats[idx] = null
                idx--
                continue
            }
            if (!passes(me, above, idx - 1)) break
            passed?.add(above.key)
            seats[idx - 1] = key
            seats[idx] = above.key
            idx--
        }
        return idx
    }

    /**
     * Give [key] a seat if it has none, then let it climb. Returns whether anything moved.
     */
    private fun enterOrBubble(key: String, nowMs: Long): Boolean {
        var moved = false
        var idx = seats.indexOf(key)
        appOrNew(key)
        if (idx < 0) {
            fit()
            var hole = seats.indexOf(null)
            if (hole == -1) { seats.add(null); hole = seats.size - 1 }
            seats[hole] = key
            compact()
            idx = seats.indexOf(key)
            moved = true
            push(Event(day, Event.Kind.EARNED, key))
        }
        val passed = ArrayList<String>()
        val to = climb(idx, passed)
        if (to < idx) {
            moved = true
            // One entry per launch, naming the app now directly below and every app passed.
            push(Event(day, Event.Kind.CLIMBED, key, seats.getOrNull(to + 1), passed))
            compact()
        }
        recentMoves.addLast(if (moved) 1 else 0)
        trimRecent()
        if (moved) lastMoved[key] = nowMs
        return moved
    }

    /**
     * Put every seated app where rule 2 would have it: each, in seat order, climbs as far as its
     * score allows. One pass is enough, because a launch only raises the launched app's score
     * and every other score decays uniformly, so a book can only ever hold apps sitting *lower*
     * than they deserve, which is exactly what the old per-launch climb budget left behind.
     * Returns whether anything moved.
     */
    fun settle(): Boolean {
        var moved = false
        for (i in 1 until seats.size) {
            if (seats[i] == null) continue
            if (climb(i) < i) moved = true
        }
        if (moved) compact()
        return moved
    }

    /** Record a launch. Returns whether the book changed. */
    fun launch(key: String, nowMs: Long = System.currentTimeMillis()): Boolean {
        val a = appOrNew(key)
        a.opens += 1
        a.ema += 1.0
        a.lastDay = day
        totalOpens += 1
        for (other in apps.values) if (other.key != key) other.ema *= LAUNCH_DECAY
        return enterOrBubble(key, nowMs)
    }

    /** A calendar day has passed. */
    fun tickDay() {
        day += 1
        for (a in apps.values) a.ema *= DAY_DECAY
        for (k in seats) if (k != null) apps[k]?.let { it.tenure += 1 }
    }

    /** Toggle a pin. Returns the new state, or null if the app has no seat. */
    fun togglePin(key: String): Boolean? {
        if (seats.indexOf(key) == -1) return null
        val a = appOrNew(key)
        a.pinned = !a.pinned
        return a.pinned
    }

    /**
     * Forget apps that are no longer installed. A vanished app's seat is freed and the book
     * closes up, so an uninstall never leaves a hole.
     */
    fun prune(liveKeys: Set<String>): Boolean {
        var changed = false
        val it = apps.keys.iterator()
        while (it.hasNext()) if (it.next() !in liveKeys) { it.remove(); changed = true }
        for (i in seats.indices) {
            val k = seats[i]
            if (k != null && k !in liveKeys) { seats[i] = null; changed = true }
        }
        if (changed) fit()
        return changed
    }

    fun reset() {
        apps.clear()
        seats.clear()
        day = 0
        totalOpens = 0
        recentMoves.clear()
        log.clear()
        lastMoved.clear()
        fit()
    }

    /**
     * Net places each app gained (+) or lost (−) over the last [moves] climbs: a climber gains one
     * per app it passed, and each app passed loses one. Biggest gain first; apps that came out
     * even are left out.
     */
    fun shifts(moves: Int = 6): List<Pair<String, Int>> {
        val net = LinkedHashMap<String, Int>()
        for (e in log.asSequence().filter { it.kind == Event.Kind.CLIMBED }.take(moves)) {
            net[e.key] = (net[e.key] ?: 0) + e.passed.size
            for (p in e.passed) net[p] = (net[p] ?: 0) - 1
        }
        return net.entries.filter { it.value != 0 }.sortedByDescending { it.value }.map { it.key to it.value }
    }

    fun note(text: String) {
        push(Event(day, Event.Kind.NOTE, "").also { it.note = text })
    }

    private fun push(e: Event) {
        log.addFirst(e)
        while (log.size > LOG_MAX) log.removeLast()
    }

    private fun trimRecent() {
        while (recentMoves.size > RECENT_WINDOW) recentMoves.removeFirst()
    }

    // ─── restore ──────────────────────────────────────────────────────────────────────────────

    /** Used by the store. Everything the constructor cannot set. */
    fun restore(day: Int, totalOpens: Int, seats: List<String?>, recent: List<Int>) {
        this.day = day
        this.totalOpens = totalOpens
        this.seats.clear()
        this.seats.addAll(seats)
        recentMoves.clear()
        recentMoves.addAll(recent.takeLast(RECENT_WINDOW))
        fit()
    }

    companion object {
        /** The prototype's seat count, the density its constants were tuned at. */
        const val PROTO_SEATS = 20
        const val RECENT_WINDOW = 24
        const val LOG_MAX = 40
        const val LAUNCH_DECAY = 0.996
        const val DAY_DECAY = 0.97
    }
}
