package com.example.stayfree.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.example.stayfree.domain.cleanfeed.CleanNetwork
import com.example.stayfree.domain.cleanfeed.InstagramNetwork
import com.example.stayfree.domain.cleanfeed.ScrollBudget
import com.example.stayfree.domain.cleanfeed.ScrollSession
import com.example.stayfree.domain.score.AppCategory
import com.example.stayfree.domain.strict.StrictAnchor
import com.example.stayfree.domain.strict.StrictState
import com.example.stayfree.domain.unlock.ShortUnlock
import com.example.stayfree.domain.unlock.UnlockDay
import com.example.stayfree.domain.unlock.UnlockGrant
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "stayfree_prefs")

@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore

    companion object {
        val DAILY_RESET_TIME_MINUTES = intPreferencesKey("daily_reset_time_minutes")
        // "light" | "dark" | "system" — the UI exposes only light/dark; "system"
        // is reserved so a future "follow system" option needs no migration.
        val APPEARANCE_MODE = stringPreferencesKey("appearance_mode")
        const val APPEARANCE_LIGHT = "light"
        const val APPEARANCE_DARK = "dark"
        const val APPEARANCE_SYSTEM = "system"
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val PIN_ENABLED = booleanPreferencesKey("pin_enabled")
        val PIN_FAILED_ATTEMPTS = intPreferencesKey("pin_failed_attempts")
        val PIN_LOCKOUT_UNTIL = longPreferencesKey("pin_lockout_until")
        // Local-only profile — never leaves the device (no INTERNET permission).
        val PROFILE_USERNAME = stringPreferencesKey("profile_username")
        val PROFILE_EMAIL = stringPreferencesKey("profile_email")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        // Master ON by default — actual delivery is additionally gated by the
        // POST_NOTIFICATIONS permission and the per-type child flag.
        val NOTIFICATIONS_MASTER = booleanPreferencesKey("notifications_master_enabled")
        val NOTIF_BEFORE_TIMEOUT = booleanPreferencesKey("notif_before_timeout")
        val NOTIF_DAILY_SUMMARY = booleanPreferencesKey("notif_daily_summary")
        val DASHBOARD_CARD_ORDER = stringPreferencesKey("dashboard_card_order")
        val FOCUS_ACTIVE = booleanPreferencesKey("focus_active")
        val FOCUS_END_TIME = longPreferencesKey("focus_end_time")
        val FOCUS_IS_WHITELIST = booleanPreferencesKey("focus_is_whitelist")
        val ACCESSIBILITY_DISCLOSURE_ACCEPTED = booleanPreferencesKey("accessibility_disclosure_accepted")
        // Last lifecycle report of the a11y service: RUNNING on connect, UNBOUND when it is
        // turned off normally. Still RUNNING while the service is down = the system killed it.
        val ACCESSIBILITY_STATE = stringPreferencesKey("accessibility_state")
        const val A11Y_RUNNING = "running"
        const val A11Y_UNBOUND = "unbound"
        // Block screens actually shown, lifetime; gates the one-time Play review prompt.
        val BLOCKS_SHOWN_TOTAL = intPreferencesKey("blocks_shown_total")
        val REVIEW_REQUESTED = booleanPreferencesKey("review_requested")
        val CONTENT_BLOCK_ENABLED = stringSetPreferencesKey("content_block_enabled_ids")
        // Whole-app block toggles (Block Apps screen). Stores package names whose
        // slider is ON. Enforcement is added later; for now this only persists UI state.
        val BLOCK_APPS_ENABLED = stringSetPreferencesKey("block_apps_enabled_pkgs")
        // Per-app daily allowance for whole-app blocks: JSON map {pkg: minutes}.
        // 0 minutes = block immediately; missing entry = DEFAULT_BLOCK_APP_LIMIT_MINUTES.
        val BLOCK_APP_LIMITS = stringPreferencesKey("block_app_limits_json")
        const val DEFAULT_BLOCK_APP_LIMIT_MINUTES = 30
        // Per-content-target daily allowance: JSON map {targetId: minutes}.
        // 0 / missing = block immediately (the original hard-block behavior).
        val CONTENT_TARGET_LIMITS = stringPreferencesKey("content_target_limits_json")
        // Accumulated on-surface time per target for ONE effective day:
        // {"date":"yyyy-MM-dd","usage":{targetId: ms}}. Written by the a11y
        // service; a date mismatch on write/read resets the day transparently.
        val CONTENT_TARGET_USAGE = stringPreferencesKey("content_target_usage_json")
        // Screen-time goal for the dashboard "Daily goal" card, in minutes.
        val DAILY_GOAL_MINUTES = intPreferencesKey("daily_goal_minutes")
        const val DEFAULT_DAILY_GOAL_MINUTES = 240
        // Content blocks fired for ONE effective day: {"date":"yyyy-MM-dd","count":N}.
        // Same rolling-day reset as CONTENT_TARGET_USAGE.
        val CONTENT_BLOCK_COUNT = stringPreferencesKey("content_block_count_json")
        // Focus-score category overrides: JSON map {pkg: AppCategory.name}.
        // Only packages the user actually changed are stored; everything else
        // resolves through AppCategory.defaultFor so the seed list can evolve.
        val APP_CATEGORIES = stringPreferencesKey("app_categories_json")
        // Cache of the last Play-verified subscription state, written only by
        // PremiumRepository; every successful Play query overwrites it.
        val PREMIUM_ACTIVE = booleanPreferencesKey("premium_active")
        val LANGUAGE_DEFAULT_APPLIED = booleanPreferencesKey("language_default_applied")
        // Strict mode (see StrictLock). UNTIL missing = off; the anchor is the clock
        // snapshot of the last check.
        val STRICT_STARTED = longPreferencesKey("strict_started")
        val STRICT_UNTIL = longPreferencesKey("strict_until")
        val STRICT_ANCHOR_WALL = longPreferencesKey("strict_anchor_wall")
        val STRICT_ANCHOR_ELAPSED = longPreferencesKey("strict_anchor_elapsed")
        val STRICT_ANCHOR_BOOT = intPreferencesKey("strict_anchor_boot")
        val STRICT_VIOLATIONS = intPreferencesKey("strict_violations")
        val STRICT_CLOCK_SKEWED = booleanPreferencesKey("strict_clock_skewed")
        // Short unlocks (see ShortUnlock): JSON array of grants, and the tally of ONE
        // effective day {"date","count","minutes"}.
        val SHORT_UNLOCKS = stringPreferencesKey("short_unlocks_json")
        val SHORT_UNLOCK_DAY = stringPreferencesKey("short_unlock_day_json")
        // Instagram's share of that day's unlocks {"date","count"}, and the day the
        // clean-feed suggestion was last shown on the unlock screen (once a day).
        val INSTAGRAM_UNLOCK_DAY = stringPreferencesKey("instagram_unlock_day_json")
        val CLEAN_FEED_NUDGE_DATE = stringPreferencesKey("clean_feed_nudge_date")
    }

    /**
     * Clean feed of one network (premium): posts per session, pause, sessions per day,
     * and the running session {"posts","last","until","day","n","dayLock"} — see
     * ScrollBudget. Instagram keeps the key names it had before other networks existed.
     */
    private class CleanFeedKeys(network: String) {
        private val suffix = if (network == InstagramNetwork.ID) "" else "_$network"
        val limit = intPreferencesKey("clean_feed_limit_posts$suffix")
        val pause = intPreferencesKey("clean_feed_pause_minutes$suffix")
        val sessions = intPreferencesKey("clean_feed_sessions_per_day$suffix")
        val session = stringPreferencesKey("clean_feed_session_json$suffix")
    }

    val dailyResetTimeMinutes: Flow<Int> = dataStore.data.map { it[DAILY_RESET_TIME_MINUTES] ?: 0 }
    // Skor is designed dark-first; light is a translation of it, not the default.
    val appearanceMode: Flow<String> = dataStore.data.map { it[APPEARANCE_MODE] ?: APPEARANCE_DARK }
    val pinHash: Flow<String?> = dataStore.data.map { it[PIN_HASH] }
    val pinEnabled: Flow<Boolean> = dataStore.data.map { it[PIN_ENABLED] ?: false }
    val pinLockoutUntil: Flow<Long> = dataStore.data.map { it[PIN_LOCKOUT_UNTIL] ?: 0L }
    val profileUsername: Flow<String> = dataStore.data.map { it[PROFILE_USERNAME] ?: "" }
    val profileEmail: Flow<String> = dataStore.data.map { it[PROFILE_EMAIL] ?: "" }
    val onboardingComplete: Flow<Boolean> = dataStore.data.map { it[ONBOARDING_COMPLETE] ?: false }
    val languageDefaultApplied: Flow<Boolean> = dataStore.data.map { it[LANGUAGE_DEFAULT_APPLIED] ?: false }
    val notificationsMaster: Flow<Boolean> = dataStore.data.map { it[NOTIFICATIONS_MASTER] ?: true }
    val notifBeforeTimeout: Flow<Boolean> = dataStore.data.map { it[NOTIF_BEFORE_TIMEOUT] ?: true }
    val notifDailySummary: Flow<Boolean> = dataStore.data.map { it[NOTIF_DAILY_SUMMARY] ?: true }
    val dashboardCardOrder: Flow<String?> = dataStore.data.map { it[DASHBOARD_CARD_ORDER] }
    val focusActive: Flow<Boolean> = dataStore.data.map { it[FOCUS_ACTIVE] ?: false }
    val focusEndTime: Flow<Long> = dataStore.data.map { it[FOCUS_END_TIME] ?: 0L }
    val focusIsWhitelist: Flow<Boolean> = dataStore.data.map { it[FOCUS_IS_WHITELIST] ?: true }
    val accessibilityDisclosureAccepted: Flow<Boolean> =
        dataStore.data.map { it[ACCESSIBILITY_DISCLOSURE_ACCEPTED] ?: false }
    val accessibilityState: Flow<String?> = dataStore.data.map { it[ACCESSIBILITY_STATE] }
    val blocksShownTotal: Flow<Int> = dataStore.data.map { it[BLOCKS_SHOWN_TOTAL] ?: 0 }
    val reviewRequested: Flow<Boolean> = dataStore.data.map { it[REVIEW_REQUESTED] ?: false }
    val contentBlockEnabledIds: Flow<Set<String>> =
        dataStore.data.map { it[CONTENT_BLOCK_ENABLED] ?: emptySet() }
    /** Package names whose whole-app block slider is ON (Block Apps screen). */
    val blockAppsEnabledPkgs: Flow<Set<String>> =
        dataStore.data.map { it[BLOCK_APPS_ENABLED] ?: emptySet() }
    /** Daily allowance in minutes per blocked app (0 = block immediately). */
    val blockAppLimitsMinutes: Flow<Map<String, Int>> =
        dataStore.data.map { parseLimits(it[BLOCK_APP_LIMITS]) }
    /** Daily allowance in minutes per content target (0 = block immediately). */
    val contentTargetLimitsMinutes: Flow<Map<String, Int>> =
        dataStore.data.map { parseLimits(it[CONTENT_TARGET_LIMITS]) }
    /** Only the packages the user re-categorised; resolve through [categoryOf]. */
    val appCategoryOverrides: Flow<Map<String, AppCategory>> = dataStore.data.map { prefs ->
        val json = prefs[APP_CATEGORIES] ?: return@map emptyMap()
        try {
            val obj = JSONObject(json)
            obj.keys().asSequence().mapNotNull { key ->
                runCatching { key to AppCategory.valueOf(obj.getString(key)) }.getOrNull()
            }.toMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    val premiumActive: Flow<Boolean> = dataStore.data.map { it[PREMIUM_ACTIVE] ?: false }

    /** A network's clean feed: posts per session, the pause after it, sessions per day. */
    fun cleanFeedRules(network: String): Flow<ScrollBudget.Rules> {
        val keys = CleanFeedKeys(network)
        val net = CleanNetwork.forId(network)
        val maxLimit = net?.limitSteps?.last() ?: Int.MAX_VALUE
        return dataStore.data.map {
            ScrollBudget.Rules(
                // A value stored before this network's ladder got shorter is cut to its top.
                limit = (it[keys.limit] ?: net?.defaultLimit ?: ScrollBudget.DEFAULT_LIMIT).coerceAtMost(maxLimit),
                pauseMinutes = it[keys.pause] ?: ScrollBudget.DEFAULT_PAUSE_MINUTES,
                sessionsPerDay = it[keys.sessions] ?: ScrollBudget.DEFAULT_SESSIONS_PER_DAY
            )
        }
    }

    val dailyGoalMinutes: Flow<Int> =
        dataStore.data.map { it[DAILY_GOAL_MINUTES] ?: DEFAULT_DAILY_GOAL_MINUTES }
    /** (effectiveDate, count) of content blocks fired that day. */
    val contentBlockCount: Flow<Pair<String, Int>> = dataStore.data.map {
        val json = it[CONTENT_BLOCK_COUNT] ?: return@map "" to 0
        try {
            val obj = JSONObject(json)
            obj.optString("date") to obj.optInt("count")
        } catch (e: Exception) {
            "" to 0
        }
    }

    suspend fun setDailyResetTime(minutes: Int) {
        dataStore.edit { it[DAILY_RESET_TIME_MINUTES] = minutes }
    }

    suspend fun setAppearanceMode(mode: String) {
        dataStore.edit { it[APPEARANCE_MODE] = mode }
    }

    suspend fun setLanguageDefaultApplied() {
        dataStore.edit { it[LANGUAGE_DEFAULT_APPLIED] = true }
    }

    suspend fun setPinHash(hash: String?) {
        dataStore.edit {
            if (hash != null) it[PIN_HASH] = hash else it.remove(PIN_HASH)
        }
    }

    suspend fun setPinEnabled(enabled: Boolean) {
        dataStore.edit { it[PIN_ENABLED] = enabled }
    }

    suspend fun setPin(hash: String) {
        dataStore.edit {
            it[PIN_HASH] = hash
            it[PIN_ENABLED] = true
            it.remove(PIN_FAILED_ATTEMPTS)
            it.remove(PIN_LOCKOUT_UNTIL)
        }
    }

    suspend fun clearPin() {
        dataStore.edit {
            it.remove(PIN_HASH)
            it[PIN_ENABLED] = false
            it.remove(PIN_FAILED_ATTEMPTS)
            it.remove(PIN_LOCKOUT_UNTIL)
        }
    }

    /** Increments the failed-attempt counter and returns the new count. */
    suspend fun registerFailedPinAttempt(): Int {
        val updated = dataStore.edit {
            it[PIN_FAILED_ATTEMPTS] = (it[PIN_FAILED_ATTEMPTS] ?: 0) + 1
        }
        return updated[PIN_FAILED_ATTEMPTS] ?: 0
    }

    suspend fun resetPinAttempts() {
        dataStore.edit {
            it.remove(PIN_FAILED_ATTEMPTS)
            it.remove(PIN_LOCKOUT_UNTIL)
        }
    }

    suspend fun setPinLockout(untilMillis: Long) {
        dataStore.edit { it[PIN_LOCKOUT_UNTIL] = untilMillis }
    }

    suspend fun setProfile(username: String, email: String) {
        dataStore.edit {
            it[PROFILE_USERNAME] = username
            it[PROFILE_EMAIL] = email
        }
    }

    suspend fun setOnboardingComplete(complete: Boolean) {
        dataStore.edit { it[ONBOARDING_COMPLETE] = complete }
    }

    suspend fun setNotificationsMaster(enabled: Boolean) {
        dataStore.edit { it[NOTIFICATIONS_MASTER] = enabled }
    }

    suspend fun setNotifBeforeTimeout(enabled: Boolean) {
        dataStore.edit { it[NOTIF_BEFORE_TIMEOUT] = enabled }
    }

    suspend fun setNotifDailySummary(enabled: Boolean) {
        dataStore.edit { it[NOTIF_DAILY_SUMMARY] = enabled }
    }

    suspend fun setDashboardCardOrder(order: String) {
        dataStore.edit { it[DASHBOARD_CARD_ORDER] = order }
    }

    suspend fun setFocusState(active: Boolean, endTime: Long, isWhitelist: Boolean) {
        dataStore.edit {
            it[FOCUS_ACTIVE] = active
            it[FOCUS_END_TIME] = endTime
            it[FOCUS_IS_WHITELIST] = isWhitelist
        }
    }

    suspend fun setAccessibilityDisclosureAccepted(accepted: Boolean) {
        dataStore.edit { it[ACCESSIBILITY_DISCLOSURE_ACCEPTED] = accepted }
    }

    suspend fun setAccessibilityState(state: String) {
        dataStore.edit { it[ACCESSIBILITY_STATE] = state }
    }

    suspend fun incrementBlocksShown() {
        dataStore.edit { it[BLOCKS_SHOWN_TOTAL] = (it[BLOCKS_SHOWN_TOTAL] ?: 0) + 1 }
    }

    suspend fun setReviewRequested() {
        dataStore.edit { it[REVIEW_REQUESTED] = true }
    }

    suspend fun setContentBlockEnabled(id: String, enabled: Boolean) {
        dataStore.edit { prefs ->
            val current = prefs[CONTENT_BLOCK_ENABLED] ?: emptySet()
            prefs[CONTENT_BLOCK_ENABLED] = if (enabled) current + id else current - id
        }
    }

    suspend fun setBlockAppEnabled(packageName: String, enabled: Boolean) {
        dataStore.edit { prefs ->
            val current = prefs[BLOCK_APPS_ENABLED] ?: emptySet()
            prefs[BLOCK_APPS_ENABLED] = if (enabled) current + packageName else current - packageName
        }
    }

    suspend fun setBlockAppLimitMinutes(packageName: String, minutes: Int) {
        putLimit(BLOCK_APP_LIMITS, packageName, minutes)
    }

    /** User overrides of the focus-score category: JSON map {pkg: AppCategory.name}. */
    suspend fun setAppCategory(packageName: String, category: AppCategory) {
        dataStore.edit { prefs ->
            val obj = try {
                JSONObject(prefs[APP_CATEGORIES] ?: "{}")
            } catch (e: Exception) {
                JSONObject()
            }
            if (category == AppCategory.defaultFor(packageName)) {
                obj.remove(packageName)
            } else {
                obj.put(packageName, category.name)
            }
            prefs[APP_CATEGORIES] = obj.toString()
        }
    }

    suspend fun setContentTargetLimitMinutes(id: String, minutes: Int) {
        putLimit(CONTENT_TARGET_LIMITS, id, minutes)
    }

    private suspend fun putLimit(key: Preferences.Key<String>, id: String, minutes: Int) {
        dataStore.edit { prefs ->
            val obj = try {
                JSONObject(prefs[key] ?: "{}")
            } catch (e: Exception) {
                JSONObject()
            }
            obj.put(id, minutes.coerceAtLeast(0))
            prefs[key] = obj.toString()
        }
    }

    /**
     * Adds [deltaMs] to the target's on-surface time for the effective day [date]
     * and returns the new total. A stored different date means the day rolled
     * over — the whole map is dropped before adding (the daily reset).
     */
    suspend fun addContentTargetUsage(id: String, date: String, deltaMs: Long): Long {
        var newTotal = 0L
        dataStore.edit { prefs ->
            val obj = try {
                JSONObject(prefs[CONTENT_TARGET_USAGE] ?: "{}")
            } catch (e: Exception) {
                JSONObject()
            }
            val usage = if (obj.optString("date") == date) {
                obj.optJSONObject("usage") ?: JSONObject()
            } else JSONObject()
            newTotal = usage.optLong(id) + deltaMs.coerceAtLeast(0L)
            usage.put(id, newTotal)
            prefs[CONTENT_TARGET_USAGE] = JSONObject().put("date", date).put("usage", usage).toString()
        }
        return newTotal
    }

    suspend fun setCleanFeedLimit(network: String, posts: Int) {
        val max = CleanNetwork.forId(network)?.limitSteps?.last() ?: Int.MAX_VALUE
        dataStore.edit { it[CleanFeedKeys(network).limit] = posts.coerceIn(1, max) }
    }

    suspend fun setCleanFeedPause(network: String, minutes: Int) {
        dataStore.edit {
            it[CleanFeedKeys(network).pause] = minutes.coerceAtLeast(ScrollBudget.PAUSE_STEPS.first())
        }
    }

    /** 0 = no daily maximum. */
    suspend fun setCleanFeedSessionsPerDay(network: String, sessions: Int) {
        dataStore.edit { it[CleanFeedKeys(network).sessions] = sessions.coerceAtLeast(0) }
    }

    /** Atomic read-modify-write of a network's clean-feed session; returns the stored result. */
    suspend fun updateCleanFeedSession(network: String, transform: (ScrollSession) -> ScrollSession): ScrollSession {
        val key = CleanFeedKeys(network).session
        var result = ScrollSession()
        dataStore.edit { prefs ->
            result = transform(parseCleanFeedSession(prefs[key]))
            prefs[key] = JSONObject()
                .put("posts", result.posts)
                .put("last", result.lastActiveAt)
                .put("until", result.lockedUntil)
                .put("day", result.day)
                .put("n", result.sessionsToday)
                .put("dayLock", result.dayLocked)
                .put("opened", result.opened.joinToString(","))
                .toString()
        }
        return result
    }

    private fun parseCleanFeedSession(json: String?): ScrollSession {
        if (json.isNullOrEmpty()) return ScrollSession()
        return try {
            val o = JSONObject(json)
            ScrollSession(o.optInt("posts"), o.optLong("last"), o.optLong("until"),
                o.optString("day"), o.optInt("n"), o.optBoolean("dayLock"),
                o.optString("opened").split(',').filter { it.isNotEmpty() })
        } catch (e: Exception) {
            ScrollSession()
        }
    }

    suspend fun setPremiumActive(active: Boolean) {
        dataStore.edit { it[PREMIUM_ACTIVE] = active }
    }

    suspend fun setDailyGoalMinutes(minutes: Int) {
        dataStore.edit { it[DAILY_GOAL_MINUTES] = minutes.coerceAtLeast(1) }
    }

    /** Bumps today's content-block counter; a stored different date resets it first. */
    suspend fun incrementContentBlockCount(date: String) {
        dataStore.edit { prefs ->
            val current = try {
                val obj = JSONObject(prefs[CONTENT_BLOCK_COUNT] ?: "{}")
                if (obj.optString("date") == date) obj.optInt("count") else 0
            } catch (e: Exception) {
                0
            }
            prefs[CONTENT_BLOCK_COUNT] =
                JSONObject().put("date", date).put("count", current + 1).toString()
        }
    }

    suspend fun getContentTargetUsageMs(id: String, date: String): Long {
        val json = dataStore.data.first()[CONTENT_TARGET_USAGE] ?: return 0L
        return try {
            val obj = JSONObject(json)
            if (obj.optString("date") != date) 0L
            else obj.optJSONObject("usage")?.optLong(id) ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    val strictState: Flow<StrictState> = dataStore.data.map { it.toStrictState() }

    /** Atomic read-modify-write: the service tick and the UI must not overwrite each other. */
    suspend fun updateStrict(transform: (StrictState) -> StrictState) {
        dataStore.edit { prefs ->
            val next = transform(prefs.toStrictState())
            if (!next.active) {
                listOf(
                    STRICT_STARTED, STRICT_UNTIL, STRICT_ANCHOR_WALL, STRICT_ANCHOR_ELAPSED,
                    STRICT_ANCHOR_BOOT, STRICT_VIOLATIONS, STRICT_CLOCK_SKEWED
                ).forEach { prefs.remove(it) }
            } else {
                prefs[STRICT_STARTED] = next.startedMs
                prefs[STRICT_UNTIL] = next.untilMs
                prefs[STRICT_ANCHOR_WALL] = next.anchor.wallMs
                prefs[STRICT_ANCHOR_ELAPSED] = next.anchor.elapsedMs
                prefs[STRICT_ANCHOR_BOOT] = next.anchor.bootCount
                prefs[STRICT_VIOLATIONS] = next.violations
                prefs[STRICT_CLOCK_SKEWED] = next.clockSkewed
            }
        }
    }

    private fun Preferences.toStrictState() = StrictState(
        startedMs = this[STRICT_STARTED] ?: 0L,
        untilMs = this[STRICT_UNTIL] ?: 0L,
        anchor = StrictAnchor(
            wallMs = this[STRICT_ANCHOR_WALL] ?: 0L,
            elapsedMs = this[STRICT_ANCHOR_ELAPSED] ?: 0L,
            bootCount = this[STRICT_ANCHOR_BOOT] ?: 0
        ),
        violations = this[STRICT_VIOLATIONS] ?: 0,
        clockSkewed = this[STRICT_CLOCK_SKEWED] ?: false
    )

    val shortUnlocks: Flow<List<UnlockGrant>> = dataStore.data.map { parseUnlocks(it[SHORT_UNLOCKS]) }

    val shortUnlockDay: Flow<UnlockDay> = dataStore.data.map { prefs ->
        try {
            val obj = JSONObject(prefs[SHORT_UNLOCK_DAY] ?: return@map UnlockDay.EMPTY)
            UnlockDay(obj.optString("date"), obj.optInt("count"), obj.optInt("minutes"))
        } catch (e: Exception) {
            UnlockDay.EMPTY
        }
    }

    /** Instagram unlocks on [date] so far. */
    suspend fun instagramUnlocksOn(date: String): Int = try {
        JSONObject(dataStore.data.first()[INSTAGRAM_UNLOCK_DAY] ?: "{}")
            .let { if (it.optString("date") == date) it.optInt("count") else 0 }
    } catch (e: Exception) {
        0
    }

    val cleanFeedNudgeDate: Flow<String?> = dataStore.data.map { it[CLEAN_FEED_NUDGE_DATE] }

    suspend fun setCleanFeedNudgeDate(date: String) {
        dataStore.edit { it[CLEAN_FEED_NUDGE_DATE] = date }
    }

    /** Stores the grant and bumps the day's tally (and Instagram's, when it is Instagram) in one write. */
    suspend fun addShortUnlock(
        grant: UnlockGrant, date: String, minutes: Int, elapsedMs: Long, bootCount: Int,
        instagram: Boolean = false
    ) {
        dataStore.edit { prefs ->
            if (instagram) {
                val ig = try {
                    JSONObject(prefs[INSTAGRAM_UNLOCK_DAY] ?: "{}")
                        .let { if (it.optString("date") == date) it.optInt("count") else 0 }
                } catch (e: Exception) {
                    0
                }
                prefs[INSTAGRAM_UNLOCK_DAY] = JSONObject().put("date", date).put("count", ig + 1).toString()
            }
            val grants = ShortUnlock.withGrant(parseUnlocks(prefs[SHORT_UNLOCKS]), grant, elapsedMs, bootCount)
            prefs[SHORT_UNLOCKS] = JSONArray().apply {
                grants.forEach {
                    put(
                        JSONObject().put("k", it.key).put("s", it.startElapsedMs)
                            .put("u", it.untilElapsedMs).put("b", it.bootCount).put("w", it.untilWallMs)
                    )
                }
            }.toString()
            val day = try {
                JSONObject(prefs[SHORT_UNLOCK_DAY] ?: "{}").let {
                    UnlockDay(it.optString("date"), it.optInt("count"), it.optInt("minutes"))
                }
            } catch (e: Exception) {
                UnlockDay.EMPTY
            }
            val next = ShortUnlock.recorded(day, date, minutes)
            prefs[SHORT_UNLOCK_DAY] = JSONObject().put("date", next.date)
                .put("count", next.count).put("minutes", next.minutes).toString()
        }
    }

    private fun parseUnlocks(json: String?): List<UnlockGrant> {
        if (json.isNullOrEmpty()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                UnlockGrant(o.getString("k"), o.getLong("s"), o.getLong("u"), o.getInt("b"), o.getLong("w"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseLimits(json: String?): Map<String, Int> {
        if (json.isNullOrEmpty()) return emptyMap()
        return try {
            val obj = JSONObject(json)
            buildMap {
                for (key in obj.keys()) put(key, obj.optInt(key))
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }

}
