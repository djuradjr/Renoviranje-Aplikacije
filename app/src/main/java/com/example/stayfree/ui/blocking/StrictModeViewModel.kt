package com.example.stayfree.ui.blocking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stayfree.data.local.preferences.AppPreferences
import com.example.stayfree.data.repository.BlockingRepository
import com.example.stayfree.data.repository.WebsiteBlockRepository
import com.example.stayfree.util.StrictGate
import com.example.stayfree.util.StrictTopAppsSource
import com.example.stayfree.util.TimeUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val DAY_MS = 86_400_000L

@HiltViewModel
class StrictModeViewModel @Inject constructor(
    private val strictGate: StrictGate,
    private val prefs: AppPreferences,
    private val topAppsSource: StrictTopAppsSource,
    blockingRepository: BlockingRepository,
    websiteBlockRepository: WebsiteBlockRepository
) : ViewModel() {

    private val _status = MutableStateFlow<StrictGate.Status?>(null)
    /** Null until the first read, so the screen never flashes the "off" state. */
    val status: StateFlow<StrictGate.Status?> = _status.asStateFlow()

    /** Whether anything is switched on for a lock to guard. */
    val hasBlocks: StateFlow<Boolean> = combine(
        prefs.blockAppsEnabledPkgs,
        prefs.contentBlockEnabledIds,
        blockingRepository.getActiveRules(),
        websiteBlockRepository.getAll()
    ) { apps, content, rules, sites ->
        apps.isNotEmpty() || content.isNotEmpty() || rules.isNotEmpty() || sites.any { it.isActive }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val topAppsOn: StateFlow<Boolean?> = prefs.strictTopAppsOn
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val topApps: StateFlow<List<String>> = prefs.strictTopApps
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun setTopAppsOn(on: Boolean) {
        viewModelScope.launch { prefs.setStrictTopAppsOn(on) }
    }

    init {
        viewModelScope.launch { runCatching { topAppsSource.refresh() } }
        viewModelScope.launch {
            strictGate.tick()
            while (true) {
                _status.value = strictGate.status()
                delay(1000L)
            }
        }
    }

    fun lock(durationMs: Long) {
        viewModelScope.launch {
            strictGate.lock(durationMs)
            _status.value = strictGate.status()
        }
    }

    /** Time left until the next daily reset — the "rest of today" length. */
    suspend fun msUntilDayEnd(): Long {
        val resetMinutes = prefs.dailyResetTimeMinutes.first()
        val dayStart = TimeUtils.getDayStartMs(TimeUtils.getEffectiveDate(resetMinutes)) ?: return DAY_MS
        val dayEnd = dayStart + DAY_MS + resetMinutes * 60_000L
        return (dayEnd - System.currentTimeMillis()).coerceIn(60_000L, DAY_MS)
    }
}
