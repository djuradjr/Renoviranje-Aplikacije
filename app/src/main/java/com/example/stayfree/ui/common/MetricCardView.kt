package com.example.stayfree.ui.common

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import com.example.stayfree.databinding.ViewMetricCardBinding
import com.example.stayfree.databinding.ViewStreakRowBinding

/** Big number over a caption, e.g. "2h 14m" / "on screen · goal 3h". */
class MetricCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GlassCardView(context, attrs) {

    private val binding = ViewMetricCardBinding.inflate(LayoutInflater.from(context), this)

    fun bind(value: CharSequence, caption: CharSequence) {
        binding.tvValue.text = value
        binding.tvCaption.text = caption
    }
}

/** "Streak without overruns ........ 5 days". */
class StreakRowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GlassCardView(context, attrs) {

    private val binding = ViewStreakRowBinding.inflate(LayoutInflater.from(context), this)

    fun bind(days: Int) {
        binding.tvValue.text = resources.getQuantityString(
            com.example.stayfree.R.plurals.streak_days, days, days
        )
    }
}
