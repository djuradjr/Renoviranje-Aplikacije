package com.example.stayfree.ui.blocking

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import com.example.stayfree.R

/**
 * The ring around a blocked app's icon: a thin orange circle while it is blocked, a
 * teal arc of the time left while a short unlock runs.
 */
class UnlockRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val blockedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2 * density
        color = ContextCompat.getColor(context, R.color.skor_distraction)
    }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3 * density
        color = ContextCompat.getColor(context, R.color.skor_line)
    }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3 * density
        strokeCap = Paint.Cap.ROUND
        color = ContextCompat.getColor(context, R.color.skor_focus)
    }
    private val bounds = RectF()
    private var unlocked = false
    private var progress = 0f
    private var animator: ValueAnimator? = null

    /** [fraction] of the unlock still left, or null while the app is blocked. */
    fun setUnlockLeft(fraction: Float?) {
        val nowUnlocked = fraction != null
        val target = fraction?.coerceIn(0f, 1f) ?: 0f
        animator?.cancel()
        if (nowUnlocked && !unlocked) {
            // Unlock just started: sweep the arc in from empty.
            unlocked = true
            animateTo(0f, target, 600L)
        } else if (nowUnlocked) {
            animateTo(progress, target, 300L)
        } else {
            unlocked = false
            progress = 0f
            invalidate()
        }
    }

    private fun animateTo(from: Float, to: Float, duration: Long) {
        animator = ValueAnimator.ofFloat(from, to).apply {
            this.duration = duration
            interpolator = DecelerateInterpolator()
            addUpdateListener { progress = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val inset = 2 * density
        bounds.set(inset, inset, width - inset, height - inset)
        if (!unlocked) {
            canvas.drawOval(bounds, blockedPaint)
            return
        }
        canvas.drawOval(bounds, trackPaint)
        canvas.drawArc(bounds, -90f, 360f * progress, false, arcPaint)
    }
}
