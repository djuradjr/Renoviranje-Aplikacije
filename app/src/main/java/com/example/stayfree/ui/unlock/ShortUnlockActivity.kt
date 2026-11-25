package com.example.stayfree.ui.unlock

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.ViewOutlineProvider
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.stayfree.R
import com.example.stayfree.data.billing.PremiumRepository
import com.example.stayfree.data.local.preferences.AppPreferences
import com.example.stayfree.databinding.ActivityShortUnlockBinding
import com.example.stayfree.domain.cleanfeed.CleanFeedRules
import com.example.stayfree.domain.content.ContentSignatures
import com.example.stayfree.domain.unlock.CleanFeedNudge
import com.example.stayfree.domain.unlock.ShortUnlock
import com.example.stayfree.domain.unlock.UnlockFlow
import com.example.stayfree.domain.unlock.UnlockFlow.Phase
import com.example.stayfree.ui.premium.PaywallActivity
import com.example.stayfree.util.AppInfoUtils
import com.example.stayfree.util.UnlockGate
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Short unlock, opened from a block screen: "are you sure?" → a 5 s breath → unlocked.
 * [UnlockFlow] decides the steps; the unlock is granted only when the breath completes,
 * and leaving the screen mid-breath sends it back to the question.
 */
@AndroidEntryPoint
class ShortUnlockActivity : AppCompatActivity() {

    @Inject lateinit var unlockGate: UnlockGate
    @Inject lateinit var premium: PremiumRepository
    @Inject lateinit var prefs: AppPreferences

    companion object {
        private const val EXTRA_PACKAGE = "extra_package"
        private const val EXTRA_TARGET_ID = "extra_target_id"
        private const val STATE_PHASE = "state_phase"
        private const val STATE_MINUTES = "state_minutes"
        private const val STATE_BREATH_START = "state_breath_start"
        private const val FRAME_MS = 32L
        /** How long "Unlocked for X min" stays before the app opens. */
        private const val DONE_HOLD_MS = 1_500L

        /** [targetId] set = unlock that content surface only; null = the whole app. */
        fun newIntent(context: Context, packageName: String, targetId: String?): Intent =
            Intent(context, ShortUnlockActivity::class.java)
                .putExtra(EXTRA_PACKAGE, packageName)
                .putExtra(EXTRA_TARGET_ID, targetId)

        /** Label shared by this screen and the countdown notification. */
        fun label(context: Context, packageName: String, targetId: String?): String {
            val appName = AppInfoUtils.getAppName(context, packageName)
            val target = targetId?.let { ContentSignatures.byId(it) }
            return if (target == null || target.matchWholeApp) appName else "$appName ${target.displayName}"
        }
    }

    private lateinit var binding: ActivityShortUnlockBinding
    private lateinit var pkg: String
    private var targetId: String? = null
    private var state = UnlockFlow.State()
    private var lockOpened = false

    /** System "remove animations": a still countdown instead of the filling ring. */
    private val reducedMotion: Boolean
        get() = Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pkg = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        if (pkg.isEmpty()) {
            finish()
            return
        }
        targetId = intent.getStringExtra(EXTRA_TARGET_ID)
        binding = ActivityShortUnlockBinding.inflate(layoutInflater)
        setContentView(binding.root)

        state = if (savedInstanceState == null) UnlockFlow.State()
        else UnlockFlow.restore(
            savedInstanceState.getString(STATE_PHASE),
            savedInstanceState.getInt(STATE_MINUTES, UnlockFlow.DEFAULT_MINUTES),
            savedInstanceState.getLong(STATE_BREATH_START),
            SystemClock.elapsedRealtime()
        )
        when (state.phase) {
            // Granted before the restart: nothing to grant again, just go in.
            Phase.UNLOCKED -> return openTarget()
            Phase.CANCELLED -> return goHome()
            else -> Unit
        }

        binding.ivAppIcon.apply {
            outlineProvider = ViewOutlineProvider.BACKGROUND
            clipToOutline = true
            setImageDrawable(AppInfoUtils.getAppIcon(this@ShortUnlockActivity, pkg))
        }
        binding.ring.ringWidth = 8f * resources.displayMetrics.density
        binding.ring.progressColor = ContextCompat.getColor(this, R.color.skor_focus)

        binding.slider.value = state.minutes.toFloat()
        binding.slider.addOnChangeListener { slider, value, fromUser ->
            state = UnlockFlow.setMinutes(state, value.toInt())
            if (fromUser) slider.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            showMinutes()
        }
        showMinutes()

