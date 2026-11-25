package com.example.stayfree.ui.cleanfeed

import android.app.Activity
import android.view.View
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleCoroutineScope
import com.example.stayfree.data.local.preferences.AppPreferences
import com.example.stayfree.ui.premium.PaywallActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** "Open clean Instagram" on a block screen: premium opens it, free sees PRO and the paywall. */
object CleanFeedEntry {

    fun bind(activity: Activity, button: View, proChip: View, scope: LifecycleCoroutineScope, prefs: AppPreferences) {
        scope.launch {
            val premium = prefs.premiumActive.first()
            proChip.isVisible = !premium
            button.setOnClickListener {
                activity.startActivity(
                    if (premium) CleanInstagramActivity.newIntent(activity) else PaywallActivity.newIntent(activity)
                )
                activity.finish()
            }
        }
    }
}
