package com.example.stayfree.ui.cleanfeed

import androidx.annotation.StringRes
import com.example.stayfree.R
import com.example.stayfree.domain.cleanfeed.CleanNetwork
import com.example.stayfree.domain.cleanfeed.FacebookNetwork
import com.example.stayfree.domain.cleanfeed.InstagramNetwork

/** The words each network's clean feed goes by in the app. */
data class CleanFeedTexts(
    @StringRes val title: Int,
    @StringRes val subtitle: Int,
    @StringRes val note: Int,
    @StringRes val open: Int
) {
    companion object {
        fun of(network: CleanNetwork): CleanFeedTexts = when (network) {
            InstagramNetwork -> CleanFeedTexts(
                R.string.clean_feed_title, R.string.clean_feed_subtitle,
                R.string.clean_feed_note, R.string.clean_feed_open)
            FacebookNetwork -> CleanFeedTexts(
                R.string.clean_facebook_title, R.string.clean_facebook_subtitle,
                R.string.clean_facebook_note, R.string.clean_facebook_open)
        }
    }
}
