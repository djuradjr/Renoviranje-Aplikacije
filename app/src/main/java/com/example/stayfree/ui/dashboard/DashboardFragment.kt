package com.example.stayfree.ui.dashboard

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.stayfree.R
import com.example.stayfree.data.billing.PremiumRepository
import com.example.stayfree.data.local.preferences.AppPreferences
import com.example.stayfree.databinding.FragmentDashboardBinding
import com.example.stayfree.domain.model.AppUsage
import com.example.stayfree.ui.common.AppUsageTileView
import com.example.stayfree.ui.common.CountUp
import com.example.stayfree.ui.common.ScoreDisplay
import com.example.stayfree.ui.onboarding.OnboardingActivity
import com.example.stayfree.util.AppInfoUtils
import com.example.stayfree.util.PermissionUtils
import com.example.stayfree.util.TimeUtils
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.transition.MaterialFadeThrough
import com.google.android.play.core.review.ReviewManagerFactory
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val REVIEW_AFTER_BLOCKS = 5

@AndroidEntryPoint
class DashboardFragment : Fragment() {

    @Inject lateinit var premium: PremiumRepository
    @Inject lateinit var prefs: AppPreferences

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DashboardViewModel by viewModels()
    private lateinit var periodAppsAdapter: AppUsageListAdapter

    // Tracks the previously shown date so day changes can slide in from the right side.
    private var lastShownDate: String? = null
    private var lastPeriod: StatsPeriod? = null

