package com.example.stayfree.ui.cleanfeed

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import android.view.View
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.stayfree.R
import com.example.stayfree.data.local.preferences.AppPreferences
import com.example.stayfree.databinding.ActivityCleanInstagramBinding
import com.example.stayfree.domain.cleanfeed.CleanFeedRules
import com.example.stayfree.domain.cleanfeed.CleanFeedRules.Nav
import com.example.stayfree.domain.cleanfeed.ScrollBudget
import com.example.stayfree.domain.cleanfeed.ScrollSession
import com.example.stayfree.ui.premium.PaywallActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

/**
 * "Clean Instagram" (premium): instagram.com in our own WebView, opened on the
 * Following feed only. Reels, Explore and the algorithmic home feed are redirected
 * away ([CleanFeedRules]); scrolling is capped per session ([ScrollBudget]) and a
 * calm cover takes over the feed when the cap is spent — messages stay open.
 *
 * Privacy: the page talks to Instagram directly. We expose no JavaScript interface,
 * never read cookies or page content, and only measure scroll distance.
 */
@AndroidEntryPoint
class CleanInstagramActivity : AppCompatActivity() {

    @Inject lateinit var prefs: AppPreferences

    private lateinit var binding: ActivityCleanInstagramBinding
    private var limit = ScrollBudget.DEFAULT_LIMIT
    private var session = ScrollSession()
    // Scrolled pixels not yet worth a whole post.
    private var pendingPx = 0f
    private var unlockJob: Job? = null
    private var webReady = false

    companion object {
        fun newIntent(context: Context): Intent =
            Intent(context, CleanInstagramActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCleanInstagramBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnClose.setOnClickListener { finish() }
        binding.btnCoverExit.setOnClickListener { goHome() }
        binding.btnCoverMessages.setOnClickListener { binding.webView.loadUrl(CleanFeedRules.INBOX_URL) }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val web = binding.webView
                if (webReady && web.canGoBack() && !binding.cover.isVisible) web.goBack() else finish()
            }
        })

        lifecycleScope.launch {
            // Same gate as every block: the clean feed is a premium feature.
            if (!prefs.premiumActive.first()) {
                startActivity(PaywallActivity.newIntent(this@CleanInstagramActivity))
                finish()
                return@launch
            }
            limit = prefs.cleanFeedLimit.first()
            session = prefs.updateCleanFeedSession { ScrollBudget.current(it, now()) }
            setUpWebView()
            // Rotation or a process restore keeps the page the user was on.
            if (savedInstanceState?.let { binding.webView.restoreState(it) } == null) {
                binding.webView.loadUrl(CleanFeedRules.HOME_URL)
            }
            render()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setUpWebView() {
        val web = binding.webView
        web.settings.apply {
            javaScriptEnabled = true          // instagram.com does not render without it
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mediaPlaybackRequiresUserGesture = true
            setSupportMultipleWindows(false)
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            // Instagram serves a degraded page to anything marked as an embedded WebView.
            userAgentString = userAgentString.replace("; wv", "")
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, false)

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (!request.isForMainFrame) return false
                return handle(view, request.url.toString())
            }

            // In-site navigation (pushState) never reaches shouldOverrideUrlLoading.
            override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                super.doUpdateVisitedHistory(view, url, isReload)
                val nav = CleanFeedRules.decide(url)
                if (nav is Nav.Redirect && nav.url != url) view.loadUrl(nav.url)
                view.evaluateJavascript(CleanFeedRules.INJECTED_JS, null)
                pendingPx = 0f
                render()
            }

            override fun onPageFinished(view: WebView, url: String) {
                view.evaluateJavascript(CleanFeedRules.INJECTED_JS, null)
                render()
            }

            @RequiresApi(Build.VERSION_CODES.O)
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                // The renderer died (memory pressure); this WebView is unusable now.
                finish()
                return true
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                binding.progress.isVisible = newProgress < 100
                binding.progress.setProgressCompat(newProgress, true)
            }
        }
        web.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
            val dy = scrollY - oldScrollY
            if (dy > 0 && !session.isLocked(now()) && CleanFeedRules.countsTowardLimit(web.url)) {
                onScrolledDown(dy)
            }
        }
        webReady = true
    }

    /** Returns true when the WebView must NOT load [url] itself. */
    private fun handle(view: WebView, url: String): Boolean = when (val nav = CleanFeedRules.decide(url)) {
        Nav.Allow -> false
        is Nav.Redirect -> { view.loadUrl(nav.url); true }
        Nav.Ignore -> true
        Nav.External -> {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (e: ActivityNotFoundException) { /* no browser: drop the link */ }
            true
        }
    }

    private fun onScrolledDown(dy: Int) {
        val postPx = binding.webView.height * ScrollBudget.POST_SCREEN_FRACTION
        if (postPx <= 0f) return
        pendingPx += dy
        val posts = (pendingPx / postPx).toInt()
        if (posts <= 0) return
        pendingPx -= posts * postPx
        lifecycleScope.launch {
            session = prefs.updateCleanFeedSession { ScrollBudget.addPosts(it, posts, limit, now()) }
            render()
        }
    }

    override fun onResume() {
        super.onResume()
        if (!webReady) return
        binding.webView.onResume()
        lifecycleScope.launch {
            limit = prefs.cleanFeedLimit.first()
            session = prefs.updateCleanFeedSession { ScrollBudget.current(it, now()) }
            render()
        }
    }

    override fun onPause() {
        super.onPause()
        if (webReady) binding.webView.onPause()
        CookieManager.getInstance().flush()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (webReady) binding.webView.saveState(outState)
    }

    override fun onDestroy() {
        unlockJob?.cancel()
        binding.webView.apply {
            stopLoading()
            webChromeClient = null
            destroy()
        }
        super.onDestroy()
    }

    private fun render() {
        val now = now()
        val locked = session.isLocked(now)
        val remaining = ScrollBudget.remaining(session, limit, now)

        binding.tvCounter.text = getString(R.string.clean_feed_counter, limit - remaining, limit)
        binding.tvCounter.contentDescription =
            getString(R.string.clean_feed_counter_cd, limit - remaining, limit)
        binding.tvCounter.setTextColor(
            ContextCompat.getColor(this, if (remaining <= 3) R.color.skor_warn else R.color.skor_text_dim)
        )

        // Spent: the feed rests, messages stay usable.
        val cover = locked && !CleanFeedRules.isMessages(binding.webView.url)
        if (cover && !binding.cover.isVisible) {
            binding.cover.alpha = 0f
            binding.cover.isVisible = true
            binding.cover.animate().alpha(1f).setDuration(200).start()
        } else if (!cover) {
            binding.cover.isVisible = false
        }
        if (locked) {
            binding.tvCoverTitle.text = getString(R.string.clean_feed_cover_title, limit)
            binding.tvCoverMessage.text = getString(
                R.string.clean_feed_cover_message,
                DateFormat.getTimeFormat(this).format(Date(session.lockedUntil))
            )
            scheduleUnlock(session.lockedUntil - now)
        }
    }

    /** The pause can end while the screen is open: lift the cover on time. */
    private fun scheduleUnlock(inMs: Long) {
        unlockJob?.cancel()
        unlockJob = lifecycleScope.launch {
            delay(inMs.coerceAtLeast(0L) + 500L)
            session = prefs.updateCleanFeedSession { ScrollBudget.current(it, now()) }
            render()
        }
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
        finish()
    }

    private fun now() = System.currentTimeMillis()
}
