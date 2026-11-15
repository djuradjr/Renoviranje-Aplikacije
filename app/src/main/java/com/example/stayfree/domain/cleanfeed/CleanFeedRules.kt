package com.example.stayfree.domain.cleanfeed

import android.net.Uri

/**
 * THE single source of rules for the clean Instagram feed (instagram.com in our own
 * WebView). Instagram changes its site often — when Reels or suggestions leak back
 * in, edit ONLY this file.
 *
 * Two layers, so a site redesign degrades gracefully instead of breaking:
 * - navigation ([decide]): paths, checked on every page and in-site (pushState)
 *   navigation. Survives redesigns because URLs rarely change.
 * - cosmetics ([INJECTED_JS]): hides the Reels tab and "Suggested" blocks.
 *   Targets link targets and visible labels only, never Instagram's generated
 *   class names.
 */
object CleanFeedRules {

    const val INSTAGRAM_PACKAGE = "com.instagram.android"

    /** The chronological feed of followed accounts only. */
    const val HOME_URL = "https://www.instagram.com/?variant=following"
    const val INBOX_URL = "https://www.instagram.com/direct/inbox/"
    /** Search without the Explore grid underneath it. */
    const val SEARCH_URL = "https://www.instagram.com/explore/search/"

    sealed interface Nav {
        /** Let the WebView load it. */
        data object Allow : Nav
        /** Load [url] instead (Reels, Explore, the algorithmic home feed). */
        data class Redirect(val url: String) : Nav
        /** A normal link out of Instagram — hand it to the browser. */
        data object External : Nav
        /** Drop it: app deep links (intent:, instagram://) would open the real app. */
        data object Ignore : Nav
    }

    // Login can bounce through Facebook ("Log in with Facebook", some checkpoints).
    private val LOGIN_HOSTS = setOf("facebook.com", "www.facebook.com", "m.facebook.com")

    fun isInstagramHost(host: String?): Boolean {
        val h = host?.lowercase() ?: return false
        return h == "instagram.com" || h.endsWith(".instagram.com")
    }

    fun decide(url: String): Nav {
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

    /** Scrolling counts toward the limit everywhere except messages and account pages. */
    fun countsTowardLimit(url: String?): Boolean {
        val path = url?.let { runCatching { Uri.parse(it).path }.getOrNull() }.orEmpty().lowercase()
        return !path.startsWith("/direct/") && !path.startsWith("/accounts/")
    }

    /** Messages stay usable after the scroll limit is spent. */
    fun isMessages(url: String?): Boolean {
        val path = url?.let { runCatching { Uri.parse(it).path }.getOrNull() }.orEmpty().lowercase()
        return path.startsWith("/direct/")
    }

    /** Visible labels of suggestion blocks, in the languages we ship (EN + SR). */
    val SUGGESTED_LABELS = listOf(
        "Suggested for you",
        "Suggested posts",
        "Suggested reels",
        "Predloženo za vas",
        "Predloženo za tebe",
        "Predložene objave"
    )

    /**
     * Idempotent: safe to run on every page load and in-site navigation. A
     * MutationObserver keeps re-applying it while Instagram renders more feed.
     * No JavaScript interface is exposed to the page — this only hides things.
     */
    val INJECTED_JS: String by lazy {
        val labels = SUGGESTED_LABELS.joinToString(",") { "\"" + it.lowercase() + "\"" }
        """
        (function(){
          if (window.__bbClean) { window.__bbClean(); return; }
          var css = 'a[href="/reels/"],a[href^="/reels/"],a[href="/explore/"]:not([href^="/explore/search"])' +
            '{display:none!important}';
          function style(){
            if (document.getElementById('bb-clean')) return;
            var s = document.createElement('style'); s.id = 'bb-clean'; s.textContent = css;
            (document.head || document.documentElement).appendChild(s);
          }
          var labels = [$labels];
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
          function hide(){
            style();
            var spans = document.querySelectorAll('span,h2,h3,div[role="heading"]');
            for (var i = 0; i < spans.length; i++) {
              var t = (spans[i].textContent || '').trim().toLowerCase();
              if (t.length > 40 || labels.indexOf(t) < 0) continue;
              var b = block(spans[i]);
              if (b) b.style.setProperty('display', 'none', 'important');
            }
          }
          var queued = false;
          window.__bbClean = function(){
            if (queued) return; queued = true;
            setTimeout(function(){ queued = false; hide(); }, 250);
          };
          hide();
          new MutationObserver(window.__bbClean)
            .observe(document.documentElement, {childList: true, subtree: true});
        })();
        """.trimIndent()
    }
}
