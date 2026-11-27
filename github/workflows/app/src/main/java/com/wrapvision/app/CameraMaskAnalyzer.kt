package com.wrapvision.app

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService

/**
 * Converts CameraX YUV frames to upright RGB bitmaps and runs segmentation.
 * Analysis is single-threaded and uses KEEP_ONLY_LATEST to avoid frame backlog.
 */
class CameraMaskAnalyzer(
    private val engine: WrapVisionSegmentationEngine,
    private val executor: ExecutorService,
    private val isWholeBodyMode: () -> Boolean,
    private val selectedWrapColor: () -> Int,
    private val onMaskReady: (Bitmap?) -> Unit
) : ImageAnalysis.Analyzer {

    override fun analyze(image: ImageProxy) {
        try {
            val bitmap = imageProxyToBitmap(image) ?: return
            val rotation = image.imageInfo.rotationDegrees
            val upright = if (rotation == 0) bitmap else {
                val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                    .also { bitmap.recycle() }
            }
            try {
                val masks = engine.infer(
                    upright,
                    isWholeBodyMode(),
                    selectedWrapColor()
                )
                val overlay = engine.renderOverlay(
                    upright.width, upright.height, masks, selectedWrapColor()
                )
                onMaskReady(overlay)
            } finally {
                upright.recycle()
            }
        } catch (_: Exception) {
            onMaskReady(null)
        } finally {
            image.close()
        }
    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
        val y = image.planes[0]
        val u = image.planes[1]
        val v = image.planes[2]
        val width = image.width
        val height = image.height
        val nv21 = ByteArray(width * height * 3 / 2)
        val yBuffer = y.buffer.duplicate()
        val uBuffer = u.buffer.duplicate()
        val vBuffer = v.buffer.duplicate()

        var out = 0
        for (row in 0 until height) {
            val rowStart = row * y.rowStride
            for (col in 0 until width) {
                nv21[out++] = yBuffer.get(rowStart + col * y.pixelStride)
            }
        }
        val chromaHeight = height / 2
        val chromaWidth = width / 2
        for (row in 0 until chromaHeight) {
            val uRow = row * u.rowStride
            val vRow = row * v.rowStride
            for (col in 0 until chromaWidth) {
                nv21[out++] = vBuffer.get(vRow + col * v.pixelStride)
                nv21[out++] = uBuffer.get(uRow + col * u.pixelStride)
            }
        }

        val yuv = YuvImage(nv21, android.graphics.ImageFormat.NV21, width, height, null)
        val jpeg = ByteArrayOutputStream()
        if (!yuv.compressToJpeg(Rect(0, 0, width, height), 88, jpeg)) return null
        val bytes = jpeg.toByteArray()
        return android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
}