    /** Length of the current per-day chart series (7 or 30) — maps a chart index
     *  back to its calendar date for the x-axis and tooltip. */
    private var periodDayCount: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = MaterialFadeThrough()
        exitTransition = MaterialFadeThrough()
        reenterTransition = MaterialFadeThrough()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupSegments()
        setupMetrics()
        setupRecyclerViews()
        setupDateNav()
        setupCharts()
        pinGlows()
        observeData()
        binding.btnProtectionRestart.setOnClickListener {
            startActivity(OnboardingActivity.protectionIntent(requireContext()))
        }
        binding.btnProtectionBattery.setOnClickListener { showBatteryDialog() }
    }

    override fun onResume() {
        super.onResume()
        bindProtectionBanner()
        maybeAskForReview()
    }

    /**
     * Play In-App Review, once, after the user has seen blocking work a few times and while it
     * still works — only here, never on a block screen. Play decides whether the sheet shows.
     */
    private fun maybeAskForReview() {
        viewLifecycleOwner.lifecycleScope.launch {
            if (prefs.reviewRequested.first()) return@launch
            if (prefs.blocksShownTotal.first() < REVIEW_AFTER_BLOCKS) return@launch
            if (!PermissionUtils.isAccessibilityServiceRunning(requireContext())) return@launch
            val manager = ReviewManagerFactory.create(requireContext())
            manager.requestReviewFlow().addOnCompleteListener { request ->
                // Failed (no Play Store, offline Play): stays unasked and retries next visit.
                if (!request.isSuccessful || !isResumed) return@addOnCompleteListener
                viewLifecycleOwner.lifecycleScope.launch { prefs.setReviewRequested() }
                manager.launchReviewFlow(requireActivity(), request.result)
            }
        }
    }

    /**
     * Premium, the service was running, nobody turned it off, and now it is off: the system
     * killed it. Play allows detecting this and nudging; re-enabling it ourselves is not.
     */
    private fun bindProtectionBanner() {
        viewLifecycleOwner.lifecycleScope.launch {
            val ctx = requireContext()
            val killed = premium.isPremium.value &&
                prefs.accessibilityState.first() == AppPreferences.A11Y_RUNNING &&
                !PermissionUtils.hasAccessibilityServiceEnabled(ctx)
            binding.cardProtectionStopped.isVisible = killed
            binding.btnProtectionBattery.isVisible =
                killed && !PermissionUtils.isIgnoringBatteryOptimizations(ctx)
        }
    }

    private fun showBatteryDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.battery_dialog_title)
            .setMessage(R.string.battery_dialog_message)
            .setPositiveButton(R.string.battery_dialog_open) { _, _ -> openBatterySettings() }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    // The user picks us in the system list: the direct REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
    // prompt is Play-restricted. App info is the fallback where the list is missing.
    private fun openBatterySettings() {
        try {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        } catch (e: ActivityNotFoundException) {
            try {
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.parse("package:${requireContext().packageName}"))
                )
            } catch (e: ActivityNotFoundException) {
                // No settings app to open — nothing more to offer.
            }
        }
    }

    /**
     * The blade starts just under whichever brain is showing and the side glow wraps
     * the cards; banners above them (protection stopped) move everything down, so
     * fixed offsets would cut through them.
     */
    private fun pinGlows() {
        val overlap = resources.getDimensionPixelSize(R.dimen.dash_blade_overlap)
        val glowLead = resources.getDimensionPixelSize(R.dimen.dash_side_glow_lead)
        val glowTail = resources.getDimensionPixelSize(R.dimen.dash_side_glow_tail)
        binding.dashboardContent.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val b = _binding ?: return@addOnLayoutChangeListener
            val brain = if (b.dailyContent.isVisible) b.ivBrain else b.ivPeriodBrain
            val origin = IntArray(2).also { b.dashboardContent.getLocationInWindow(it) }[1]
            fun topOf(v: View) = IntArray(2).also { v.getLocationInWindow(it) }[1] - origin

            pinTop(b.glowBlade, (topOf(brain) + brain.height - overlap).coerceAtLeast(0), null)

            // Side glow spans the cards, a little past them at both ends; it is
            // strongest at its middle and fades out toward both ends by itself.
            val daily = b.dailyContent.isVisible
            val first = if (daily) b.hourlyCard else b.rowPeriodMetrics
            val last = if (daily) b.rowTopApps else b.cardPeriodChart
            val blockTop = topOf(first)
            val blockBottom = topOf(last) + last.height
            val top = (blockTop - glowLead).coerceAtLeast(0)
            // Never taller than the content, or it would add empty scroll at the bottom.
            pinTop(b.sideGlow, top, minOf(blockBottom + glowTail, b.dashboardContent.height) - top)
            b.sideGlow.setBlock(blockTop - top, blockBottom - blockTop)
        }
    }

    private fun pinTop(view: View, top: Int, height: Int?) {
        val params = view.layoutParams as ViewGroup.MarginLayoutParams
        val h = height ?: params.height
        if (params.topMargin == top && params.height == h) return
        params.topMargin = top
        params.height = h
        // Requesting layout from inside a layout pass is dropped; post it.
        view.post { view.layoutParams = params }
    }

    private fun setupSegments() {
        val periods = StatsPeriod.entries
        binding.segPeriods.setOptions(
            listOf(getString(R.string.stats_daily), getString(R.string.stats_weekly), getString(R.string.stats_monthly)),
            selected = viewModel.period.value.ordinal
        )
        binding.segPeriods.onSelected = { index ->
            binding.segPeriods.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            viewModel.setPeriod(periods[index])
        }
    }

    private fun setupMetrics() {
        binding.metricScreen.bind("", getString(R.string.dashboard_screen_caption_nogoal))
        binding.metricUnlocks.bind("", getString(R.string.dashboard_unlocks))
        binding.metricPeriodAvg.bind("", getString(R.string.dashboard_period_daily_avg))
        binding.metricPeriodUnlocks.bind("", getString(R.string.dashboard_unlocks))
        binding.metricPeriodTotal.bind("", getString(R.string.dashboard_period_total_caption))
        binding.metricPeriodInGoal.bind("", getString(R.string.dashboard_period_in_goal))
    }

    /** "4h", "4h 30m" or "45m" — goal copy without the seconds noise. */
    private fun formatMinutesCompact(totalMinutes: Int): String {
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        return when {
            h > 0 && m == 0 -> "${h}h"
            h > 0 -> "${h}h ${m}m"
            else -> "${m}m"
        }
    }

    private fun openAppDetail(packageName: String) {
        findNavController().navigate(
            R.id.action_dashboard_to_appDetail,
            bundleOf("packageName" to packageName)
        )
    }

    private fun setupRecyclerViews() {
        periodAppsAdapter = AppUsageListAdapter { openAppDetail(it.packageName) }
        binding.rvPeriodApps.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = periodAppsAdapter
        }
    }

    private fun bindTopApps(apps: List<AppUsage>) {
        val tiles: List<AppUsageTileView> =
            listOf(binding.tileApp1, binding.tileApp2, binding.tileApp3, binding.tileApp4)
        binding.rowTopApps.isVisible = apps.isNotEmpty()
        tiles.forEachIndexed { i, tile ->
            val app = apps.getOrNull(i)
            // INVISIBLE, not GONE: fewer than four apps keep the tile width.
            tile.visibility = if (app == null) View.INVISIBLE else View.VISIBLE
            if (app == null) {
                tile.setOnClickListener(null)
                return@forEachIndexed
            }
            tile.bind(
                label = app.appName,
                packageName = app.packageName,
                icon = AppInfoUtils.getAppIcon(requireContext(), app.packageName),
                time = TimeUtils.formatDuration(app.totalTimeMs)
            )
            tile.setOnClickListener { openAppDetail(app.packageName) }
        }
    }

    private fun setupDateNav() {
        binding.btnPrevDay.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            viewModel.goToPreviousDay()
        }
        binding.btnNextDay.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            viewModel.goToNextDay()
        }
    }

    private fun setupCharts() {
        val minutesToDuration: (Float) -> String = { minutes ->
            TimeUtils.formatDuration((minutes * 60_000f).toLong())
        }
        // Dragging the bars reports an hour; the peak caption doubles as the readout.
        binding.chartHourly.onHourSelected = { hour ->
            binding.tvPeakTime.text = if (hour == null) {
                peakCaption(viewModel.peakHour.value)
            } else {
                val (distraction, other) = viewModel.hourlyBreakdown.value.getOrNull(hour)
                    ?: (0L to 0L)
                getString(
                    R.string.dashboard_hour_readout,
                    hour,
                    TimeUtils.formatDuration(distraction + other)
                )
            }
        }
        // Weekly/Monthly: per-day points; ticks are day initials (7d) or dates (30d).
        binding.chartPeriod.apply {
            tooltipValueFormatter = minutesToDuration
            tooltipCaptionFormatter = { index -> TimeUtils.formatDisplayDate(dateForPeriodIndex(index)) }
            xLabelFormatter = { index ->
                val date = dateForPeriodIndex(index)
                if (viewModel.period.value == StatsPeriod.WEEKLY) {
                    TimeUtils.dayInitial(date)
                } else if (index % 5 == 0 || index == periodDayCount - 1) {
                    TimeUtils.dayOfMonth(date)
                } else ""
            }
        }
    }

    /** Maps a per-day chart index (oldest→today) back to its "yyyy-MM-dd" date. */
    private fun dateForPeriodIndex(index: Int): String {
        val daysAgo = (periodDayCount - 1 - index).coerceAtLeast(0)
        return TimeUtils.getDateStringDaysAgo(daysAgo)
    }

    private fun peakCaption(hour: Int?): String =
        if (hour == null) "" else getString(R.string.hourly_peak, hour)

    private fun observeData() {
        val empty = getString(R.string.score_empty)
        viewLifecycleOwner.lifecycleScope.launch {
            launch {
                viewModel.period.collectLatest { period ->
                    binding.segPeriods.select(period.ordinal)
                    val daily = period == StatsPeriod.DAILY
                    binding.dailyContent.isVisible = daily
                    binding.periodContent.isVisible = !daily
                    if (!daily) {
                        binding.tvPeriodPill.setText(
                            if (period == StatsPeriod.WEEKLY) R.string.dashboard_period_last_week
                            else R.string.dashboard_period_last_month
                        )
                    }
                    animatePeriodChange(period)
                }
            }
            launch {
                viewModel.totalScreenTime.collectLatest { ms ->
                    CountUp.animate(binding.metricScreen.valueView, ms) { TimeUtils.formatDuration(it) }
                }
            }
            launch {
                viewModel.totalUnlocks.collectLatest { count ->
                    CountUp.animate(binding.metricUnlocks.valueView, count.toLong()) { it.toString() }
                }
            }
            launch {
                viewModel.topApps.collectLatest { apps -> bindTopApps(apps) }
            }
            launch {
                viewModel.goalUi.collectLatest { goal ->
                    val caption = binding.metricScreen.captionView
                    if (goal == null) {
                        caption.setText(R.string.dashboard_screen_caption_nogoal)
                        return@collectLatest
                    }
                    caption.text = getString(
                        R.string.dashboard_screen_caption, formatMinutesCompact(goal.goalMinutes)
                    )
                    // Over the goal is the one place the number itself turns orange.
                    val over = goal.remainingMs <= 0
                    binding.metricScreen.valueView.setTextColor(
                        ContextCompat.getColor(
                            requireContext(), if (over) R.color.skor_distraction else R.color.skor_text
                        )
                    )
                }
            }
            launch {
                viewModel.selectedDate.collectLatest { date ->
                    val today = date == TimeUtils.getTodayString()
                    binding.tvDate.text = TimeUtils.formatHeroDate(date)
                    binding.btnNextDay.isEnabled = !today
                    binding.btnNextDay.alpha = if (today) 0.3f else 1f
                    animateDayChange(date)
                }
            }
            launch {
                viewModel.hourlyBreakdown.collect { hours -> binding.chartHourly.setData(hours) }
            }
            launch {
                viewModel.focusScore.collectLatest { breakdown ->
                    val score = breakdown?.score
                    binding.scoreRing.setScore(score)
                    if (score == null) {
                        // No data yet: a dash, never a zero — zero reads as "you failed".
                        binding.tvScore.text = ScoreDisplay.text(null, empty)
                    } else {
                        CountUp.animate(binding.tvScore, score.toLong()) { it.toString() }
                    }
                }
            }
            launch {
                viewModel.categoryResolver.collectLatest { resolver ->
                    periodAppsAdapter.categoryOf = resolver
                }
            }
            launch {
                viewModel.currentStreak.collectLatest { days -> binding.streakRow.bind(days) }
            }
            launch {
                viewModel.peakHour.collectLatest { hour ->
                    binding.tvPeakTime.text = peakCaption(hour)
                }
            }
            launch {
                viewModel.periodAverageScreenTime.collectLatest { ms ->
                    CountUp.animate(binding.metricPeriodAvg.valueView, ms) { TimeUtils.formatDuration(it) }
                }
            }
            launch {
                viewModel.periodTotalScreenTime.collectLatest { ms ->
                    CountUp.animate(binding.metricPeriodTotal.valueView, ms) { TimeUtils.formatDuration(it) }
                }
            }
            launch {
                viewModel.periodTotalUnlocks.collectLatest { count ->
                    CountUp.animate(binding.metricPeriodUnlocks.valueView, count.toLong()) { it.toString() }
                }
            }
            launch {
                viewModel.screenTimeComparison.collectLatest { comparison ->
                    bindTrendBadge(comparison)
                }
            }
            launch {
                viewModel.periodAverageScore.collectLatest { score ->
                    binding.periodScoreRing.setScore(score)
                    if (score == null) {
                        binding.tvPeriodScore.text = ScoreDisplay.text(null, empty)
                    } else {
                        CountUp.animate(binding.tvPeriodScore, score.toLong()) { it.toString() }
                    }
                }
            }
            launch {
                viewModel.periodDaysInGoal.collectLatest { (inGoal, total) ->
                    binding.metricPeriodInGoal.valueView.text =
                        getString(R.string.dashboard_period_in_goal_value, inGoal, total)
                }
            }
            launch {
                viewModel.periodAppUsage.collectLatest { list ->
                    periodAppsAdapter.submitList(list)
                }
            }
            launch {
                viewModel.periodDailyUsage.collectLatest { daily ->
                    periodDayCount = daily.size
                    binding.chartPeriod.markersAllPoints = viewModel.period.value == StatsPeriod.WEEKLY
                    binding.chartPeriod.setData(daily.map { it / 60_000f })
                }
            }
        }
    }

    private fun bindTrendBadge(comparison: PeriodComparison?) {
        val badge = binding.tvTrend
        if (comparison == null || comparison.previousMs <= 0L) {
            badge.visibility = View.GONE
            return
        }
        val deltaPct = ((comparison.currentMs - comparison.previousMs) * 100 / comparison.previousMs).toInt()
        if (deltaPct == 0) {
            badge.visibility = View.GONE
            return
        }
        badge.visibility = View.VISIBLE
        badge.text = if (deltaPct < 0) {
            getString(R.string.stats_trend_down, -deltaPct)
        } else {
            getString(R.string.stats_trend_up, deltaPct)
        }
        // Less screen time reads as a win; more is a nudge, never an alarm.
        val (fill, text) = if (deltaPct < 0) {
            R.color.skor_focus_soft to R.color.skor_focus
        } else {
            R.color.skor_warn_soft to R.color.skor_warn
        }
        badge.backgroundTintList = ContextCompat.getColorStateList(requireContext(), fill)
        badge.setTextColor(ContextCompat.getColor(requireContext(), text))
    }

    /** Crossfades the content when switching Daily/Weekly/Monthly. */
    private fun animatePeriodChange(period: StatsPeriod) {
        val previous = lastPeriod
        lastPeriod = period
        if (previous == null || previous == period) return
        binding.dashboardContent.alpha = 0f
        binding.dashboardContent.translationY = 24f
        binding.dashboardContent.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(250)
            .setInterpolator(DecelerateInterpolator())
            .start()
        if (period != StatsPeriod.DAILY) binding.rvPeriodApps.scheduleLayoutAnimation()
    }

    /** Slides the whole content sideways when the user browses to another day. */
    private fun animateDayChange(newDate: String) {
        val previous = lastShownDate
        lastShownDate = newDate
        if (previous == null || previous == newDate) return
        // ISO yyyy-MM-dd strings compare chronologically.
        val direction = if (newDate > previous) 1f else -1f
        binding.dashboardContent.translationX = 48f * direction
        binding.dashboardContent.alpha = 0.4f
        binding.dashboardContent.animate()
            .translationX(0f)
            .alpha(1f)
            .setDuration(240)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
