package com.example.stayfree.ui.common

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import com.example.stayfree.R
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 24 stacked bars, one per clock hour: distraction time at the base, everything
 * else above it. Replaces the single-colour line chart — colour is what makes
 * the chart readable at a glance ("evenings are eating me"), not the shape.
 *
 * Dragging across the chart selects an hour and reports it through
 * [onHourSelected]; releasing clears the selection.
 */
class HourlyBarsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /** (distractionMs, otherMs) per hour, 24 entries. */
    private var data: List<Pair<Long, Long>> = emptyList()
    private var maxTotal = 1L
    private var selectedHour: Int? = null
    private var reveal = 1f
    private var animator: ValueAnimator? = null

    private val distractionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.skor_distraction)
    }
    private val otherPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.skor_focus)
    }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.skor_track)
    }
    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val clip = Path()

    private val barGap = dp(3f)
    private val maxBarWidth = dp(10f)

    /** Reports the hour under the finger, or null once the touch ends. */
    var onHourSelected: ((Int?) -> Unit)? = null

    fun setData(hours: List<Pair<Long, Long>>, animate: Boolean = true) {
        data = hours
        maxTotal = HourlyStack.maxTotal(hours)
        animator?.cancel()
        if (!animate || !isAttachedToWindow) {
            reveal = 1f
            invalidate()
            return
        }
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 600L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                reveal = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (data.isEmpty()) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                val usable = width - paddingLeft - paddingRight
                if (usable <= 0) return true
                val slot = usable.toFloat() / data.size
                val hour = ((event.x - paddingLeft) / slot).toInt().coerceIn(0, data.size - 1)
                if (hour != selectedHour) {
                    selectedHour = hour
                    onHourSelected?.invoke(hour)
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                selectedHour = null
                onHourSelected?.invoke(null)
                invalidate()
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        val left = paddingLeft.toFloat()
        val top = paddingTop.toFloat()
        val usableW = width - paddingLeft - paddingRight
        val usableH = (height - paddingTop - paddingBottom).toFloat()
        if (usableW <= 0 || usableH <= 0f) return

        val slot = usableW.toFloat() / HOURS
        val barW = (slot - barGap).coerceIn(1f, maxBarWidth)
        val radius = barW / 2f
        val bottom = top + usableH

        for (hour in 0 until HOURS) {
            val x = left + hour * slot + (slot - barW) / 2f
            // Every hour keeps its empty track, so a quiet day still shows the clock.
            rect.set(x, top, x + barW, bottom)
            canvas.drawRoundRect(rect, radius, radius, trackPaint)

            val (distractionMs, usefulMs) = data.getOrNull(hour) ?: continue
            val seg = HourlyStack.segments(distractionMs, usefulMs, maxTotal, usableH * reveal)
            if (seg.total <= 0f) continue
            val dimmed = selectedHour != null && selectedHour != hour

            // One rounded silhouette per bar, so the colour seam stays straight.
            clip.reset()
            rect.set(x, bottom - max(seg.total, barW), x + barW, bottom)
            clip.addRoundRect(rect, radius, radius, Path.Direction.CW)
            canvas.save()
            canvas.clipPath(clip)
            if (seg.distraction > 0f) {
                rect.set(x, bottom - seg.distraction, x + barW, bottom)
                canvas.drawRect(rect, paint(distractionPaint, dimmed))
            }
            if (seg.useful > 0f) {
                rect.set(x, bottom - seg.total, x + barW, bottom - seg.distraction)
                canvas.drawRect(rect, paint(otherPaint, dimmed))
            }
            canvas.restore()
        }
    }

    private fun paint(source: Paint, dimmed: Boolean): Paint {
        if (!dimmed) return source
        dimPaint.color = source.color
        dimPaint.alpha = (255 * DIM_ALPHA).roundToInt()
        return dimPaint
    }

    private fun dp(value: Float) = value * resources.displayMetrics.density

    private companion object {
        const val DIM_ALPHA = 0.32f
        const val HOURS = 24
    }
}
