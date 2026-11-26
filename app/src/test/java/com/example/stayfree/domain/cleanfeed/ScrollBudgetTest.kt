package com.example.stayfree.domain.cleanfeed

import com.example.stayfree.domain.cleanfeed.ScrollBudget.Day
import com.example.stayfree.domain.cleanfeed.ScrollBudget.Rules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrollBudgetTest {

    private val t0 = 1_000_000_000L
    private val min = 60_000L
    private val rules = Rules(limit = 20, pauseMinutes = 30)
    private val pause = 30 * min
    private val today = Day("2026-11-26", t0 + 12 * 60 * min)
    private val tomorrow = Day("2026-11-27", t0 + 36 * 60 * min)

    private fun session(posts: Int = 0, last: Long = 0L, until: Long = 0L, n: Int = 0, dayLock: Boolean = false) =
        ScrollSession(posts, last, until, today.key, n, dayLock)

    @Test fun `posts add up under the limit`() {
        val s = ScrollBudget.addPosts(session(), 5, rules, today, t0)
        assertEquals(5, s.posts)
        assertFalse(s.isLocked(t0))
        assertEquals(15, ScrollBudget.remaining(s, rules, today, t0))
    }

    @Test fun `reaching the limit locks for the pause`() {
        val s = ScrollBudget.addPosts(session(posts = 18, last = t0, n = 1), 3, rules, today, t0)
        assertTrue(s.isLocked(t0))
        assertEquals(t0 + pause, s.lockedUntil)
        assertFalse(s.dayLocked)
        assertEquals(0, ScrollBudget.remaining(s, rules, today, t0))
    }

    @Test fun `the chosen pause is used`() {
        val hour = rules.copy(pauseMinutes = 60)
        val s = ScrollBudget.addPosts(session(posts = 19, last = t0, n = 1), 1, hour, today, t0)
        assertEquals(t0 + 60 * min, s.lockedUntil)
    }

    @Test fun `a pause shorter than 30 minutes is never used`() {
        assertEquals(30 * min, rules.copy(pauseMinutes = 5).pauseMs)
    }

    @Test fun `scrolling while locked changes nothing`() {
        val locked = session(20, t0, t0 + pause, n = 1)
        assertEquals(locked, ScrollBudget.addPosts(locked, 4, rules, today, t0 + min))
    }

    @Test fun `pause over starts a fresh session and keeps the day count`() {
        val locked = session(20, t0, t0 + pause, n = 2)
        val after = ScrollBudget.current(locked, rules, today, t0 + pause)
        assertEquals(session(n = 2), after)
    }

    @Test fun `closing and reopening soon keeps the count`() {
        val s = session(posts = 15, last = t0, n = 1)
        assertEquals(15, ScrollBudget.current(s, rules, today, t0 + 5 * min).posts)
    }

    @Test fun `long idle starts over`() {
        val s = session(posts = 15, last = t0, n = 1)
        assertEquals(0, ScrollBudget.current(s, rules, today, t0 + pause).posts)
    }

    @Test fun `clock moved back never locks longer than one pause`() {
        val s = session(20, t0, t0 + pause, n = 1)
        val back = t0 - 10 * 60 * min
        assertEquals(back + pause, ScrollBudget.current(s, rules, today, back).lockedUntil)
    }

    @Test fun `the first post of a session counts the session`() {
        val s = ScrollBudget.addPosts(session(n = 1), 1, rules, today, t0)
        assertEquals(2, s.sessionsToday)
        assertEquals(2, ScrollBudget.addPosts(s, 1, rules, today, t0).sessionsToday)
    }

    @Test fun `the last session of the day ends until tomorrow`() {
        val three = rules.copy(sessionsPerDay = 3)
        val s = ScrollBudget.addPosts(session(posts = 19, last = t0, n = 3), 1, three, today, t0)
        assertTrue(s.dayLocked)
        assertEquals(today.nextStartsAt, s.lockedUntil)
    }

    @Test fun `no new session once the day is used up`() {
        val three = rules.copy(sessionsPerDay = 3)
        val idle = session(posts = 5, last = t0, n = 3)
        val later = ScrollBudget.current(idle, three, today, t0 + pause)
        assertTrue(later.isLocked(t0 + pause))
        assertTrue(later.dayLocked)
    }

    @Test fun `a new day lifts the day lock and resets the count`() {
        val locked = session(20, t0, today.nextStartsAt, n = 3, dayLock = true)
        val next = ScrollBudget.current(locked, rules.copy(sessionsPerDay = 3), tomorrow, today.nextStartsAt + min)
        assertFalse(next.isLocked(today.nextStartsAt + min))
        assertEquals(0, next.sessionsToday)
        assertEquals(tomorrow.key, next.day)
    }

    @Test fun `raising the daily maximum lifts the day lock`() {
        val locked = session(10, t0, today.nextStartsAt, n = 1, dayLock = true)
        val after = ScrollBudget.current(locked, rules.copy(sessionsPerDay = 2), today, t0 + min)
        assertFalse(after.isLocked(t0 + min))
        assertEquals(1, after.sessionsToday)
    }

    @Test fun `no daily maximum means sessions never end the day`() {
        val s = ScrollBudget.addPosts(session(posts = 19, last = t0, n = 40), 1, rules, today, t0)
        assertFalse(s.dayLocked)
    }
}
