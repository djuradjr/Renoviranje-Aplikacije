package com.example.stayfree.domain.cleanfeed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrollBudgetTest {

    private val t0 = 1_000_000_000L
    private val min = 60_000L

    @Test fun `posts add up under the limit`() {
        val s = ScrollBudget.addPosts(ScrollSession(), 5, 20, t0)
        assertEquals(5, s.posts)
        assertFalse(s.isLocked(t0))
        assertEquals(15, ScrollBudget.remaining(s, 20, t0))
    }

    @Test fun `reaching the limit locks for the pause`() {
        val s = ScrollBudget.addPosts(ScrollSession(posts = 18, lastActiveAt = t0), 3, 20, t0)
        assertTrue(s.isLocked(t0))
        assertEquals(t0 + ScrollBudget.PAUSE_MS, s.lockedUntil)
        assertEquals(0, ScrollBudget.remaining(s, 20, t0))
    }

    @Test fun `scrolling while locked changes nothing`() {
        val locked = ScrollSession(20, t0, t0 + ScrollBudget.PAUSE_MS)
        assertEquals(locked, ScrollBudget.addPosts(locked, 4, 20, t0 + min))
    }

    @Test fun `pause over starts a fresh session`() {
        val locked = ScrollSession(20, t0, t0 + ScrollBudget.PAUSE_MS)
        val after = ScrollBudget.current(locked, t0 + ScrollBudget.PAUSE_MS)
        assertEquals(ScrollSession(), after)
    }

    @Test fun `closing and reopening soon keeps the count`() {
        val s = ScrollSession(posts = 15, lastActiveAt = t0)
        assertEquals(15, ScrollBudget.current(s, t0 + 5 * min).posts)
    }

    @Test fun `long idle starts over`() {
        val s = ScrollSession(posts = 15, lastActiveAt = t0)
        assertEquals(0, ScrollBudget.current(s, t0 + ScrollBudget.PAUSE_MS).posts)
    }

    @Test fun `clock moved back never locks longer than one pause`() {
        val s = ScrollSession(20, t0, t0 + ScrollBudget.PAUSE_MS)
        val back = t0 - 10 * 60 * min
        assertEquals(back + ScrollBudget.PAUSE_MS, ScrollBudget.current(s, back).lockedUntil)
    }
}
