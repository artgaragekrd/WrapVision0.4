
package com.wrapvision.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
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
    private lateinit var statusText: TextView
    private lateinit var homeColorInfo: TextView

    private var cameraProvider: ProcessCameraProvider? = null

    private var selectedMode = "Весь кузов"

    private val cameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startCamera()
        } else {
            statusText.text = "Необходимо разрешить доступ к камере"
        }
    }

    private val colorPicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            readSampleColor(uri)
        } else {
            statusText.text = "Выбор фотографии отменён"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        homeScreen = findViewById(R.id.homeScreen)
        cameraScreen = findViewById(R.id.cameraScreen)
        cameraPreview = findViewById(R.id.cameraPreview)
        statusText = findViewById(R.id.statusText)
        homeColorInfo = findViewById(R.id.homeColorInfo)

        findViewById<Button>(R.id.startCameraButton)
            .setOnClickListener {
                openCameraScreen()
            }

        findViewById<Button>(R.id.homeSelectColorButton)
            .setOnClickListener {
                colorPicker.launch("image/*")
            }

        findViewById<Button>(R.id.myProjectsButton)
            .setOnClickListener {
                Toast.makeText(
                    this,
                    "Раздел проектов готовится",
                    Toast.LENGTH_SHORT
                ).show()
            }

        findViewById<Button>(R.id.bodyModeButton)
            .setOnClickListener {
                selectedMode = "Весь кузов"
                statusText.text = "Режим: $selectedMode"
            }

        findViewById<Button>(R.id.partsModeButton)
            .setOnClickListener {
                selectedMode = "Отдельные детали"
                statusText.text = "Режим: $selectedMode"
            }

        findViewById<Button>(R.id.selectColorButton)
            .setOnClickListener {
                colorPicker.launch("image/*")
            }

        findViewById<Button>(R.id.backToHomeButton)
            .setOnClickListener {
                showHomeScreen()
            }

        showHomeScreen()
    }

    private fun showHomeScreen() {
        homeScreen.visibility = View.VISIBLE
        cameraScreen.visibility = View.GONE

        cameraProvider?.unbindAll()
    }

    private fun openCameraScreen() {
        homeScreen.visibility = View.GONE
        cameraScreen.visibility = View.VISIBLE

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun readSampleColor(uri: android.net.Uri) {
        try {
            val bounds = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }

            contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }

            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                statusText.text = "Не удалось прочитать изображение"
                return
            }

            val options = BitmapFactory.Options().apply {
                inSampleSize = calculateSampleSize(
                    bounds.outWidth,
                    bounds.outHeight
                )
            }

            val bitmap = contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }

            if (bitmap == null) {
                statusText.text = "Не удалось прочитать фотографию"
                return
            }

            val pixel = Bitmap.createScaledBitmap(
                bitmap, 1, 1, true
            )

            val color = pixel.getPixel(0, 0)

            val colorCode = String.format(
                "#%06X",
                0xFFFFFF and color
            )

            homeColorInfo.text = "Выбран цвет плёнки: $colorCode"
            statusText.text = "Цвет образца: $colorCode"

            homeColorInfo.setTextColor(Color.WHITE)

            if (pixel !== bitmap) {
                pixel.recycle()
            }

            bitmap.recycle()

        } catch (e: Exception) {
            statusText.text = "Ошибка чтения фотографии"
        }
    }

    private fun calculateSampleSize(
        width: Int,
        height: Int
    ): Int {
        var sampleSize = 1

        while (width / sampleSize > 1024 ||
            height / sampleSize > 1024
        ) {
            sampleSize *= 2
        }

        return sampleSize
    }

    private fun startCamera() {
        val cameraProviderFuture =
            ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({

            try {
                val provider = cameraProviderFuture.get()
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
                statusText.text = "Не удалось запустить камеру"
            }

        }, ContextCompat.getMainExecutor(this))
    }

    @Deprecated("Deprecated in Android")
    override fun onBackPressed() {
        if (cameraScreen.visibility == View.VISIBLE) {
            showHomeScreen()
        } else {
            super.onBackPressed()
        }
    }
}
