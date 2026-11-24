package com.example.stayfree.ui.common

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import com.example.stayfree.R

/**
 * D4 glass card. The fill is translucent (55% on API 31+, where a blurred
 * [GlowView] sits behind it; 85% below), so it must never get an elevation shadow.
 */
open class GlassCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {
    init {
        setBackgroundResource(R.drawable.bg_skor_card)
        clipToOutline = true
    }
}
