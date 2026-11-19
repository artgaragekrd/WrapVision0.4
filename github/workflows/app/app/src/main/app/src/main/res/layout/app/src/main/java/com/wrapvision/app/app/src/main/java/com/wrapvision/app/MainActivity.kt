package com.wrapvision.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.camera.view.PreviewView
import android.widget.Button
import android.widget.TextView
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var statusText: TextView
    private lateinit var cameraExecutor: ExecutorService

    private val cameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            if (it) startCamera()
            else statusText.text = "Необходимо разрешение на камеру"
        }

    private val imagePicker =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                try {
                    val bitmap = contentResolver.openInputStream(uri).use {
                        BitmapFactory.decodeStream(it)
                    }

                    if (bitmap != null) {
                        val x = bitmap.width / 2
                        val y = bitmap.height / 2
                        val pixel = bitmap.getPixel(x, y)

                        statusText.text =
                            "Образец выбран: цвет #${Integer.toHexString(pixel)}"
                    } else {
                        statusText.text = "Не удалось прочитать изображение"
                    }
                } catch (e: Exception) {
                    statusText.text = "Ошибка загрузки образца"
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        cameraExecutor = Executors.newSingleThreadExecutor()

        previewView = findViewById(R.id.cameraPreview)
        statusText = findViewById(R.id.statusText)

        findViewById<Button>(R.id.selectColorButton).setOnClickListener {
            imagePicker.launch("image/*")
        }

        findViewById<Button>(R.id.bodyModeButton).setOnClickListener {
            statusText.text = "Выбран режим: весь кузов"
        }

        findViewById<Button>(R.id.partsModeButton).setOnClickListener {
            statusText.text = "Выбран режим: отдельные детали"
        }

        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build()
            preview.setSurfaceProvider(previewView.surfaceProvider)

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview
            )

            statusText.text = "Камера готова. Выберите образец плёнки."
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
