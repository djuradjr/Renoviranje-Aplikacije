package com.example.stayfree.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.example.stayfree.data.local.preferences.AppPreferences
import com.example.stayfree.domain.cleanfeed.FacebookNetwork
import com.example.stayfree.ui.cleanfeed.CleanFeedActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Debug-only door to clean Facebook until the app's own entry points exist.
 * `--ei limit <posts>` sets its post limit first (no settings screen for it yet).
 */
@AndroidEntryPoint
class OpenCleanFacebookActivity : ComponentActivity() {

    @Inject lateinit var prefs: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            if (intent.hasExtra("limit")) prefs.setCleanFeedLimit(FacebookNetwork.ID, intent.getIntExtra("limit", 0))
            startActivity(CleanFeedActivity.newIntent(this@OpenCleanFacebookActivity, FacebookNetwork))
            finish()
        }
    }
}
