package com.example.stayfree.domain.cleanfeed

/**
 * The page script every clean feed shares: the post budget, the "caught up" card
 * at the limit and cutting the feed off below it. A network plugs in an adapter
 * (plain JavaScript, same scope) that defines:
 * - `CSS` (extra rules) and `INBOX` (path of the messages page),
 * - `home()` (is this the feed page), `posts()` (post elements, page order),
 * - `postId(post)` (stable id, or null while it cannot be told yet),
 * - `tidy()` (hide what the network must not show), `cardHost()` (where the card
 *   goes when the page has no post at all).
 * The adapter may use the core's `off(el)`, `on(el)`, `shown(el)` and `ended`.
 *
 * Idempotent: safe to run on every page load and in-site navigation. No JavaScript
 * interface is exposed to the page: the app pulls the count with `__bbTake()` and
 * pushes what is left with `__bbSet()`.
 */
object CleanFeedJs {

    fun build(adapter: String): String =
        "(function(){\n" +
            "if (window.__bbClean) { window.__bbClean(); return; }\n" +
            adapter + "\n" + CORE + "\n})();"

    private val CORE = """
        var css = CSS + '[data-bb-endlist]{box-sizing:border-box!important;padding-bottom:0!important;' +
          'max-height:var(--bb-max)!important;overflow:hidden!important}' +
          '[data-bb-endwrap]~*{display:none!important}';
        function style(){
          if (document.getElementById('bb-clean')) return;
          var s = document.createElement('style'); s.id = 'bb-clean'; s.textContent = css;
          (document.head || document.documentElement).appendChild(s);
        }
        function hide(){ style(); tidy(); }
        // Collapsed to 1px, never display:none: a feed that is a virtual list treats
        // a 0px post as unmeasured, keeps its estimated height and leaves a
        // screens-long blank gap.
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
        function shown(a){ return !a.hasAttribute('data-bb-off'); }
        function track(){
          if (!home() || budget < 0 || ended) return;
          hide();
          var arts = posts(), line = window.innerHeight * 0.5;
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
            var dm = document.createElement('a'); dm.href = INBOX; dm.style.cssText = btn;
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
        // is hidden and the card takes its place. That post is remembered: a feed
        // may empty posts far off screen, and an emptied post has no id.
        var endAt = null;
        function endPass(){
          var arts = posts(), at = -1, last = null;
          for (var i = 0; i < arts.length; i++) {
            if (arts[i] === endAt || (seen[postId(arts[i])] && shown(arts[i]))) at = i;
          }
          for (var j = at + 1; j < arts.length; j++) {
            if (shown(arts[j])) { arts[j].setAttribute('data-bb-end', '1'); off(arts[j]); }
          }
          if (at >= 0) last = endAt = arts[at];
          var c = card();
          if (last) { if (last.nextSibling !== c) last.after(c); }
          else if (arts.length) { if (arts[0].previousSibling !== c) arts[0].before(c); }
          else if (!c.isConnected) { var m = cardHost(); if (m) m.appendChild(c); }
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
          ended = false; endAt = null;
          var hidden = document.querySelectorAll('[data-bb-end]');
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
    """.trimIndent()
}
