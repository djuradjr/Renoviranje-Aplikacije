package com.example.stayfree.domain.unlock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortUnlockTest {

    private val key = ShortUnlock.contentKey("ig_reels")

    @Test
    fun `grant runs for the chosen minutes on the monotonic clock`() {
        val g = ShortUnlock.grant(key, 4, elapsedMs = 1_000L, bootCount = 3, wallMs = 50_000L)
        assertEquals(240_000L, ShortUnlock.remainingMs(g, 1_000L, 3))
        assertEquals(60_000L, ShortUnlock.remainingMs(g, 181_000L, 3))
        assertEquals(0L, ShortUnlock.remainingMs(g, 241_000L, 3))
        assertEquals(290_000L, g.untilWallMs)
    }

    @Test
    fun `minutes are clamped to 1-15`() {
        assertEquals(60_000L, ShortUnlock.grant(key, 0, 0L, 1, 0L).untilElapsedMs)
        assertEquals(900_000L, ShortUnlock.grant(key, 99, 0L, 1, 0L).untilElapsedMs)
    }

    @Test
    fun `wall clock plays no part, a reboot ends the unlock`() {
        val g = ShortUnlock.grant(key, 15, elapsedMs = 10_000L, bootCount = 3, wallMs = 0L)
        assertTrue(ShortUnlock.isActive(listOf(g), key, 20_000L, 3))
        assertFalse(ShortUnlock.isActive(listOf(g), key, 20_000L, 4))
        // Monotonic clock behind the grant's start can only mean another boot.
        assertFalse(ShortUnlock.isActive(listOf(g), key, 5_000L, 3))
    }

    @Test
    fun `keys do not leak between targets`() {
        val g = ShortUnlock.grant(key, 5, 0L, 1, 0L)
        assertFalse(ShortUnlock.isActive(listOf(g), ShortUnlock.contentKey("yt_shorts"), 1L, 1))
        assertFalse(ShortUnlock.isActive(listOf(g), ShortUnlock.appKey("ig_reels"), 1L, 1))
    }

    @Test
    fun `new grant replaces the same key and drops expired ones`() {
        val old = ShortUnlock.grant(key, 5, 0L, 1, 0L)
        val expired = ShortUnlock.grant(ShortUnlock.appKey("x"), 1, 0L, 1, 0L)
        val other = ShortUnlock.grant(ShortUnlock.appKey("y"), 15, 0L, 1, 0L)
        val fresh = ShortUnlock.grant(key, 2, 120_000L, 1, 0L)
        val result = ShortUnlock.withGrant(listOf(old, expired, other), fresh, 120_000L, 1)
        assertEquals(listOf(other, fresh), result)
    }

    @Test
    fun `daily tally adds up and resets on a new day`() {
        var day = UnlockDay.EMPTY
        day = ShortUnlock.recorded(day, "2026-11-15", 5)
        day = ShortUnlock.recorded(day, "2026-11-15", 15)
        assertEquals(UnlockDay("2026-11-15", 2, 20), day)
        assertEquals(UnlockDay("2026-11-16", 1, 3), ShortUnlock.recorded(day, "2026-11-16", 3))
        assertEquals(UnlockDay("2026-11-16", 0, 0), ShortUnlock.today(day, "2026-11-16"))
        assertEquals(day, ShortUnlock.today(day, "2026-11-15"))
    }
}
