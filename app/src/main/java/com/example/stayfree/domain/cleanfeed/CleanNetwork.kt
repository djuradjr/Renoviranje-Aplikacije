package com.example.stayfree.domain.cleanfeed

/**
 * One social network's clean feed: where its pages live, which navigations are
 * redirected and its page script. The screen, the post limit ([ScrollBudget]) and
 * the end card ([CleanFeedJs]) are shared by every network.
 */
sealed interface CleanNetwork {
    /** Key of this network's stored settings and session. */
    val id: String
    /** The real app(s) this clean feed stands in for. */
    val packages: Set<String>
    val homeUrl: String
    /** The page the stories tray is cut from; null = no tray. */
    val storiesUrl: String?
    val inboxUrl: String
    val activityUrl: String
    /** Run on every page and in-site navigation: [CleanFeedJs] around this network's rules. */
    val injectedJs: String
    /**
     * The site's own header stays on the feed (it carries search, messages and
     * notifications), so the screen adds no notifications button of its own.
     */
    val keepsSiteHeader: Boolean
    /** Cuts [storiesUrl] down to the tray; evaluates to its height in dp, 0 until it renders. */
    val trayJs: String?

    fun userAgent(webViewDefault: String): String
    fun decide(url: String): Nav
    /** The tray WebView keeps only this page; anything else opens in the feed. */
    fun isStoriesTray(url: String?): Boolean
    /** The id of a single post page, or null for any other page. */
    fun postIdOf(url: String?): String?
    /** Messages: a post opened from here is always allowed and never counted. */
    fun isMessages(url: String?): Boolean
    /** The feed page, where the stories tray belongs above the posts. */
    fun isFeed(url: String?): Boolean

    companion object {
        val ALL: List<CleanNetwork> get() = listOf(InstagramNetwork, FacebookNetwork, YouTubeNetwork)

        /** The clean feed that stands in for [packageName], or null if it has none. */
        fun forPackage(packageName: String?): CleanNetwork? = ALL.firstOrNull { packageName in it.packages }
    }

    sealed interface Nav {
        /** Let the WebView load it. */
        data object Allow : Nav
        /** Load [url] instead (Reels, Explore, the algorithmic home feed). */
        data class Redirect(val url: String) : Nav
        /** A normal link out of the network — hand it to the browser. */
        data object External : Nav
        /** Drop it: app deep links (intent:, instagram://, fb://) would open the real app. */
        data object Ignore : Nav
    }
}
