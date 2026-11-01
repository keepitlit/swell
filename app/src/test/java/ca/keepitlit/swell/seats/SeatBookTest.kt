package ca.keepitlit.swell.seats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeatBookTest {

    private fun book() = SeatBook()

    @Test
    fun `a fresh book has no seats`() {
        assertEquals(0, book().total)
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
        b.seatAll((1..20).map { "app$it" })
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
    fun `orderDock reorders what is shown and keeps the rest after it`() {
        val b = book()
        for (k in listOf("a", "b", "c", "d")) b.toggleDock(k)
        b.orderDock(listOf("c", "a", "b"))
        assertEquals(listOf("c", "a", "b", "d"), b.dock)
        b.orderDock(listOf("d", "gone"))
        assertEquals(listOf("d", "c", "a", "b"), b.dock)
    }

    @Test
    fun `seatAll gives every app a seat, alphabetically, and no more`() {
        val b = book()
        val keys = (1..50).map { "app%02d".format(it) }
        assertTrue(b.seatAll(keys))
        for ((i, k) in keys.withIndex()) assertEquals(i, b.seatOf(k))
        assertEquals(50, b.total)
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
    fun `pruning shrinks the book to the apps left`() {
        val b = book()
        b.seatAll((1..40).map { "app$it" })
        assertEquals(40, b.total)
        b.prune((1..10).map { "app$it" }.toSet())
        assertEquals(10, b.total)
    }

    @Test
    fun `a docked app keeps its seat and leaves the dock when uninstalled`() {
        val b = book()
        b.launch("a"); b.launch("b")
        assertTrue(b.toggleDock("b"))
        assertEquals(1, b.seatOf("b"))
        assertTrue(b.prune(setOf("a")))
        assertEquals(emptyList<String>(), b.dock)
        assertTrue(b.toggleDock("a"))
        assertFalse(b.toggleDock("a"))
        assertEquals(emptyList<String>(), b.dock)
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
        assertEquals(2, b.total)
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
