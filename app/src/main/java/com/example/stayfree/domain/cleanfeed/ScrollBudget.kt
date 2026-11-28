package com.example.stayfree.domain.cleanfeed

/**
 * Post limit for the clean feed: [ScrollSession.posts] counts friends' posts seen.
 * At the limit the feed ends for the pause; when it ends a fresh session starts. A
 * session also starts over after one pause of not scrolling, so closing and
 * reopening the screen never resets the count early.
 *
 * [ScrollSession.sessionsToday] counts sessions started on [ScrollSession.day]. Once
 * the daily maximum is used up, the feed stays shut until the next day starts
 * ([ScrollSession.dayLocked]).
 */
data class ScrollSession(
    val posts: Int = 0,
    val lastActiveAt: Long = 0L,
    val lockedUntil: Long = 0L,
    val day: String = "",
    val sessionsToday: Int = 0,
    val dayLocked: Boolean = false,
    /** Single posts/videos already counted this session: reopening one costs nothing. */
    val opened: List<String> = emptyList()
) {
    fun isLocked(now: Long): Boolean = lockedUntil > now
}

object ScrollBudget {

    const val DEFAULT_LIMIT = 20
    /** Choices offered in the app, in posts per session. */
    val LIMIT_STEPS = listOf(10, 20, 30, 50, 80)
    /** Pause choices in minutes; 30 is the shortest on purpose. */
    val PAUSE_STEPS = listOf(30, 60, 120)
    const val DEFAULT_PAUSE_MINUTES = 30
    /** Sessions per day; 0 means no daily maximum. */
    val SESSIONS_STEPS = listOf(1, 2, 3, 5, 0)
    const val DEFAULT_SESSIONS_PER_DAY = 0

    /** The user's settings. */
    data class Rules(
        val limit: Int = DEFAULT_LIMIT,
        val pauseMinutes: Int = DEFAULT_PAUSE_MINUTES,
        val sessionsPerDay: Int = DEFAULT_SESSIONS_PER_DAY
    ) {
        val pauseMs: Long get() = pauseMinutes.coerceAtLeast(PAUSE_STEPS.first()) * 60_000L
    }

    /** The app's current day (it starts at the user's reset time) and when the next one starts. */
    data class Day(val key: String, val nextStartsAt: Long)

    /** The session as it stands at [now]: an ended pause, a long idle or a new day start over. */
    fun current(s: ScrollSession, rules: Rules, day: Day, now: Long): ScrollSession {
        var c = s
        if (c.day != day.key) {
            // A new day: the daily count starts over and a day lock is lifted. A normal
            // pause running across the day boundary still runs out on its own.
            c = c.copy(day = day.key, sessionsToday = 0,
                lockedUntil = if (c.dayLocked) 0L else c.lockedUntil, dayLocked = false)
        }
        // The daily maximum was raised (a loosening the app gates): the day lock goes.
        if (c.dayLocked && !usedUp(c, rules)) c = fresh(c)
        // Clock moved back: never lock for longer than one pause, or past the next day.
        val maxLock = if (c.dayLocked) day.nextStartsAt - now else rules.pauseMs
        if (c.lockedUntil - now > maxLock) c = c.copy(lockedUntil = now + maxLock)
        if (c.lockedUntil in 1..now) c = fresh(c)
        if (c.isLocked(now)) return c
        if (c.lastActiveAt > 0L && now - c.lastActiveAt >= rules.pauseMs) c = fresh(c)
        // The last session of the day ended without reaching the limit: no new one today.
        if (c.posts == 0 && usedUp(c, rules)) {
            c = c.copy(lockedUntil = day.nextStartsAt, dayLocked = true)
        }
        return c
    }

    /** Adds [count] seen posts; reaching the limit starts the pause, or ends the day. */
    fun addPosts(s: ScrollSession, count: Int, rules: Rules, day: Day, now: Long): ScrollSession {
        val c = current(s, rules, day, now)
        if (c.isLocked(now) || count <= 0) return c
        val sessions = if (c.posts == 0) c.sessionsToday + 1 else c.sessionsToday
        val posts = c.posts + count
        if (posts < rules.limit) return c.copy(posts = posts, lastActiveAt = now, sessionsToday = sessions)
        val spent = c.copy(posts = rules.limit, lastActiveAt = now, sessionsToday = sessions)
        return if (usedUp(spent, rules)) spent.copy(lockedUntil = day.nextStartsAt, dayLocked = true)
        else spent.copy(lockedUntil = now + rules.pauseMs)
    }

    /**
     * A single post or video opened by id: counted once per session. One already
     * counted stays open even past the limit, so the video that spent it can be finished.
     */
    fun openPost(s: ScrollSession, id: String, rules: Rules, day: Day, now: Long): ScrollSession {
        val c = current(s, rules, day, now)
        if (id in c.opened || c.isLocked(now)) return c
        return addPosts(c, 1, rules, day, now).copy(opened = (c.opened + id).takeLast(MAX_OPENED))
    }

    private const val MAX_OPENED = 100

    /** Remaining posts this session (0 while locked). */
    fun remaining(s: ScrollSession, rules: Rules, day: Day, now: Long): Int {
        val c = current(s, rules, day, now)
        return if (c.isLocked(now)) 0 else (rules.limit - c.posts).coerceAtLeast(0)
    }

    private fun fresh(s: ScrollSession) = ScrollSession(day = s.day, sessionsToday = s.sessionsToday)

    private fun usedUp(s: ScrollSession, rules: Rules) =
        rules.sessionsPerDay > 0 && s.sessionsToday >= rules.sessionsPerDay
}
