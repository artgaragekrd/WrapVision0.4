
package com.wrapvision.app

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
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

        // ЕДИНОЕ МЕТАЛЛИЧЕСКОЕ ОФОРМЛЕНИЕ
        applyMetalButtonStyle(
            findViewById(R.id.startPreviewButton)
        )

        applyMetalButtonStyle(
            findViewById(R.id.chooseColorButton)
        )

        applyMetalButtonStyle(
            findViewById(R.id.projectsButton)
        )

        applyMetalButtonStyle(
            bodyModeButton
        )

        applyMetalButtonStyle(
            partsModeButton
        )

        applyMetalButtonStyle(
            findViewById(R.id.backHomeButton)
        )

        // ВРЕМЕННЫЙ ДИАГНОСТИЧЕСКИЙ ТЕСТ
        findViewById<Button>(R.id.startPreviewButton)
            .setBackgroundColor(Color.MAGENTA)

        homeScreen.visibility = View.VISIBLE
        cameraScreen.visibility = View.GONE

        colorSwatch.backgroundTintList =
            ColorStateList.valueOf(selectedColor)

        colorCode.text = selectedColorCode

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

        updateModeButtons()
    }

    // ПРИНУДИТЕЛЬНО НАЗНАЧАЕМ МЕТАЛЛИЧЕСКИЙ ФОН
    private fun applyMetalButtonStyle(button: Button) {

        button.backgroundTintList = null

        button.background =
            ContextCompat.getDrawable(
                this,
                R.drawable.bg_button_primary
            )

        button.backgroundTintList = null

        button.setTextColor(
            Color.rgb(32, 42, 52)
        )

        button.stateListAnimator = null
    }

    private fun updateModeButtons() {
        bodyModeButton.isSelected = selectedMode == "body"
        partsModeButton.isSelected = selectedMode == "parts"
    }

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

    private fun returnHome() {
        cameraProvider?.unbindAll()

        cameraPreview.visibility = View.GONE
        cameraScreen.visibility = View.GONE
        homeScreen.visibility = View.VISIBLE
    }

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

    override fun onDestroy() {
        cameraProvider?.unbindAll()
        super.onDestroy()
    }

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
