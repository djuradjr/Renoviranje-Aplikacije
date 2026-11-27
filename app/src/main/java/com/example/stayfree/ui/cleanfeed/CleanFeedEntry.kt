package com.example.stayfree.ui.cleanfeed

import android.app.Activity
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleCoroutineScope
import com.example.stayfree.data.local.preferences.AppPreferences
import com.example.stayfree.databinding.ViewCleanFeedButtonBinding
import com.example.stayfree.domain.cleanfeed.CleanNetwork
import com.example.stayfree.ui.premium.PaywallActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** "Open clean <network>" on a block screen: premium opens it, free sees PRO and the paywall. */
object CleanFeedEntry {

    fun bind(
        activity: Activity,
        entry: ViewCleanFeedButtonBinding,
        network: CleanNetwork,
        scope: LifecycleCoroutineScope,
        prefs: AppPreferences
    ) {
        entry.tvCleanOpen.setText(CleanFeedTexts.of(network).open)
        scope.launch {
            val premium = prefs.premiumActive.first()
            entry.chipCleanPro.isVisible = !premium
            entry.root.setOnClickListener {
                activity.startActivity(
                    if (premium) CleanFeedActivity.newIntent(activity, network) else PaywallActivity.newIntent(activity)
                )
                activity.finish()
            }
        }
    }
}
