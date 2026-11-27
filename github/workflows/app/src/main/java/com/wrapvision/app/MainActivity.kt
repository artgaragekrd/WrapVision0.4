
package com.wrapvision.app

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.util.Size
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var homeScreen: View
    private lateinit var cameraScreen: View
    private lateinit var cameraPreview: PreviewView
    private lateinit var maskOverlay: CarMaskOverlayView

    private lateinit var colorSwatch: View
    private lateinit var colorCode: TextView

    private lateinit var bodyModeButton: Button
    private lateinit var partsModeButton: Button

    private var cameraProvider: ProcessCameraProvider? = null
    private var segmentationEngine: WrapVisionSegmentationEngine? = null
    private var analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private var selectedColor: Int = Color.rgb(40, 45, 55)
    private var selectedColorCode = "ЦВЕТ НЕ ВЫБРАН"
    private var selectedMode = "body"

    private val cameraPermission =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                openCamera()
            } else {
                Toast.makeText(
                    this,
                    "Необходимо разрешить доступ к камере",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    private val modelPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) installModel(uri)
    }

    private val colorPicker =
        registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri ->
            if (uri != null) {
                readSampleColor(uri)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.rgb(7, 10, 15)
        window.navigationBarColor = Color.rgb(7, 10, 15)

        setContentView(R.layout.activity_main)

        homeScreen = findViewById(R.id.homeScreen)
        cameraScreen = findViewById(R.id.cameraScreen)
        cameraPreview = findViewById(R.id.cameraPreview)
        maskOverlay = findViewById(R.id.carMaskOverlay)

        colorSwatch = findViewById(R.id.colorSwatch)
        colorCode = findViewById(R.id.colorCode)

        bodyModeButton = findViewById(R.id.bodyModeButton)
        partsModeButton = findViewById(R.id.partsModeButton)

        // ЕДИНЫЙ СТИЛЬ DARK NEON STUDIO

        val startPreviewButton =
            findViewById<Button>(R.id.startPreviewButton)

        val chooseColorButton =
            findViewById<Button>(R.id.chooseColorButton)

        val projectsButton =
            findViewById<Button>(R.id.projectsButton)

        val backHomeButton =
            findViewById<Button>(R.id.backHomeButton)
        val loadModelButton = findViewById<Button>(R.id.loadModelButton)

        applyNeonButtonStyle(startPreviewButton)
        applyNeonButtonStyle(chooseColorButton)
        applyNeonButtonStyle(projectsButton)
        applyNeonButtonStyle(bodyModeButton)
        applyNeonButtonStyle(partsModeButton)
        applyNeonButtonStyle(backHomeButton)
        applyNeonButtonStyle(loadModelButton)

        // УБИРАЕМ ИКОНКИ С ТРЁХ ОСНОВНЫХ КНОПОК

        removeButtonIcons(startPreviewButton)
        removeButtonIcons(chooseColorButton)
        removeButtonIcons(projectsButton)

        homeScreen.visibility = View.VISIBLE
        cameraScreen.visibility = View.GONE

        colorSwatch.backgroundTintList =
            ColorStateList.valueOf(selectedColor)

        colorCode.text = selectedColorCode

        // НАЧАТЬ ПРОСМОТР

        startPreviewButton.setOnClickListener {
            requestCamera()
        }

        // ВЫБРАТЬ ЦВЕТ

        chooseColorButton.setOnClickListener {
            colorPicker.launch("image/*")
        }

        // МОИ ПРОЕКТЫ

        projectsButton.setOnClickListener {
            Toast.makeText(
                this,
                "Раздел проектов будет добавлен следующим этапом",
                Toast.LENGTH_SHORT
            ).show()
        }

        // ВОЗВРАТ НА ГЛАВНЫЙ ЭКРАН

        backHomeButton.setOnClickListener {
            returnHome()
        }
        loadModelButton.setOnClickListener {
            modelPicker.launch(arrayOf("application/octet-stream", "application/onnx", "*/*"))
        }

        // РЕЖИМ ВЕСЬ КУЗОВ

        bodyModeButton.setOnClickListener {
            selectedMode = "body"
            updateModeButtons()
        }

        // РЕЖИМ ПО ДЕТАЛЯМ

        partsModeButton.setOnClickListener {
            selectedMode = "parts"
            updateModeButtons()
        }

        updateModeButtons()

        // Автоматически открываем уже установленную модель при следующем запуске.
        analysisExecutor.execute {
            val installedModel = File(filesDir, "models/wrapvision_seg_nano_fp32.onnx")
            if (installedModel.isFile && segmentationEngine == null) {
                try {
                    segmentationEngine = WrapVisionSegmentationEngine(installedModel)
                } catch (_: Exception) {
                    runOnUiThread {
                        Toast.makeText(this, "Не удалось открыть сохранённую модель. Загрузите её заново.", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    // ЕДИНЫЙ СТИЛЬ DARK NEON STUDIO

    private fun applyNeonButtonStyle(button: Button) {

        button.backgroundTintList = null

        button.background =
            ContextCompat.getDrawable(
                this,
                R.drawable.bg_button_neon
            )

        button.backgroundTintList = null

        // БЕЛЫЕ НАДПИСИ

        button.setTextColor(
            Color.rgb(235, 246, 255)
        )

        // СПОРТИВНЫЙ ШРИФТ

        button.typeface = Typeface.create(
            "sans-serif-condensed",
            Typeface.BOLD
        )

        button.letterSpacing = 0.07f
        button.isAllCaps = false

        // ГОЛУБЫЕ ИКОНКИ

        button.compoundDrawableTintList =
            ColorStateList.valueOf(
                Color.rgb(0, 185, 255)
            )

        // МЯГКИЙ ОБЪЁМ

        button.elevation =
            5f * resources.displayMetrics.density

        button.stateListAnimator = null
    }

    // УДАЛЕНИЕ ИКОНОК С КНОПКИ

    private fun removeButtonIcons(button: Button) {
        button.setCompoundDrawables(
            null,
            null,
            null,
            null
        )

        button.setCompoundDrawablesRelative(
            null,
            null,
            null,
            null
        )
    }

    // ПЕРЕКЛЮЧЕНИЕ РЕЖИМОВ

    private fun updateModeButtons() {
        bodyModeButton.isSelected = selectedMode == "body"
        partsModeButton.isSelected = selectedMode == "parts"
    }

    // ПРОВЕРКА РАЗРЕШЕНИЯ КАМЕРЫ

    private fun requestCamera() {
        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            openCamera()
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    // ЗАПУСК КАМЕРЫ

    private fun openCamera() {
        homeScreen.visibility = View.GONE
        cameraScreen.visibility = View.VISIBLE
        cameraPreview.visibility = View.VISIBLE

        val future = ProcessCameraProvider.getInstance(this)

        future.addListener({
            try {
                val provider = future.get()
                cameraProvider = provider

                val preview = Preview.Builder().build()
                preview.surfaceProvider = cameraPreview.surfaceProvider
                provider.unbindAll()
                val engine = segmentationEngine
                if (engine != null) {
                    val analysis = ImageAnalysis.Builder()
                        .setTargetResolution(Size(640, 480))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    analysis.setAnalyzer(analysisExecutor, CameraMaskAnalyzer(
                        engine = engine,
                        executor = analysisExecutor,
                        isWholeBodyMode = { selectedMode == "body" },
                        selectedWrapColor = { selectedColor },
                        onMaskReady = { bitmap ->
                            runOnUiThread {
                                if (cameraScreen.visibility == View.VISIBLE) maskOverlay.setOverlay(bitmap)
                                else bitmap?.recycle()
                            }
                        }
                    ))
                    provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                } else {
                    provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview)
                    Toast.makeText(this, "Камера работает. Для нанесения цвета загрузите ONNX-модель.", Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                Toast.makeText(
                    this,
                    "Не удалось запустить камеру",
                    Toast.LENGTH_LONG
                ).show()

                returnHome()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    // ВОЗВРАТ НА ГЛАВНЫЙ ЭКРАН

    private fun returnHome() {
        cameraProvider?.unbindAll()
        maskOverlay.clear()

        cameraPreview.visibility = View.GONE
        cameraScreen.visibility = View.GONE
        homeScreen.visibility = View.VISIBLE
    }

    // ОБРАБОТКА ФОТОГРАФИИ И ВЫБОР ЦВЕТА

    private fun readSampleColor(uri: Uri) {
        var bitmap: Bitmap? = null

        try {
            val bounds = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }

            contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }

            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                throw IllegalArgumentException("Invalid image")
            }

            var sample = 1

            while (
                bounds.outWidth / sample > 1024 ||
                bounds.outHeight / sample > 1024
            ) {
                sample *= 2
            }

            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
            }

            bitmap = contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }

            val image = bitmap
                ?: throw IllegalArgumentException("No bitmap")

            val left = image.width / 3
            val right = image.width * 2 / 3
            val top = image.height / 3
            val bottom = image.height * 2 / 3

            var red = 0L
            var green = 0L
            var blue = 0L
            var count = 0L

            val step = maxOf(
                1,
                minOf(image.width, image.height) / 100
            )

            for (y in top until bottom step step) {
                for (x in left until right step step) {
                    val pixel = image.getPixel(x, y)

                    red += Color.red(pixel)
                    green += Color.green(pixel)
                    blue += Color.blue(pixel)

                    count++
                }
            }

            if (count == 0L) {
                throw IllegalArgumentException("Empty sample")
            }

            selectedColor = Color.rgb(
                (red / count).toInt(),
                (green / count).toInt(),
                (blue / count).toInt()
            )

            selectedColorCode = String.format(
                "#%06X",
                0xFFFFFF and selectedColor
            )

            colorSwatch.backgroundTintList =
                ColorStateList.valueOf(selectedColor)

            colorCode.text = selectedColorCode

            Toast.makeText(
                this,
                "Выбран цвет: $selectedColorCode",
                Toast.LENGTH_SHORT
            ).show()

        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Не удалось обработать изображение",
                Toast.LENGTH_SHORT
            ).show()

        } finally {
            bitmap?.recycle()
        }
    }

    // КОПИРОВАНИЕ МОДЕЛИ ИЗ ВЫБРАННОГО ФАЙЛА

    private fun installModel(uri: Uri) {
        Toast.makeText(this, "Копирую файл модели…", Toast.LENGTH_SHORT).show()
        analysisExecutor.execute {
            try {
                val dir = File(filesDir, "models").apply { mkdirs() }
                val target = File(dir, "wrapvision_seg_nano_fp32.onnx")
                val temp = File(dir, "model.tmp")
                contentResolver.openInputStream(uri)?.use { input ->
                    temp.outputStream().use { output -> input.copyTo(output) }
                } ?: throw IllegalStateException("Не удалось открыть выбранный файл")
                if (target.exists()) target.delete()
                if (!temp.renameTo(target)) throw IllegalStateException("Не удалось сохранить модель")
                val loaded = WrapVisionSegmentationEngine(target)
                val previous = segmentationEngine
                segmentationEngine = loaded
                previous?.close()
                runOnUiThread {
                    Toast.makeText(this, "Модель загружена. Перезапустите просмотр камеры.", Toast.LENGTH_LONG).show()
                    if (cameraScreen.visibility == View.VISIBLE) {
                        cameraProvider?.unbindAll()
                        openCamera()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Ошибка загрузки модели: " + e.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // ОСВОБОЖДЕНИЕ КАМЕРЫ

    override fun onDestroy() {
        cameraProvider?.unbindAll()
        maskOverlay.clear()
        analysisExecutor.shutdown()
        segmentationEngine?.close()
        super.onDestroy()
    }

    // ОБРАБОТКА СИСТЕМНОЙ КНОПКИ НАЗАД

    @Deprecated("Deprecated in Android")
    override fun onBackPressed() {
        if (
            ::cameraScreen.isInitialized &&
            cameraScreen.visibility == View.VISIBLE
        ) {
            returnHome()
        } else {
            super.onBackPressed()
        }
    }
}
