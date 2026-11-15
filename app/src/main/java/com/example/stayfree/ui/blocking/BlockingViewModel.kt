package com.example.stayfree.ui.blocking

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stayfree.data.local.entity.BlockRuleEntity
import com.example.stayfree.data.local.preferences.AppPreferences
import com.example.stayfree.data.repository.BlockingRepository
import com.example.stayfree.data.repository.WebsiteBlockRepository
import com.example.stayfree.domain.BlockRuleEvaluator
import com.example.stayfree.domain.content.ContentSignatures
import com.example.stayfree.domain.strict.StrictSocialApps
import com.example.stayfree.domain.unlock.ShortUnlock
import com.example.stayfree.util.UnlockGate
import dagger.hilt.android.qualifiers.ApplicationContext
import com.example.stayfree.util.StrictGate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val STRICT_STATUS_TICK_MS = 20_000L
private const val UNLOCK_TICK_MS = 1_000L
private const val FOCUS_TICK_MS = 15_000L

/** An app on the Blocking tab's row; [leftMs]/[totalMs] are set while a short unlock runs. */
data class BlockedApp(val packageName: String, val leftMs: Long?, val totalMs: Long)

/** One row in the "App block" list: a classic rule or any other switched-on block. */
sealed class ActiveBlockItem {
    abstract val key: String

    data class Rule(val rule: BlockRuleEntity) : ActiveBlockItem() {
        override val key get() = "rule:${rule.id}"
    }

    /** Whole-app block from the Block Apps screen (slider ON). */
    data class App(val packageName: String, val limitMinutes: Int) : ActiveBlockItem() {
        override val key get() = "app:$packageName"
    }

    /** Enabled content target (Reels/Shorts/Stories/TikTok). */
    data class Content(
        val targetId: String,
        val displayName: String,
        val packageName: String,
        val limitMinutes: Int
    ) : ActiveBlockItem() {
        override val key get() = "content:$targetId"
    }

    /** Active website block. */
    data class Site(val id: Long, val domain: String, val dailyCapMs: Long?) : ActiveBlockItem() {
        override val key get() = "site:$id"
    }
}

@HiltViewModel
class BlockingViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val blockingRepository: BlockingRepository,
    websiteBlockRepository: WebsiteBlockRepository,
    prefs: AppPreferences,
    private val strictGate: StrictGate,
    private val unlockGate: UnlockGate
) : ViewModel() {

    private val installedSocial: List<String> =
        StrictSocialApps.installed { context.packageManager.getLaunchIntentForPackage(it) != null }

    private fun ticker(periodMs: Long) = flow { while (true) { emit(Unit); delay(periodMs) } }

    /** Strict mode's social apps, then the Block Apps choices; short unlocks tick every second. */
    val blockedApps: StateFlow<List<BlockedApp>> = combine(
        prefs.blockAppsEnabledPkgs, prefs.shortUnlocks, ticker(UNLOCK_TICK_MS)
    ) { enabled, grants, _ ->
        (installedSocial + enabled.sorted()).distinct().map { pkg ->
            val grant = grants.firstOrNull { it.key == ShortUnlock.appKey(pkg) }
            val left = grant?.let { unlockGate.remainingMs(it) }?.takeIf { it > 0L }
            BlockedApp(pkg, left, grant?.let { it.untilElapsedMs - it.startElapsedMs } ?: 0L)
        }
    }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Active website blocks as (id, domain). */
    val blockedSites: StateFlow<List<Pair<Long, String>>> = websiteBlockRepository.getAll()
        .map { sites -> sites.filter { it.isActive }.map { it.id to it.domain } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Time left in the running Focus session, 0 when none runs. */
    val focusLeftMs: StateFlow<Long> = combine(
        prefs.focusActive, prefs.focusEndTime, ticker(FOCUS_TICK_MS)
    ) { active, end, _ -> if (active) (end - System.currentTimeMillis()).coerceAtLeast(0L) else 0L }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    private val allRules: StateFlow<List<BlockRuleEntity>> = blockingRepository.getAllRules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val appBlocks = combine(
        prefs.blockAppsEnabledPkgs, prefs.blockAppLimitsMinutes
    ) { pkgs, limits ->
        pkgs.map { it to (limits[it] ?: AppPreferences.DEFAULT_BLOCK_APP_LIMIT_MINUTES) }
    }

    private val contentBlocks = combine(
        prefs.contentBlockEnabledIds, prefs.contentTargetLimitsMinutes
    ) { enabled, limits ->
        ContentSignatures.ALL.filter { it.id in enabled }
            .map { ActiveBlockItem.Content(it.id, it.displayName, it.packageName, limits[it.id] ?: 0) }
    }

    /** Every currently-ON block: whole-app sliders, enabled content targets,
     *  active site blocks and active classic rules — only what's actually
     *  blocking right now (the screen is a live "what am I blocking" list). */
    val activeBlocks: StateFlow<List<ActiveBlockItem>> = combine(
        allRules,
        appBlocks,
        contentBlocks,
        websiteBlockRepository.getAll()
    ) { rules, apps, content, sites ->
        buildList {
            apps.sortedBy { it.first }.forEach { (pkg, limit) -> add(ActiveBlockItem.App(pkg, limit)) }
            addAll(content)
            sites.filter { it.isActive }.forEach { add(ActiveBlockItem.Site(it.id, it.domain, it.dailyCapMs)) }
            rules.filter { it.isActive }.forEach { add(ActiveBlockItem.Rule(it)) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Live tool-card statuses. The stored flag stays true when the Focus screen was left
    // before the session ended (its countdown dies with it), so the end time decides.
    // distinctUntilChanged: every DataStore write re-emits, which would restart the timer.
    val focusActive: StateFlow<Boolean> = combine(prefs.focusActive, prefs.focusEndTime, ::Pair)
        .distinctUntilChanged()
        .transformLatest { (active, endTime) ->
            val left = endTime - System.currentTimeMillis()
            if (active && left > 0) {
                emit(true)
                delay(left)
            }
            emit(false)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** End of the active sleep window in minutes from midnight, or null when off. */
    val sleepEndMinutes: StateFlow<Int?> = blockingRepository
        .observeActiveScheduleEnd(BlockRuleEvaluator.SLEEP_MODE_PACKAGE)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val activeWebsiteCount: StateFlow<Int> = websiteBlockRepository.getAll()
        .map { sites -> sites.count { it.isActive } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val contentTargetCount: StateFlow<Int> = prefs.contentBlockEnabledIds
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** Re-read on every change of the stored lock and on a slow tick, since time alone ends it. */
    val strictRemainingMs: StateFlow<Long> = combine(
        prefs.strictState,
        prefs.premiumActive,
        flow { while (true) { emit(Unit); delay(STRICT_STATUS_TICK_MS) } }
    ) { _, _, _ -> strictGate.remainingMs() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    fun toggleRule(id: Long, active: Boolean) {
        viewModelScope.launch { blockingRepository.setRuleActive(id, active) }
    }

    fun deleteRule(id: Long) {
        viewModelScope.launch { blockingRepository.deleteRule(id) }
    }
}
