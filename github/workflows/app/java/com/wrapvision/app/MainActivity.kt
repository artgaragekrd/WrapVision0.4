
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
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var homeScreen: View
    private lateinit var cameraScreen: View
    private lateinit var cameraPreview: PreviewView

    private lateinit var colorSwatch: View
    private lateinit var colorCode: TextView

    private lateinit var bodyModeButton: Button
    private lateinit var partsModeButton: Button

    private var cameraProvider: ProcessCameraProvider? = null

    private var selectedColor = Color.rgb(48, 52, 58)
    private var selectedColorCode = "#30343A"
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

        colorSwatch = findViewById(R.id.colorSwatch)
        colorCode = findViewById(R.id.colorCode)

        bodyModeButton = findViewById(R.id.bodyModeButton)
        partsModeButton = findViewById(R.id.partsModeButton)

        val logo = findViewById<TextView>(R.id.logo)
        val logoEmblem = findViewById<TextView>(R.id.logoEmblem)

        applyMetallicEffect(logo)
        applyMetallicEffect(logoEmblem)

        homeScreen.visibility = View.VISIBLE
        cameraScreen.visibility = View.GONE

        updateSelectedColor()
        updateModeButtons()

        findViewById<Button>(R.id.startPreviewButton)
            .setOnClickListener {
                requestCamera()
            }

        findViewById<Button>(R.id.chooseColorButton)
            .setOnClickListener {
                colorPicker.launch("image/*")
            }

        findViewById<Button>(R.id.projectsButton)
            .setOnClickListener {
                Toast.makeText(
                    this,
                    "Раздел проектов будет добавлен следующим этапом",
                    Toast.LENGTH_SHORT
                ).show()
            }

        findViewById<Button>(R.id.backHomeButton)
            .setOnClickListener {
                returnHome()
            }

        bodyModeButton.setOnClickListener {
            selectedMode = "body"
            updateModeButtons()
        }

        partsModeButton.setOnClickListener {
            selectedMode = "parts"
            updateModeButtons()
        }
    }

    // МЕТАЛЛИЧЕСКИЙ ЭФФЕКТ ЛОГОТИПА

    private fun applyMetallicEffect(view: TextView) {

        view.post {

            val height = view.height.toFloat().coerceAtLeast(1f)

            val metallicColors = intArrayOf(
                Color.rgb(75, 91, 111),
                Color.rgb(215, 226, 240),
                Color.WHITE,
                Color.rgb(155, 174, 195),
                Color.rgb(245, 250, 255),
                Color.rgb(95, 117, 143),
                Color.rgb(190, 211, 232)
            )

            val positions = floatArrayOf(
                0.0f,
                0.18f,
                0.32f,
                0.48f,
                0.62f,
                0.82f,
                1.0f
            )

            view.paint.shader = LinearGradient(
                0f,
                0f,
                0f,
                height,
                metallicColors,
                positions,
                Shader.TileMode.CLAMP
            )

            view.setShadowLayer(
                5f,
                0f,
                1f,
                Color.rgb(100, 175, 235)
            )

            view.invalidate()
        }
    }

    // ВЫБРАННЫЙ ЦВЕТ

    private fun updateSelectedColor() {

        colorSwatch.backgroundTintList =
            ColorStateList.valueOf(selectedColor)

        colorCode.text = selectedColorCode
    }

    // РЕЖИМ НАНЕСЕНИЯ

    private fun updateModeButtons() {

        bodyModeButton.isSelected = selectedMode == "body"
        partsModeButton.isSelected = selectedMode == "parts"
    }

    // РАЗРЕШЕНИЕ КАМЕРЫ

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

    // ОТКРЫТИЕ КАМЕРЫ

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

                preview.surfaceProvider =
                    cameraPreview.surfaceProvider

                provider.unbindAll()

                provider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview
                )

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

        cameraPreview.visibility = View.GONE
        cameraScreen.visibility = View.GONE
        homeScreen.visibility = View.VISIBLE
    }

    // ЗАГРУЗКА ОБРАЗЦА ЦВЕТА

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

            updateSelectedColor()

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

    // СИСТЕМНАЯ КНОПКА НАЗАД

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

    // ЗАКРЫТИЕ ПРИЛОЖЕНИЯ

    override fun onDestroy() {
        cameraProvider?.unbindAll()
        super.onDestroy()
    }
}
