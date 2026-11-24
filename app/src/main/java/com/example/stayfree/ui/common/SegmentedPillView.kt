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

/** D4 segmented switch (Dan / Nedelja): light active pill on a near-black track. */
class SegmentedPillView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private val segments = mutableListOf<TextView>()
    private val pad = (4 * resources.displayMetrics.density).toInt()

    var selectedIndex: Int = 0
        private set

    var onSelected: ((Int) -> Unit)? = null

    init {
        orientation = HORIZONTAL
        setBackgroundResource(R.drawable.bg_skor_seg_track)
        setPadding(pad, pad, pad, pad)
    }

    fun setOptions(labels: List<String>, selected: Int = 0) {
        removeAllViews()
        segments.clear()
        labels.forEachIndexed { index, label ->
            val segment = TextView(context).apply {
                text = label
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                typeface = ResourcesCompat.getFont(context, R.font.archivo_600)
                setPadding(pad * 3, pad * 2, pad * 3, pad * 2)
                isClickable = true
                isFocusable = true
                setOnClickListener { select(index, notify = true) }
            }
            // Wraps its label inside a wrap_content pill; shares spare width when stretched.
            addView(segment, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, 1f))
            segments += segment
        }
        select(selected.coerceIn(0, (labels.size - 1).coerceAtLeast(0)), notify = false)
    }

    fun select(index: Int, notify: Boolean = false) {
        selectedIndex = index
        segments.forEachIndexed { i, segment ->
            val active = i == index
            segment.setBackgroundResource(if (active) R.drawable.bg_skor_seg_active else 0)
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
