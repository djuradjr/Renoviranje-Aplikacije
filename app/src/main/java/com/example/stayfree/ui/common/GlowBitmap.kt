package com.example.stayfree.ui.common

import android.graphics.Bitmap
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * A solid ellipse of [rgb] Gaussian-blurred into a [w]×[h] bitmap, for the D4 glows.
 * The blur runs on a coarse float grid (the glow is wide, so 4px cells lose nothing),
 * is interpolated to full resolution and dithered before quantising — that is what
 * keeps long faint falloffs free of steps. Ellipse coordinates may lie off the bitmap.
 */
object GlowBitmap {

    private const val CELL = 4

    fun render(
        w: Int, h: Int,
        cx: Float, cy: Float, rx: Float, ry: Float,
        sigma: Float, rgb: Int, peakAlpha: Float
    ): Bitmap {
        val margin = (3 * sigma / CELL).roundToInt() + 1
        val gw = w / CELL + 2 + 2 * margin
        val gh = h / CELL + 2 + 2 * margin
        val field = FloatArray(gw * gh)
        for (y in 0 until gh) {
            val dy = ((y - margin) * CELL - cy) / ry
            for (x in 0 until gw) {
                val dx = ((x - margin) * CELL - cx) / rx
                if (dx * dx + dy * dy <= 1f) field[y * gw + x] = 1f
            }
        }
        gaussian(field, gw, gh, sigma / CELL)

        val out = IntArray(w * h)
        val color = rgb and 0xFFFFFF
        val rnd = Random(7)
        for (y in 0 until h) {
            val fy = y / CELL.toFloat() + margin
            val y0 = floor(fy).toInt().coerceIn(0, gh - 2)
            val ty = fy - y0
            for (x in 0 until w) {
                val fx = x / CELL.toFloat() + margin
                val x0 = floor(fx).toInt().coerceIn(0, gw - 2)
                val tx = fx - x0
                val i = y0 * gw + x0
                val top = field[i] + (field[i + 1] - field[i]) * tx
                val bot = field[i + gw] + (field[i + gw + 1] - field[i + gw]) * tx
                val v = top + (bot - top) * ty
                // Triangular dither before rounding.
                val a = (v * peakAlpha + rnd.nextFloat() - rnd.nextFloat()).roundToInt().coerceIn(0, 255)
                out[y * w + x] = (a shl 24) or color
            }
        }
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }

    /** Three box-blur passes per axis ≈ a Gaussian of [sigma]. */
    private fun gaussian(data: FloatArray, w: Int, h: Int, sigma: Float) {
        val r = ((sqrt(4f * sigma * sigma + 1f) - 1f) / 2f).roundToInt().coerceAtLeast(1)
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
}
