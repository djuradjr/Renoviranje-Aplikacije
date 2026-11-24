package com.example.stayfree.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.stayfree.R
import kotlin.math.roundToInt

/**
 * D4 dashboard background: one teal ellipse off each side of the screen,
 * Gaussian-blurred, strongest beside the middle of the cards and fading to pure
 * black above and below. Rendered once per size by [GlowBitmap]; the right side
 * is the left one mirrored.
 */
class SideGlowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val teal = ContextCompat.getColor(context, R.color.skor_focus)
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private var lobe: Bitmap? = null
    private var blockTop = 0
    private var blockHeight = 0

    /** The cards the glow sits beside, in px from this view's top. */
    fun setBlock(top: Int, height: Int) {
        if (top == blockTop && height == blockHeight) return
        blockTop = top
        blockHeight = height
        rebuild()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = rebuild()

    private fun rebuild() {
        lobe?.recycle()
        lobe = null
        if (width == 0 || height == 0 || blockHeight <= 0) return invalidate()
        lobe = GlowBitmap.render(
            w = minOf(width / 2, (LOBE_WIDTH_DP * density).roundToInt()),
            h = height,
            cx = -CENTER_OUTSIDE_DP * density,
            cy = blockTop + blockHeight / 2f,
            rx = RADIUS_X_DP * density,
            ry = blockHeight / 2f,
            sigma = SIGMA_DP * density,
            rgb = teal,
            peakAlpha = PEAK_ALPHA
        )
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val bmp = lobe ?: return
        canvas.drawBitmap(bmp, 0f, 0f, paint)
        canvas.save()
        canvas.scale(-1f, 1f, width / 2f, 0f)
        canvas.drawBitmap(bmp, 0f, 0f, paint)
        canvas.restore()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        lobe?.recycle()
        lobe = null
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (lobe == null) rebuild()
    }

    private companion object {
        const val PEAK_ALPHA = 140f // #3FBFA3 at 55%
        const val CENTER_OUTSIDE_DP = 20f
        const val RADIUS_X_DP = 97f
        const val SIGMA_DP = 34f
        const val LOBE_WIDTH_DP = 200f
    }
}
