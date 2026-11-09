package com.blackout.camera.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream
import kotlin.math.pow
import kotlin.random.Random

/**
 * Night tab looks.
 * - Night: the phone's own night mode (when the phone offers it to apps).
 * - White / Green phosphor: night vision. Brightens the dark a lot, averages frames to cut noise,
 *   then shows it like a white (P45) or green (P43) phosphor tube.
 * - Thermal style: false color. A phone camera can't see heat, so colors show brightness, not temperature.
 */
enum class Vision(val label: String) { Night("Night"), White("White"), Green("Green"), Thermal("Thermal") }

/** Turns camera frames into night vision. Runs on the camera's worker thread. */
class VisionProcessor {
    @Volatile var vision = Vision.White
    /** 0..1 from the Boost slider: higher lifts the shadows more. */
    @Volatile var boost = 0.6f

    private var acc: FloatArray? = null
    private var bufs = arrayOfNulls<Bitmap>(2)
    private var pixels: IntArray? = null
    private var flip = 0
    private var lo = 0f
    private var hi = 255f
    private var frame = 0
    private val noise = IntArray(4096) { Random.nextInt(-5, 6) }
    private val hist = IntArray(256)
    private val lut = IntArray(256)

    fun reset() { acc = null; frame = 0 }

    /** Returns a ready-to-show, upright bitmap of the frame, or null. */
    fun process(image: ImageProxy): Bitmap? {
        val plane = image.planes[0]
        val w = image.width; val h = image.height
        val row = plane.rowStride; val px = plane.pixelStride
        val buf = plane.buffer
        val n = w * h
        var a = acc
        val fresh = a == null || a.size != n
        if (fresh) { a = FloatArray(n); acc = a }
        a!!
        // Average with earlier frames: the random sensor noise cancels out, the picture stays.
        val keep = if (fresh) 0f else if (vision == Vision.Thermal) 0.7f else 0.55f
        java.util.Arrays.fill(hist, 0)
        var i = 0
        for (y in 0 until h) {
            var off = y * row
            for (x in 0 until w) {
                val v = (buf.get(off).toInt() and 0xFF).toFloat()
                val s = a[i] * keep + v * (1f - keep)
                a[i] = s
                hist[s.toInt()]++
                off += px; i++
            }
        }
        // Auto gain: stretch the darkest 1% to black and the brightest 0.5% to full.
        var c = 0; var newLo = 0
        while (newLo < 255 && c + hist[newLo] < n / 100) { c += hist[newLo]; newLo++ }
        c = 0; var newHi = 255
        while (newHi > 0 && c + hist[newHi] < n / 200) { c += hist[newHi]; newHi-- }
        if (newHi - newLo < 10) newHi = (newLo + 10).coerceAtMost(255)
        if (fresh) { lo = newLo.toFloat(); hi = newHi.toFloat() } else { lo += (newLo - lo) * 0.15f; hi += (newHi - hi) * 0.15f }
        buildLut(lo, hi, vision, boost, lut)

        val rot = image.imageInfo.rotationDegrees
        val ow = if (rot == 90 || rot == 270) h else w
        val oh = if (rot == 90 || rot == 270) w else h
        var out = pixels
        if (out == null || out.size != n) { out = IntArray(n); pixels = out }
        val grain = vision == Vision.White || vision == Vision.Green
        frame++
        val shift = (frame * 977) and 4095
        i = 0
        for (y in 0 until h) {
            for (x in 0 until w) {
                var v = a[i].toInt()
                if (grain) v = (v + noise[(i + shift) and 4095]).coerceIn(0, 255)
                val o = when (rot) {
                    90 -> x * ow + (h - 1 - y)
                    180 -> (h - 1 - y) * ow + (w - 1 - x)
                    270 -> (w - 1 - x) * ow + y
                    else -> y * ow + x
                }
                out[o] = lut[v]
                i++
            }
        }
        flip = 1 - flip
        var bmp = bufs[flip]
        if (bmp == null || bmp.width != ow || bmp.height != oh) { bmp = Bitmap.createBitmap(ow, oh, Bitmap.Config.ARGB_8888); bufs[flip] = bmp }
        bmp!!.setPixels(out, 0, ow, 0, 0, ow, oh)
        return bmp
    }

