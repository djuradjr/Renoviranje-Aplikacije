package com.example.stayfree.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.stayfree.data.local.preferences.AppPreferences
import com.example.stayfree.service.TrackingScheduler
import com.example.stayfree.util.StrictGate
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var prefs: AppPreferences
    @Inject lateinit var strictGate: StrictGate

    override fun onReceive(context: Context, intent: Intent) {
        // No super.onReceive: BroadcastReceiver.onReceive is abstract; the Hilt
        // Gradle plugin's bytecode transform wires up injection for receivers.
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val resetTime = prefs.dailyResetTimeMinutes.first()
                TrackingScheduler.ensureWorkScheduled(context, resetTime)
                TrackingScheduler.ensureStarted(context)
                // The monotonic clock restarted: anchor a running strict lock to this boot
                // before the system clock can be changed.
                strictGate.tick()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
