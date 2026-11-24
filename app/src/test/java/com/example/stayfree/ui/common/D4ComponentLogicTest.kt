package com.example.stayfree.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class D4ComponentLogicTest {

    @Test
    fun `score tone follows the band thresholds`() {
        assertEquals(ScoreDisplay.Tone.FOCUS, ScoreDisplay.toneOf(100))
        assertEquals(ScoreDisplay.Tone.FOCUS, ScoreDisplay.toneOf(80))
        assertEquals(ScoreDisplay.Tone.WARN, ScoreDisplay.toneOf(79))
        assertEquals(ScoreDisplay.Tone.WARN, ScoreDisplay.toneOf(55))
        assertEquals(ScoreDisplay.Tone.DISTRACTION, ScoreDisplay.toneOf(54))
        assertEquals(ScoreDisplay.Tone.DISTRACTION, ScoreDisplay.toneOf(0))
    }

    @Test
    fun `no data shows a dash and an empty ring, never zero`() {
        assertEquals(ScoreDisplay.Tone.EMPTY, ScoreDisplay.toneOf(null))
        assertEquals("–", ScoreDisplay.text(null, "–"))
        assertEquals(0f, ScoreDisplay.ringFraction(null), 0f)
        assertEquals("0", ScoreDisplay.text(0, "–"))
    }

    @Test
    fun `ring fraction is clamped to 0-1`() {
        assertEquals(0.76f, ScoreDisplay.ringFraction(76), 1e-6f)
        assertEquals(1f, ScoreDisplay.ringFraction(140), 0f)
        assertEquals(0f, ScoreDisplay.ringFraction(-5), 0f)
    }

    @Test
    fun `bar stacks distraction under useful in proportion`() {
        val seg = HourlyStack.segments(distractionMs = 30, usefulMs = 10, maxTotalMs = 80, height = 100f)
        assertEquals(50f, seg.total, 1e-4f)
        assertEquals(37.5f, seg.distraction, 1e-4f)
        assertEquals(12.5f, seg.useful, 1e-4f)
    }

    @Test
    fun `tallest hour fills the height and empty hours are flat`() {
        val hours = listOf(0L to 0L, 20L to 40L, 5L to 5L)
        val max = HourlyStack.maxTotal(hours)
        assertEquals(60L, max)
        assertEquals(100f, HourlyStack.segments(20, 40, max, 100f).total, 1e-4f)
        assertEquals(0f, HourlyStack.segments(0, 0, max, 100f).total, 0f)
        assertEquals(1L, HourlyStack.maxTotal(List(24) { 0L to 0L }))
    }

    @Test
    fun `negative or bad input never draws`() {
        assertEquals(0f, HourlyStack.segments(-5, -1, 10, 100f).total, 0f)
        assertEquals(0f, HourlyStack.segments(5, 5, 10, 0f).total, 0f)
        assertEquals(100f, HourlyStack.segments(50, 50, 10, 100f).total, 1e-4f)
    }

    @Test
    fun `peak hour is the busiest, earliest on a tie, null when empty`() {
        val hours = List(24) { h -> if (h == 21) 30L to 20L else if (h == 9) 25L to 25L else 1L to 0L }
        assertEquals(9, HourlyStack.peakHour(hours))
        assertNull(HourlyStack.peakHour(List(24) { 0L to 0L }))
        assertNull(HourlyStack.peakHour(emptyList()))
    }

    @Test
    fun `app tile fallback uses brand colour and first letter`() {
        assertEquals(0xFFC13584.toInt(), AppBrand.colorFor("com.instagram.android"))
        assertEquals(0xFFE02424.toInt(), AppBrand.colorFor("com.google.android.youtube"))
        assertEquals(0xFF2B2B2B.toInt(), AppBrand.colorFor("com.zhiliaoapp.musically"))
        assertEquals(0xFF1A73E8.toInt(), AppBrand.colorFor("com.android.chrome"))
        assertEquals(AppBrand.DEFAULT_COLOR, AppBrand.colorFor("org.example.unknown"))
        assertEquals("T", AppBrand.letterFor("  tikTok"))
        assertEquals("?", AppBrand.letterFor(""))
    }
}
