package com.example.stayfree.ui.cleanfeed

import android.app.Activity
import androidx.lifecycle.LifecycleCoroutineScope
import com.example.stayfree.R
import com.example.stayfree.data.local.preferences.AppPreferences
import com.example.stayfree.ui.premium.PaywallActivity
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** The "Open clean Instagram" button on a block screen: premium opens it, free sees the paywall. */
object CleanFeedEntry {

    fun bind(activity: Activity, button: MaterialButton, scope: LifecycleCoroutineScope, prefs: AppPreferences) {
        scope.launch {
            val premium = prefs.premiumActive.first()
            button.setText(if (premium) R.string.clean_feed_open else R.string.clean_feed_open_premium)
            button.setOnClickListener {
                activity.startActivity(
                    if (premium) CleanInstagramActivity.newIntent(activity) else PaywallActivity.newIntent(activity)
                )
                activity.finish()
            }
        }
    }
}
