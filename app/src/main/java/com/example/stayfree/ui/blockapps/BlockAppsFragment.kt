package com.example.stayfree.ui.blockapps

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.stayfree.R
import com.example.stayfree.data.billing.PremiumRepository
import com.example.stayfree.databinding.FragmentBlockAppsBinding
import com.example.stayfree.ui.cleanfeed.CleanInstagramActivity
import com.example.stayfree.ui.common.LoosenGate
import com.example.stayfree.ui.common.bindBackHeader
import com.example.stayfree.ui.premium.requireBlocking
import com.example.stayfree.ui.premium.requirePremium
import com.example.stayfree.util.PinGate
import com.example.stayfree.util.StrictGate
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BlockAppsFragment : Fragment() {

    @Inject lateinit var premium: PremiumRepository

    private var _binding: FragmentBlockAppsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: BlockAppsViewModel by viewModels()
    private lateinit var adapter: BlockAppsAdapter

    @Inject lateinit var pinGate: PinGate
    @Inject lateinit var strictGate: StrictGate
    private val loosenGate by lazy { LoosenGate(this, strictGate, pinGate) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentBlockAppsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bindBackHeader(binding.backHeader)

        // Loosening (turning protection off, raising a limit) goes through the gate;
        // tightening is always free.
        adapter = BlockAppsAdapter(
            onToggle = { pkg, blocked ->
                if (blocked) {
                    if (requireBlocking(premium)) viewModel.setBlocked(pkg, true)
                    else adapter.notifyDataSetChanged()
                } else loosenGate.run(onDenied = { adapter.notifyDataSetChanged() }) {
                    viewModel.setBlocked(pkg, false)
                }
            },
            onLimitChange = { pkg, minutes, increase ->
                if (!increase) viewModel.setLimit(pkg, minutes)
                else loosenGate.run { viewModel.setLimit(pkg, minutes) }
            },
            onContentToggle = { id, enabled ->
                if (enabled) {
                    if (requireBlocking(premium)) viewModel.setContentEnabled(id, true)
                    else adapter.notifyDataSetChanged()
                } else loosenGate.run(onDenied = { adapter.notifyDataSetChanged() }) {
                    viewModel.setContentEnabled(id, false)
                }
            },
            onContentLimitChange = { id, minutes, increase ->
                if (!increase) viewModel.setContentLimit(id, minutes)
                else loosenGate.run { viewModel.setContentLimit(id, minutes) }
            },
            onCleanFeedOpen = {
                if (requirePremium(premium)) startActivity(CleanInstagramActivity.newIntent(requireContext()))
            },
            // More posts per session is loosening, like a higher time limit.
            onCleanFeedLimitChange = { posts, increase ->
                if (!increase) viewModel.setCleanFeedLimit(posts)
                else loosenGate.run { viewModel.setCleanFeedLimit(posts) }
            },
            onCleanFeedPauseChange = { minutes, loosen ->
                if (!loosen) viewModel.setCleanFeedPause(minutes)
                else loosenGate.run { viewModel.setCleanFeedPause(minutes) }
            },
            onCleanFeedSessionsChange = { sessions, loosen ->
                if (!loosen) viewModel.setCleanFeedSessionsPerDay(sessions)
                else loosenGate.run { viewModel.setCleanFeedSessionsPerDay(sessions) }
            }
        )
        binding.rvBlockApps.apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter = this@BlockAppsFragment.adapter
        }

        binding.btnSearch.setOnClickListener { toggleSearch() }
        binding.etSearch.doOnTextChanged { text, _, _, _ ->
            viewModel.setQuery(text?.toString().orEmpty())
        }

        binding.btnAddRule.setOnClickListener {
            if (requirePremium(premium)) findNavController().navigate(R.id.action_blockApps_to_addRule)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.items.collectLatest { list ->
                val loading = list == null
                binding.progressLoading.visibility = if (loading) View.VISIBLE else View.GONE
                adapter.submitList(list.orEmpty())
                val empty = !loading && list.orEmpty().isEmpty()
                binding.tvEmpty.setText(
                    if (binding.etSearch.text.isNullOrBlank()) R.string.block_apps_empty
                    else R.string.block_apps_no_results
                )
                binding.tvEmpty.visibility = if (empty) View.VISIBLE else View.GONE
                binding.rvBlockApps.visibility = if (empty || loading) View.GONE else View.VISIBLE
            }
        }
    }

    private fun toggleSearch() {
        val show = binding.tilSearch.visibility != View.VISIBLE
        binding.tilSearch.visibility = if (show) View.VISIBLE else View.GONE
        val imm = requireContext().getSystemService(InputMethodManager::class.java)
        if (show) {
            binding.etSearch.requestFocus()
            imm.showSoftInput(binding.etSearch, 0)
        } else {
            binding.etSearch.setText("")
            imm.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
