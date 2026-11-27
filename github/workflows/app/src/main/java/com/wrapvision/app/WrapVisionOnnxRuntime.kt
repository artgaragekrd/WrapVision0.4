package com.wrapvision.app

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import java.io.File

/**
 * Owns the ONNX Runtime session for WrapVision.
 *
 * The trained model is deliberately kept outside the APK because it is ~125 MB.
 * Place the exported file at filesDir/models/wrapvision_seg_nano_fp32.onnx.
 */
class WrapVisionOnnxRuntime(
    private val modelFile: File
) : AutoCloseable {

    data class ModelDescription(
        val inputName: String,
        val inputShape: LongArray,
        val outputShapes: Map<String, LongArray>
    )

    private val environment: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var session: OrtSession? = null

    @Synchronized
    fun open(): ModelDescription {
        check(modelFile.isFile) {
            "Файл модели не найден: ${modelFile.absolutePath}"
        }

        session?.close()
        val options = OrtSession.SessionOptions()
        try {
            // CPU execution is the safe baseline; acceleration can be added
            // after correctness and device compatibility are verified.
            session = environment.createSession(modelFile.absolutePath, options)
        } finally {
            options.close()
        }

        val active = checkNotNull(session)
        val inputEntry = active.inputInfo.entries.firstOrNull()
            ?: error("ONNX-модель не содержит входного тензора")

        val inputInfo = inputEntry.value.info as? TensorInfo
            ?: error("Вход модели не является Tensor")

        val outputShapes = active.outputInfo.mapValues { (_, nodeInfo) ->
            val tensorInfo = nodeInfo.info as? TensorInfo
                ?: error("Один из выходов модели не является Tensor")
            tensorInfo.shape
        }

        return ModelDescription(
            inputName = inputEntry.key,
            inputShape = inputInfo.shape,
            outputShapes = outputShapes
        )
    }

    @Synchronized
    fun run(input: OnnxTensor): OrtSession.Result {
        val active = checkNotNull(session) {
            "ONNX-сессия ещё не открыта"
        }
        val inputName = active.inputNames.firstOrNull()
            ?: error("У ONNX-модели отсутствует имя входа")
        return active.run(mapOf(inputName to input))
    }

    @Synchronized
    override fun close() {
        session?.close()
        session = null
    }
}
