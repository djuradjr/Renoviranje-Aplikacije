package com.example.stayfree.util

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import com.example.stayfree.data.local.preferences.AppPreferences
import com.example.stayfree.domain.strict.StrictAnchor
import com.example.stayfree.domain.strict.StrictLock
import com.example.stayfree.domain.strict.StrictState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Strict mode: a self-chosen lock during which nothing in the app can be loosened, with no
 * early exit. It only holds with premium — a lapsed subscriber must always be able to switch
 * their blocks off (see PremiumGate) — and resumes if premium returns before it runs out.
 */
@Singleton
class StrictGate @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: AppPreferences
) {
    /** [totalMs] is the whole span of the running lock, extensions included. */
    data class Status(val remainingMs: Long, val totalMs: Long, val violations: Int) {
        val locked: Boolean get() = remainingMs > 0L
    }

    suspend fun status(): Status {
        val state = prefs.strictState.first()
        val remaining =
            if (prefs.premiumActive.first()) StrictLock.remainingMs(state, wall(), elapsed(), bootCount()) else 0L
        return if (remaining > 0L) Status(remaining, state.untilMs - state.startedMs, state.violations) else UNLOCKED
    }

    suspend fun remainingMs(): Long = status().remainingMs

    suspend fun isLocked(): Boolean = remainingMs() > 0L

    /** Starts the lock, or appends [durationMs] to a running one. It can never be shortened. */
    suspend fun lock(durationMs: Long) {
        val wall = wall()
        val elapsed = elapsed()
        val boot = bootCount()
        prefs.updateStrict { state ->
            val remaining = StrictLock.remainingMs(state, wall, elapsed, boot)
            val running = remaining > 0L
            val now = if (running) StrictLock.trustedNow(state.anchor, wall, elapsed, boot) else wall
            StrictState(
                startedMs = if (running) state.startedMs else now,
                untilMs = now + StrictLock.extendedRemainingMs(remaining, durationMs),
                anchor = StrictAnchor(now, elapsed, boot),
                violations = if (running) state.violations else 0,
                clockSkewed = running && state.clockSkewed
            )
        }
    }

    /**
     * Re-anchors a running lock to the current clocks and clears one that has run out.
     * Returns true when this call found the system clock newly changed (one violation).
     */
    suspend fun tick(): Boolean {
        val wall = wall()
        val elapsed = elapsed()
        val boot = bootCount()
        var newViolation = false
        prefs.updateStrict { state ->
            if (!state.active) return@updateStrict state
            if (StrictLock.remainingMs(state, wall, elapsed, boot) <= 0L) return@updateStrict StrictState.OFF
            val skewed = StrictLock.clockJumped(state.anchor, wall, elapsed, boot)
            newViolation = skewed && !state.clockSkewed
            state.copy(
                anchor = StrictAnchor(StrictLock.trustedNow(state.anchor, wall, elapsed, boot), elapsed, boot),
                violations = state.violations + if (newViolation) 1 else 0,
                clockSkewed = skewed
            )
        }
        return newViolation
    }

    suspend fun recordViolation() {
        prefs.updateStrict { if (it.active) it.copy(violations = it.violations + 1) else it }
    }

    private fun wall() = System.currentTimeMillis()
    private fun elapsed() = SystemClock.elapsedRealtime()
    private fun bootCount() = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0)

    private companion object {
        val UNLOCKED = Status(0L, 0L, 0)
    }
}
