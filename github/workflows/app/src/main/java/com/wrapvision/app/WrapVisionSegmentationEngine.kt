package com.wrapvision.app

import android.graphics.Bitmap
import android.graphics.Color
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * Bitmap-to-mask inference for the exported WrapVision RF-DETR Seg-Nano model.
 *
 * Expected export: input [1,3,312,312], dets [1,100,4],
 * labels [1,100,24] (23 foreground classes + final no-object slot),
 * masks [1,100,78,78].
 */
class WrapVisionSegmentationEngine(
    modelFile: File
) : AutoCloseable {
    companion object {
        private const val INPUT_SIZE = 312
        private const val QUERY_COUNT = 100
        private const val CLASS_COUNT = 23
        private const val MASK_SIZE = 78
        private const val SCORE_THRESHOLD = 0.45f
        private val MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
        private val STD = floatArrayOf(0.229f, 0.224f, 0.225f)
    }

    data class DetectionMask(
        val classId: Int,
        val className: String,
        val confidence: Float,
        val mask: ByteArray,
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int
    )

    private val env = OrtEnvironment.getEnvironment()
    private val session: OrtSession

    init {
        require(modelFile.isFile) { "Файл модели не найден: ${modelFile.absolutePath}" }
        val options = OrtSession.SessionOptions()
        session = try {
            env.createSession(modelFile.absolutePath, options)
        } finally {
            options.close()
        }
        val input = session.inputInfo.values.firstOrNull()?.info
            ?: error("У модели отсутствует вход")
        val shape = (input as? ai.onnxruntime.TensorInfo)?.shape
            ?: error("Вход модели не является тензором")
        require(shape.contentEquals(longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong()))) {
            "Неожиданный размер входа модели: ${shape.contentToString()}"
        }
        require(session.outputNames.containsAll(listOf("dets", "labels", "masks"))) {
            "В ONNX отсутствуют ожидаемые выходы dets/labels/masks: ${session.outputNames}"
        }
    }

    @Synchronized
    fun infer(source: Bitmap, wholeBodyMode: Boolean, wrapColor: Int): List<DetectionMask> {
        val resized = Bitmap.createScaledBitmap(source, INPUT_SIZE, INPUT_SIZE, true)
        val inputValues = FloatArray(3 * INPUT_SIZE * INPUT_SIZE)
        var p = 0
        for (channel in 0..2) {
            for (y in 0 until INPUT_SIZE) {
                for (x in 0 until INPUT_SIZE) {
                    val pixel = resized.getPixel(x, y)
                    val value = when (channel) {
                        0 -> Color.red(pixel) / 255f
                        1 -> Color.green(pixel) / 255f
                        else -> Color.blue(pixel) / 255f
                    }
                    inputValues[p++] = (value - MEAN[channel]) / STD[channel]
                }
            }
        }
        if (resized !== source) resized.recycle()

        val inputBuffer = FloatBuffer.wrap(inputValues)
        val inputTensor = OnnxTensor.createTensor(
            env, inputBuffer, longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong())
        )
        try {
            session.run(mapOf(session.inputNames.first() to inputTensor)).use { result ->
                val boxes = tensorFloats(result.get("dets").get() as OnnxTensor)
                val logits = tensorFloats(result.get("labels").get() as OnnxTensor)
                val maskLogits = tensorFloats(result.get("masks").get() as OnnxTensor)

                require(boxes.size == QUERY_COUNT * 4) { "Неверная форма dets: ${boxes.size}" }
                require(logits.size == QUERY_COUNT * (CLASS_COUNT + 1)) {
                    "Неверная форма labels: ${logits.size}"
                }
                require(maskLogits.size == QUERY_COUNT * MASK_SIZE * MASK_SIZE) {
                    "Неверная форма masks: ${maskLogits.size}"
                }

                val detections = ArrayList<DetectionMask>()
                for (query in 0 until QUERY_COUNT) {
                    var bestClass = -1
                    var bestScore = 0f
                    val classOffset = query * (CLASS_COUNT + 1)
                    for (classId in 0 until CLASS_COUNT) {
                        val raw = logits[classOffset + classId].coerceIn(-16f, 16f)
                        val score = (1.0 / (1.0 + exp(-raw.toDouble()))).toFloat()
                        if (score > bestScore) {
                            bestScore = score
                            bestClass = classId
                        }
                    }
                    if (bestClass < 0 || bestScore < SCORE_THRESHOLD) continue
                    if (!WrapVisionClassMap.shouldApplyWrap(bestClass, wholeBodyMode)) continue

                    // RF-DETR boxes are normalized cx,cy,w,h. Use them as a
                    // conservative spatial guard so a mask cannot paint far outside
                    // the detected vehicle part.
                    val boxOffset = query * 4
                    val cx = boxes[boxOffset].coerceIn(0f, 1f)
                    val cy = boxes[boxOffset + 1].coerceIn(0f, 1f)
                    val bw = boxes[boxOffset + 2].coerceIn(0f, 1f)
                    val bh = boxes[boxOffset + 3].coerceIn(0f, 1f)
                    if (bw <= 0f || bh <= 0f) continue
                    val padX = bw * 0.08f
                    val padY = bh * 0.08f
                    val left = (((cx - bw / 2f - padX).coerceAtLeast(0f)) * source.width).toInt()
                    val top = (((cy - bh / 2f - padY).coerceAtLeast(0f)) * source.height).toInt()
                    val right = (((cx + bw / 2f + padX).coerceAtMost(1f)) * source.width).toInt()
                    val bottom = (((cy + bh / 2f + padY).coerceAtMost(1f)) * source.height).toInt()
                    if (right <= left || bottom <= top) continue

                    val offset = query * MASK_SIZE * MASK_SIZE
                    val smallMask = maskLogits.copyOfRange(offset, offset + MASK_SIZE * MASK_SIZE)
                    val fullMask = resizeMaskBilinear(smallMask, source.width, source.height)
                    // Clamp the low-resolution mask to its corresponding detection box.
                    for (y in 0 until source.height) {
                        if (y < top || y >= bottom) {
                            java.util.Arrays.fill(fullMask, y * source.width, (y + 1) * source.width, 0)
                        } else {
                            val row = y * source.width
                            for (x in 0 until left) fullMask[row + x] = 0
                            for (x in right until source.width) fullMask[row + x] = 0
                        }
                    }
                    if (fullMask.any { it.toInt() != 0 }) {
                        detections.add(
                            DetectionMask(
                                bestClass,
                                WrapVisionClassMap.classNames[bestClass],
                                bestScore,
                                fullMask,
                                left, top, right, bottom
                            )
                        )
                    }
                }
                return detections.sortedByDescending { it.confidence }
            }
        } finally {
            inputTensor.close()
        }
    }

    /** Creates a transparent ARGB overlay from the selected instance masks. */
    fun renderOverlay(
        source: Bitmap,
        detections: List<DetectionMask>,
        wrapColor: Int,
        alpha: Int = 245
    ): Bitmap {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        val baseR = Color.red(wrapColor)
        val baseG = Color.green(wrapColor)
        val baseB = Color.blue(wrapColor)
        val a = alpha.coerceIn(0, 255)
        for (detection in detections) {
            val mask = detection.mask
            if (mask.size != width * height) continue
            for (i in mask.indices) {
                if (mask[i].toInt() == 0) continue
                val original = source.getPixel(i % width, i / width)
                // Transfer the selected wrap hue while retaining the source panel's
                // light/shadow structure instead of laying a flat translucent tint.
                val luminance = (0.2126f * Color.red(original) +
                    0.7152f * Color.green(original) +
                    0.0722f * Color.blue(original)) / 255f
                val shade = (0.38f + luminance * 0.92f).coerceIn(0.38f, 1.28f)
                pixels[i] = Color.argb(
                    a,
                    (baseR * shade).toInt().coerceIn(0, 255),
                    (baseG * shade).toInt().coerceIn(0, 255),
                    (baseB * shade).toInt().coerceIn(0, 255)
                )
            }
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun tensorFloats(tensor: OnnxTensor): FloatArray {
        val buffer = tensor.floatBuffer
        val copy = buffer.duplicate()
        copy.rewind()
        val values = FloatArray(copy.remaining())
        copy.get(values)
        return values
    }

    private fun resizeMaskBilinear(input: FloatArray, outWidth: Int, outHeight: Int): ByteArray {
        val output = ByteArray(outWidth * outHeight)
        val scaleX = MASK_SIZE.toFloat() / outWidth
        val scaleY = MASK_SIZE.toFloat() / outHeight
        for (y in 0 until outHeight) {
            val fy = ((y + 0.5f) * scaleY - 0.5f).coerceIn(0f, (MASK_SIZE - 1).toFloat())
            val y0 = fy.toInt()
            val y1 = min(y0 + 1, MASK_SIZE - 1)
            val wy = fy - y0
            for (x in 0 until outWidth) {
                val fx = ((x + 0.5f) * scaleX - 0.5f).coerceIn(0f, (MASK_SIZE - 1).toFloat())
                val x0 = fx.toInt()
                val x1 = min(x0 + 1, MASK_SIZE - 1)
                val wx = fx - x0
                val v00 = input[y0 * MASK_SIZE + x0]
                val v01 = input[y0 * MASK_SIZE + x1]
                val v10 = input[y1 * MASK_SIZE + x0]
                val v11 = input[y1 * MASK_SIZE + x1]
                val top = v00 * (1f - wx) + v01 * wx
                val bottom = v10 * (1f - wx) + v11 * wx
                val value = top * (1f - wy) + bottom * wy
                if (value > 0f) output[y * outWidth + x] = 1
            }
        }
        return output
    }

    override fun close() {
        session.close()
    }
}
