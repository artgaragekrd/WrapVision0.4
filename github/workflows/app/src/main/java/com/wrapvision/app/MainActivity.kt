
package com.wrapvision.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var cameraPreview: PreviewView
    private lateinit var statusText: TextView

    private var selectedColor: Int? = null

    // Запрос разрешения камеры
    private val cameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startCamera()
        } else {
            statusText.text = "Необходимо разрешить доступ к камере"
        }
    }

    // Открытие галереи
    private val colorPicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            readSampleColor(uri)
        } else {
            statusText.text = "Фотография не выбрана"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        cameraPreview = findViewById(R.id.cameraPreview)
        statusText = findViewById(R.id.statusText)

        // Режим всего кузова
        findViewById<Button>(R.id.bodyModeButton)
            .setOnClickListener {
                statusText.text = "Режим: Весь кузов"
            }

        // Режим отдельных деталей
        findViewById<Button>(R.id.partsModeButton)
            .setOnClickListener {
                statusText.text = "Режим: Отдельные детали"
            }

        // Выбор фотографии плёнки
        findViewById<Button>(R.id.selectColorButton)
            .setOnClickListener {
                statusText.text = "Открываем галерею..."
                colorPicker.launch("image/*")
            }

        // Проверка разрешения камеры
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

    // Чтение фотографии и определение примерного цвета
    private fun readSampleColor(uri: android.net.Uri) {
        var bitmap: Bitmap? = null

        try {
            bitmap = contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it)
            }

            if (bitmap == null) {
                statusText.text = "Не удалось открыть фотографию"
                return
            }

            val smallBitmap = Bitmap.createScaledBitmap(
                bitmap,
                1,
                1,
                true
            )

            val color = smallBitmap.getPixel(0, 0)
            selectedColor = color

            val colorCode = String.format(
                "#%06X",
                0xFFFFFF and color
            )

            statusText.text = "Выбран цвет плёнки: $colorCode"

            smallBitmap.recycle()

        } catch (e: Exception) {
            statusText.text = "Ошибка обработки фотографии"
        } finally {
            bitmap?.recycle()
        }
    }

    // Запуск камеры
    private fun startCamera() {
        try {
            val cameraProviderFuture =
                ProcessCameraProvider.getInstance(this)

            cameraProviderFuture.addListener({

                try {
                    val cameraProvider = cameraProviderFuture.get()

                    val preview = Preview.Builder().build()
                    preview.surfaceProvider =
                        cameraPreview.surfaceProvider

                    cameraProvider.unbindAll()

                    cameraProvider.bindToLifecycle(
                        this,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview
                    )

                } catch (e: Exception) {
                    statusText.text = "Ошибка запуска камеры"
                }

            }, ContextCompat.getMainExecutor(this))

        } catch (e: Exception) {
            statusText.text = "Не удалось подключить камеру"
        }
    }
}
