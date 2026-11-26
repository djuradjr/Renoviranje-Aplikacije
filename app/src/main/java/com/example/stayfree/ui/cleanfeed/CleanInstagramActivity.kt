package com.example.stayfree.ui.cleanfeed

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import android.transition.ChangeBounds
import android.transition.TransitionManager
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebResourceError
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
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
import org.json.JSONObject
import java.util.Date
import javax.inject.Inject

/**
 * "Clean Instagram" (premium): instagram.com in our own WebViews: the stories tray on
 * top, the Following feed below. Reels, Explore and the algorithmic home feed are
 * redirected away ([CleanFeedRules]); posts are capped per session ([ScrollBudget])
 * and the feed ends in a "caught up" card when the cap is spent.
 *
 * Privacy: the page talks to Instagram directly. We expose no JavaScript interface,
 * never read cookies or page content, and only read back a count of posts seen.
 */
@AndroidEntryPoint
class CleanInstagramActivity : AppCompatActivity() {

    @Inject lateinit var prefs: AppPreferences

    private lateinit var binding: ActivityCleanInstagramBinding
    private var limit = ScrollBudget.DEFAULT_LIMIT
    private var session = ScrollSession()
    private var unlockJob: Job? = null
    private var webReady = false
    private var trayReady = false
    private var trayWanted = true
    private var lastFeedUrl: String? = null
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    // Main-frame loads that failed, by URL: retried a couple of times, then left.
    private val failedLoads = mutableMapOf<String, Int>()

