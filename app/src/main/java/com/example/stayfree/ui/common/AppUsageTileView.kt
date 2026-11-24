package com.example.stayfree.ui.common

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.example.stayfree.databinding.ViewAppUsageTileBinding

/** One app in the "most used" row: icon on top, name, time. */
class AppUsageTileView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GlassCardView(context, attrs) {

    private val binding = ViewAppUsageTileBinding.inflate(LayoutInflater.from(context), this)

    init {
        val ripple = TypedValue()
        context.theme.resolveAttribute(android.R.attr.selectableItemBackground, ripple, true)
        foreground = ContextCompat.getDrawable(context, ripple.resourceId)
    }

    /** [icon] null → brand-coloured square with the first letter. */
    fun bind(label: String, packageName: String, icon: Drawable?, time: String) {
        binding.tvName.text = label
        binding.tvTime.text = time
        binding.ivIcon.isVisible = icon != null
        binding.tvLetter.isVisible = icon == null
        if (icon != null) {
            binding.ivIcon.setImageDrawable(icon)
        } else {
            binding.tvLetter.text = AppBrand.letterFor(label)
            binding.tvLetter.backgroundTintList =
                ColorStateList.valueOf(AppBrand.colorFor(packageName))
        }
        contentDescription = "$label, $time"
    }
}
