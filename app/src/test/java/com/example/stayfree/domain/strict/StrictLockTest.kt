package com.example.stayfree.domain.strict

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StrictLockTest {

    private val hour = 3_600_000L
    private val day = 24 * hour
    private val anchor = StrictAnchor(wallMs = 1_000_000L, elapsedMs = 50_000L, bootCount = 7)
    private val state = StrictState(anchor.wallMs, anchor.wallMs + day, anchor, 0, false)

    @Test
    fun `same boot - remaining follows the monotonic clock`() {
        val remaining = StrictLock.remainingMs(state, anchor.wallMs + hour, anchor.elapsedMs + hour, 7)
        assertEquals(day - hour, remaining)
    }

    @Test
    fun `same boot - moving the system clock forward does not end the lock`() {
        val remaining = StrictLock.remainingMs(state, anchor.wallMs + 30 * day, anchor.elapsedMs + hour, 7)
        assertEquals(day - hour, remaining)
    }

    @Test
    fun `same boot - clock jump is reported in both directions`() {
        assertTrue(StrictLock.clockJumped(anchor, anchor.wallMs + 2 * day, anchor.elapsedMs + hour, 7))
        assertTrue(StrictLock.clockJumped(anchor, anchor.wallMs - hour, anchor.elapsedMs + hour, 7))
    }

    @Test
    fun `same boot - drift inside the tolerance is not a jump`() {
        val wall = anchor.wallMs + hour + StrictLock.CLOCK_JUMP_TOLERANCE_MS
        assertFalse(StrictLock.clockJumped(anchor, wall, anchor.elapsedMs + hour, 7))
    }

    @Test
    fun `after reboot - the system clock is used and no jump is claimed`() {
        val wall = anchor.wallMs + 5 * hour
        assertEquals(day - 5 * hour, StrictLock.remainingMs(state, wall, 1_000L, 8))
        assertFalse(StrictLock.clockJumped(anchor, wall, 1_000L, 8))
    }

    @Test
    fun `after reboot - a clock set back never counts as earlier than the anchor`() {
        assertEquals(day, StrictLock.remainingMs(state, anchor.wallMs - 10 * day, 1_000L, 8))
    }

    @Test
    fun `monotonic clock behind the anchor is treated as a reboot`() {
        val wall = anchor.wallMs + hour
        assertEquals(day - hour, StrictLock.remainingMs(state, wall, anchor.elapsedMs - 1, 7))
    }

    @Test
    fun `extending appends to a running lock and starts an idle one from now`() {
        assertEquals(3 * day, StrictLock.extendedRemainingMs(2 * day, day))
        assertEquals(day, StrictLock.extendedRemainingMs(0L, day))
    }

    @Test
    fun `extending is capped and never shortens`() {
        val max = StrictLock.MAX_LOCK_MS
        assertEquals(max, StrictLock.extendedRemainingMs(max - hour, day))
        assertEquals(max + hour, StrictLock.extendedRemainingMs(max + hour, day))
    }

    @Test
    fun `expired or inactive lock has nothing remaining`() {
        assertEquals(0L, StrictLock.remainingMs(state, anchor.wallMs, anchor.elapsedMs + 2 * day, 7))
        assertEquals(0L, StrictLock.remainingMs(StrictState.OFF, anchor.wallMs, anchor.elapsedMs, 7))
    }
}