    companion object {
        /** Applies the same look to a full-size photo. Returns JPEG bytes, still sideways like the camera's. */
        fun photo(jpeg: ByteArray, vision: Vision, boost: Float, quality: Int): ByteArray {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 4096) sample *= 2
            val src = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, BitmapFactory.Options().apply { inSampleSize = sample; inMutable = true })
                ?: return jpeg
            val w = src.width; val h = src.height
            val line = IntArray(w)
            val hist = IntArray(256)
            for (y in 0 until h) {
                src.getPixels(line, 0, w, 0, y, w, 1)
                for (p in line) hist[luma(p)]++
            }
            val n = w * h
            var c = 0; var lo = 0
            while (lo < 255 && c + hist[lo] < n / 100) { c += hist[lo]; lo++ }
            c = 0; var hi = 255
            while (hi > 0 && c + hist[hi] < n / 200) { c += hist[hi]; hi-- }
            if (hi - lo < 10) hi = (lo + 10).coerceAtMost(255)
            val lut = IntArray(256)
            buildLut(lo.toFloat(), hi.toFloat(), vision, boost, lut)
            val grain = vision == Vision.White || vision == Vision.Green
            val rnd = Random(System.nanoTime())
            for (y in 0 until h) {
                src.getPixels(line, 0, w, 0, y, w, 1)
                for (x in 0 until w) {
                    var v = luma(line[x])
                    if (grain) v = (v + rnd.nextInt(-4, 5)).coerceIn(0, 255)
                    line[x] = lut[v]
                }
                src.setPixels(line, 0, w, 0, y, w, 1)
            }
            val out = ByteArrayOutputStream()
            src.compress(Bitmap.CompressFormat.JPEG, quality, out)
            src.recycle()
            return out.toByteArray()
        }

        private fun luma(p: Int): Int = ((p shr 16 and 0xFF) * 77 + (p shr 8 and 0xFF) * 150 + (p and 0xFF) * 29) shr 8

        // Ironbow: black, deep purple, magenta, orange, yellow, white.
        private val IRON = arrayOf(
            0f to intArrayOf(0, 0, 0), 0.15f to intArrayOf(30, 0, 110), 0.35f to intArrayOf(150, 0, 155),
            0.55f to intArrayOf(230, 60, 40), 0.75f to intArrayOf(255, 160, 0), 0.9f to intArrayOf(255, 230, 60),
            1f to intArrayOf(255, 255, 235),
        )

        fun buildLut(lo: Float, hi: Float, vision: Vision, boost: Float, lut: IntArray) {
            // More boost = lower gamma = brighter shadows.
            val gamma = if (vision == Vision.Thermal) 1.1f - boost * 0.5f else 1.0f - boost * 0.6f
            val span = (hi - lo).coerceAtLeast(1f)
            for (x in 0..255) {
                val t = ((x - lo) / span).coerceIn(0f, 1f).pow(gamma)
                lut[x] = when (vision) {
                    Vision.Green -> rgb(t * t * 70, t * 255, t * t * 90 + t * 10)
                    Vision.Thermal -> iron(t)
                    else -> rgb(t * 228 + 4, t * 236 + 4, t * 246 + 9) // white phosphor: cool blue-white
                }
            }
        }

        private fun iron(t: Float): Int {
            for (k in 1 until IRON.size) {
                val (p1, c1) = IRON[k]
                if (t <= p1) {
                    val (p0, c0) = IRON[k - 1]
                    val f = (t - p0) / (p1 - p0)
                    return rgb(c0[0] + (c1[0] - c0[0]) * f, c0[1] + (c1[1] - c0[1]) * f, c0[2] + (c1[2] - c0[2]) * f)
                }
            }
            return rgb(255f, 255f, 235f)
        }

        private fun rgb(r: Float, g: Float, b: Float): Int =
            (0xFF shl 24) or (r.toInt().coerceIn(0, 255) shl 16) or (g.toInt().coerceIn(0, 255) shl 8) or b.toInt().coerceIn(0, 255)
    }
}
