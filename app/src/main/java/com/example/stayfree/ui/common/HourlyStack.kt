package com.example.stayfree.ui.common

/** Bar geometry for [HourlyBarsView]: distraction at the base, useful time on top. */
object HourlyStack {

    data class Segments(val distraction: Float, val useful: Float) {
        val total: Float get() = distraction + useful
    }

    /** The tallest hour fills [height]; an all-empty day still divides by 1. */
    fun maxTotal(hours: List<Pair<Long, Long>>): Long =
        maxOf(1L, hours.maxOfOrNull { it.first + it.second } ?: 0L)

    fun segments(distractionMs: Long, usefulMs: Long, maxTotalMs: Long, height: Float): Segments {
        val d = distractionMs.coerceAtLeast(0L)
        val u = usefulMs.coerceAtLeast(0L)
        val total = d + u
        if (total == 0L || maxTotalMs <= 0L || height <= 0f) return Segments(0f, 0f)
        val full = height * (total.toFloat() / maxTotalMs).coerceAtMost(1f)
        val distraction = full * (d.toFloat() / total)
        return Segments(distraction, full - distraction)
    }

    /** Hour with the most screen time; null when the day is empty. Earliest wins a tie. */
    fun peakHour(hours: List<Pair<Long, Long>>): Int? {
        var best = -1
        var bestTotal = 0L
        hours.forEachIndexed { hour, (d, u) ->
            val total = d + u
            if (total > bestTotal) {
                best = hour
                bestTotal = total
            }
        }
        return best.takeIf { it >= 0 }
    }
}
