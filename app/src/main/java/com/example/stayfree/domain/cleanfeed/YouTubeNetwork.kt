package com.example.stayfree.domain.cleanfeed

import android.net.Uri
import com.example.stayfree.domain.cleanfeed.CleanNetwork.Nav
import java.net.URLEncoder

/**
 * THE single source of rules for clean YouTube (m.youtube.com in our own WebView).
 * When Shorts or recommendations leak back in, edit ONLY this file.
 *
 * The WebView's own user agent goes out unchanged: Google forbids disguising an
 * embedded browser to get through its sign-in, so if Google refuses the sign-in
 * here, that is the answer, not something to work around.
 *
 * Ads are left alone: hiding them breaks YouTube's terms.
 */
data object YouTubeNetwork : CleanNetwork {

    const val ID = "youtube"
    const val YOUTUBE_PACKAGE = "com.google.android.youtube"

    private const val BASE = "https://m.youtube.com"
    /** Subscriptions, newest first: no recommendations. */
    const val HOME_URL = "$BASE/feed/subscriptions"
    const val LIBRARY_URL = "$BASE/feed/library"
    const val SEARCH_URL = "$BASE/results"

    /** The /feed/ pages that are the user's own; every other feed is algorithmic. */
    private val OWN_FEEDS = setOf("subscriptions", "library", "you", "history", "playlists", "channels")
    private val YOUTUBE_HOSTS = setOf("youtube.com", "www.youtube.com", "m.youtube.com")
    private val LOGIN_HOSTS = setOf(
        "accounts.google.com", "accounts.youtube.com", "consent.youtube.com",
        "consent.google.com", "myaccount.google.com",
        // Google's interstitials inside sign-in (account prompts); sent out, they break the chain.
        "gds.google.com"
    )

    override val id = ID
    override val packages = setOf(YOUTUBE_PACKAGE)
    override val homeUrl = HOME_URL
    override val storiesUrl: String? = null
    override val inboxUrl = LIBRARY_URL
    override val activityUrl = LIBRARY_URL
    override val keepsSiteHeader = true
    override val trayJs: String? = null

    /** Videos per session: a video is long, so the ladder is short. */
    override val limitSteps = listOf(1, 2, 3, 4, 5)
    override val defaultLimit = 3

    override fun userAgent(webViewDefault: String) = webViewDefault

    override fun decide(url: String): Nav {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return Nav.Ignore
        val scheme = uri.scheme?.lowercase()
        if (scheme != "https" && scheme != "http") {
            return if (scheme == "about" || scheme == "data" || scheme == "blob") Nav.Allow else Nav.Ignore
        }
        val host = uri.host?.lowercase()
        val path = uri.path.orEmpty()
        return when {
            host == "youtu.be" -> path.trim('/').takeIf { it.isNotEmpty() }
                ?.let { Nav.Redirect(watchUrl(it)) } ?: Nav.Redirect(HOME_URL)
            host in LOGIN_HOSTS || host?.startsWith("accounts.google.") == true -> Nav.Allow
            host !in YOUTUBE_HOSTS -> Nav.External
            // YouTube's redirector for links that leave YouTube.
            path.trimEnd('/') == "/redirect" -> Nav.External
            host != "m.youtube.com" ->
                Nav.Redirect(uri.buildUpon().scheme("https").authority("m.youtube.com").build().toString())
            else -> decidePath(path)
        }
    }

    /** Path-level rule, split out so it is testable without android.net.Uri. */
    fun decidePath(rawPath: String): Nav {
        val path = rawPath.lowercase().let { if (it.endsWith("/")) it else "$it/" }
        SHORT_PATH.find(rawPath)?.let { return Nav.Redirect(watchUrl(it.groupValues[1])) }
        CHANNEL_SHORTS.find(rawPath)?.let { return Nav.Redirect("$BASE/${it.groupValues[1]}/videos") }
        val hashtag = HASHTAG_PATH.find(rawPath)?.groupValues?.get(1)
        return when {
            path == "/" || path == "/index/" -> Nav.Redirect(HOME_URL)
            path.startsWith("/shorts/") -> Nav.Redirect(HOME_URL)
            path.startsWith("/feed/") ->
                if (path.removePrefix("/feed/").trimEnd('/') in OWN_FEEDS) Nav.Allow else Nav.Redirect(HOME_URL)
            // A hashtag page is an endless Shorts-heavy feed: plain search for it instead.
            hashtag != null -> Nav.Redirect("$SEARCH_URL?search_query=" + URLEncoder.encode("#$hashtag", "UTF-8"))
            else -> Nav.Allow
        }
    }

    private fun watchUrl(videoId: String) = "$BASE/watch?v=$videoId"

