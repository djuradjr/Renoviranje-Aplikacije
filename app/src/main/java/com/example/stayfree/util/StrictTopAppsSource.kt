package com.example.stayfree.util

import android.content.Context
import com.example.stayfree.data.local.preferences.AppPreferences
import com.example.stayfree.domain.onboarding.UsageHistory
import com.example.stayfree.domain.score.AppCategory
import com.example.stayfree.domain.strict.StrictTopApps
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** Re-picks the strict top apps from last week's usage, at most once per [StrictTopApps.REFRESH_MS]. */
@Singleton
class StrictTopAppsSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: AppPreferences,
    private val usageHistory: UsageHistory
) {
    suspend fun refresh() {
        val now = System.currentTimeMillis()
        val at = prefs.strictTopAppsAt.first()
        if (at in (now - StrictTopApps.REFRESH_MS)..now) return
        val week = usageHistory.lastWeek()
        val overrides = prefs.appCategoryOverrides.first()
        val picked = StrictTopApps.pick(
            week.msByApp,
            { overrides[it] ?: AppCategory.defaultFor(it) },
            SystemPackages.exempt(context) + SystemPackages.keyboards(context)
        )
        // No usage access or an empty log says nothing about the user; keep the last list.
        if (picked.isEmpty() && week.msByApp.isEmpty()) return
        prefs.setStrictTopApps(picked, now)
    }
}
