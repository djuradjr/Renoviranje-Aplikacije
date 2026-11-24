package com.example.stayfree.ui.common

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
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
 * Vertical teal "blade" for the dashboard centre: a hard-edged bright core with a
 * short linear halo, narrowing to a sharp point. Drawn with gradients only (no blur
 * pass), so it stays crisp and never turns milky. It fades in from nothing at the
 * top so it tucks under the brain without a seam. Static.
 */
class GlowBladeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val coreHalf = 13f * density
    private val haloReach = 45f * density
    private val tipLength = 70f * density
    private val fadeIn = 64f * density

    private val teal = ContextCompat.getColor(context, R.color.skor_focus)
    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isDither = true }
    private val haloPaint = Paint().apply { isDither = true }
    private val maskPaint = Paint().apply {
        isDither = true
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }
    private val scale = Matrix()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val cx = w / 2f
        val half = coreHalf + haloReach
        // Halo: full teal at the core edge, linear to nothing 45dp out.
        val edge = haloReach / (2f * half)
        haloPaint.shader = LinearGradient(
            cx - half, 0f, cx + half, 0f,
            intArrayOf(ColorUtils.setAlphaComponent(teal, 0), teal, teal, ColorUtils.setAlphaComponent(teal, 0)),
            floatArrayOf(0f, edge, 1f - edge, 1f),
            Shader.TileMode.CLAMP
        )
        // Core: crisp — the darker rim only lives in the outer ~3px.
        val rim = (3f / (2f * coreHalf)).coerceAtMost(0.2f)
        corePaint.shader = LinearGradient(
            cx - coreHalf, 0f, cx + coreHalf, 0f,
            intArrayOf(RIM, CORE, CENTRE, CORE, RIM),
            floatArrayOf(0f, rim, 0.5f, 1f - rim, 1f),
            Shader.TileMode.CLAMP
        )
        maskPaint.shader = LinearGradient(
            0f, 0f, 0f, fadeIn,
            intArrayOf(0, -0x1000000), null, Shader.TileMode.CLAMP
        )
    }

    /** Core half-width at [fromTip] px above the point: 26 → 18 → 9 → 2dp over the last 70dp. */
    private fun halfWidthAt(fromTip: Float): Float {
        if (fromTip >= tipLength) return coreHalf
        val t = (fromTip / tipLength).coerceIn(0f, 1f)
        val dp = when {
            t > 2f / 3f -> lerp(9f, 13f, (t - 2f / 3f) * 3f)
            t > 1f / 3f -> lerp(4.5f, 9f, (t - 1f / 3f) * 3f)
            else -> lerp(1f, 4.5f, t * 3f)
        }
        return dp * density
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    override fun onDraw(canvas: Canvas) {
        if (width == 0 || height == 0) return
        val cx = width / 2f
        val tip = height.toFloat()
        // Whole pixel rows from here on, so the slices meet without anti-aliased seams.
        val bodyEnd = kotlin.math.floor(tip - tipLength)
        val layer = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)

        // Straight part.
        canvas.drawRect(cx - coreHalf - haloReach, 0f, cx + coreHalf + haloReach, bodyEnd, haloPaint)
        canvas.drawRect(cx - coreHalf, 0f, cx + coreHalf, bodyEnd, corePaint)

        // Tip: thin slices, each the straight blade squeezed to that width, so halo and
        // core narrow together and the point stays sharp.
        val slice = 1f
        var y = bodyEnd
        while (y < tip) {
            val s = halfWidthAt(tip - (y + slice / 2f)) / coreHalf
            scale.setScale(s, 1f, cx, 0f)
            haloPaint.shader.setLocalMatrix(scale)
            corePaint.shader.setLocalMatrix(scale)
            val hw = (coreHalf + haloReach) * s
            canvas.drawRect(cx - hw, y, cx + hw, y + slice, haloPaint)
            canvas.drawRect(cx - coreHalf * s, y, cx + coreHalf * s, y + slice, corePaint)
            y += slice
        }
        scale.reset()
        haloPaint.shader.setLocalMatrix(scale)
        corePaint.shader.setLocalMatrix(scale)

        canvas.drawRect(0f, 0f, width.toFloat(), fadeIn, maskPaint)
        canvas.restoreToCount(layer)
    }

    private companion object {
        val CORE = 0xFF61FFF6.toInt()
        val CENTRE = 0xFF70FFFF.toInt()
        val RIM = 0xFF37BEB3.toInt()
    }
}
