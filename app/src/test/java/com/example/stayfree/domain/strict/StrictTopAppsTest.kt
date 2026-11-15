package com.example.stayfree.domain.strict

import com.example.stayfree.domain.score.AppCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class StrictTopAppsTest {

    private val min = 60_000L
    private val ig = "com.instagram.android"

    private fun pick(usage: Map<String, Long>, excluded: Set<String> = emptySet(), neutral: Set<String> = emptySet()) =
        StrictTopApps.pick(usage, { if (it in neutral) AppCategory.NEUTRAL else AppCategory.DISTRACTION }, excluded)

    @Test
    fun takesTopFiveDistractionsByTime() {
        val usage = (1..7).associate { "app$it" to it * 10 * min }
        assertEquals(listOf("app7", "app6", "app5", "app4", "app3"), pick(usage))
    }

    @Test
    fun skipsNonDistractionExcludedAndTapThroughs() {
        val usage = mapOf("a" to 50 * min, "b" to 40 * min, "c" to 30 * min, "d" to 59_999L)
        assertEquals(listOf("c"), pick(usage, excluded = setOf("a"), neutral = setOf("b")))
    }

    @Test
    fun instagramKeepsAPlaceEvenOutsideTheTopFive() {
        val usage = (1..6).associate { "app$it" to (10 + it) * min } + (ig to 2 * min)
        assertEquals(listOf("app6", "app5", "app4", "app3", ig), pick(usage))
    }

    @Test
    fun instagramCountsEvenWhenRecategorised() {
        assertEquals(listOf(ig), pick(mapOf(ig to 5 * min), neutral = setOf(ig)))
    }

    @Test
    fun instagramWithoutRealUsageIsLeftOut() {
        assertEquals(emptyList<String>(), pick(mapOf(ig to 30_000L)))
    }

    @Test
    fun excludedInstagramStaysOut() {
        assertEquals(listOf("a"), pick(mapOf(ig to 50 * min, "a" to min), excluded = setOf(ig)))
    }
}
