package com.example.stayfree.ui.blocking

import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.stayfree.R
import com.example.stayfree.databinding.ItemBlockedAppBinding
import com.example.stayfree.databinding.ItemBlockedSiteBinding
import com.example.stayfree.util.AppInfoUtils

class BlockedAppsAdapter(
    private val onUnlock: (String) -> Unit,
    private val onOpen: (String) -> Unit
): ListAdapter<BlockedApp, BlockedAppsAdapter.Holder>(Diff) {

    class Holder(val binding: ItemBlockedAppBinding) : RecyclerView.ViewHolder(binding.root) {
        var boundPackage: String? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemBlockedAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        binding.ivIcon.outlineProvider = ViewOutlineProvider.BACKGROUND
        binding.ivIcon.clipToOutline = true
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val app = getItem(position)
        val b = holder.binding
        val context = b.root.context
        if (holder.boundPackage != app.packageName) {
            // Icon and label only change when the row now shows another app.
            holder.boundPackage = app.packageName
            b.ivIcon.setImageDrawable(AppInfoUtils.getAppIcon(context, app.packageName))
            b.tvName.text = AppInfoUtils.getAppName(context, app.packageName)
            b.ring.setUnlockLeft(null)
        }
        val left = app.leftMs
        val unlocked = left != null
        b.ring.setUnlockLeft(if (left != null && app.totalMs > 0L) left.toFloat() / app.totalMs else null)
        b.badge.isVisible = !unlocked
        // Dimmed while blocked, full colour while the user is allowed in.
        b.ivIcon.alpha = if (unlocked) 1f else 0.6f
        b.tvAction.text = if (left != null) {
            context.getString(R.string.blocking_unlock_left, ((left + 59_999L) / 60_000L).toInt())
        } else context.getString(R.string.blocking_unlock)
        b.root.setOnClickListener { if (unlocked) onOpen(app.packageName) else onUnlock(app.packageName) }
    }

    private object Diff : DiffUtil.ItemCallback<BlockedApp>() {
        override fun areItemsTheSame(a: BlockedApp, b: BlockedApp) = a.packageName == b.packageName
        override fun areContentsTheSame(a: BlockedApp, b: BlockedApp) = a == b
        // Rebind in place: the default change animation would cross-fade the icon every second.
        override fun getChangePayload(oldItem: BlockedApp, newItem: BlockedApp) = Unit
    }
}

class BlockedSitesAdapter(
    private val onOpen: () -> Unit
) : ListAdapter<Pair<Long, String>, BlockedSitesAdapter.Holder>(Diff) {

    class Holder(val binding: ItemBlockedSiteBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemBlockedSiteBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.binding.tvDomain.text = getItem(position).second
        holder.binding.root.setOnClickListener { onOpen() }
    }

    private object Diff : DiffUtil.ItemCallback<Pair<Long, String>>() {
        override fun areItemsTheSame(a: Pair<Long, String>, b: Pair<Long, String>) = a.first == b.first
        override fun areContentsTheSame(a: Pair<Long, String>, b: Pair<Long, String>) = a == b
    }
}
