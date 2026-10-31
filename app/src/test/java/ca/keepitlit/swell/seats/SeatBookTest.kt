package ca.keepitlit.swell.seats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeatBookTest {

    private fun book() = SeatBook(4, 4)

    @Test
    fun `a fresh book is one page plus the shelf`() {
        val b = book()
        assertEquals(20, b.total)
        assertEquals(1, b.pages)
        assertEquals(4, b.dockSeats)
    }

    @Test
    fun `first launch earns the first seat`() {
        val b = book()
        assertTrue(b.launch("a"))
        assertEquals(0, b.seatOf("a"))
        assertEquals(1, b.totalOpens)
    }

    @Test
    fun `a new app fills the next hole and does not leapfrog a hotter one`() {
        val b = book()
        repeat(5) { b.launch("a") }
        b.launch("b")
        assertEquals(0, b.seatOf("a"))
        assertEquals(1, b.seatOf("b"))
    }

    @Test
    fun `an app climbs past a colder occupant once it clears the gap`() {
        val b = book()
        b.launch("a")
        b.launch("b")
        assertEquals(1, b.seatOf("b"))
        repeat(3) { b.launch("b") }
        assertEquals(0, b.seatOf("b"))
        assertEquals(1, b.seatOf("a"))
    }

    @Test
    fun `the gap at twenty seats matches the prototype exactly`() {
        val b = book()
        // base 0.22 + prestige (20 - seat) * 0.035 + 0 evidence, hysteresis 1.
        assertEquals(0.22 + 20 * 0.035, b.gapNeeded(0), 1e-12)
        assertEquals(0.22 + 1 * 0.035, b.gapNeeded(19), 1e-12)
    }

    @Test
    fun `one launch carries a used app past every unused one, in a single move`() {
        val b = book()
        b.seatAll((1..100).map { "app%03d".format(it) })
        b.launch("app100")
        assertEquals(0, b.seatOf("app100"))
        // A second used app lines up right behind the first: ahead of all the unused, but the
        // gap keeps it from taking seat 0 on one launch.
        b.launch("app050")
        assertEquals(1, b.seatOf("app050"))
        assertEquals(2, b.seatOf("app001"))
        assertEquals(1, b.log.count { it.kind == SeatBook.Event.Kind.CLIMBED && it.key == "app050" })
    }

    @Test
    fun `settle lifts an app the old climb budget left behind, and is idempotent`() {
        val b = book()
        b.seatAll(listOf("a", "b", "c", "d"))
        b.appOrNew("d").apply { opens = 3; ema = 3.0 }
        assertTrue(b.settle())
        assertEquals(listOf("d", "a", "b", "c"), b.seats.filterNotNull())
        assertFalse(b.settle())
    }

    @Test
    fun `a pinned occupant is never passed`() {
        val b = book()
        b.launch("a")
        b.launch("b")
        assertEquals(true, b.togglePin("a"))
        repeat(50) { b.launch("b") }
        assertEquals(0, b.seatOf("a"))
        assertEquals(1, b.seatOf("b"))
    }

    @Test
    fun `pinning an unseated app is refused`() {
        val b = book()
        assertNull(b.togglePin("nobody"))
    }

    @Test
    fun `seatAll gives every app a seat, alphabetically, on as many pages as it takes`() {
        val b = book()
        val keys = (1..50).map { "app%02d".format(it) }
        assertTrue(b.seatAll(keys))
        for ((i, k) in keys.withIndex()) assertEquals(i, b.seatOf(k))
        // 50 apps: 4 on the shelf, 46 on pages of 16 -> 3 pages -> 52 seats.
        assertEquals(3, b.pages)
        assertEquals(52, b.total)
        assertFalse(b.seatAll(keys))
    }

    @Test
    fun `a newly installed app lands after everything already seated`() {
        val b = book()
        b.seatAll(listOf("b", "c"))
        b.launch("c")
        b.seatAll(listOf("a", "b", "c"))
        assertEquals(2, b.seatOf("a"))
    }

    @Test
    fun `changing the grid keeps the rank and re-chunks the pages`() {
        val b = book()
        val keys = (1..30).map { "app%02d".format(it) }
        b.seatAll(keys)
        b.configure(5, 5)
        for ((i, k) in keys.withIndex()) assertEquals(i, b.seatOf(k))
        assertEquals(5, b.dockSeats)
        assertEquals(25, b.pageSeats)
        // 30 apps: 5 shelf + 25 page = one page exactly.
        assertEquals(1, b.pages)
        assertEquals(30, b.total)
    }

    @Test
    fun `pruning frees the seat and closes the gap`() {
        val b = book()
        b.launch("a"); b.launch("b"); b.launch("c")
        assertTrue(b.prune(setOf("a", "c")))
        assertEquals(0, b.seatOf("a"))
        assertEquals(1, b.seatOf("c"))
        assertEquals(-1, b.seatOf("b"))
        assertNull(b.app("b"))
        assertFalse(b.prune(setOf("a", "c")))
    }

    @Test
    fun `pruning shrinks the book back to whole pages`() {
        val b = book()
        b.seatAll((1..40).map { "app$it" })
        assertEquals(3, b.pages)
        b.prune((1..10).map { "app$it" }.toSet())
        assertEquals(1, b.pages)
    }

    @Test
    fun `a day decays every score and grows tenure`() {
        val b = book()
        b.launch("a")
        val before = b.app("a")!!.ema
        b.tickDay()
        assertEquals(before * SeatBook.DAY_DECAY, b.app("a")!!.ema, 1e-12)
        assertEquals(1, b.app("a")!!.tenure)
        assertEquals(1, b.day)
    }

    @Test
    fun `stability is the share of recent launches that moved nothing`() {
        val b = book()
        repeat(10) { b.launch("a") }
        assertEquals(90, b.stability())
    }

    @Test
    fun `restore round trips the seats`() {
        val b = book()
        b.restore(day = 3, totalOpens = 9, seats = listOf("x", null, "y"), recent = listOf(1, 0, 0))
        assertEquals(0, b.seatOf("x"))
        assertEquals(1, b.seatOf("y"))
        assertEquals(3, b.day)
        assertEquals(9, b.totalOpens)
        assertEquals(67, b.stability())
        assertEquals(20, b.total)
    }

    @Test
    fun `hiding the shelf hands its apps to the first row of the first page, and back`() {
        val b = book()
        for (k in listOf("a", "b", "c", "d", "e", "f")) b.launch(k)
        val ranked = b.seats.filterNotNull()
        assertEquals(listOf("a", "b", "c", "d"), ranked.take(4))

        b.configure(4, 4, shelf = false)
        assertEquals(0, b.dockSeats)
        assertEquals(16, b.total)
        assertEquals(ranked, b.seats.filterNotNull())
        assertEquals(ranked.take(4), b.pageSeats(0).take(4))
        assertFalse(b.isOnShelf("a"))

        b.configure(4, 4, shelf = true)
        assertEquals(20, b.total)
        assertEquals(ranked, b.seats.filterNotNull())
        assertTrue(b.isOnShelf("a"))
    }

    @Test
    fun `shifts net each app's places over recent climbs`() {
        val b = book()
        b.seatAll(listOf("a", "b", "c"))
        b.launch("c") // passes b and a: c +2, b -1, a -1
        b.launch("b") // passes a only: b +1, a -1
        assertEquals(listOf("c" to 2, "a" to -2), b.shifts())
    }
}
