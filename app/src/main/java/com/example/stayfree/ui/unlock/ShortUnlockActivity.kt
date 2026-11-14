package com.example.stayfree.ui.unlock

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.stayfree.R
import com.example.stayfree.databinding.ActivityShortUnlockBinding
import com.example.stayfree.domain.content.ContentSignatures
import com.example.stayfree.domain.unlock.ShortUnlock
import com.example.stayfree.util.AppInfoUtils
import com.example.stayfree.util.UnlockGate
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Opened from a block screen. The breath pause is the whole point of the screen: the
 * controls stay inert for [BREATHE_MS], and the pause survives rotation so it can't be
 * skipped by recreating the activity.
 */
@AndroidEntryPoint
class ShortUnlockActivity : AppCompatActivity() {

    @Inject lateinit var unlockGate: UnlockGate

    companion object {
        private const val EXTRA_PACKAGE = "extra_package"
        private const val EXTRA_TARGET_ID = "extra_target_id"
        private const val STATE_BREATH_START = "state_breath_start"
        private const val BREATHE_MS = 5_000L

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
    private var breathStart = 0L
    private var breathAnimator: ValueAnimator? = null

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

        AppInfoUtils.getAppIcon(this, pkg)?.let { binding.ivAppIcon.setImageDrawable(it) }
        binding.tvTitle.text = getString(R.string.unlock_title, label(this, pkg, targetId))

        binding.slider.addOnChangeListener { _, value, _ -> showMinutes(value.toInt()) }
        showMinutes(binding.slider.value.toInt())

        binding.btnUnlock.setOnClickListener { unlock() }
        binding.btnCancel.setOnClickListener { goHome() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goHome()
        })

        breathStart = savedInstanceState?.getLong(STATE_BREATH_START)
            ?.takeIf { it in 1..SystemClock.elapsedRealtime() }
            ?: SystemClock.elapsedRealtime()
        startBreath()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(STATE_BREATH_START, breathStart)
    }

    override fun onDestroy() {
        breathAnimator?.cancel()
        super.onDestroy()
    }

    private fun startBreath() {
        val done = (SystemClock.elapsedRealtime() - breathStart).coerceIn(0L, BREATHE_MS)
        if (done >= BREATHE_MS) {
            onBreathDone()
            return
        }
        breathAnimator = ValueAnimator.ofFloat(done / BREATHE_MS.toFloat(), 1f).apply {
            duration = BREATHE_MS - done
            interpolator = LinearInterpolator()
            addUpdateListener {
                val p = it.animatedValue as Float
                binding.ring.setProgress(p, animate = false)
                val secondsLeft = ((1f - p) * BREATHE_MS / 1000f).toInt() + 1
                binding.tvBreatheCount.text =
                    getString(R.string.unlock_breathe_count, secondsLeft.coerceAtMost((BREATHE_MS / 1000).toInt()))
                if (p >= 1f) onBreathDone()
            }
            start()
        }
    }

    private fun onBreathDone() {
        binding.ring.setProgress(binding.slider.value / ShortUnlock.MAX_MINUTES, animate = false)
        binding.groupBreathe.visibility = View.INVISIBLE
        binding.groupMinutes.visibility = View.VISIBLE
        binding.slider.isEnabled = true
        binding.btnUnlock.isEnabled = true
    }

    private fun showMinutes(minutes: Int) {
        binding.tvMinutes.text = minutes.toString()
        binding.btnUnlock.text = getString(R.string.unlock_confirm, minutes)
        if (binding.slider.isEnabled) binding.ring.setProgress(minutes / ShortUnlock.MAX_MINUTES.toFloat(), animate = false)
    }

    private fun unlock() {
        binding.btnUnlock.isEnabled = false
        val minutes = binding.slider.value.toInt()
        val key = targetId?.let { ShortUnlock.contentKey(it) } ?: ShortUnlock.appKey(pkg)
        lifecycleScope.launch {
            unlockGate.grant(key, minutes)
            val launch = packageManager.getLaunchIntentForPackage(pkg)
            if (launch != null) {
                startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                finish()
                overridePendingTransition(0, 0)
            } else goHome()
        }
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
        overridePendingTransition(0, 0)
    }
}
