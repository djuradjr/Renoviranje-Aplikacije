package com.example.stayfree.ui.common

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.example.stayfree.R

/**
 * D4 background: two teal lobes hugging the left and right screen edges, black in
 * between. Pure gradients (horizontal falloff × vertical mask), no blur pass, so
 * nothing bands or turns milky. The vertical band is set by [setBand]. Static.
 */
class SideGlowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val teal = ContextCompat.getColor(context, R.color.skor_focus)
    private val lobePaint = Paint().apply { isDither = true }
    private val maskPaint = Paint().apply {
        isDither = true
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }
    private var fadeInEnd = 0f
    private var fadeOutStart = 0f

    /**
     * Fully lit between [fullFrom] and [fullTo] (px from this view's top); fades in
     * from the top edge and out to the bottom edge.
     */
    fun setBand(fullFrom: Int, fullTo: Int) {
        if (fadeInEnd == fullFrom.toFloat() && fadeOutStart == fullTo.toFloat()) return
        fadeInEnd = fullFrom.toFloat()
        fadeOutStart = fullTo.toFloat()
        updateMask()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        updateMask()
    }

    private fun updateMask() {
        val h = height.toFloat()
        if (h <= 0f) return
        val a = (fadeInEnd / h).coerceIn(0f, 1f)
        val b = (fadeOutStart / h).coerceIn(a, 1f)
        maskPaint.shader = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(0, -0x1000000, -0x1000000, 0),
            floatArrayOf(0f, a, b, 1f),
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        if (width == 0 || height == 0) return
        val reach = LOBE_DP.last() * density
        val layer = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        // Left lobe, then the same mirrored.
        lobePaint.shader = LinearGradient(0f, 0f, reach, 0f, lobeColors, lobeStops, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, reach, height.toFloat(), lobePaint)
        lobePaint.shader = LinearGradient(width.toFloat(), 0f, width - reach, 0f, lobeColors, lobeStops, Shader.TileMode.CLAMP)
        canvas.drawRect(width - reach, 0f, width.toFloat(), height.toFloat(), lobePaint)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), maskPaint)
        canvas.restoreToCount(layer)
    }

    // Teal at 55% over black at the very edge (#236959), falling off like a gaussian.
    private val lobeColors = LOBE_STRENGTH.map { ColorUtils.setAlphaComponent(teal, (140 * it).toInt()) }.toIntArray()
    private val lobeStops = LOBE_DP.map { it / LOBE_DP.last() }.toFloatArray()

    private companion object {
        val LOBE_DP = floatArrayOf(0f, 25f, 50f, 75f, 100f, 125f, 140f)
        val LOBE_STRENGTH = floatArrayOf(1f, 0.97f, 0.80f, 0.55f, 0.26f, 0.08f, 0f)
    }
}
