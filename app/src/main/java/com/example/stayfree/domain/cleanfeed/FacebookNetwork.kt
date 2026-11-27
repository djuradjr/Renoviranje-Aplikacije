package com.example.stayfree.domain.cleanfeed

import android.net.Uri
import com.example.stayfree.domain.cleanfeed.CleanNetwork.Nav
import java.net.URLEncoder

/**
 * THE single source of rules for the clean Facebook feed. When Reels or suggestions
 * leak back in, edit ONLY this file.
 *
 * It runs the DESKTOP site (www.facebook.com with a desktop user agent): the mobile
 * site has no chronological feed (every filter lands back on the algorithmic one),
 * no real links in the page, and sends messages to the Messenger app. The desktop
 * "Feeds" pages are chronological and messages work there.
 */
data object FacebookNetwork : CleanNetwork {

    const val ID = "facebook"
    const val FACEBOOK_PACKAGE = "com.facebook.katana"
    const val FACEBOOK_LITE_PACKAGE = "com.facebook.lite"

    private const val BASE = "https://www.facebook.com"
    /** Feeds → All, newest first: friends, followed pages and groups, no suggestions. */
    const val HOME_URL = "$BASE/?filter=all&sk=h_chr"
    const val SEARCH_URL = "$BASE/search/top/"
    const val INBOX_URL = "$BASE/messages/t/"
    const val ACTIVITY_URL = "$BASE/notifications/"

    /** The chronological "Feeds" filters the desktop site offers. */
    private val FEEDS = setOf("all", "favorites", "friends", "groups", "pages")
    // The mobile sites: the same page is opened on the desktop site instead.
    private val MOBILE_HOSTS = setOf("m.facebook.com", "mbasic.facebook.com", "touch.facebook.com")
    // Facebook's redirector for links that leave Facebook.
    private val LINK_SHIM_HOSTS = setOf("l.facebook.com", "lm.facebook.com")

    override val id = ID
    override val packages = setOf(FACEBOOK_PACKAGE, FACEBOOK_LITE_PACKAGE)
    override val homeUrl = HOME_URL
    override val storiesUrl: String? = null
    override val inboxUrl = INBOX_URL
    override val activityUrl = ACTIVITY_URL
    override val trayJs: String? = null

    /** Desktop Chrome on Linux, with the WebView's own Chrome version. */
    override fun userAgent(webViewDefault: String): String {
        val chrome = Regex("Chrome/([\\d.]+)").find(webViewDefault)?.groupValues?.get(1) ?: "131.0.0.0"
        return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/$chrome Safari/537.36"
    }

    fun isFacebookHost(host: String?): Boolean {
        val h = host?.lowercase() ?: return false
        return h == "facebook.com" || h.endsWith(".facebook.com")
    }

    override fun decide(url: String): Nav {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return Nav.Ignore
        val scheme = uri.scheme?.lowercase()
        if (scheme != "https" && scheme != "http") {
            return if (scheme == "about" || scheme == "data" || scheme == "blob") Nav.Allow else Nav.Ignore
        }
        val host = uri.host?.lowercase()
        if (!isFacebookHost(host) || host in LINK_SHIM_HOSTS) return Nav.External
        if (host in MOBILE_HOSTS) return Nav.Redirect(uri.buildUpon().authority("www.facebook.com").build().toString())
        return decidePath(uri.path.orEmpty()) { uri.getQueryParameter(it) }
    }

    /** Path-level rule, split out so it is testable without android.net.Uri. */
    fun decidePath(rawPath: String, param: (String) -> String?): Nav {
        val path = rawPath.lowercase().let { if (it.endsWith("/")) it else "$it/" }
        val hashtag = HASHTAG_PATH.find(path)?.groupValues?.get(1)
        return when {
            // Home: only a chronological "Feeds" page, never the algorithmic feed.
            path == "/" || path == "/home.php/" ->
                if (param("sk") == "h_chr" && param("filter") in FEEDS) Nav.Allow
                else Nav.Redirect(HOME_URL)
            // The endless Reels feed, single reels included: their page swipes on.
            path.startsWith("/reel/") || path.startsWith("/reels/") -> Nav.Redirect(HOME_URL)
            // A hashtag opens its endless video feed: plain search for it instead.
            hashtag != null -> Nav.Redirect(searchUrl("#$hashtag"))
            // Watch: one video (?v=) stays, the endless video feed does not.
            path == "/watch/" -> if (param("v") != null) Nav.Allow else Nav.Redirect(HOME_URL)
            path.startsWith("/watch/") -> Nav.Redirect(HOME_URL)
            path.startsWith("/marketplace/") || path.startsWith("/gaming/") -> Nav.Redirect(HOME_URL)
            // Search stays, minus its video and reel feeds.
            path.startsWith("/search/videos/") || path.startsWith("/search/reels/") ->
                Nav.Redirect(searchUrl(param("q")))
            else -> Nav.Allow
        }
    }

