package com.example.stayfree.domain.cleanfeed

import android.net.Uri
import com.example.stayfree.domain.cleanfeed.CleanNetwork.Nav

/**
 * THE single source of rules for the clean Instagram feed (instagram.com in our own
 * WebView). Instagram changes its site often — when Reels or suggestions leak back
 * in, edit ONLY this file.
 *
 * Two layers, so a site redesign degrades gracefully instead of breaking:
 * - navigation ([decide]): paths, checked on every page and in-site (pushState)
 *   navigation. Survives redesigns because URLs rarely change.
 * - cosmetics ([ADAPTER] inside [CleanFeedJs]): hides the Reels tab and "Suggested"
 *   blocks, tells posts apart by id. Targets link targets and visible labels only,
 *   never Instagram's generated class names.
 *
 * The stories tray lives only on the algorithmic home page, so it is shown from a
 * second, tray-sized WebView ([STORIES_URL], [trayJs]); the feed itself is the
 * Following page. Hiding non-friend posts inside the home feed instead breaks
 * Instagram's virtual list (the feed jumps by screens while scrolling).
 */
data object InstagramNetwork : CleanNetwork {

    const val ID = "instagram"
    const val INSTAGRAM_PACKAGE = "com.instagram.android"

    /** The chronological feed of followed accounts only: no suggestions, no ads. */
    const val HOME_URL = "https://www.instagram.com/?variant=following"
    /** The algorithmic home page, loaded only for its stories tray. */
    const val STORIES_URL = "https://www.instagram.com/"
    /** Search without the Explore grid underneath it. */
    const val SEARCH_URL = "https://www.instagram.com/explore/search/"
    const val INBOX_URL = "https://www.instagram.com/direct/inbox/"
    const val ACTIVITY_URL = "https://www.instagram.com/accounts/activity/"

    override val id = ID
    override val packages = setOf(INSTAGRAM_PACKAGE)
    override val homeUrl = HOME_URL
    override val storiesUrl = STORIES_URL
    override val inboxUrl = INBOX_URL
    override val activityUrl = ACTIVITY_URL
    override val keepsSiteHeader = false

    // Instagram serves a degraded page to anything marked as an embedded WebView.
    override fun userAgent(webViewDefault: String) = webViewDefault.replace("; wv", "")

    // Login can bounce through Facebook ("Log in with Facebook", some checkpoints).
    private val LOGIN_HOSTS = setOf("facebook.com", "www.facebook.com", "m.facebook.com")

    fun isInstagramHost(host: String?): Boolean {
        val h = host?.lowercase() ?: return false
        return h == "instagram.com" || h.endsWith(".instagram.com")
    }

    override fun decide(url: String): Nav {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return Nav.Ignore
        val scheme = uri.scheme?.lowercase()
        if (scheme != "https" && scheme != "http") {
            return if (scheme == "about" || scheme == "data" || scheme == "blob") Nav.Allow else Nav.Ignore
        }
        val host = uri.host?.lowercase()
        if (!isInstagramHost(host)) {
            return if (host in LOGIN_HOSTS) Nav.Allow else Nav.External
        }
        return decidePath(uri.path.orEmpty(), uri.getQueryParameter("variant"))
    }

    /** Path-level rule, split out so it is testable without android.net.Uri. */
    fun decidePath(rawPath: String, variant: String?): Nav {
        val path = rawPath.lowercase().let { if (it.endsWith("/")) it else "$it/" }
        return when {
            // Home: only the Following (or Favorites) feed, never "For you".
            path == "/" ->
                if (variant == "following" || variant == "favorites") Nav.Allow
                else Nav.Redirect(HOME_URL)
            // The endless Reels feed. A single friend's reel (/reel/<id>/) stays.
            path.startsWith("/reels/") -> Nav.Redirect(HOME_URL)
            // Search stays; the Explore grid, hashtag and place feeds don't.
            path.startsWith("/explore/search/") -> Nav.Allow
            path.startsWith("/explore/") -> Nav.Redirect(SEARCH_URL)
            else -> Nav.Allow
        }
    }

