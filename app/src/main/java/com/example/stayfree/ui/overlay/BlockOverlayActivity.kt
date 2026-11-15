package com.example.stayfree.ui.overlay

import android.content.Intent
import android.os.Bundle
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.stayfree.R
import com.example.stayfree.data.local.preferences.AppPreferences
import com.example.stayfree.databinding.ActivityBlockOverlayBinding
import com.example.stayfree.domain.cleanfeed.CleanFeedRules
import com.example.stayfree.ui.cleanfeed.CleanInstagramActivity
import com.example.stayfree.util.AppInfoUtils
import com.example.stayfree.util.PinGate
import com.example.stayfree.util.PinPrompt
import com.example.stayfree.ui.unlock.ShortUnlockActivity
import com.example.stayfree.util.StrictGate
import com.example.stayfree.util.UnlockGate
import com.example.stayfree.util.TimeUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BlockOverlayActivity : AppCompatActivity() {

    @Inject lateinit var prefs: AppPreferences
    @Inject lateinit var pinGate: PinGate
    @Inject lateinit var strictGate: StrictGate
    @Inject lateinit var unlockGate: UnlockGate

    private lateinit var binding: ActivityBlockOverlayBinding
    private var packageName_: String = ""
    private var blockReason: String = ""

    companion object {
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_BLOCK_REASON = "extra_block_reason"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBlockOverlayBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setFinishOnTouchOutside(false)
        if (savedInstanceState == null) lifecycleScope.launch { prefs.incrementBlocksShown() }

        packageName_ = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: ""
        blockReason = intent.getStringExtra(EXTRA_BLOCK_REASON) ?: "BLOCK_NOW"

        setupUI()

        binding.btnGoBack.setOnClickListener {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(homeIntent)
            finish()
        }

        binding.btnUnlock.setOnClickListener {
            if (packageName_.isEmpty()) return@setOnClickListener
            startActivity(ShortUnlockActivity.newIntent(this, packageName_, null))
            finish()
        }

        binding.btnCleanInstagram.setOnClickListener {
            startActivity(CleanInstagramActivity.newIntent(this))
            finish()
        }

        binding.btnOverride.setOnClickListener {
            showPinDialog()
        }

        // Back must not dismiss the block screen — send the user home instead.
        onBackPressedDispatcher.addCallback(this) {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(homeIntent)
        }
    }

    // Launched with CLEAR_TOP|SINGLE_TOP, so blocking a second app reuses this same
    // instance — refresh the app name/icon/reason instead of showing the stale one.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        lifecycleScope.launch { prefs.incrementBlocksShown() }
        packageName_ = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: ""
        blockReason = intent.getStringExtra(EXTRA_BLOCK_REASON) ?: "BLOCK_NOW"
        setupUI()
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            val day = unlockGate.today()
            binding.tvUnlockTally.isVisible = day.count > 0
            binding.tvUnlockTally.text = getString(R.string.unlock_today_tally, day.count, day.minutes)
        }
    }

    private fun setupUI() {
        val appName = AppInfoUtils.getAppName(this, packageName_)
        val appIcon = AppInfoUtils.getAppIcon(this, packageName_)

        binding.tvAppName.text = appName
        // Only for the Block Apps block of Instagram itself. Focus, Sleep and schedules
        // mean "no Instagram at all", so the clean feed is not offered there.
        binding.btnCleanInstagram.isVisible = packageName_ == CleanFeedRules.INSTAGRAM_PACKAGE &&
            blockReason in setOf("APP_BLOCKED", "APP_LIMIT_REACHED")
        if (appIcon != null) binding.ivAppIcon.setImageDrawable(appIcon)

        binding.tvBlockReason.text = when (blockReason) {
            "SCHEDULED" -> getString(R.string.overlay_by_schedule)
            "DAILY_LIMIT", "APP_LIMIT_REACHED" -> getString(R.string.overlay_daily_limit_reached)
            "FOCUS" -> getString(R.string.overlay_focus_mode)
            "SLEEP" -> getString(R.string.overlay_sleep_mode)
            "WEBSITE_BLOCKED", "WEBSITE_CAP_REACHED" -> getString(R.string.overlay_website_blocked)
            "APP_BLOCKED" -> getString(R.string.overlay_app_blocked)
            else -> getString(R.string.overlay_title)
        }

        lifecycleScope.launch {
            // Override is only offered when a PIN actually exists — otherwise the
            // dialog would accept any input (no stored hash to compare against).
            val hasPin = prefs.pinHash.first() != null
            // Strict mode has no way out, the PIN included.
            val strictMs = strictGate.remainingMs()
            val strict = strictMs > 0L
            binding.btnOverride.isVisible = hasPin && !strict
            binding.tvPinHint.isVisible = !hasPin && !strict
            binding.tvStrict.isVisible = strict
            binding.tvStrict.text =
                getString(R.string.strict_block_screen_line, TimeUtils.formatLongDuration(strictMs))
        }
    }

    private fun showPinDialog() {
        PinPrompt.show(
            context = this,
            scope = lifecycleScope,
            pinGate = pinGate,
            title = R.string.pin_override_title,
            message = R.string.pin_override_message
        ) { finish() }
    }

}
