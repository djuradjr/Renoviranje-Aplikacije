package com.example.stayfree.ui.blocking

import android.Manifest
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.stayfree.R
import com.example.stayfree.data.billing.PremiumRepository
import com.example.stayfree.databinding.FragmentStrictModeBinding
import com.example.stayfree.domain.strict.StrictLock
import com.example.stayfree.ui.common.LoosenGate
import com.example.stayfree.ui.common.bindBackHeader
import com.example.stayfree.ui.premium.ensureProtection
import com.example.stayfree.ui.premium.requirePremium
import com.example.stayfree.util.AppInfoUtils
import com.example.stayfree.util.PermissionUtils
import com.example.stayfree.util.PinGate
import com.example.stayfree.util.StrictGate
import com.example.stayfree.util.TimeUtils
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

private const val MINUTE_MS = 60_000L
private const val HOUR_MS = 60 * MINUTE_MS
private const val DAY_MS = 24 * HOUR_MS

@AndroidEntryPoint
class StrictModeFragment : Fragment() {

    @Inject lateinit var premium: PremiumRepository
    @Inject lateinit var strictGate: StrictGate
    @Inject lateinit var pinGate: PinGate

    private var _binding: FragmentStrictModeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: StrictModeViewModel by viewModels()

    private var pendingDurationMs = 0L
    private var shownLocked: Boolean? = null

    // Asked here, in context: the "protection is off" notification is the one consequence
    // strict mode has. The lock goes ahead whatever the answer.
    private val askNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { confirmLock() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentStrictModeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindBackHeader(binding.backHeader)
        shownLocked = null

        binding.chipDebug.isVisible =
            requireContext().applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        binding.chipGroup.setOnCheckedStateChangeListener { _, _ -> renderButton() }
        binding.btnLock.setOnClickListener { onLockClicked() }
        bindTopApps()

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.status.collectLatest { status -> if (status != null) render(status) }
        }
    }

    private fun bindTopApps() {
        val loosenGate = LoosenGate(this, strictGate, pinGate)
        // A click, not a checked-change listener: the collector below sets the state too.
        binding.switchTopApps.setOnClickListener {
            if (binding.switchTopApps.isChecked) viewModel.setTopAppsOn(true)
            else loosenGate.run(onDenied = { _binding?.switchTopApps?.isChecked = true }) {
                viewModel.setTopAppsOn(false)
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.topAppsOn.collectLatest { on -> if (on != null) binding.switchTopApps.isChecked = on }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.topApps.collectLatest { apps ->
                binding.tvTopApps.text =
                    if (apps.isEmpty()) getString(R.string.strict_top_empty)
                    else apps.joinToString(" · ") { AppInfoUtils.getAppName(requireContext(), it) }
            }
        }
    }

    private fun render(status: StrictGate.Status) {
        val locked = status.locked
        binding.ivLock.isVisible = !locked
        binding.tvCountdown.isVisible = locked
        binding.tvCountdown.text = countdown(status.remainingMs)
        binding.tvRingLabel.setText(if (locked) R.string.strict_ring_left else R.string.strict_ring_off)
        // Animate the ring only when the state flips; the per-second drain just redraws.
        binding.ring.setProgress(
            if (locked) status.remainingMs.toFloat() / status.totalMs else 0f,
            animate = shownLocked != locked
        )
        binding.tvHeadline.text =
            if (locked) getString(R.string.strict_headline_on, formatEnd(status.remainingMs))
            else getString(R.string.strict_headline_off)
        binding.tvBody.setText(if (locked) R.string.strict_body_on else R.string.strict_body_off)
        binding.tvViolations.isVisible = locked && status.violations > 0
        binding.tvViolations.text =
            resources.getQuantityString(R.plurals.strict_violations, status.violations, status.violations)
        if (shownLocked != locked) {
            shownLocked = locked
            renderButton()
        }
    }

    private fun renderButton() {
        val length = binding.chipGroup.findViewById<Chip>(binding.chipGroup.checkedChipId)?.text ?: return
        binding.btnLock.text = getString(
            if (shownLocked == true) R.string.strict_cta_extend else R.string.strict_cta_lock,
            length
        )
    }

    private fun onLockClicked() {
        if (!requirePremium(premium)) return
        // A lock with the service off would guard nothing.
        if (!PermissionUtils.hasAccessibilityServiceEnabled(requireContext())) {
            ensureProtection()
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val status = viewModel.status.value ?: return@launch
            if (!status.locked && !viewModel.hasBlocks.value) {
                toast(R.string.strict_need_block)
                return@launch
            }
            val duration = selectedDurationMs()
            if (StrictLock.extendedRemainingMs(status.remainingMs, duration) <= status.remainingMs) {
                toast(R.string.strict_max_reached)
                return@launch
            }
            pendingDurationMs = duration
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !PermissionUtils.hasNotificationPermission(requireContext())
            ) {
                askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else confirmLock()
        }
    }

    private fun confirmLock() {
        val duration = pendingDurationMs
        val status = viewModel.status.value
        if (duration <= 0L || status == null || _binding == null) return
        val remaining = StrictLock.extendedRemainingMs(status.remainingMs, duration)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(
                getString(
                    if (status.locked) R.string.strict_confirm_extend_title else R.string.strict_confirm_title,
                    formatEnd(remaining)
                )
            )
            .setMessage(R.string.strict_confirm_message)
            .setNegativeButton(R.string.btn_cancel, null)
            .setPositiveButton(
                if (status.locked) R.string.strict_action_extend else R.string.strict_action_lock
            ) { _, _ -> viewModel.lock(duration) }
            .show()
    }

    private suspend fun selectedDurationMs(): Long = when (binding.chipGroup.checkedChipId) {
        binding.chipDebug.id -> 2 * MINUTE_MS
        binding.chip1h.id -> HOUR_MS
        binding.chipToday.id -> viewModel.msUntilDayEnd()
        binding.chip24h.id -> DAY_MS
        binding.chip30d.id -> 30 * DAY_MS
        else -> 7 * DAY_MS
    }

    /** Seconds are dropped once days show: the ring has no room for both. */
    private fun countdown(ms: Long): String {
        val s = ms / 1000
        val hours = s % 86_400 / 3600
        val minutes = s % 3600 / 60
        return if (s >= 86_400) String.format(Locale.US, "%dd %02d:%02d", s / 86_400, hours, minutes)
        else String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, s % 60)
    }

    private fun formatEnd(remainingMs: Long): String {
        val locale = TimeUtils.displayLocale()
        val skeleton = if (DateFormat.is24HourFormat(requireContext())) "EEEdMMMHm" else "EEEdMMMhma"
        return SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
            .format(Date(System.currentTimeMillis() + remainingMs))
    }

    private fun toast(message: Int) = Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