    /**
     * Visible labels of suggestion blocks, in the languages we ship (EN + SR). With
     * the app in Serbian the WebView asks for "sr" and Instagram answers in Cyrillic,
     * so both scripts are listed.
     */
    val SUGGESTED_LABELS = listOf(
        "Suggested for you",
        "Suggested posts",
        "Suggested reels",
        "Predlažemo za vas",
        "Predloženo za vas",
        "Predloženo za tebe",
        "Predložene objave",
        "Предлажемо за вас",
        "Предложено за вас",
        "Предложено за тебе",
        "Предложене објаве"
    )

    /** Labels on a sponsored post: a second signal beside the missing post link. */
    val AD_LABELS = listOf("Sponsored", "Ad", "Sponzorisano", "Спонзорисано")

    override fun isStoriesTray(url: String?): Boolean {
        val uri = url?.let { runCatching { Uri.parse(it) }.getOrNull() } ?: return false
        return isInstagramHost(uri.host) && uri.path.orEmpty().let { it == "" || it == "/" } &&
            uri.query.isNullOrEmpty()
    }

    /**
     * The id of a single post or reel page (/p/<id>/, /reel/<id>/, also under a
     * username), or null for any other page. Path-only so it is unit-testable.
     */
    fun postIdOfPath(rawPath: String): String? =
        POST_PATH.find(rawPath)?.groupValues?.get(2)

    // Ids are case-sensitive: only the /p/ part is matched without case.
    private val POST_PATH = Regex("/(p|reel|tv)/([^/?#]+)", RegexOption.IGNORE_CASE)

    override fun postIdOf(url: String?): String? {
        val uri = url?.let { runCatching { Uri.parse(it) }.getOrNull() } ?: return null
        if (!isInstagramHost(uri.host)) return null
        return postIdOfPath(uri.path.orEmpty())
    }

    override fun isMessages(url: String?): Boolean {
        val uri = url?.let { runCatching { Uri.parse(it) }.getOrNull() } ?: return false
        return isInstagramHost(uri.host) && uri.path.orEmpty().startsWith("/direct/")
    }

    override fun isFeed(url: String?): Boolean {
        val uri = url?.let { runCatching { Uri.parse(it) }.getOrNull() } ?: return false
        return isInstagramHost(uri.host) && uri.path.orEmpty().let { it == "" || it == "/" }
    }

    /**
     * Instagram's half of the page script. Posts are <article>s, told apart by their
     * /p/ or /reel/ link, so ads and hidden posts never count.
     */
    private val ADAPTER: String by lazy {
        val labels = SUGGESTED_LABELS.joinToString(",") { "\"" + it.lowercase() + "\"" }
        val adLabels = AD_LABELS.joinToString(",") { "\"" + it.lowercase() + "\"" }
        """
        // The Explore tab stays: it is the only way to search, and the grid it would
        // open is redirected to plain search by decide().
        var CSS = 'a[href="/reels/"],a[href^="/reels/"]{display:none!important}' +
          '[data-bb-home] header{display:none!important}';
        var INBOX = '/direct/inbox/';
        var labels = [$labels];
        var adLabels = [$adLabels];
        function home(){ return location.pathname === '/'; }
        function posts(){ return document.querySelectorAll('article'); }
        function cardHost(){ return document.querySelector('main'); }
        function postId(a){
          var links = a.querySelectorAll('a[href]');
          for (var i = 0; i < links.length; i++) {
            var m = links[i].getAttribute('href').match(/\/(p|reel)\/([^\/?]+)/);
            if (m) return m[2];
          }
          return null;
        }
        function block(el){
          var art = el.closest('article');
          if (art) return art;
          var best = null, node = el.parentElement, max = window.innerHeight * 1.5;
          for (var i = 0; node && i < 8; i++, node = node.parentElement) {
            if (node.tagName === 'MAIN' || node === document.body) break;
            if (node.offsetHeight > max) break;
            best = node;
          }
          return best;
        }
        function tidy(){
          var spans = document.querySelectorAll('span,h2,h3,div[role="heading"]');
          for (var i = 0; i < spans.length; i++) {
            var t = (spans[i].textContent || '').trim().toLowerCase();
            if (t.length > 40 || labels.indexOf(t) < 0) continue;
            var b = block(spans[i]);
            if (b) off(b);
          }
          // On the feed the stories tray sits above, so Instagram's own header goes.
          if (home()) document.documentElement.setAttribute('data-bb-home', '1');
          else document.documentElement.removeAttribute('data-bb-home');
          if (home()) hideAds();
        }
        // The Following feed carries no ads today; this is the net if that changes.
        // An ad is a post with media but no link to a post (/p/ or /reel/): true in
        // every language. The "Sponsored" label is only a second signal. A post still
        // loading has no link yet either, so the mark is lifted once its link shows up.
        function hideAds(){
          var arts = document.querySelectorAll('article');
          for (var i = 0; i < arts.length; i++) {
            var a = arts[i], ad = a.hasAttribute('data-bb-ad');
            if (postId(a)) {
              if (ad && !hasAdLabel(a)) {
                a.removeAttribute('data-bb-ad');
                if (ended) a.setAttribute('data-bb-end', '1'); else on(a);
              }
              continue;
            }
            if (!ad && (hasAdLabel(a) || a.querySelector('img,video'))) { a.setAttribute('data-bb-ad', '1'); off(a); }
          }
        }
        function hasAdLabel(a){
          var spans = a.querySelectorAll('span,a');
          for (var i = 0; i < spans.length; i++) {
            if (spans[i].childElementCount) continue;
            if (adLabels.indexOf((spans[i].textContent || '').trim().toLowerCase()) >= 0) return true;
          }
          return false;
        }
        """.trimIndent()
    }

