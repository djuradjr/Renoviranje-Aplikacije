package com.example.stayfree.ui.common

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.os.Build
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.stayfree.R

/**
 * Vertical teal "blade" of light for the dashboard centre: a bright core that
 * narrows to a point at the bottom, inside a blurred halo. Static.
 */
class GlowBladeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val haloBlur = 34f * density
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.skor_focus_glow)
        maskFilter = BlurMaskFilter(haloBlur, BlurMaskFilter.Blur.NORMAL)
    }
    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        alpha = 210
        maskFilter = BlurMaskFilter(3f * density, BlurMaskFilter.Blur.NORMAL)
    }
    private val halo = Path()
    private val core = Path()

    init {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val cx = w / 2f
        val top = haloBlur
        val tip = h - haloBlur
        // The blur spreads ~2× its radius past the path; keep that inside the view
        // or the halo is cut off in hard vertical lines.
        blade(halo, cx, top, tip, (w / 2f - 2f * haloBlur).coerceAtLeast(density))
        blade(core, cx, top, tip, w * 0.07f)
        corePaint.shader = LinearGradient(
            0f, top, 0f, tip,
            ContextCompat.getColor(context, R.color.skor_glow_core),
            ContextCompat.getColor(context, R.color.skor_focus),
            Shader.TileMode.CLAMP
        )
    }

    /** Straight sides, then a taper to the point over the last 30% (at most 160dp). */
    private fun blade(path: Path, cx: Float, top: Float, tip: Float, halfWidth: Float) {
        val shoulder = tip - minOf((tip - top) * 0.3f, 160f * density)
        path.reset()
        path.moveTo(cx - halfWidth, top + halfWidth)
        path.quadTo(cx - halfWidth, top, cx, top)
        path.quadTo(cx + halfWidth, top, cx + halfWidth, top + halfWidth)
        path.lineTo(cx + halfWidth, shoulder)
        path.lineTo(cx, tip)
        path.lineTo(cx - halfWidth, shoulder)
        path.close()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawPath(halo, haloPaint)
        canvas.drawPath(core, corePaint)
    }
}
