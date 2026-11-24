package com.example.stayfree.domain.score

import org.junit.Assert.assertEquals
import org.junit.Test

class GoalStreakTest {

    private val goal = 180L

    private fun streak(vararg daysAgo: Long?) =
        GoalStreak.current(goal, windowDays = 30) { ago -> daysAgo.getOrNull(ago) }

    @Test
    fun `counts consecutive days under the goal ending today`() {
        assertEquals(3, streak(100, 150, 180, 200, 50))
    }

    @Test
    fun `today over the goal resets to zero`() {
        assertEquals(0, streak(181, 100, 100))
    }

    @Test
    fun `today without data yet keeps yesterday's run`() {
        assertEquals(2, streak(null, 100, 100, 999))
        assertEquals(2, streak(0, 100, 100))
    }

    @Test
    fun `a missing earlier day ends the run`() {
        assertEquals(2, streak(100, 100, null, 100))
    }

    @Test
    fun `run is capped by the window`() {
        assertEquals(31, GoalStreak.current(goal, windowDays = 30) { 60L })
    }
}
