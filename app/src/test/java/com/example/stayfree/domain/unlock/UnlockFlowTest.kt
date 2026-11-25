package com.example.stayfree.domain.unlock

import com.example.stayfree.domain.unlock.UnlockFlow.Phase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnlockFlowTest {

    private val start = UnlockFlow.State()

    @Test
    fun `grant happens only when the breath completes`() {
        val breathing = UnlockFlow.confirm(start, 1_000L)
        val early = UnlockFlow.tick(breathing, 5_999L)
        assertEquals(Phase.BREATHING, early.phase)
        assertFalse(UnlockFlow.grants(breathing, early))
        val done = UnlockFlow.tick(breathing, 6_000L)
        assertEquals(Phase.UNLOCKED, done.phase)
        assertTrue(UnlockFlow.grants(breathing, done))
        assertFalse(UnlockFlow.grants(done, UnlockFlow.tick(done, 9_000L)))
    }

    @Test
    fun `cancel never grants, from any phase`() {
        val asked = UnlockFlow.cancel(start)
        assertEquals(Phase.CANCELLED, asked.phase)
        val breathing = UnlockFlow.confirm(start, 0L)
        val cancelled = UnlockFlow.cancel(breathing)
        assertEquals(Phase.CANCELLED, cancelled.phase)
        assertFalse(UnlockFlow.grants(breathing, cancelled))
        // Ticking a cancelled flow past the end still does nothing.
        assertEquals(Phase.CANCELLED, UnlockFlow.tick(cancelled, 60_000L).phase)
    }

    @Test
    fun `leaving mid-breath restarts the question, no grant on return`() {
        val breathing = UnlockFlow.confirm(start, 0L)
        val left = UnlockFlow.leave(breathing)
        assertEquals(Phase.CONFIRM, left.phase)
        assertEquals(Phase.CONFIRM, UnlockFlow.tick(left, 60_000L).phase)
    }

    @Test
    fun `minutes are clamped and frozen once breathing`() {
        assertEquals(15, UnlockFlow.setMinutes(start, 99).minutes)
        assertEquals(1, UnlockFlow.setMinutes(start, 0).minutes)
        val breathing = UnlockFlow.confirm(UnlockFlow.setMinutes(start, 7), 0L)
        assertEquals(7, UnlockFlow.setMinutes(breathing, 12).minutes)
    }

    @Test
    fun `confirm only from the question`() {
        val breathing = UnlockFlow.confirm(start, 100L)
        assertEquals(100L, UnlockFlow.confirm(breathing, 4_000L).breathStartMs)
    }

    @Test
    fun `restore keeps a running breath, rejects an impossible one`() {
        val kept = UnlockFlow.restore("BREATHING", 5, 2_000L, 3_000L)
        assertEquals(Phase.BREATHING, kept.phase)
        assertEquals(2_000L, kept.breathStartMs)
        assertEquals(Phase.CONFIRM, UnlockFlow.restore("BREATHING", 5, 9_000L, 3_000L).phase)
        assertEquals(Phase.CONFIRM, UnlockFlow.restore("BREATHING", 5, 0L, 3_000L).phase)
        assertEquals(Phase.CONFIRM, UnlockFlow.restore("garbage", 5, 0L, 3_000L).phase)
        assertEquals(15, UnlockFlow.restore(null, 40, 0L, 0L).minutes)
    }

    @Test
    fun `breath alternates in and out, seconds count down`() {
        val b = UnlockFlow.confirm(start, 0L)
        assertTrue(UnlockFlow.breathingIn(b, 1_000L))
        assertFalse(UnlockFlow.breathingIn(b, 3_000L))
        assertEquals(5, UnlockFlow.secondsLeft(b, 0L))
        assertEquals(1, UnlockFlow.secondsLeft(b, 4_500L))
        assertEquals(0.5f, UnlockFlow.progress(b, 2_500L), 1e-6f)
    }

    @Test
    fun `nudge from the third Instagram unlock, once a day, free only`() {
        fun show(premium: Boolean = false, ig: Boolean = true, before: Int = 2, last: String? = null) =
            CleanFeedNudge.shouldShow(premium, ig, before, last, "2026-11-25")
        assertFalse(show(before = 0))
        assertFalse(show(before = 1))
        assertTrue(show(before = 2))
        assertTrue(show(before = 5))
        assertFalse(show(last = "2026-11-25"))
        assertTrue(show(last = "2026-11-24"))
        assertFalse(show(premium = true))
        assertFalse(show(ig = false))
    }
}
