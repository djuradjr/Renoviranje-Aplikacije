package com.example.stayfree.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.stayfree.R

/** Soft teal glow from the top-right corner (Settings header), drawn by [GlowBitmap]. */
class CornerGlowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private var glow: Bitmap? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = rebuild()

    private fun rebuild() {
        glow?.recycle()
        glow = null
        if (width == 0 || height == 0) return
        glow = GlowBitmap.render(
            w = width, h = height,
            // Kept ~2σ clear of the top edge: the screen content starts under the
            // status bar, and a glow still bright there would be cut off flat.
            cx = width + 10f * density, cy = 120f * density,
            rx = 100f * density, ry = 55f * density,
            sigma = 34f * density,
            rgb = ContextCompat.getColor(context, R.color.skor_focus),
            peakAlpha = 140f
        )
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        glow?.let { canvas.drawBitmap(it, 0f, 0f, paint) }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        glow?.recycle()
        glow = null
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (glow == null && width > 0) rebuild()
    }
}
