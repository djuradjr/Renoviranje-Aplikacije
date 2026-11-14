package com.example.stayfree.domain.unlock

/**
 * One short unlock. Lives on the monotonic clock of the boot that granted it: moving the
 * system clock cannot stretch it, and a reboot ends it. [untilWallMs] is display-only
 * (the notification's countdown), never used to decide whether the unlock still holds.
 */
data class UnlockGrant(
    val key: String,
    val startElapsedMs: Long,
    val untilElapsedMs: Long,
    val bootCount: Int,
    val untilWallMs: Long
)

/** Short unlocks granted on one effective day: how many, and how many minutes chosen. */
data class UnlockDay(val date: String, val count: Int, val minutes: Int) {
    companion object {
        val EMPTY = UnlockDay("", 0, 0)
    }
}

object ShortUnlock {
    const val MIN_MINUTES = 1
    const val MAX_MINUTES = 15
    const val WARNING_BEFORE_END_MS = 60_000L

    private const val CONTENT_PREFIX = "content:"
    private const val APP_PREFIX = "app:"

    fun contentKey(targetId: String) = CONTENT_PREFIX + targetId
    fun appKey(packageName: String) = APP_PREFIX + packageName
    fun contentTargetIdOf(key: String): String? = if (key.startsWith(CONTENT_PREFIX)) key.removePrefix(CONTENT_PREFIX) else null
    fun packageOf(key: String): String? = if (key.startsWith(APP_PREFIX)) key.removePrefix(APP_PREFIX) else null

    fun grant(key: String, minutes: Int, elapsedMs: Long, bootCount: Int, wallMs: Long): UnlockGrant {
        val durationMs = minutes.coerceIn(MIN_MINUTES, MAX_MINUTES) * 60_000L
        return UnlockGrant(key, elapsedMs, elapsedMs + durationMs, bootCount, wallMs + durationMs)
    }

    fun remainingMs(grant: UnlockGrant, elapsedMs: Long, bootCount: Int): Long =
        if (bootCount != grant.bootCount || elapsedMs < grant.startElapsedMs) 0L
        else (grant.untilElapsedMs - elapsedMs).coerceAtLeast(0L)

    fun isActive(grants: Collection<UnlockGrant>, key: String, elapsedMs: Long, bootCount: Int): Boolean =
        grants.any { it.key == key && remainingMs(it, elapsedMs, bootCount) > 0L }

    /** Keeps the still-running grants and replaces any earlier grant for the same key. */
    fun withGrant(grants: Collection<UnlockGrant>, grant: UnlockGrant, elapsedMs: Long, bootCount: Int) =
        grants.filter { it.key != grant.key && remainingMs(it, elapsedMs, bootCount) > 0L } + grant

    /** Today's tally after one more unlock; a different stored date means the day rolled over. */
    fun recorded(day: UnlockDay, date: String, minutes: Int): UnlockDay {
        val base = if (day.date == date) day else UnlockDay(date, 0, 0)
        return base.copy(count = base.count + 1, minutes = base.minutes + minutes.coerceIn(MIN_MINUTES, MAX_MINUTES))
    }

    fun today(day: UnlockDay, date: String): UnlockDay = if (day.date == date) day else UnlockDay(date, 0, 0)
}
