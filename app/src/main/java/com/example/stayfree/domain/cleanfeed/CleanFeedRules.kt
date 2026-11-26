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
 * - cosmetics ([INJECTED_JS]): hides the Reels tab and "Suggested" blocks, counts
 *   posts and ends the feed at the limit. Targets link targets and visible labels
 *   only, never Instagram's generated class names.
 *
 * The stories tray lives only on the algorithmic home page, so it is shown from a
 * second, tray-sized WebView ([STORIES_URL], [TRAY_JS]); the feed itself is the
 * Following page. Hiding non-friend posts inside the home feed instead breaks
 * Instagram's virtual list (the feed jumps by screens while scrolling).
 */
object CleanFeedRules {

    const val INSTAGRAM_PACKAGE = "com.instagram.android"

    /** The chronological feed of followed accounts only: no suggestions, no ads. */
    const val HOME_URL = "https://www.instagram.com/?variant=following"
    /** The algorithmic home page, loaded only for its stories tray. */
    const val STORIES_URL = "https://www.instagram.com/"
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

    /** The stories WebView shows only the bare home page; anything else opens in the feed. */
    fun isStoriesTray(url: String?): Boolean {
        val uri = url?.let { runCatching { Uri.parse(it) }.getOrNull() } ?: return false
        return isInstagramHost(uri.host) && uri.path.orEmpty().let { it == "" || it == "/" } &&
            uri.query.isNullOrEmpty()
    }

    const val INBOX_URL = "https://www.instagram.com/direct/inbox/"
    const val ACTIVITY_URL = "https://www.instagram.com/accounts/activity/"

    /**
     * The id of a single post or reel page (/p/<id>/, /reel/<id>/, also under a
     * username), or null for any other page. Path-only so it is unit-testable.
     */
    fun postIdOfPath(rawPath: String): String? =
        POST_PATH.find(rawPath)?.groupValues?.get(2)

    // Ids are case-sensitive: only the /p/ part is matched without case.
    private val POST_PATH = Regex("/(p|reel|tv)/([^/?#]+)", RegexOption.IGNORE_CASE)

    fun postIdOf(url: String?): String? {
        val uri = url?.let { runCatching { Uri.parse(it) }.getOrNull() } ?: return null
        if (!isInstagramHost(uri.host)) return null
        return postIdOfPath(uri.path.orEmpty())
    }

    /** Messages: a post opened from here is always allowed and never counted. */
    fun isMessages(url: String?): Boolean {
        val uri = url?.let { runCatching { Uri.parse(it) }.getOrNull() } ?: return false
        return isInstagramHost(uri.host) && uri.path.orEmpty().startsWith("/direct/")
    }

    /** The feed page, where the stories tray belongs above the posts. */
    fun isFeed(url: String?): Boolean {
        val uri = url?.let { runCatching { Uri.parse(it) }.getOrNull() } ?: return false
        return isInstagramHost(uri.host) && uri.path.orEmpty().let { it == "" || it == "/" }
    }