    /** Photos for DMs and posts: the page's file input opens the system picker. */
    private val pickFile = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        fileCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data))
        fileCallback = null
    }

    companion object {
        private const val POLL_MS = 1_000L
        private const val TRAY_SCROLL_SLOP = 12
        private const val TRAY_FIT_ATTEMPTS = 20
        private const val LOAD_RETRIES = 2
        private const val TAKE_JS = "window.__bbTake ? window.__bbTake() : 0"
        private const val EXTRA_HOME_ON_CLOSE = "home_on_close"

        /**
         * [homeOnClose]: opened in place of the real Instagram app. Closing must then go
         * to the launcher: falling back to Instagram would trigger the block, which
         * sends the user here again.
         */
        fun newIntent(context: Context, homeOnClose: Boolean = false): Intent =
            Intent(context, CleanInstagramActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_HOME_ON_CLOSE, homeOnClose)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCleanInstagramBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnClose.setOnClickListener { close() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val web = binding.webView
                if (webReady && web.canGoBack()) web.goBack() else close()
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
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    delay(POLL_MS)
                    pollSeenPosts()
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configure(web: WebView) {
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
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, false)
    }

    private fun setUpWebView() {
        val web = binding.webView
        configure(web)
        CookieManager.getInstance().setAcceptCookie(true)
        setUpStories()

        web.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
            // Like the app: the tray slides away while reading down, returns on the way up.
            val dy = scrollY - oldScrollY
            if (dy > TRAY_SCROLL_SLOP && scrollY > binding.webStories.height) showTray(false)
            else if (dy < -TRAY_SCROLL_SLOP || scrollY == 0) showTray(true)
        }
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
                // Back from a story: the tray's rings may have changed.
                if (CleanFeedRules.isFeed(url) && !CleanFeedRules.isFeed(lastFeedUrl)) binding.webStories.reload()
                lastFeedUrl = url
                updateTray()
                render()
            }

            override fun onPageFinished(view: WebView, url: String) {
                view.evaluateJavascript(CleanFeedRules.INJECTED_JS, null)
                render()
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) retryLoad(view, request.url.toString())
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

            override fun onShowFileChooser(
                view: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = callback
                return try {
                    pickFile.launch(params.createIntent())
                    true
                } catch (e: ActivityNotFoundException) {
                    fileCallback = null
                    false
                }
            }
        }
        webReady = true
    }

    /**
     * The tray WebView only ever shows the bare home page, cut down to the stories
     * tray by [CleanFeedRules.TRAY_JS]. Tapping a story (or anything else) opens it in
     * the feed WebView, full screen, and the tray steps back to the home page.
     */
    private fun setUpStories() {
        val tray = binding.webStories
        configure(tray)
        tray.isVerticalScrollBarEnabled = false
        tray.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (!request.isForMainFrame) return false
                val url = request.url.toString()
                if (CleanFeedRules.isStoriesTray(url)) return false
                openInFeed(url)
                return true
            }

            override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                super.doUpdateVisitedHistory(view, url, isReload)
                if (CleanFeedRules.isStoriesTray(url)) return
                openInFeed(url)
                if (view.canGoBack()) view.goBack() else view.loadUrl(CleanFeedRules.STORIES_URL)
            }

            override fun onPageFinished(view: WebView, url: String) = fitTray()

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) retryLoad(view, request.url.toString())
            }

            @RequiresApi(Build.VERSION_CODES.O)
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                finish()
                return true
            }
        }
        tray.loadUrl(CleanFeedRules.STORIES_URL)
    }

    /** The tray renders a moment after the page loads: measure until it is there. */
    private fun fitTray(attempt: Int = 0) {
        binding.webStories.evaluateJavascript(CleanFeedRules.TRAY_JS) { result ->
            val heightDp = result?.toIntOrNull() ?: 0
            if (heightDp <= 0) {
                if (attempt < TRAY_FIT_ATTEMPTS) binding.webStories.postDelayed({ fitTray(attempt + 1) }, 500L)
                return@evaluateJavascript
            }
            val px = (heightDp * resources.displayMetrics.density).toInt()
            binding.webStories.updateLayoutParams { height = px }
            trayReady = true
            updateTray()
        }
    }

    /**
     * Instagram sometimes refuses a load once (seen on a story opened by URL) and
     * WebView would show its bare error page. Try again shortly, a couple of times.
     */
    private fun retryLoad(view: WebView, url: String) {
        val attempt = (failedLoads[url] ?: 0) + 1
        if (attempt > LOAD_RETRIES) return
        failedLoads[url] = attempt
        view.postDelayed({ if (!isDestroyed) view.loadUrl(url) }, 1_000L * attempt)
    }

    private fun close() {
        if (intent.getBooleanExtra(EXTRA_HOME_ON_CLOSE, false)) {
            startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        finish()
    }

    private fun openInFeed(url: String) {
        if (!handle(binding.webView, url)) binding.webView.loadUrl(url)
    }

    private fun showTray(show: Boolean) {
        if (trayWanted == show) return
        trayWanted = show
        updateTray()
    }

    /** Above the feed only: a story, messages or a profile get the whole screen. */
    private fun updateTray() {
        val show = trayReady && trayWanted && CleanFeedRules.isFeed(binding.webView.url)
        if (binding.webStories.isVisible == show) return
        TransitionManager.beginDelayedTransition(binding.root as ViewGroup, ChangeBounds().setDuration(180L))
        binding.webStories.isVisible = show
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

    /** The page counts friends' posts as they scroll into view; we persist them. */
    private fun pollSeenPosts() {
        binding.webView.evaluateJavascript(TAKE_JS) { result ->
            val posts = result?.toIntOrNull() ?: 0
            if (posts <= 0) return@evaluateJavascript
            lifecycleScope.launch {
                session = prefs.updateCleanFeedSession { ScrollBudget.addPosts(it, posts, limit, now()) }
                render()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!webReady) return
        binding.webView.onResume()
        binding.webStories.onResume()
        lifecycleScope.launch {
            limit = prefs.cleanFeedLimit.first()
            session = prefs.updateCleanFeedSession { ScrollBudget.current(it, now()) }
            render()
        }
    }

    override fun onPause() {
        super.onPause()
        if (webReady) {
            binding.webView.onPause()
            binding.webStories.onPause()
        }
        CookieManager.getInstance().flush()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (webReady) binding.webView.saveState(outState)
    }

    override fun onDestroy() {
        unlockJob?.cancel()
        fileCallback?.onReceiveValue(null)
        binding.webView.apply {
            stopLoading()
            webChromeClient = null
            destroy()
        }
        binding.webStories.apply {
            stopLoading()
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

        // Spent: the feed ends in a "caught up" card; stories and messages stay usable.
        val card = JSONObject()
            .put("title", getString(R.string.clean_feed_end_title))
            .put("message", if (!locked) "" else getString(
                R.string.clean_feed_end_message,
                DateFormat.getTimeFormat(this).format(Date(session.lockedUntil))
            ))
            .put("messages", getString(R.string.clean_feed_messages))
            .put("stories", getString(R.string.clean_feed_stories))
            .put("accent", String.format("#%06X", 0xFFFFFF and ContextCompat.getColor(this, R.color.skor_focus)))
        binding.webView.evaluateJavascript("window.__bbSet && window.__bbSet($remaining, $card)", null)
        if (locked) scheduleUnlock(session.lockedUntil - now)
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

    private fun now() = System.currentTimeMillis()
}
