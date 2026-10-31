package ca.keepitlit.swell.seats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LeastUsedTest {

    @Test
    fun `a launch records its day`() {
        val b = SeatBook(4, 4)
        b.launch("a")
        repeat(3) { b.tickDay() }
        assertEquals(3, b.use(listOf("a")).daysSince)
    }

    @Test
    fun `an app never opened has no last day`() {
        val b = SeatBook(4, 4)
        b.seatAll(listOf("a"))
        val u = b.use(listOf("a"))
        assertEquals(0, u.opens)
        assertNull(u.daysSince)
    }

    @Test
    fun `a package sums its keys and takes the latest launch`() {
        val b = SeatBook(4, 4)
        b.launch("p|One")
        b.tickDay(); b.tickDay()
        b.launch("p|Two")
        val u = b.use(listOf("p|One", "p|Two"))
        assertEquals(2, u.opens)
        assertEquals(0, u.daysSince)
    }

    @Test
    fun `never opened comes first, longest watched first, then the lowest score`() {
        val b = SeatBook(4, 4)
        b.seatAll(listOf("old", "new"))
        b.appOrNew("old").tenure = 40
        b.appOrNew("new").tenure = 2
        repeat(5) { b.launch("busy") }
        b.launch("rare")
        val keys = listOf("busy", "new", "rare", "old")
        val order = keys.sortedWith(compareBy(LEAST_USED_FIRST) { b.use(listOf(it)) })
        assertEquals(listOf("old", "new", "rare", "busy"), order)
    }
}