    /**
     * Idempotent: safe to run on every page load and in-site navigation. A
     * MutationObserver keeps re-applying it while Instagram renders more feed.
     * No JavaScript interface is exposed to the page: it hides things and counts
     * posts on the feed (by post id, so ads and hidden posts never count). At the
     * limit the feed ends in a "caught up" card.
     */
    val INJECTED_JS: String by lazy {
        val labels = SUGGESTED_LABELS.joinToString(",") { "\"" + it.lowercase() + "\"" }
        val adLabels = AD_LABELS.joinToString(",") { "\"" + it.lowercase() + "\"" }
        """
        (function(){
          if (window.__bbClean) { window.__bbClean(); return; }
          // The Explore tab stays: it is the only way to search, and the grid it would
          // open is redirected to plain search by decide().
          var css = 'a[href="/reels/"],a[href^="/reels/"]{display:none!important}' +
            '[data-bb-endlist]{box-sizing:border-box!important;padding-bottom:0!important;' +
            'max-height:var(--bb-max)!important;overflow:hidden!important}' +
            '[data-bb-endwrap]~*{display:none!important}' +
            '[data-bb-home] header{display:none!important}';
          function style(){
            if (document.getElementById('bb-clean')) return;
            var s = document.createElement('style'); s.id = 'bb-clean'; s.textContent = css;
            (document.head || document.documentElement).appendChild(s);
          }
          var labels = [$labels];
          var adLabels = [$adLabels];
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
          // Collapsed to 1px, never display:none: the feed is a virtual list that
          // treats a 0px post as unmeasured, keeps its estimated height and leaves
          // a screens-long blank gap.
          var OFF = ['height', 'min-height', 'overflow', 'visibility', 'margin', 'padding', 'border'];
          var OFF_VAL = ['1px', '0', 'hidden', 'hidden', '0', '0', '0'];
          function off(el){
            if (el.hasAttribute('data-bb-off')) return;
            el.setAttribute('data-bb-off', '1');
            for (var i = 0; i < OFF.length; i++) el.style.setProperty(OFF[i], OFF_VAL[i], 'important');
          }
          function on(el){
            el.removeAttribute('data-bb-off');
            for (var i = 0; i < OFF.length; i++) el.style.removeProperty(OFF[i]);
          }
          function home(){ return location.pathname === '/'; }
          function hide(){
            style();
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

          // Post budget. The app pulls the count with __bbTake() and pushes what is
          // left with __bbSet(); the page never calls into the app.
          var seen = {}, seenList = [], fresh = 0, budget = -1, ended = false, cfg = {};
          // Kept for the tab's life: opening a story or a profile reloads the feed, and
          // the posts on top must not be counted again each time.
          try {
            seenList = (sessionStorage.getItem('bb-seen') || '').split(',').filter(Boolean);
            for (var k = 0; k < seenList.length; k++) seen[seenList[k]] = 1;
          } catch (e) {}
          function markSeen(id){
            seen[id] = 1; seenList.push(id);
            if (seenList.length > 500) seenList = seenList.slice(-500);
            try { sessionStorage.setItem('bb-seen', seenList.join(',')); } catch (e) {}
          }
          function postId(a){
            var links = a.querySelectorAll('a[href]');
            for (var i = 0; i < links.length; i++) {
              var m = links[i].getAttribute('href').match(/\/(p|reel)\/([^\/?]+)/);
              if (m) return m[2];
            }
            return null;
          }
          function shown(a){ return !a.hasAttribute('data-bb-off'); }
          function track(){
            if (!home() || budget < 0 || ended) return;
            hide();
            var arts = document.querySelectorAll('article'), line = window.innerHeight * 0.5;
            for (var i = 0; i < arts.length && fresh < budget; i++) {
              if (!shown(arts[i]) || arts[i].getBoundingClientRect().top > line) continue;
              var id = postId(arts[i]);
              if (!id || seen[id]) continue;
              markSeen(id); fresh++;
            }
            if (fresh >= budget) end();
          }
          function card(){
            var c = document.getElementById('bb-end');
            if (!c) {
              c = document.createElement('div'); c.id = 'bb-end';
              c.style.cssText = 'padding:48px 24px;text-align:center;';
              var ring = document.createElement('div');
              ring.style.cssText = 'width:64px;height:64px;margin:0 auto 16px;border-radius:50%;' +
                'border:2px solid;display:flex;align-items:center;justify-content:center;font-size:30px;';
              ring.textContent = '✓';
              var title = document.createElement('div');
              title.style.cssText = 'font-size:18px;font-weight:700;';
              var msg = document.createElement('div');
              msg.style.cssText = 'margin-top:8px;font-size:14px;opacity:.65;';
              var row = document.createElement('div');
              row.style.cssText = 'margin-top:20px;display:flex;gap:12px;justify-content:center;';
              var btn = 'padding:10px 20px;border-radius:10px;font-size:14px;font-weight:600;' +
                'background:transparent;color:inherit;border:1px solid currentColor;text-decoration:none;';
              var dm = document.createElement('a'); dm.href = '/direct/inbox/'; dm.style.cssText = btn;
              var st = document.createElement('button'); st.style.cssText = btn;
              // Instant: a smooth scroll fights the virtual list and stops halfway.
              st.onclick = function(){ window.scrollTo(0, 0); };
              row.appendChild(dm); row.appendChild(st);
              c.appendChild(ring); c.appendChild(title); c.appendChild(msg); c.appendChild(row);
            }
            // Rewriting equal text would wake the MutationObserver into a loop.
            function txt(el, v){ v = v || ''; if (el.textContent !== v) el.textContent = v; }
            var parts = c.children;
            parts[0].style.color = cfg.accent || 'inherit';
            txt(parts[1], cfg.title);
            txt(parts[2], cfg.message);
            txt(parts[3].children[0], cfg.messages);
            txt(parts[3].children[1], cfg.stories);
            return c;
          }
          // The feed stops after the last post the user has seen; everything below
          // is hidden and the card takes its place.
          function endPass(){
            var arts = document.querySelectorAll('article'), last = null;
            for (var i = 0; i < arts.length; i++) {
              if (seen[postId(arts[i])]) { if (shown(arts[i])) last = arts[i]; continue; }
              if (shown(arts[i])) { arts[i].setAttribute('data-bb-end', '1'); off(arts[i]); }
            }
            var c = card();
            if (last) { if (last.nextSibling !== c) last.after(c); }
            else if (arts.length) { if (arts[0].previousSibling !== c) arts[0].before(c); }
            else if (!c.isConnected) { var m = document.querySelector('main'); if (m) m.appendChild(c); }
            clip();
          }
          // The list keeps a padding for posts it has fetched but not drawn, which
          // would be blank space below the card: cut the list off at the card.
          function clip(){
            var c = document.getElementById('bb-end');
            if (!c || !c.parentElement) return;
            var list = c.parentElement;
            if (!list.hasAttribute('data-bb-endlist')) {
              unclip();  // the card moved: never leave an old list cut off
              list.setAttribute('data-bb-endlist', '1');
              // The "loading more" spinner sits after the list and would spin forever.
              if (list.parentElement) list.parentElement.setAttribute('data-bb-endwrap', '1');
            }
            list.style.setProperty('--bb-max', (c.offsetTop + c.offsetHeight) + 'px');
          }
          function unclip(){
            var marked = document.querySelectorAll('[data-bb-endlist],[data-bb-endwrap]');
            for (var i = 0; i < marked.length; i++) {
              marked[i].removeAttribute('data-bb-endlist'); marked[i].removeAttribute('data-bb-endwrap');
            }
          }
          function end(){ ended = true; if (home()) endPass(); }
          function reopen(){
            ended = false;
            var hidden = document.querySelectorAll('article[data-bb-end]');
            for (var i = 0; i < hidden.length; i++) {
              hidden[i].removeAttribute('data-bb-end'); on(hidden[i]);
            }
            var c = document.getElementById('bb-end'); if (c) c.remove();
            unclip();
            hide();
          }
          // A post opened outside the feed (search, a profile, notifications): the app
          // counts it only if the feed has not already.
          window.__bbMarkSeen = function(id){
            if (seen[id]) return 0;
            markSeen(id);
            return 1;
          };
          // Polled every second: also the safety net that re-applies the end state and
          // counts posts already on screen without a scroll.
          window.__bbTake = function(){
            if (ended) { if (home()) endPass(); } else track();
            var n = fresh; fresh = 0;
            if (budget > 0) budget = Math.max(budget - n, 0);
            return n;
          };
          window.__bbSet = function(remaining, c){
            cfg = c; budget = remaining;
            if (remaining <= 0) end();
            else if (ended) reopen();
            if (ended && home()) card();
            track();
          };

          var queued = false;
          window.__bbClean = function(){
            if (ended && home()) endPass();
            if (queued) return; queued = true;
            setTimeout(function(){ queued = false; hide(); }, 250);
          };
          hide();
          // The document itself, not documentElement: injected at commit time the
          // page's <html> may not be the final one yet, and the observer would go deaf.
          new MutationObserver(window.__bbClean)
            .observe(document, {childList: true, subtree: true});
          window.addEventListener('scroll', function(){ if (ended) clip(); else track(); }, {passive: true});
        })();
        """.trimIndent()
    }

    /**
     * For the stories WebView: page scrolling off, every fixed bar (header, tab bar,
     * "Use the app") hidden, the page scrolled so the tray sits at the top. The tray
     * is found as the horizontal scroller around a story ring (a canvas inside a
     * button), never by label, so it works in any language. Evaluates to the tray
     * height in CSS px (= dp), or 0 while the tray has not rendered yet.
     */
    val TRAY_JS = """
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
