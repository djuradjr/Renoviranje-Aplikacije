package com.example.stayfree.debug

import android.app.Activity
import android.os.Bundle
import com.example.stayfree.domain.cleanfeed.FacebookNetwork
import com.example.stayfree.ui.cleanfeed.CleanFeedActivity

/** Debug-only door to clean Facebook until the app's own entry points exist. */
class OpenCleanFacebookActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(CleanFeedActivity.newIntent(this, FacebookNetwork))
        finish()
    }
}
