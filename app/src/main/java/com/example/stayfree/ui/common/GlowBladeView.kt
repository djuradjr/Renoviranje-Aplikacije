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
import androidx.core.graphics.ColorUtils
import com.example.stayfree.R

/**
 * Vertical teal "blade" of light for the dashboard centre: a crisp bright core
 * that narrows to a sharp point, inside a soft halo. Both fade in from nothing at
 * the top so the blade tucks under the brain without a seam. Static.
 */
class GlowBladeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val haloBlur = 34f * density
    private val sheenBlur = 6f * density
    private val glow = ContextCompat.getColor(context, R.color.skor_focus_glow)
    private val coreTop = ContextCompat.getColor(context, R.color.skor_glow_core)
    private val coreBottom = ContextCompat.getColor(context, R.color.skor_focus)

    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isDither = true
        maskFilter = BlurMaskFilter(haloBlur, BlurMaskFilter.Blur.NORMAL)
    }
    private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isDither = true
        maskFilter = BlurMaskFilter(sheenBlur, BlurMaskFilter.Blur.NORMAL)
    }
    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isDither = true }
    private val halo = Path()
    private val sheen = Path()
    private val core = Path()

    init {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val cx = w / 2f
        // The blur spreads ~2× its radius past the path; keep that inside the view on
        // every side, or the halo is cut off in hard lines.
        val top = 2f * haloBlur
        val tip = h - 8f * density
        val coreHalf = w * 0.07f
        val fadeIn = 64f * density

        // The halo stops short of the tip so its blur never smears the point into a stub.
        blade(halo, cx, top, tip - 2f * haloBlur, (w / 2f - 2f * haloBlur).coerceAtLeast(density))
        blade(sheen, cx, top, tip - sheenBlur, coreHalf * 1.6f)
        blade(core, cx, top, tip, coreHalf)

        haloPaint.shader = fade(top, tip - 2f * haloBlur, fadeIn * 1.5f, glow, glow)
        sheenPaint.shader = fade(top, tip, fadeIn, ColorUtils.setAlphaComponent(coreTop, 110), ColorUtils.setAlphaComponent(coreBottom, 90))
        corePaint.shader = fade(top, tip, fadeIn, ColorUtils.setAlphaComponent(coreTop, 225), ColorUtils.setAlphaComponent(coreBottom, 235))
    }

    /** Transparent at [top], full [from] after [fadeIn], easing to [to] at [bottom]. */
    private fun fade(top: Float, bottom: Float, fadeIn: Float, from: Int, to: Int): Shader {
        val span = (bottom - top).coerceAtLeast(1f)
        val f = (fadeIn / span).coerceIn(0.01f, 0.5f)
        return LinearGradient(
            0f, top, 0f, bottom,
            intArrayOf(ColorUtils.setAlphaComponent(from, 0), from, to),
            floatArrayOf(0f, f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    /** Straight sides, then a taper to the point over the last 30% (at most 160dp). */
    private fun blade(path: Path, cx: Float, top: Float, tip: Float, halfWidth: Float) {
        val shoulder = tip - minOf((tip - top) * 0.3f, 160f * density)
        path.reset()
        path.moveTo(cx - halfWidth, top)
        path.lineTo(cx + halfWidth, top)
        path.lineTo(cx + halfWidth, shoulder)
        path.lineTo(cx, tip)
        path.lineTo(cx - halfWidth, shoulder)
        path.close()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawPath(halo, haloPaint)
        canvas.drawPath(sheen, sheenPaint)
        canvas.drawPath(core, corePaint)
    }
}
