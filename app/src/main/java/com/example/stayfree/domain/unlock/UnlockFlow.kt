package com.example.stayfree.domain.unlock

/**
 * The short-unlock screen as a state machine: ask → breathe for [BREATHE_MS] → unlocked.
 * The unlock is granted only on the BREATHING → UNLOCKED step; every other way out
 * (cancel, back, leaving the screen) ends without a grant. Times are elapsed-realtime ms.
 */
object UnlockFlow {

    const val BREATHE_MS = 5_000L
    const val DEFAULT_MINUTES = 5
    /** One breath: in, then out. */
    const val HALF_BREATH_MS = 2_500L

    enum class Phase { CONFIRM, BREATHING, UNLOCKED, CANCELLED }

    data class State(
        val phase: Phase = Phase.CONFIRM,
        val minutes: Int = DEFAULT_MINUTES,
        val breathStartMs: Long = 0L
    )

    fun setMinutes(s: State, minutes: Int): State =
        if (s.phase != Phase.CONFIRM) s
        else s.copy(minutes = minutes.coerceIn(ShortUnlock.MIN_MINUTES, ShortUnlock.MAX_MINUTES))

    fun confirm(s: State, nowMs: Long): State =
        if (s.phase != Phase.CONFIRM) s else s.copy(phase = Phase.BREATHING, breathStartMs = nowMs)

    /** Any exit before the end. An unlock already granted stays granted. */
    fun cancel(s: State): State = if (s.phase == Phase.UNLOCKED) s else s.copy(phase = Phase.CANCELLED)

    /** The user left the screen mid-breath: back to the question, the pause starts over. */
    fun leave(s: State): State =
        if (s.phase == Phase.BREATHING) s.copy(phase = Phase.CONFIRM, breathStartMs = 0L) else s

    fun tick(s: State, nowMs: Long): State =
        if (s.phase == Phase.BREATHING && nowMs - s.breathStartMs >= BREATHE_MS) s.copy(phase = Phase.UNLOCKED)
        else s

    /** True exactly when this step is the one that unlocks. */
    fun grants(before: State, after: State): Boolean =
        before.phase == Phase.BREATHING && after.phase == Phase.UNLOCKED

    fun progress(s: State, nowMs: Long): Float = when (s.phase) {
        Phase.BREATHING -> ((nowMs - s.breathStartMs).toFloat() / BREATHE_MS).coerceIn(0f, 1f)
        Phase.UNLOCKED -> 1f
        else -> 0f
    }

    fun secondsLeft(s: State, nowMs: Long): Int =
        if (s.phase != Phase.BREATHING) 0
        else ((BREATHE_MS - (nowMs - s.breathStartMs) + 999) / 1000).toInt().coerceIn(0, (BREATHE_MS / 1000).toInt())

    fun breathingIn(s: State, nowMs: Long): Boolean =
        s.phase != Phase.BREATHING || ((nowMs - s.breathStartMs) / HALF_BREATH_MS) % 2L == 0L

    /**
     * State saved across rotation or process death. A breath that cannot have been
     * running on this boot's clock (start in the future) goes back to the question.
     */
    fun restore(phase: String?, minutes: Int, breathStartMs: Long, nowMs: Long): State {
        val p = Phase.entries.firstOrNull { it.name == phase } ?: Phase.CONFIRM
        val base = State(Phase.CONFIRM, minutes.coerceIn(ShortUnlock.MIN_MINUTES, ShortUnlock.MAX_MINUTES), 0L)
        return when (p) {
            Phase.BREATHING ->
                if (breathStartMs in 1..nowMs) base.copy(phase = p, breathStartMs = breathStartMs) else base
            else -> base.copy(phase = p)
        }
    }
}

/** When the clean-Instagram suggestion shows on the unlock screen. */
object CleanFeedNudge {
    /** From the third unlock of the day on (2 already done), at most once a day, free users only. */
    const val FROM_UNLOCK = 3

    fun shouldShow(
        premium: Boolean,
        isInstagram: Boolean,
        instagramUnlocksToday: Int,
        lastShownDate: String?,
        today: String
    ): Boolean = !premium && isInstagram && instagramUnlocksToday + 1 >= FROM_UNLOCK && lastShownDate != today
}
