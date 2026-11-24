package com.example.stayfree.domain.score

/** Days in a row spent under the daily screen-time goal. */
object GoalStreak {

    /**
     * Current run, ending today. [totalMsDaysAgo] gives a day's screen time (null =
     * no data). Today still in progress doesn't break the run until it goes over;
     * an earlier day without data does, so a gap never counts as a win.
     */
    fun current(goalMs: Long, windowDays: Int, totalMsDaysAgo: (Int) -> Long?): Int {
        val today = totalMsDaysAgo(0)
        if (today != null && today > goalMs) return 0
        var run = if (today != null && today > 0L) 1 else 0
        for (ago in 1..windowDays) {
            val total = totalMsDaysAgo(ago) ?: break
            if (total <= 0L || total > goalMs) break
            run++
        }
        return run
    }
}
