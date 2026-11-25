package com.example.stayfree.util

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import com.example.stayfree.data.local.preferences.AppPreferences
import com.example.stayfree.domain.cleanfeed.CleanFeedRules
import com.example.stayfree.domain.content.ContentSignatures
import com.example.stayfree.domain.unlock.ShortUnlock
import com.example.stayfree.domain.unlock.UnlockDay
import com.example.stayfree.domain.unlock.UnlockGrant
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Short unlock from a block screen: the escape hatch of the blocking model. Deliberately
 * outside StrictGate/LoosenGate — strict mode locks the settings, not this.
 */
@Singleton
class UnlockGate @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: AppPreferences
) {
    val bootCount: Int
        get() = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0)

    suspend fun grant(key: String, minutes: Int) {
        val elapsed = SystemClock.elapsedRealtime()
        val boot = bootCount
        val grant = ShortUnlock.grant(key, minutes, elapsed, boot, System.currentTimeMillis())
        prefs.addShortUnlock(
            grant, effectiveDate(), minutes, elapsed, boot,
            instagram = packageOfKey(key) == CleanFeedRules.INSTAGRAM_PACKAGE
        )
    }

    /** Instagram unlocks so far today (app or any of its content surfaces). */
    suspend fun instagramUnlocksToday(): Int = prefs.instagramUnlocksOn(effectiveDate())

    suspend fun todayDate(): String = effectiveDate()

    private fun packageOfKey(key: String): String? =
        ShortUnlock.packageOf(key)
            ?: ShortUnlock.contentTargetIdOf(key)?.let { ContentSignatures.byId(it)?.packageName }

    fun remainingMs(grant: UnlockGrant): Long =
        ShortUnlock.remainingMs(grant, SystemClock.elapsedRealtime(), bootCount)

    suspend fun today(): UnlockDay = ShortUnlock.today(prefs.shortUnlockDay.first(), effectiveDate())

    private suspend fun effectiveDate() = TimeUtils.getEffectiveDate(prefs.dailyResetTimeMinutes.first())
}
