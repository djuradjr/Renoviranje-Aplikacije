package com.example.stayfree.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.stayfree.R
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * D4 background: one teal ellipse off each side of the screen, Gaussian-blurred,
 * strongest beside the middle of the cards and fading to pure black above and below.
 *
 * Rendered once per size into a cached bitmap: the blur is computed on a coarse float
 * grid, interpolated up to full resolution and dithered before it is quantised, so the
 * long faint falloff has no steps. The right side is the left one mirrored.
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
        lobe = renderLobe(
            w = minOf(width / 2, (LOBE_WIDTH_DP * density).roundToInt()),
            h = height,
            cx = -CENTER_OUTSIDE_DP * density,
            cy = blockTop + blockHeight / 2f,
            rx = RADIUS_X_DP * density,
            ry = blockHeight / 2f,
            sigma = SIGMA_DP * density
        )
        invalidate()
    }

    private fun renderLobe(w: Int, h: Int, cx: Float, cy: Float, rx: Float, ry: Float, sigma: Float): Bitmap {
        // Coarse grid: the blur is very wide, so 4px cells lose nothing once interpolated.
        val cell = 4
        val gw = w / cell + 2
        val gh = h / cell + 2
        val margin = (3 * sigma / cell).roundToInt() + 1
        // The grid extends left of the screen so the off-screen part of the ellipse
        // still feeds the blur.
        val ox = margin
        val fw = gw + ox
        val field = FloatArray(fw * gh)
        for (y in 0 until gh) {
            val dy = (y * cell - cy) / ry
            for (x in 0 until fw) {
                val dx = ((x - ox) * cell - cx) / rx
                if (dx * dx + dy * dy <= 1f) field[y * fw + x] = 1f
            }
        }
        gaussian(field, fw, gh, sigma / cell)

        val out = IntArray(w * h)
        val rgb = teal and 0xFFFFFF
        val rnd = Random(7)
        for (y in 0 until h) {
            val fy = y / cell.toFloat()
            val y0 = floor(fy).toInt().coerceIn(0, gh - 2)
            val ty = fy - y0
            for (x in 0 until w) {
                val fx = x / cell.toFloat() + ox
                val x0 = floor(fx).toInt().coerceIn(0, fw - 2)
                val tx = fx - x0
                val i = y0 * fw + x0
                val top = field[i] + (field[i + 1] - field[i]) * tx
                val bot = field[i + fw] + (field[i + fw + 1] - field[i + fw]) * tx
                val v = top + (bot - top) * ty
                // 55% teal at full strength; triangular dither before rounding.
                val a = (v * PEAK_ALPHA + rnd.nextFloat() - rnd.nextFloat()).roundToInt().coerceIn(0, 255)
                out[y * w + x] = (a shl 24) or rgb
            }
        }
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }

    /** True-ish Gaussian: three box-blur passes per axis. */
    private fun gaussian(data: FloatArray, w: Int, h: Int, sigma: Float) {
        val r = ((kotlin.math.sqrt(12f * sigma * sigma / 3f + 1f) - 1f) / 2f).roundToInt().coerceAtLeast(1)
        val tmp = FloatArray(data.size)
        repeat(3) {
            boxH(data, tmp, w, h, r)
            boxV(tmp, data, w, h, r)
        }
    }

    private fun boxH(src: FloatArray, dst: FloatArray, w: Int, h: Int, r: Int) {
        val n = 2f * r + 1f
        for (y in 0 until h) {
            val row = y * w
            var sum = 0f
            for (k in -r..r) sum += src[row + k.coerceIn(0, w - 1)]
            for (x in 0 until w) {
                dst[row + x] = sum / n
                sum += src[row + (x + r + 1).coerceAtMost(w - 1)] - src[row + (x - r).coerceAtLeast(0)]
            }
        }
    }

    private fun boxV(src: FloatArray, dst: FloatArray, w: Int, h: Int, r: Int) {
        val n = 2f * r + 1f
        for (x in 0 until w) {
            var sum = 0f
            for (k in -r..r) sum += src[k.coerceIn(0, h - 1) * w + x]
            for (y in 0 until h) {
                dst[y * w + x] = sum / n
                sum += src[(y + r + 1).coerceAtMost(h - 1) * w + x] - src[(y - r).coerceAtLeast(0) * w + x]
            }
        }
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
