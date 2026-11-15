package com.example.stayfree.ui.blocking

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.stayfree.R
import com.example.stayfree.data.billing.PremiumRepository
import com.example.stayfree.databinding.FragmentBlockingRulesBinding
import com.example.stayfree.ui.common.LoosenGate
import com.example.stayfree.ui.common.bindBackHeader
import com.example.stayfree.ui.premium.requireBlocking
import com.example.stayfree.util.PinGate
import com.example.stayfree.util.StrictGate
import com.example.stayfree.util.TimeUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

/** Every tool and every active block — what the Blocking tab showed before its redesign. */
@AndroidEntryPoint
class BlockingRulesFragment : Fragment() {

    @Inject lateinit var premium: PremiumRepository
    @Inject lateinit var pinGate: PinGate
    @Inject lateinit var strictGate: StrictGate
    private val loosenGate by lazy { LoosenGate(this, strictGate, pinGate) }

    private var _binding: FragmentBlockingRulesBinding? = null
    private val binding get() = _binding!!
    private val viewModel: BlockingViewModel by viewModels()
    private lateinit var adapter: BlockRulesAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentBlockingRulesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindBackHeader(binding.backHeader)
        val nav = findNavController()

        adapter = BlockRulesAdapter(
            onToggle = { id, active ->
                if (!active) {
                    loosenGate.run(onDenied = { adapter.notifyDataSetChanged() }) {
                        viewModel.toggleRule(id, false)
                    }
                } else if (requireBlocking(premium)) viewModel.toggleRule(id, true)
                else adapter.notifyDataSetChanged()
            },
            onDelete = { id -> loosenGate.run { viewModel.deleteRule(id) } },
            onOpenApps = { nav.navigate(R.id.blockAppsFragment) },
            onOpenSites = { nav.navigate(R.id.websiteBlockFragment) }
        )
        binding.rvBlockRules.apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter = this@BlockingRulesFragment.adapter
        }

        binding.btnInAppBlocking.setOnClickListener { nav.navigate(R.id.blockAppsFragment) }
        binding.btnWebsiteBlocking.setOnClickListener { nav.navigate(R.id.websiteBlockFragment) }
        binding.btnFocusMode.setOnClickListener { nav.navigate(R.id.focusModeFragment) }
        binding.btnSleepMode.setOnClickListener { nav.navigate(R.id.sleepModeFragment) }
        binding.btnStrictMode.setOnClickListener { nav.navigate(R.id.strictModeFragment) }

        observeData()
    }

    private fun lockIcon() = ContextCompat.getDrawable(requireContext(), R.drawable.ic_lock)?.mutate()?.apply {
        val size = resources.getDimensionPixelSize(R.dimen.premium_lock_size)
        setBounds(0, 0, size, size)
        setTint(ContextCompat.getColor(requireContext(), R.color.skor_text_dim))
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            launch {
                premium.isPremium.collectLatest { active ->
                    val lock = if (active) null else lockIcon()
                    listOf(
                        binding.statusBlockApps, binding.statusWebsites, binding.statusFocus,
                        binding.statusSleep, binding.statusStrict
                    ).forEach {
                        it.setCompoundDrawablesRelative(null, null, lock, null)
                        it.compoundDrawablePadding = resources.getDimensionPixelSize(R.dimen.premium_lock_gap)
                    }
                }
            }
            launch {
                viewModel.activeBlocks.collectLatest { items ->
                    adapter.submitList(items)
                    binding.tvRulesCount.text = resources.getString(R.string.blocking_rules_count, items.size)
                    val empty = items.isEmpty()
                    binding.emptyState.visibility = if (empty) View.VISIBLE else View.GONE
                    binding.rvBlockRules.visibility = if (empty) View.GONE else View.VISIBLE
                }
            }
            launch {
                viewModel.focusActive.collectLatest { active ->
                    bindStatus(
                        binding.statusFocus, active,
                        getString(if (active) R.string.blocking_status_active else R.string.blocking_status_off)
                    )
                }
            }
            launch {
                viewModel.sleepEndMinutes.collectLatest { end ->
                    bindStatus(
                        binding.statusSleep, end != null,
                        if (end != null) {
                            val time = String.format(Locale.US, "%02d:%02d", end / 60, end % 60)
                            getString(R.string.blocking_status_until, time)
                        } else getString(R.string.blocking_status_off)
                    )
                }
            }
            launch {
                viewModel.strictRemainingMs.collectLatest { ms ->
                    bindStatus(
                        binding.statusStrict, ms > 0L,
                        if (ms > 0L) getString(R.string.strict_status_left, TimeUtils.formatLongDuration(ms))
                        else getString(R.string.blocking_status_off)
                    )
                }
            }
            launch {
                viewModel.activeWebsiteCount.collectLatest { count ->
                    bindStatus(
                        binding.statusWebsites, count > 0,
                        resources.getQuantityString(R.plurals.blocking_sites_status, count, count)
                    )
                }
            }
            launch {
                viewModel.contentTargetCount.collectLatest { count ->
                    bindStatus(
                        binding.statusBlockApps, count > 0,
                        resources.getQuantityString(R.plurals.blocking_targets_status, count, count)
                    )
                }
            }
        }
    }

    /** Active statuses take the action colour; off states stay dim. */
    private fun bindStatus(view: TextView, active: Boolean, text: String) {
        view.text = text
        view.setTextColor(
            ContextCompat.getColor(requireContext(), if (active) R.color.skor_focus else R.color.skor_text_dim)
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
