package com.example.stayfree.ui.blocking

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.stayfree.R
import com.example.stayfree.data.billing.PremiumRepository
import com.example.stayfree.databinding.FragmentBlockingBinding
import com.example.stayfree.domain.cleanfeed.CleanNetwork
import com.example.stayfree.domain.cleanfeed.InstagramNetwork
import com.example.stayfree.ui.cleanfeed.CleanFeedActivity
import com.example.stayfree.ui.onboarding.OnboardingActivity
import com.example.stayfree.ui.premium.PaywallActivity
import com.example.stayfree.ui.premium.requirePremium
import com.example.stayfree.ui.unlock.ShortUnlockActivity
import com.example.stayfree.util.PermissionUtils
import com.example.stayfree.util.TimeUtils
import com.google.android.material.transition.MaterialFadeThrough
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What is blocked right now, each with its way in; every tool lives behind the rules arrow. */
@AndroidEntryPoint
class BlockingFragment : Fragment() {

    @Inject lateinit var premium: PremiumRepository

    private var _binding: FragmentBlockingBinding? = null
    private val binding get() = _binding!!
    private val viewModel: BlockingViewModel by viewModels()
    /** The clean feed of the first blocked app that has one (Instagram before Facebook). */
    private var blockedCleanNetwork: CleanNetwork? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = MaterialFadeThrough()
        exitTransition = MaterialFadeThrough()
        reenterTransition = MaterialFadeThrough()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentBlockingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val nav = findNavController()

        val appsAdapter = BlockedAppsAdapter(
            onUnlock = { pkg -> startActivity(ShortUnlockActivity.newIntent(requireContext(), pkg, null)) },
            onOpen = { pkg ->
                requireContext().packageManager.getLaunchIntentForPackage(pkg)?.let { startActivity(it) }
            }
        )
        binding.rvApps.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = appsAdapter
            itemAnimator = null
        }
        // Opening a site's screen loosens nothing by itself; its switches go through LoosenGate.
        val sitesAdapter = BlockedSitesAdapter(onOpen = { nav.navigate(R.id.action_blocking_to_website) })
        binding.rvSites.apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            adapter = sitesAdapter
        }

        binding.btnAddApp.setOnClickListener { nav.navigate(R.id.action_blocking_to_blockApps) }
        binding.btnAddSite.setOnClickListener { nav.navigate(R.id.action_blocking_to_website) }
        binding.btnRules.setOnClickListener { nav.navigate(R.id.action_blocking_to_rules) }
        binding.cardFocus.setOnClickListener { nav.navigate(R.id.action_blocking_to_focusMode) }
        binding.pillCleanFeed.setOnClickListener { openCleanFeed() }
        binding.cardCleanPro.setOnClickListener { openCleanFeed() }
        binding.btnPremium.setOnClickListener { requirePremium(premium) }
        binding.btnSetup.setOnClickListener { openProtection() }

        viewLifecycleOwner.lifecycleScope.launch {
            launch {
                premium.isPremium.collectLatest { active ->
                    binding.cardPremium.isVisible = !active
                    // Premium users just go in; free users see what they would be paying for.
                    binding.ivCleanFeedLock.isVisible = !active
                    binding.chipCleanFeedPro.isVisible = !active
                    binding.chipCleanPro.isVisible = !active
                    bindProtection(active)
                }
            }
            launch {
                viewModel.blockedApps.collectLatest { apps ->
                    appsAdapter.submitList(apps)
                    binding.rvApps.isVisible = apps.isNotEmpty()
                    binding.tvAppsEmpty.isVisible = apps.isEmpty()
                    blockedCleanNetwork = CleanNetwork.ALL.firstOrNull { n -> apps.any { it.packageName in n.packages } }
                    binding.pillCleanFeed.isVisible = blockedCleanNetwork != null
                }
            }
            launch {
                viewModel.blockedSites.collectLatest { sites ->
                    sitesAdapter.submitList(sites)
                    binding.rvSites.isVisible = sites.isNotEmpty()
                    binding.tvSitesEmpty.isVisible = sites.isEmpty()
                }
            }
            launch { viewModel.focusLeftMs.collectLatest(::bindFocusPill) }
        }
    }

    override fun onResume() {
        super.onResume()
        // Accessibility is switched in system settings while we are away.
        bindProtection(premium.isPremium.value)
    }

    private fun openProtection() = startActivity(OnboardingActivity.protectionIntent(requireContext()))

    /** The clean feed is premium: free users land on the paywall instead. */
    private fun openCleanFeed() = startActivity(
        if (premium.isPremium.value) CleanFeedActivity.newIntent(requireContext(), blockedCleanNetwork ?: InstagramNetwork)
        else PaywallActivity.newIntent(requireContext())
    )

    /** Status line under the title, plus the premium-only setup card when access is missing. */
    private fun bindProtection(premiumActive: Boolean) {
        val on = PermissionUtils.hasAccessibilityServiceEnabled(requireContext())
        binding.cardSetup.isVisible = premiumActive && !on
        binding.tvStatus.setText(if (on) R.string.blocking_status_strict_on else R.string.blocking_status_protection_off)
        binding.tvStatus.setTextColor(
            ContextCompat.getColor(requireContext(), if (on) R.color.skor_text_subtitle else R.color.skor_focus)
        )
        binding.tvStatus.setCompoundDrawablesRelativeWithIntrinsicBounds(
            if (on) R.drawable.ic_status_dot else 0, 0, 0, 0
        )
        binding.tvStatus.isClickable = !on
        binding.tvStatus.setOnClickListener(if (on) null else View.OnClickListener { openProtection() })
    }

    private fun bindFocusPill(leftMs: Long) {
        val pill = binding.tvFocusPill
        if (leftMs > 0L) {
            pill.text = getString(R.string.blocking_focus_left, TimeUtils.formatLongDuration(leftMs))
            pill.setBackgroundResource(R.drawable.bg_skor_chip)
            pill.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.skor_focus_soft)
            pill.setTextColor(ContextCompat.getColor(requireContext(), R.color.skor_focus))
        } else {
            pill.setText(R.string.blocking_focus_start)
            pill.setBackgroundResource(R.drawable.bg_skor_chip_outline)
            pill.backgroundTintList = null
            pill.setTextColor(ContextCompat.getColor(requireContext(), R.color.skor_text_dim))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
