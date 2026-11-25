package com.example.stayfree.ui.common

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.example.stayfree.R

/**
 * D4 period switch (Dan / Nedelja): separate pills 6dp apart — the active one
 * light with black text, the others near-black with a hairline.
 */
class SegmentedPillView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private val density = resources.displayMetrics.density
    private val segments = mutableListOf<TextView>()

    var selectedIndex: Int = 0
        private set

    var onSelected: ((Int) -> Unit)? = null

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    fun setOptions(labels: List<String>, selected: Int = 0) {
        removeAllViews()
        segments.clear()
        val padH = (13 * density).toInt()
        labels.forEachIndexed { index, label ->
            val segment = TextView(context).apply {
                text = label
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                typeface = ResourcesCompat.getFont(context, R.font.archivo_600)
                includeFontPadding = false
                setPadding(padH, 0, padH, 0)
                minWidth = (54 * density).toInt()
                isClickable = true
                isFocusable = true
                setOnClickListener { select(index, notify = true) }
            }
            val lp = LayoutParams(LayoutParams.WRAP_CONTENT, (31 * density).toInt())
            if (index > 0) lp.marginStart = (6 * density).toInt()
            addView(segment, lp)
            segments += segment
        }
        select(selected.coerceIn(0, (labels.size - 1).coerceAtLeast(0)), notify = false)
    }

    fun select(index: Int, notify: Boolean = false) {
        selectedIndex = index
        segments.forEachIndexed { i, segment ->
            val active = i == index
            segment.setBackgroundResource(if (active) R.drawable.bg_skor_seg_active else R.drawable.bg_skor_seg_inactive)
            segment.setTextColor(
                ContextCompat.getColor(
                    context, if (active) R.color.skor_on_seg_active else R.color.skor_text_secondary
                )
            )
            segment.isSelected = active
        }
        if (notify) onSelected?.invoke(index)
    }
}
