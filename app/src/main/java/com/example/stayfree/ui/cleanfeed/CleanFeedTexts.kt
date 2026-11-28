package com.example.stayfree.ui.cleanfeed

import androidx.annotation.StringRes
import com.example.stayfree.R
import com.example.stayfree.domain.cleanfeed.CleanNetwork
import com.example.stayfree.domain.cleanfeed.FacebookNetwork
import com.example.stayfree.domain.cleanfeed.InstagramNetwork
import com.example.stayfree.domain.cleanfeed.YouTubeNetwork

/** The words each network's clean feed goes by in the app. */
data class CleanFeedTexts(
    @StringRes val title: Int,
    @StringRes val subtitle: Int,
    @StringRes val note: Int,
    @StringRes val open: Int,
    /** End card: the link to [CleanNetwork.inboxUrl] and the scroll-to-top button. */
    @StringRes val cardLink: Int = R.string.clean_feed_messages,
    @StringRes val cardTop: Int = R.string.clean_feed_stories,
    /** Post-limit cover: the button that opens [CleanNetwork.inboxUrl]. */
    @StringRes val toLink: Int = R.string.clean_feed_to_messages
) {
    companion object {
        fun of(network: CleanNetwork): CleanFeedTexts = when (network) {
            InstagramNetwork -> CleanFeedTexts(
                R.string.clean_feed_title, R.string.clean_feed_subtitle,
                R.string.clean_feed_note, R.string.clean_feed_open)
            FacebookNetwork -> CleanFeedTexts(
                R.string.clean_facebook_title, R.string.clean_facebook_subtitle,
                R.string.clean_facebook_note, R.string.clean_facebook_open)
            YouTubeNetwork -> CleanFeedTexts(
                R.string.clean_youtube_title, R.string.clean_youtube_subtitle,
                R.string.clean_youtube_note, R.string.clean_youtube_open,
                cardLink = R.string.clean_youtube_library, cardTop = R.string.clean_feed_to_top,
                toLink = R.string.clean_youtube_to_library)
        }
    }
}
