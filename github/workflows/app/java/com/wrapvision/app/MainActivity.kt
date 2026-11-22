
package com.wrapvision.app

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var homeScreen: View
    private lateinit var cameraScreen: FrameLayout
    private lateinit var cameraPreview: PreviewView

    private lateinit var colorSwatch: View
    private lateinit var colorCode: TextView

    private lateinit var bodyModeButton: Button
    private lateinit var partsModeButton: Button

    private var selectedColor: Int =
        Color.rgb(48, 52, 58)

    private var selectedMode = "body"

    private var cameraProvider: ProcessCameraProvider? = null
    private lateinit var cameraExecutor: ExecutorService

    private val imagePicker =
        registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri: Uri? ->

            if (uri != null) {
                readSampleColor(uri)
            }
        }

    private val cameraPermission =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->

            if (granted) {
                openCameraScreen()
            } else {
                Toast.makeText(
                    this,
                    "Для просмотра необходимо разрешение камеры",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        cameraExecutor = Executors.newSingleThreadExecutor()

        homeScreen = findViewById(R.id.homeScreen)
        cameraScreen = findViewById(R.id.cameraScreen)
        cameraPreview = findViewById(R.id.cameraPreview)

        colorSwatch = findViewById(R.id.colorSwatch)
        colorCode = findViewById(R.id.colorCode)

        bodyModeButton = findViewById(R.id.bodyModeButton)
        partsModeButton = findViewById(R.id.partsModeButton)

        val logo: TextView = findViewById(R.id.logo)

        val startButton: Button =
            findViewById(R.id.startPreviewButton)

        val chooseColorButton: Button =
            findViewById(R.id.chooseColorButton)

        val projectsButton: Button =
            findViewById(R.id.projectsButton)

        val backButton: Button =
            findViewById(R.id.backHomeButton)

        applyMetallicLogo(logo)

        updateSelectedColor()
        updateModeButtons()

        startButton.setOnClickListener {
            checkCameraPermission()
        }

        chooseColorButton.setOnClickListener {
            imagePicker.launch("image/*")
        }

        projectsButton.setOnClickListener {
            Toast.makeText(
                this,
                "Раздел проектов будет добавлен на следующем этапе",
                Toast.LENGTH_SHORT
            ).show()
        }

        bodyModeButton.setOnClickListener {
            selectedMode = "body"
            updateModeButtons()
        }

        partsModeButton.setOnClickListener {
            selectedMode = "parts"
            updateModeButtons()
        }

        backButton.setOnClickListener {
            showHomeScreen()
        }
    }

    // МЕТАЛЛИЧЕСКИЙ ЛОГОТИП

    private fun applyMetallicLogo(logo: TextView) {

        logo.post {

            val height = logo.textSize

            logo.paint.shader = LinearGradient(
                0f,
                0f,
                0f,
                height,
                intArrayOf(
                    Color.rgb(105, 120, 140),
                    Color.rgb(245, 249, 255),
                    Color.rgb(170, 190, 210),
                    Color.rgb(255, 255, 255),
                    Color.rgb(105, 125, 150)
                ),
                floatArrayOf(
                    0f,
                    0.25f,
                    0.50f,
                    0.72f,
                    1f
                ),
                Shader.TileMode.CLAMP
            )

            logo.setShadowLayer(
                9f,
                0f,
                0f,
                Color.rgb(75, 160, 230)
            )

            logo.invalidate()
        }
    }

    // ОБНОВЛЕНИЕ ВЫБРАННОГО ЦВЕТА

    private fun updateSelectedColor() {

        colorSwatch.backgroundTintList =
            ColorStateList.valueOf(selectedColor)

        colorCode.text = String.format(
            "#%06X",
            0xFFFFFF and selectedColor
        )
    }

    // РЕЖИМ НАНЕСЕНИЯ

    private fun updateModeButtons() {

        if (selectedMode == "body") {

            bodyModeButton.setBackgroundResource(
                R.drawable.bg_button_selected
            )

            partsModeButton.setBackgroundResource(
                R.drawable.bg_button_secondary
            )

        } else {

            bodyModeButton.setBackgroundResource(
                R.drawable.bg_button_secondary
            )

            partsModeButton.setBackgroundResource(
                R.drawable.bg_button_selected
            )
        }

        bodyModeButton.backgroundTintList = null
        partsModeButton.backgroundTintList = null
    }

    // ПРОВЕРКА РАЗРЕШЕНИЯ КАМЕРЫ

    private fun checkCameraPermission() {

        when {

            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {

                openCameraScreen()
            }

            else -> {
                cameraPermission.launch(
                    Manifest.permission.CAMERA
                )
            }
        }
    }

    // ОТКРЫТИЕ ЭКРАНА КАМЕРЫ

    private fun openCameraScreen() {

        homeScreen.visibility = View.GONE
        cameraScreen.visibility = View.VISIBLE

        startCamera()
    }

    // ВОЗВРАТ НА ГЛАВНЫЙ ЭКРАН

    private fun showHomeScreen() {

        cameraScreen.visibility = View.GONE
        homeScreen.visibility = View.VISIBLE

        cameraProvider?.unbindAll()
    }

    // ЗАПУСК КАМЕРЫ

    private fun startCamera() {

        val cameraProviderFuture =
            ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({

            try {

                val provider = cameraProviderFuture.get()

                cameraProvider = provider

                val preview = Preview.Builder().build()

                preview.setSurfaceProvider(
                    cameraPreview.surfaceProvider
                )

                val cameraSelector =
                    CameraSelector.DEFAULT_BACK_CAMERA

                provider.unbindAll()

                provider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview
                )

            } catch (exception: Exception) {

                Toast.makeText(
                    this,
                    "Не удалось запустить камеру: ${exception.message}",
                    Toast.LENGTH_LONG
                ).show()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    // ЗАГРУЗКА ОБРАЗЦА ЦВЕТА

    private fun readSampleColor(uri: Uri) {

        try {

            val inputStream =
                contentResolver.openInputStream(uri)

            val bitmap = inputStream?.use {
                BitmapFactory.decodeStream(it)
            }

            if (bitmap == null) {

                Toast.makeText(
                    this,
                    "Не удалось открыть изображение",
                    Toast.LENGTH_SHORT
                ).show()

                return
            }

            selectedColor =
                getAverageCenterColor(bitmap)

            updateSelectedColor()

            Toast.makeText(
                this,
                "Цвет образца выбран",
                Toast.LENGTH_SHORT
            ).show()

            bitmap.recycle()

        } catch (exception: Exception) {

            Toast.makeText(
                this,
                "Ошибка чтения изображения",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // ОПРЕДЕЛЕНИЕ СРЕДНЕГО ЦВЕТА ОБРАЗЦА

    private fun getAverageCenterColor(
        bitmap: Bitmap
    ): Int {

        val width = bitmap.width
        val height = bitmap.height

        val left = width / 3
        val right = (width * 2) / 3
        val top = height / 3
        val bottom = (height * 2) / 3

        var red = 0L
        var green = 0L
        var blue = 0L
        var count = 0L

        val stepX = maxOf(
            1,
            (right - left) / 50
        )

        val stepY = maxOf(
            1,
            (bottom - top) / 50
        )

        var y = top

        while (y < bottom) {

            var x = left

            while (x < right) {

                val pixel = bitmap.getPixel(x, y)

                red += Color.red(pixel)
                green += Color.green(pixel)
                blue += Color.blue(pixel)

                count++

                x += stepX
            }

            y += stepY
        }

        if (count == 0L) {
            return Color.DKGRAY
        }

        return Color.rgb(
            (red / count).toInt(),
            (green / count).toInt(),
            (blue / count).toInt()
        )
    }

    // КНОПКА НАЗАД ANDROID

    @Deprecated("Deprecated in Android API")
    override fun onBackPressed() {

        if (
            cameraScreen.visibility == View.VISIBLE
        ) {

            showHomeScreen()

        } else {

            super.onBackPressed()
        }
    }

    // ЗАКРЫТИЕ ПРИЛОЖЕНИЯ

    override fun onDestroy() {

        super.onDestroy()

        if (::cameraExecutor.isInitialized) {
            cameraExecutor.shutdown()
        }

        cameraProvider?.unbindAll()
    }
}
