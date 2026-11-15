package com.example.stayfree.domain.strict

import com.example.stayfree.domain.cleanfeed.CleanFeedRules
import com.example.stayfree.domain.score.AppCategory

/**
 * The apps strict mode keeps closed for every user, premium or not: the most used
 * distracting apps of the last week. Instagram always earns a place once it has real
 * usage, because the clean feed is the designed way back into it.
 */
object StrictTopApps {

    const val COUNT = 5
    /** Below a minute in a week an app is a tap-through, not a habit. */
    const val MIN_WEEK_MS = 60_000L
    const val REFRESH_MS = 60 * 60_000L

    fun pick(
        weekMsByApp: Map<String, Long>,
        categoryOf: (String) -> AppCategory,
        excluded: Set<String>
    ): List<String> {
        val ranked = weekMsByApp.entries
            .filter { (pkg, ms) ->
                ms >= MIN_WEEK_MS && pkg !in excluded &&
                    (pkg == CleanFeedRules.INSTAGRAM_PACKAGE || categoryOf(pkg) == AppCategory.DISTRACTION)
            }
            .sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key })
            .map { it.key }
        val top = ranked.take(COUNT)
        val instagram = CleanFeedRules.INSTAGRAM_PACKAGE
        return if (instagram in ranked && instagram !in top) top.dropLast(1) + instagram else top
    }
}
