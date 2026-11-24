package com.example.stayfree.ui.common

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.stayfree.R
import kotlin.math.PI
import kotlin.math.sin

/**
 * Static wavy teal glow that sits behind glass cards. API 31+ blurs the whole
 * layer with RenderEffect; below that the paint itself is blurred. Nothing animates.
 */
class GlowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val blurPx = 34f * resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.skor_focus_glow)
    }
    private val wave = Path()

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setRenderEffect(RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.DECAL))
        } else {
            paint.maskFilter = BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)
            // Hardware layers ignore mask filters before API 28.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) setLayerType(LAYER_TYPE_SOFTWARE, null)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        wave.reset()
        if (w == 0 || h == 0) return
        // A soft band that rises and falls twice across the width, kept clear of the
        // edges so the blur has room to fade out.
        val margin = blurPx
        val mid = h / 2f
        val amp = (h / 2f - margin).coerceAtLeast(0f) * 0.5f
        val thickness = (h / 2f - margin).coerceAtLeast(blurPx) * 0.9f
        val steps = 48
        for (i in 0..steps) {
            val x = margin + (w - 2 * margin) * i / steps
            val y = mid + amp * sin(i * 4 * PI / steps).toFloat() - thickness / 2f
            if (i == 0) wave.moveTo(x, y) else wave.lineTo(x, y)
        }
        for (i in steps downTo 0) {
            val x = margin + (w - 2 * margin) * i / steps
            val y = mid + amp * sin(i * 4 * PI / steps).toFloat() + thickness / 2f
            wave.lineTo(x, y)
        }
        wave.close()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawPath(wave, paint)
    }
}
