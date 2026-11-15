package com.example.stayfree.domain.cleanfeed

/**
 * Scroll limit for the clean feed: [ScrollSession.posts] counts posts scrolled past.
 * At the limit the feed locks for [PAUSE_MS]; when the pause ends a fresh session
 * starts. A session also starts over after [PAUSE_MS] of not scrolling, so closing
 * and reopening the screen never resets the count early.
 */
data class ScrollSession(
    val posts: Int = 0,
    val lastActiveAt: Long = 0L,
    val lockedUntil: Long = 0L
) {
    fun isLocked(now: Long): Boolean = lockedUntil > now
}

object ScrollBudget {

    const val DEFAULT_LIMIT = 20
    /** Choices offered in the app, in posts per session. */
    val LIMIT_STEPS = listOf(10, 20, 30, 50)
    const val PAUSE_MS = 30 * 60_000L

    /**
     * One post on instagram.com's phone layout is a little under a screen
     * (header, square media, actions, caption). Scrolled pixels / (screen height
     * × this) ≈ posts scrolled past. Measured, not read from the page, so it
     * survives any redesign.
     */
    const val POST_SCREEN_FRACTION = 0.8f

    /** The session as it stands at [now]: an ended pause or a long idle start over. */
    fun current(s: ScrollSession, now: Long): ScrollSession {
        // Clock moved back: never lock for longer than one pause.
        if (s.lockedUntil - now > PAUSE_MS) return s.copy(lockedUntil = now + PAUSE_MS)
        if (s.lockedUntil in 1..now) return ScrollSession()
        if (s.isLocked(now)) return s
        if (s.lastActiveAt > 0L && now - s.lastActiveAt >= PAUSE_MS) return ScrollSession()
        return s
    }

    /** Adds [count] scrolled posts; reaching [limit] starts the pause. */
    fun addPosts(s: ScrollSession, count: Int, limit: Int, now: Long): ScrollSession {
        val c = current(s, now)
        if (c.isLocked(now) || count <= 0) return c
        val posts = c.posts + count
        return if (posts >= limit) {
            ScrollSession(posts = limit, lastActiveAt = now, lockedUntil = now + PAUSE_MS)
        } else {
            c.copy(posts = posts, lastActiveAt = now)
        }
    }

    /** Remaining posts this session (0 while locked). */
    fun remaining(s: ScrollSession, limit: Int, now: Long): Int {
        val c = current(s, now)
        return if (c.isLocked(now)) 0 else (limit - c.posts).coerceAtLeast(0)
    }
}
