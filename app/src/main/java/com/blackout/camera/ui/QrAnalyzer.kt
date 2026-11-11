package com.blackout.camera.ui

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer

/** Reads QR codes from the camera picture, on the phone only. */
class QrAnalyzer(private val onFound: (String) -> Unit) : ImageAnalysis.Analyzer {
    private val reader = MultiFormatReader().apply {
        setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE), DecodeHintType.TRY_HARDER to true))
    }
    private var last = 0L

    override fun analyze(image: ImageProxy) {
        try {
            val now = System.currentTimeMillis()
            if (now - last < 250) return
            last = now
            val plane = image.planes[0]
            val buf = plane.buffer
            val data = ByteArray(buf.remaining()).also { buf.get(it) }
            // Own offline reader first (rowStride-aware copy), ZXing only as backup.
            val w = image.width
            val h = image.height
            val stride = plane.rowStride
            val lum = if (stride == w) data else ByteArray(w * h).also { out ->
                for (y in 0 until h) System.arraycopy(data, y * stride, out, y * w, w)
            }
            val own = runCatching { QrDecoder.decode(lum, w, h) }.getOrNull()
            if (own != null) { onFound(own); return }
            val source = PlanarYUVLuminanceSource(data, plane.rowStride, image.height, 0, 0, image.width, image.height, false)
            val result = runCatching { reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))) }.getOrNull()
            if (result != null) onFound(result.text)
        } finally {
            reader.reset()
            image.close()
        }
    }
}
