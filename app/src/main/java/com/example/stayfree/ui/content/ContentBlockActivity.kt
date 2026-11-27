package com.example.stayfree.ui.content

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewOutlineProvider
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.stayfree.R
import com.example.stayfree.data.local.preferences.AppPreferences
import com.example.stayfree.databinding.ActivityContentBlockBinding
import com.example.stayfree.domain.cleanfeed.InstagramNetwork
import com.example.stayfree.domain.content.ContentBlockTarget
import com.example.stayfree.ui.cleanfeed.CleanFeedEntry
import com.example.stayfree.ui.unlock.ShortUnlockActivity
import com.example.stayfree.util.AppInfoUtils
import com.example.stayfree.util.StrictGate
import com.example.stayfree.util.UnlockGate
import com.example.stayfree.util.TimeUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Hard-block screen shown the moment a blocked content surface (Reels, Stories,
 * Shorts, TikTok) is detected. The accessibility service optionally presses Back
 * first (so the host saves a non-blocked last state), then starts this activity
 * over it. Exit and Back both go home; the only way back in is a short unlock.
 */
@AndroidEntryPoint
class ContentBlockActivity : AppCompatActivity() {

    @Inject lateinit var prefs: AppPreferences
    @Inject lateinit var strictGate: StrictGate
    @Inject lateinit var unlockGate: UnlockGate

    companion object {
        const val EXTRA_DISPLAY_NAME = "extra_display_name"
        private const val EXTRA_PACKAGE = "extra_package"
        private const val EXTRA_TARGET_ID = "extra_target_id"
        private const val STATE_LINE = "state_line"

        // TikTok opens its next screen over the block right after its splash, so the
        // service re-fires within seconds; keeping the pick stops a visible swap.
        private const val SAME_BLOCK_WINDOW_MS = 10_000L
        private var lastLine = -1
        private var lastPickAt = 0L

        /** Motivation line: random, never repeated across separate blocks. */
        private fun nextLine(lineCount: Int): Int {
            val now = SystemClock.elapsedRealtime()
            val refire = lastLine in 0 until lineCount && now - lastPickAt < SAME_BLOCK_WINDOW_MS
            lastPickAt = now
            if (!refire) lastLine = randomExcept(lineCount, lastLine)
            return lastLine
        }

        private fun randomExcept(size: Int, previous: Int): Int {
            var i: Int
            do i = (0 until size).random() while (i == previous && size > 1)
            return i
        }

        fun newIntent(context: Context, target: ContentBlockTarget): Intent =
            Intent(context, ContentBlockActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION
                putExtra(EXTRA_DISPLAY_NAME, target.displayName)
                putExtra(EXTRA_PACKAGE, target.packageName)
                putExtra(EXTRA_TARGET_ID, target.id)
            }
    }

    private lateinit var binding: ActivityContentBlockBinding
    private var line = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        // White copy over a dark photo: the light app theme would turn the text dark.
        delegate.localNightMode = AppCompatDelegate.MODE_NIGHT_YES
        super.onCreate(savedInstanceState)
        binding = ActivityContentBlockBinding.inflate(layoutInflater)
        setContentView(binding.root)
        goImmersive()
        if (savedInstanceState == null) lifecycleScope.launch { prefs.incrementBlocksShown() }

        val lines = resources.getStringArray(R.array.content_block_motivation)
        line = savedInstanceState?.getInt(STATE_LINE, -1)?.takeIf { it in lines.indices } ?: nextLine(lines.size)
        binding.tvMotivation.text = lines[line]

        val displayName = intent.getStringExtra(EXTRA_DISPLAY_NAME) ?: getString(R.string.app_name)
        binding.tvTitle.text = getString(R.string.content_block_title, displayName)
        binding.appMark.ivAppIcon.apply {
            outlineProvider = ViewOutlineProvider.BACKGROUND
            clipToOutline = true
            setImageDrawable(intent.getStringExtra(EXTRA_PACKAGE)?.let { AppInfoUtils.getAppIcon(this@ContentBlockActivity, it) })
        }

        // The service increments the counter before starting us, so this read
        // already includes the block the user is looking at.
        lifecycleScope.launch {
            val (date, count) = prefs.contentBlockCount.first()
            if (date == TimeUtils.getTodayString() && count > 0) {
                binding.tvBlockCount.text = getString(R.string.content_block_count, count)
                binding.tvBlockCountCaption.text = getString(R.string.content_block_count_caption)
                binding.cardCount.visibility = View.VISIBLE
            }
        }

        lifecycleScope.launch {
            val strictMs = strictGate.remainingMs()
            if (strictMs > 0L) {
                binding.tvStrict.text =
                    getString(R.string.strict_block_screen_line, TimeUtils.formatLongDuration(strictMs))
                binding.tvStrict.visibility = View.VISIBLE
            }
        }

        if (intent.getStringExtra(EXTRA_PACKAGE) == InstagramNetwork.INSTAGRAM_PACKAGE) {
            binding.cleanFeed.root.visibility = View.VISIBLE
            binding.tvCleanCaption.visibility = View.VISIBLE
            CleanFeedEntry.bind(this, binding.cleanFeed.root, binding.cleanFeed.chipCleanPro, lifecycleScope, prefs)
        }

        binding.btnExit.setOnClickListener { goHome() }
        binding.btnUnlock.setOnClickListener { openUnlock() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goHome()
        })
    }

    // singleTask: a block that fires while this screen is still alive (left with Home, not
    // Exit) arrives here instead of onCreate.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        lifecycleScope.launch { prefs.incrementBlocksShown() }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            val day = unlockGate.today()
            binding.tvUnlockTally.isVisible = day.count > 0
            binding.tvUnlockTally.text = getString(R.string.unlock_today_tally, day.count, day.minutes)
        }
    }

    private fun openUnlock() {
        val pkg = intent.getStringExtra(EXTRA_PACKAGE) ?: return
        startActivity(ShortUnlockActivity.newIntent(this, pkg, intent.getStringExtra(EXTRA_TARGET_ID)))
        finish()
        overridePendingTransition(0, 0)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_LINE, line)
    }

    private fun goHome() {
        val home = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(home)
        finish()
        overridePendingTransition(0, 0)
    }

    private fun goImmersive() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.hide(
                android.view.WindowInsets.Type.statusBars() or
                        android.view.WindowInsets.Type.navigationBars()
            )
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }
    }
}
