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
    /** The algorithmic home page, loaded only for its stories tray. */
    const val STORIES_URL = "$BASE/"
    override val storiesUrl = STORIES_URL
    override val inboxUrl = INBOX_URL
    override val activityUrl = ACTIVITY_URL
    /**
     * For the stories WebView: the desktop page fitted to the phone, page scrolling
     * off, every fixed or sticky bar hidden, the tray pinned to the top. The tray is
     * found as the horizontal scroller around a story link, never by label, so it
     * works in any language. Evaluates to the tray height in CSS px (= dp), or 0
     * while the tray has not rendered yet.
     */
    override val trayJs: String by lazy {
        "(function(){\n" + FIT_JS + "\n" + """
          function tray(){
            var a = document.querySelector('a[href*="/stories/"]');
            for (var n = a; n && n !== document.body; n = n.parentElement) {
              var ox = getComputedStyle(n).overflowX;
              if (ox === 'auto' || ox === 'scroll') return n;
            }
            return null;
          }
          function pin(){
            fit();
            if (!document.getElementById('bb-tray')) {
              var s = document.createElement('style'); s.id = 'bb-tray';
              s.textContent = 'html,body{overflow:hidden!important}';
              (document.head || document.documentElement).appendChild(s);
            }
            var t = tray();
            var all = document.body ? document.body.getElementsByTagName('*') : [];
            for (var i = 0; i < all.length; i++) {
              if (t && (all[i] === t || all[i].contains(t))) continue;
              var p = getComputedStyle(all[i]).position;
              if (p === 'fixed' || p === 'sticky') all[i].style.setProperty('display', 'none', 'important');
            }
            if (!t) return 0;
            if (!t.hasAttribute('data-bb-pinned')) {
              t.setAttribute('data-bb-pinned', '1');
              var bg = getComputedStyle(document.body).backgroundColor;
              if (!bg || bg === 'rgba(0, 0, 0, 0)') bg = getComputedStyle(document.documentElement).backgroundColor;
              t.style.cssText += ';position:fixed!important;top:0!important;left:0!important;right:0!important;' +
                'width:100%!important;z-index:2147483647!important;background-color:' + bg + '!important;';
            }
            return Math.ceil(t.getBoundingClientRect().height);
          }
          if (!window.__bbTray) {
            var queued = false;
            window.__bbTray = pin;
            new MutationObserver(function(){
              if (queued) return; queued = true;
              setTimeout(function(){ queued = false; pin(); }, 300);
            }).observe(document, {childList: true, subtree: true});
          }
          return pin();
        })()
        """.trimIndent()
    }

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

    override fun isStoriesTray(url: String?): Boolean {
        val uri = url?.let { runCatching { Uri.parse(it) }.getOrNull() } ?: return false
        return isFacebookHost(uri.host) && uri.path.orEmpty().let { it == "" || it == "/" } &&
            uri.query.isNullOrEmpty()
    }

    /**
     * The id of a single post or video page, or null for any other page. Photo pages
     * are left out: the photo viewer changes the URL on every photo of an album, and
     * one album must not spend the whole limit. Path + query only so it is unit-testable.
     */
    fun postIdOfPath(rawPath: String, param: (String) -> String?): String? {
        POST_PATH.find(rawPath)?.let { return it.groupValues[1] }
        val path = rawPath.lowercase().trimEnd('/')
        return when (path) {
            "/permalink.php", "/story.php" -> param("story_fbid")
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
     * Visible labels of suggestion blocks in the feed, in the languages we ship (EN +
     * SR, both scripts). Matched as a whole short text, never as a substring.
     */
    val SUGGESTED_LABELS = listOf(
        "Suggested for you",
        "People you may know",
        "Reels and short videos",
        "Reels",
        "Suggested groups",
        "Suggested pages",
        "Predloženo za tebe",
        "Predloženo za vas",
        "Osobe koje možda znaš",
        "Предложено за тебе",
        "Предложено за вас",
        "Особе које можда знаш"
    )

    /** Labels on a sponsored post: a second signal beside the "about this ad" link. */
    val AD_LABELS = listOf("Sponsored", "Sponzorisano", "Спонзорисано")

    /**
     * The desktop page keeps a 900px minimum width (one container carries it) and puts
     * side columns next to the content. Only while the page is wider than the screen:
     * lift the minimum, drop the side columns, let the content fill the width.
     * screen.width, not innerWidth: the viewport itself widens to the wide page.
     */
    private val FIT_JS = """
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
    """.trimIndent()

    /**
     * Facebook's half of the page script. Targets roles, link targets, visible labels
     * and computed sizes, never Facebook's generated class names.
     *
     * A post is the list item around an `[aria-posinset]` element. Facebook keeps its
     * id out of the page (the permalink appears only on hover) and scrambles some of
     * its text, so a post is told apart by its author and the start of its message
     * (or its photo's file name), hashed. A post with neither does not count.
     */
    private val ADAPTER: String by lazy {
        val labels = SUGGESTED_LABELS.joinToString(",") { "\"" + it.lowercase() + "\"" }
        val adLabels = AD_LABELS.joinToString(",") { "\"" + it.lowercase() + "\"" }
        """
        var CSS =
          // Top bar tabs that only lead to endless video, shopping and games feeds.
          '[role="banner"] a[href*="/watch"],[role="banner"] a[href*="/reel"],' +
          '[role="banner"] a[href*="/marketplace"],[role="banner"] a[href*="/gaming"]{display:none!important}';
        var INBOX = '/messages/t/';
        var labels = [$labels];
        var adLabels = [$adLabels];
        function home(){ return location.pathname === '/'; }
        function cardHost(){ return document.querySelector('[role="main"]'); }
        // Facebook wraps each post in a few single-child divs; the outermost one is the
        // list item.
        function unit(p){
          var n = p;
          for (var i = 0; i < 8 && n.parentElement && n.parentElement !== document.body &&
              n.parentElement.children.length === 1; i++) n = n.parentElement;
          return n;
        }
        function posts(){
          var ps = document.querySelectorAll('[aria-posinset]'), out = [];
          for (var i = 0; i < ps.length; i++) {
            // A shared post inside a post is part of it.
            if (ps[i].parentElement && ps[i].parentElement.closest('[aria-posinset]')) continue;
            var u = unit(ps[i]);
            // Facebook keeps 0px copies of some posts: not a post anyone sees.
            if (!u.offsetHeight || out.indexOf(u) >= 0) continue;
            out.push(u);
          }
          return out;
        }
        function hash(s){
          var h = 5381;
          for (var i = 0; i < s.length; i++) h = ((h << 5) + h + s.charCodeAt(i)) | 0;
          return 'f' + (h >>> 0).toString(36);
        }
        function author(u){
          var links = u.querySelectorAll('a[href^="https://www.facebook.com/"]');
          for (var i = 0; i < links.length; i++) {
            var t = (links[i].textContent || '').trim();
            if (t && t.length < 80 && !/\/(hashtag|stories|watch)\//.test(links[i].href)) return t;
          }
          return '';
        }
        function postId(u){
          if (u.hasAttribute('data-bb-ad')) return null;
          var m = u.querySelector('[data-ad-preview="message"],[data-ad-comet-preview="message"]');
          // The start only: "See more" expands the text in place.
          var key = m ? (m.textContent || '').replace(/\s+/g, ' ').trim().slice(0, 60) : '';
          if (!key) {
            var imgs = u.querySelectorAll('img');
            for (var i = 0; i < imgs.length && !key; i++) {
              var f = (imgs[i].getAttribute('src') || '').match(/\/(\d+_\d+_\d+)_[no]\./);
              if (f) key = f[1];
            }
          }
          return key ? hash(author(u) + '|' + key) : null;
        }
        function hasLabel(el, list){
          var spans = el.querySelectorAll('span,a,h2,h3,h4');
          for (var i = 0; i < spans.length; i++) {
            if (spans[i].childElementCount) continue;
            var t = (spans[i].textContent || '').trim().toLowerCase();
            if (t.length <= 40 && list.indexOf(t) >= 0) return true;
          }
          return false;
        }
        // The chronological feeds carry few ads; this is the net for those that come.
        function hideAds(us){
          for (var i = 0; i < us.length; i++) {
            var u = us[i];
            if (u.hasAttribute('data-bb-ad')) continue;
            if (u.querySelector('a[href*="/ads/about"]') || hasLabel(u, adLabels)) {
              u.setAttribute('data-bb-ad', '1'); off(u);
            }
          }
        }
        function tidy(){
          fit();
          if (!home()) return;
          var us = posts();
          if (!us.length) return;
          hideAds(us);
          // Suggestion blocks sit between the posts as list items of their own.
          var list = us[0].parentElement.children;
          for (var j = 0; j < list.length; j++) {
            var c = list[j];
            if (c.id === 'bb-end' || c.querySelector('[aria-posinset]')) continue;
            if (hasLabel(c, labels)) off(c);
          }
        }
        """.trimIndent() + "\n" + FIT_JS
    }

    override val injectedJs: String by lazy { CleanFeedJs.build(ADAPTER) }
}
