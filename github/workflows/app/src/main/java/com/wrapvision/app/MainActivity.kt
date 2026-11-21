
package com.wrapvision.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {

    private lateinit var cameraPreview: PreviewView
    private lateinit var statusText: TextView
    private lateinit var homeScreen: View
    private lateinit var cameraScreen: View
    private lateinit var homeSelectColorButton: Button
    private lateinit var startCameraButton: Button

    private var selectedColor: Int? = null
    private var cameraProvider: ProcessCameraProvider? = null

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
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        window.statusBarColor = Color.rgb(8, 11, 16)
        window.navigationBarColor = Color.rgb(8, 11, 16)

        WindowCompat.getInsetsController(
            window,
            window.decorView
        ).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        setContentView(R.layout.activity_main)

        val root = findViewById<View>(android.R.id.content)

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
            )

            view.setPadding(
                bars.left,
                bars.top,
                bars.right,
                bars.bottom
            )

            insets
        }

        ViewCompat.requestApplyInsets(root)

        homeScreen = findViewById(R.id.homeScreen)
        cameraScreen = findViewById(R.id.cameraScreen)
        cameraPreview = findViewById(R.id.cameraPreview)
        statusText = findViewById(R.id.statusText)

        homeSelectColorButton =
            findViewById(R.id.homeSelectColorButton)

        startCameraButton =
            findViewById(R.id.startCameraButton)

        // Принудительно восстанавливаем надпись кнопки
        startCameraButton.apply {
            text = "НАЧАТЬ ПРОСМОТР"
            setTextColor(Color.rgb(16, 24, 32))
            textSize = 17f
            isAllCaps = false
            gravity = Gravity.CENTER
            visibility = View.VISIBLE
            backgroundTintList = null
            setBackgroundResource(R.drawable.bg_button_primary)
        }

        startCameraButton.setOnClickListener {
            openCameraScreen()
        }

        homeSelectColorButton.setOnClickListener {
            colorPicker.launch("image/*")
        }

        findViewById<Button>(R.id.selectColorButton)
            .setOnClickListener {
                colorPicker.launch("image/*")
            }

        findViewById<Button>(R.id.bodyModeButton)
            .setOnClickListener {
                statusText.text = "Режим: Весь кузов"
            }

        findViewById<Button>(R.id.partsModeButton)
            .setOnClickListener {
                statusText.text = "Режим: Отдельные детали"
            }

        findViewById<Button>(R.id.backToHomeButton)
            .setOnClickListener {
                showHomeScreen()
            }

        findViewById<Button>(R.id.myProjectsButton)
            .setOnClickListener {
                Toast.makeText(
                    this,
                    "Раздел проектов добавим следующим этапом",
                    Toast.LENGTH_SHORT
                ).show()
            }

        showHomeScreen()
    }

    private fun showHomeScreen() {
        cameraProvider?.unbindAll()

        homeScreen.visibility = View.VISIBLE
        cameraScreen.visibility = View.GONE
    }

    private fun openCameraScreen() {
        homeScreen.visibility = View.GONE
        cameraScreen.visibility = View.VISIBLE

        if (
            ContextCompat.checkSelfPermission(
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
        var bitmap: Bitmap? = null
        var smallBitmap: Bitmap? = null

        try {
            bitmap = contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it)
            }

            if (bitmap == null) {
                Toast.makeText(
                    this,
                    "Не удалось открыть фотографию",
                    Toast.LENGTH_SHORT
                ).show()
                return
            }

            smallBitmap = Bitmap.createScaledBitmap(
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

            homeSelectColorButton.text =
                "Цвет выбран: $colorCode"

            statusText.text =
                "Выбран цвет плёнки: $colorCode"

            Toast.makeText(
                this,
                "Выбран цвет: $colorCode",
                Toast.LENGTH_SHORT
            ).show()

        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Ошибка обработки фотографии",
                Toast.LENGTH_SHORT
            ).show()
        } finally {
            smallBitmap?.recycle()
            bitmap?.recycle()
        }
    }

    private fun startCamera() {
        try {
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

                    statusText.text = "Камера готова"

                } catch (e: Exception) {
                    statusText.text = "Ошибка запуска камеры"
                }

            }, ContextCompat.getMainExecutor(this))

        } catch (e: Exception) {
            statusText.text = "Не удалось подключить камеру"
        }
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