    override val injectedJs: String by lazy { CleanFeedJs.build(ADAPTER) }

    /**
     * For the stories WebView: page scrolling off, every fixed bar (header, tab bar,
     * "Use the app") hidden, the page scrolled so the tray sits at the top. The tray
     * is found as the horizontal scroller around a story ring (a canvas inside a
     * button), never by label, so it works in any language. Evaluates to the tray
     * height in CSS px (= dp), or 0 while the tray has not rendered yet.
     */
    override val trayJs = """
        (function(){
          function tray(){
            var ring = document.querySelector('[role="button"] canvas');
            for (var n = ring; n && n !== document.body; n = n.parentElement) {
              var ox = getComputedStyle(n).overflowX;
              if (ox === 'auto' || ox === 'scroll') return n;
            }
            return null;
          }
          function fit(){
            if (!document.getElementById('bb-tray')) {
              var s = document.createElement('style'); s.id = 'bb-tray';
              s.textContent = 'html,body{overflow:hidden!important}';
              (document.head || document.documentElement).appendChild(s);
            }
            var t = tray();
            var all = document.body ? document.body.getElementsByTagName('*') : [];
            for (var i = 0; i < all.length; i++) {
              if (t && (all[i] === t || all[i].contains(t))) continue;
              if (getComputedStyle(all[i]).position === 'fixed') all[i].style.setProperty('display', 'none', 'important');
            }
            if (!t) return 0;
            // The page does not scroll (its layout is one viewport tall), so the tray is
            // pinned to the top of the viewport instead.
            if (!t.hasAttribute('data-bb-pinned')) {
              t.setAttribute('data-bb-pinned', '1');
              var bg = getComputedStyle(document.body).backgroundColor;
              if (!bg || bg === 'rgba(0, 0, 0, 0)') bg = getComputedStyle(document.documentElement).backgroundColor;
              t.style.cssText += ';position:fixed!important;top:0!important;left:0!important;right:0!important;' +
                'z-index:2147483647!important;background-color:' + bg + '!important;';
            }
            return Math.ceil(t.getBoundingClientRect().height);
          }
          if (!window.__bbTray) {
            var queued = false;
            window.__bbTray = fit;
            new MutationObserver(function(){
              if (queued) return; queued = true;
              setTimeout(function(){ queued = false; fit(); }, 300);
            }).observe(document, {childList: true, subtree: true});
          }
          return fit();
        })()
    """.trimIndent()
}