    private val HASHTAG_PATH = Regex("^/(?:watch/)?hashtag/([^/]+)/")

    private fun searchUrl(query: String?): String =
        if (query.isNullOrBlank()) SEARCH_URL else SEARCH_URL + "?q=" + URLEncoder.encode(query, "UTF-8")

    override fun isStoriesTray(url: String?): Boolean = false

    /**
     * The id of a single post, photo or video page, or null for any other page.
     * Path + query only so it is unit-testable.
     */
    fun postIdOfPath(rawPath: String, param: (String) -> String?): String? {
        POST_PATH.find(rawPath)?.let { return it.groupValues[1] }
        val path = rawPath.lowercase().trimEnd('/')
        return when (path) {
            "/permalink.php", "/story.php" -> param("story_fbid")
            "/photo", "/photo.php" -> param("fbid")
            "/watch", "/video.php" -> param("v")
            else -> null
        }
    }

    // /<user>/posts/<id>, /groups/<g>/posts/<id>, /groups/<g>/permalink/<id>, /<page>/videos/<id>
    private val POST_PATH = Regex("/(?:posts|permalink|videos)/([^/?#]+)", RegexOption.IGNORE_CASE)

    override fun postIdOf(url: String?): String? {
        val uri = url?.let { runCatching { Uri.parse(it) }.getOrNull() } ?: return null
        if (!isFacebookHost(uri.host)) return null
        return postIdOfPath(uri.path.orEmpty()) { uri.getQueryParameter(it) }
    }

    override fun isMessages(url: String?): Boolean {
        val uri = url?.let { runCatching { Uri.parse(it) }.getOrNull() } ?: return false
        return isFacebookHost(uri.host) && uri.path.orEmpty().startsWith("/messages/")
    }

    override fun isFeed(url: String?): Boolean {
        val uri = url?.let { runCatching { Uri.parse(it) }.getOrNull() } ?: return false
        return isFacebookHost(uri.host) && uri.path.orEmpty().let { it == "" || it == "/" }
    }

    /**
     * Facebook's half of the page script. The desktop layout is fitted to a phone
     * (`fit()`). Targets roles, link targets and computed sizes, never Facebook's
     * generated class names. Posts are not told apart yet, so none count toward the
     * limit.
     */
    private val ADAPTER = """
        var CSS =
          // Top bar tabs that only lead to endless video, shopping and games feeds.
          '[role="banner"] a[href*="/watch"],[role="banner"] a[href*="/reel"],' +
          '[role="banner"] a[href*="/marketplace"],[role="banner"] a[href*="/gaming"]{display:none!important}';
        var INBOX = '/messages/t/';
        function home(){ return location.pathname === '/'; }
        function posts(){ return document.querySelectorAll('[aria-posinset]'); }
        function cardHost(){ return document.querySelector('[role="main"]'); }
        function postId(a){ return null; }
        // The desktop page keeps a 900px minimum width (one container carries it) and
        // puts side columns next to the content. Only while the page is wider than
        // the screen: lift the minimum, drop the side columns, let the content fill it.
        // screen.width, not innerWidth: the viewport itself widens to the wide page.
        function fit(){
          if (document.documentElement.scrollWidth <= screen.width + 1) return;
          var divs = document.getElementsByTagName('div');
          for (var i = 0; i < divs.length; i++) {
            if ((parseFloat(getComputedStyle(divs[i]).minWidth) || 0) >= 600) {
              divs[i].style.setProperty('min-width', '0', 'important');
            }
          }
          var main = document.querySelector('[role="main"]');
          if (!main || !main.parentElement) return;
          var side = main.parentElement.children;
          for (var j = 0; j < side.length; j++) {
            if (side[j] !== main) side[j].style.setProperty('display', 'none', 'important');
          }
          main.style.setProperty('width', '100%', 'important');
          main.style.setProperty('max-width', '100%', 'important');
          main.style.setProperty('flex', '1 1 100%', 'important');
        }
        function tidy(){ fit(); }
    """.trimIndent()

    override val injectedJs: String by lazy { CleanFeedJs.build(ADAPTER) }
}