        binding.btnBack.setOnClickListener { cancel() }
        binding.btnClose.setOnClickListener { cancel() }
        binding.btnNeverMind.setOnClickListener { cancel() }
        binding.btnUnlock.setOnClickListener {
            state = UnlockFlow.confirm(state, SystemClock.elapsedRealtime())
            render()
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = cancel()
        })

        lifecycleScope.launch {
            val day = unlockGate.today()
            binding.chipToday.isVisible = day.count > 0
            binding.chipToday.text = getString(R.string.unlock2_today, day.count, day.minutes)
        }
        bindNudge(firstShow = savedInstanceState == null)

        render()
        // The breath only runs while the screen is visible; see onStop.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    if (state.phase == Phase.BREATHING) step()
                    delay(FRAME_MS)
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Leaving mid-breath never unlocks: back to the question, the pause starts over.
        // Rotation is not leaving.
        if (!isChangingConfigurations && state.phase == Phase.BREATHING) {
            state = UnlockFlow.leave(state)
            render()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_PHASE, state.phase.name)
        outState.putInt(STATE_MINUTES, state.minutes)
        outState.putLong(STATE_BREATH_START, state.breathStartMs)
    }

    private fun step() {
        val now = SystemClock.elapsedRealtime()
        val before = state
        state = UnlockFlow.tick(state, now)
        if (UnlockFlow.grants(before, state)) onBreathDone() else renderBreath(now)
    }

    private fun render() {
        binding.panelConfirm.isVisible = state.phase == Phase.CONFIRM
        binding.panelBreathe.isVisible = state.phase == Phase.BREATHING
        binding.panelDone.isVisible = state.phase == Phase.UNLOCKED
        binding.btnClose.isVisible = state.phase != Phase.UNLOCKED
        if (state.phase == Phase.BREATHING) {
            lockOpened = false
            binding.ivLock.setImageResource(R.drawable.ic_lock)
            binding.ivLock.imageTintList = ContextCompat.getColorStateList(this, R.color.skor_distraction)
            val still = reducedMotion
            binding.ring.isVisible = !still
            binding.ivLock.isVisible = !still
            binding.tvSeconds.isVisible = still
            renderBreath(SystemClock.elapsedRealtime())
        }
    }

    private fun renderBreath(now: Long) {
        binding.ring.setProgress(UnlockFlow.progress(state, now), animate = false)
        binding.tvSeconds.text = UnlockFlow.secondsLeft(state, now).toString()
        binding.tvBreath.setText(
            if (UnlockFlow.breathingIn(state, now)) R.string.unlock2_breathe_in else R.string.unlock2_breathe_out
        )
    }

    private fun showMinutes() {
        binding.tvMinutes.text = getString(R.string.unlock2_minutes, state.minutes)
        binding.btnUnlock.text = getString(R.string.unlock2_confirm, state.minutes)
    }

    /** The breath completed: this is the one place that unlocks. */
    private fun onBreathDone() {
        if (!lockOpened) {
            lockOpened = true
            binding.ring.setProgress(1f, animate = false)
            binding.ivLock.setImageResource(R.drawable.ic_lock_open)
            binding.ivLock.imageTintList = ContextCompat.getColorStateList(this, R.color.skor_focus)
            binding.root.performHapticFeedback(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM
                else HapticFeedbackConstants.VIRTUAL_KEY
            )
        }
        val minutes = state.minutes
        val key = targetId?.let { ShortUnlock.contentKey(it) } ?: ShortUnlock.appKey(pkg)
        lifecycleScope.launch {
            unlockGate.grant(key, minutes)
            binding.tvDone.text = getString(R.string.unlock2_done, minutes)
            render()
            val shownAt = SystemClock.elapsedRealtime()
            val totalMs = minutes * 60_000L
            while (true) {
                val held = SystemClock.elapsedRealtime() - shownAt
                val leftS = ((totalMs - held).coerceAtLeast(0L) + 999) / 1000
                binding.tvCountdown.text = getString(R.string.unlock2_countdown, leftS / 60, leftS % 60)
                if (held >= DONE_HOLD_MS) break
                delay(250)
            }
            openTarget()
        }
    }

    /** Clean-Instagram suggestion: never in the way of unlocking, and only a link. */
    private fun bindNudge(firstShow: Boolean) {
        lifecycleScope.launch {
            val today = unlockGate.todayDate()
            val eligible = CleanFeedNudge.shouldShow(
                premium = premium.isPremium.value,
                isInstagram = pkg == CleanFeedRules.INSTAGRAM_PACKAGE,
                instagramUnlocksToday = unlockGate.instagramUnlocksToday(),
                lastShownDate = null,
                today = today
            )
            val shownToday = prefs.cleanFeedNudgeDate.first() == today
            // Once a day: a fresh screen needs it not shown yet; a rotated one keeps the
            // card it was already showing.
            if (!eligible || shownToday == firstShow) return@launch
            binding.cardNudge.isVisible = true
            binding.cardNudge.setOnClickListener {
                startActivity(PaywallActivity.newIntent(this@ShortUnlockActivity))
            }
            binding.tvNudgeLink.setText(R.string.unlock2_nudge_see)
            if (firstShow) prefs.setCleanFeedNudgeDate(today)
            // Only promise a trial that Play actually offers.
            val trialDays = premium.loadPlans()?.maxOfOrNull { it.trialDays } ?: 0
            if (trialDays > 0) {
                binding.tvNudgeLink.text =
                    resources.getQuantityString(R.plurals.unlock2_nudge_trial, trialDays, trialDays)
            }
        }
    }

    private fun cancel() {
        state = UnlockFlow.cancel(state)
        if (state.phase == Phase.UNLOCKED) return // already granted; it opens on its own
        goHome()
    }

    private fun openTarget() {
        val launch = packageManager.getLaunchIntentForPackage(pkg)
        if (launch == null) return goHome()
        startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
        overridePendingTransition(0, 0)
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
        overridePendingTransition(0, 0)
    }
}