    // Ids are case-sensitive: matched on the raw path.
    private val SHORT_PATH = Regex("^/shorts/([\\w-]+)", RegexOption.IGNORE_CASE)
    private val CHANNEL_SHORTS =
        Regex("^/(@[^/]+|(?:channel|c|user)/[^/]+)/shorts/?$", RegexOption.IGNORE_CASE)
    private val HASHTAG_PATH = Regex("^/hashtag/([^/]+)", RegexOption.IGNORE_CASE)

    override fun isStoriesTray(url: String?) = false

    /** The id of a single video (/watch?v=, /shorts/, /live/), or null. Unit-testable. */
    fun postIdOfPath(rawPath: String, param: (String) -> String?): String? {
        if (rawPath.trimEnd('/').equals("/watch", ignoreCase = true)) return param("v")?.takeIf { it.isNotBlank() }
        return VIDEO_PATH.find(rawPath)?.groupValues?.get(1)
    }

    private val VIDEO_PATH = Regex("^/(?:shorts|live)/([\\w-]+)", RegexOption.IGNORE_CASE)

    override fun postIdOf(url: String?): String? {
        val uri = url?.let { runCatching { Uri.parse(it) }.getOrNull() } ?: return null
        if (uri.host?.lowercase() !in YOUTUBE_HOSTS) return null
        return postIdOfPath(uri.path.orEmpty()) { uri.getQueryParameter(it) }
    }

    override fun isMessages(url: String?) = false

    override fun isFeed(url: String?): Boolean {
        val uri = url?.let { runCatching { Uri.parse(it) }.getOrNull() } ?: return false
        return uri.host?.lowercase() in YOUTUBE_HOSTS && uri.path.orEmpty().trimEnd('/') == "/feed/subscriptions"
    }

    /**
     * YouTube's half of the page script. Targets YouTube's element names (ytm-*,
     * *-view-model), link targets and ARIA state, never its generated class names.
     * Comments stay; ads are not touched.
     */
    private val ADAPTER = """
        var CSS =
          // Shorts: the bottom tab, shelves and single Shorts tiles.
          'ytm-pivot-bar-item-renderer:has(.pivot-shorts),ytm-reel-shelf-renderer,ytm-shorts-lockup-view-model,' +
          // Under a video: the related list ("Up next"); the comments teaser is a section of its own.
          'ytm-item-section-renderer[section-identifier="related-items"],' +
          // In the player: next video, end-screen suggestions and in-video cards.
          '.player-middle-controls-prev-next-button,[class*="endscreen"],[class*="ytp-ce-"],[class*="videowall"]' +
          '{display:none!important}';
        var INBOX = '/feed/library';
        function home(){ return location.pathname === '/feed/subscriptions'; }
        // Scrolling subscriptions is free; each video opened is counted by URL.
        function counts(){ return false; }
        function cardHost(){ return document.querySelector('ytm-browse,ytm-section-list-renderer'); }
        var ITEM = 'ytm-rich-item-renderer,ytm-video-with-context-renderer';
        function posts(){
          var ps = document.querySelectorAll(ITEM), out = [];
          for (var i = 0; i < ps.length; i++) {
            if (ps[i].parentElement && ps[i].parentElement.closest(ITEM)) continue;
            if (ps[i].offsetHeight) out.push(ps[i]);
          }
          return out;
        }
        function postId(u){
          var a = u.querySelector('a[href*="/watch?v="]');
          var m = a && (a.getAttribute('href') || '').match(/[?&]v=([\w-]+)/);
          return m ? m[1] : null;
        }
        function tidy(){
          // :has() is new: the Shorts tab by its own marker too.
          var tabs = document.querySelectorAll('.pivot-shorts');
          for (var t = 0; t < tabs.length; t++) {
            var tab = tabs[t].closest('ytm-pivot-bar-item-renderer');
            if (tab) tab.style.setProperty('display', 'none', 'important');
          }
          // A Short in a list of normal videos, and shelves left with nothing but Shorts.
          var sl = document.querySelectorAll('a[href^="/shorts/"]');
          for (var i = 0; i < sl.length; i++) {
            var item = sl[i].closest(ITEM);
            if (item) { off(item); continue; }
            var shelf = sl[i].closest('grid-shelf-view-model,ytm-rich-section-renderer,ytm-horizontal-card-list-renderer');
            if (shelf && !shelf.querySelector('a[href*="/watch?v="]')) off(shelf);
          }
          // Autoplay off, once per toggle: a toggle YouTube redraws as still on is not fought.
          var ap = document.querySelectorAll('button[aria-pressed="true"]');
          for (var j = 0; j < ap.length; j++) {
            if (!/autonav/i.test(ap[j].className) || ap[j].hasAttribute('data-bb-ap')) continue;
            ap[j].setAttribute('data-bb-ap', '1');
            ap[j].click();
          }
        }
    """.trimIndent()

    override val injectedJs: String by lazy { CleanFeedJs.build(ADAPTER) }
}
