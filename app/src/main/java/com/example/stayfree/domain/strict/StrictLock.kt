package com.example.stayfree.domain.strict

import kotlin.math.abs

/** Clock snapshot the lock was last checked against. */
data class StrictAnchor(val wallMs: Long, val elapsedMs: Long, val bootCount: Int)

data class StrictState(
    val startedMs: Long,
    val untilMs: Long,
    val anchor: StrictAnchor,
    val violations: Int,
    val clockSkewed: Boolean
) {
    val active: Boolean get() = untilMs > 0L

    companion object {
        val OFF = StrictState(0L, 0L, StrictAnchor(0L, 0L, 0), 0, false)
    }
}

/**
 * Time arithmetic for strict mode. The system clock is user-editable, so on its own a date
 * change would end the lock. Within one boot the lock therefore advances with the monotonic
 * clock; across a reboot that clock restarts and the system clock is the only source left,
 * but it never counts as earlier than the last anchor.
 */
object StrictLock {

    // Network time corrections are seconds; anything larger is a manual change.
    const val CLOCK_JUMP_TOLERANCE_MS = 2 * 60_000L

    // There is no way out of a running lock, so its length is capped.
    const val MAX_LOCK_MS = 60 * 86_400_000L

    private fun sameBoot(anchor: StrictAnchor, elapsedMs: Long, bootCount: Int) =
        bootCount == anchor.bootCount && elapsedMs >= anchor.elapsedMs

    fun trustedNow(anchor: StrictAnchor, wallMs: Long, elapsedMs: Long, bootCount: Int): Long =
        if (sameBoot(anchor, elapsedMs, bootCount)) anchor.wallMs + (elapsedMs - anchor.elapsedMs)
        else maxOf(wallMs, anchor.wallMs)

    fun remainingMs(state: StrictState, wallMs: Long, elapsedMs: Long, bootCount: Int): Long =
        if (!state.active) 0L
        else (state.untilMs - trustedNow(state.anchor, wallMs, elapsedMs, bootCount)).coerceAtLeast(0L)

    /** The system clock no longer matches the monotonic one. Undetectable across a reboot. */
    fun clockJumped(anchor: StrictAnchor, wallMs: Long, elapsedMs: Long, bootCount: Int): Boolean =
        sameBoot(anchor, elapsedMs, bootCount) &&
            abs(wallMs - trustedNow(anchor, wallMs, elapsedMs, bootCount)) > CLOCK_JUMP_TOLERANCE_MS

    /**
     * Time left after adding [durationMs]: appended to a running lock, counted from now
     * otherwise, never past [MAX_LOCK_MS] and never less than what is already left.
     */
    fun extendedRemainingMs(remainingMs: Long, durationMs: Long): Long =
        maxOf(remainingMs, minOf(remainingMs + durationMs, MAX_LOCK_MS))
}
