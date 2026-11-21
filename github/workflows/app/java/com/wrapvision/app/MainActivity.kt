
package com.wrapvision.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var cameraPreview: PreviewView
    private lateinit var studioView: StudioView

    private var cameraProvider: ProcessCameraProvider? = null

    private var selectedColor = Color.rgb(40, 45, 55)
    private var selectedColorCode = "Цвет не выбран"
    private var selectedMode = "ВЕСЬ КУЗОВ"

    private val cameraPermission = registerForActivityResult(
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

    private val colorPicker = registerForActivityResult(
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

        cameraPreview = PreviewView(this).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            visibility = View.GONE
        }

        studioView = StudioView(this).apply {
            onAction = { action ->
                handleAction(action)
            }
        }

        val root = FrameLayout(this)

        root.addView(
            cameraPreview,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        root.addView(
            studioView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        setContentView(root)
    }

    private fun handleAction(action: String) {
        when (action) {
            "camera" -> requestCamera()
            "color" -> colorPicker.launch("image/*")
            "projects" -> Toast.makeText(
                this,
                "Раздел проектов будет добавлен следующим этапом",
                Toast.LENGTH_SHORT
            ).show()

            "back" -> returnHome()

            "body" -> {
                selectedMode = "ВЕСЬ КУЗОВ"
                studioView.setMode(selectedMode)
            }

            "parts" -> {
                selectedMode = "ПО ДЕТАЛЯМ"
                studioView.setMode(selectedMode)
            }
        }
    }

    private fun requestCamera() {
        if (ContextCompat.checkSelfPermission(
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
        studioView.showCameraScreen()
        cameraPreview.visibility = View.VISIBLE

        val future = ProcessCameraProvider.getInstance(this)

        future.addListener({

            try {
                val provider = future.get()
                cameraProvider = provider

                val preview = Preview.Builder().build()
                preview.surfaceProvider = cameraPreview.surfaceProvider

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
        studioView.showHomeScreen()
    }

    private fun readSampleColor(uri: Uri) {
        try {
            val bounds = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }

            contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }

            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
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
                Toast.makeText(
                    this,
                    "Не удалось прочитать изображение",
                    Toast.LENGTH_SHORT
                ).show()
                return
            }

            val pixel = Bitmap.createScaledBitmap(bitmap, 1, 1, true)
            selectedColor = pixel.getPixel(0, 0)

            selectedColorCode = String.format(
                "#%06X",
                0xFFFFFF and selectedColor
            )

            studioView.setSelectedColor(
                selectedColor,
                selectedColorCode
            )

            if (pixel !== bitmap) pixel.recycle()
            bitmap.recycle()

        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Ошибка чтения изображения",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun calculateSampleSize(width: Int, height: Int): Int {
        var sample = 1

        while (
            width / sample > 1024 ||
            height / sample > 1024
        ) {
            sample *= 2
        }

        return sample
    }

    @Deprecated("Deprecated in Android")
    override fun onBackPressed() {
        if (studioView.isCameraScreen()) {
            returnHome()
        } else {
            super.onBackPressed()
        }
    }
}

class StudioView(
    context: android.content.Context
) : View(context) {

    var onAction: ((String) -> Unit)? = null

    private var cameraScreen = false
    private var color = Color.rgb(40, 45, 55)
    private var colorCode = "Цвет не выбран"
    private var mode = "ВЕСЬ КУЗОВ"

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var carBitmap: Bitmap? = null

    init {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)

        try {
            carBitmap = BitmapFactory.decodeResource(
                resources,
                R.drawable.mustang_home
            )
        } catch (_: Exception) {
            carBitmap = null
        }
    }

    fun showCameraScreen() {
        cameraScreen = true
        invalidate()
    }

    fun showHomeScreen() {
        cameraScreen = false
        invalidate()
    }

    fun isCameraScreen() = cameraScreen

    fun setSelectedColor(value: Int, code: String) {
        color = value
        colorCode = code
        invalidate()
    }

    fun setMode(value: String) {
        mode = value
        invalidate()
    }

    private fun drawRound(
        canvas: Canvas,
        rect: RectF,
        radius: Float,
        color: Int,
        stroke: Int = 0,
        strokeWidth: Float = 1f
    ) {
        paint.reset()
        paint.isAntiAlias = true
        paint.color = color
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(rect, radius, radius, paint)

        if (stroke != 0) {
            paint.color = stroke
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = strokeWidth
            canvas.drawRoundRect(rect, radius, radius, paint)
        }

        paint.style = Paint.Style.FILL
    }

    private fun drawText(
        canvas: Canvas,
        value: String,
        x: Float,
        y: Float,
        size: Float,
        color: Int,
        bold: Boolean = false,
        centered: Boolean = false,
        spacing: Float = 0f
    ) {
        paint.reset()
        paint.isAntiAlias = true
        paint.color = color
        paint.textSize = size
        paint.typeface = if (bold) {
            Typeface.create("sans-serif", Typeface.BOLD)
        } else {
            Typeface.create("sans-serif-light", Typeface.NORMAL)
        }
        paint.textAlign = if (centered) {
            Paint.Align.CENTER
        } else {
            Paint.Align.LEFT
        }
        paint.letterSpacing = spacing
        canvas.drawText(value, x, y, paint)
    }

    private fun drawButton(
        canvas: Canvas,
        rect: RectF,
        label: String,
        primary: Boolean
    ) {
        paint.reset()
        paint.isAntiAlias = true

        paint.setShadowLayer(
            if (primary) 22f else 12f,
            0f,
            4f,
            if (primary) 0x663D9DFF else 0x443D9DFF
        )

        paint.shader = if (primary) {
            LinearGradient(
                rect.left,
                rect.top,
                rect.right,
                rect.bottom,
                intArrayOf(
                    Color.rgb(235, 243, 251),
                    Color.rgb(155, 183, 211),
                    Color.rgb(215, 227, 240)
                ),
                null,
                Shader.TileMode.CLAMP
            )
        } else {
            LinearGradient(
                rect.left,
                rect.top,
                rect.right,
                rect.bottom,
                intArrayOf(
                    Color.rgb(29, 40, 55),
                    Color.rgb(13, 20, 30)
                ),
                null,
                Shader.TileMode.CLAMP
            )
        }

        canvas.drawRoundRect(rect, 17f, 17f, paint)

        paint.clearShadowLayer()
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.2f
        paint.color = if (primary) {
            0xFFEAF4FF.toInt()
        } else {
            0xFF548DC4.toInt()
        }

        canvas.drawRoundRect(rect, 17f, 17f, paint)

        drawText(
            canvas,
            label,
            rect.centerX(),
            rect.centerY() + 5f,
            13f,
            if (primary) {
                Color.rgb(15, 25, 37)
            } else {
                Color.rgb(224, 237, 250)
            },
            true,
            true,
            0.08f
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val scale = width / 400f
        canvas.save()
        canvas.scale(scale, scale)

        val h = height / scale

        if (cameraScreen) {
            drawCamera(canvas, h)
        } else {
            drawHome(canvas, h)
        }

        canvas.restore()
    }

    private fun drawBackground(canvas: Canvas, h: Float) {
        paint.reset()
        paint.isAntiAlias = true
        paint.shader = LinearGradient(
            0f,
            0f,
            400f,
            h,
            intArrayOf(
                Color.rgb(17, 25, 37),
                Color.rgb(8, 12, 19),
                Color.rgb(4, 7, 12)
            ),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, 400f, h, paint)
        paint.shader = null

        paint.shader = RadialGradient(
            200f,
            h * 0.35f,
            280f,
            intArrayOf(
                0x333B8ACB,
                0x003B8ACB
            ),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, 400f, h, paint)
        paint.shader = null
    }

    private fun drawHome(canvas: Canvas, h: Float) {
        drawBackground(canvas, h)

        // Логотип
        drawText(
            canvas,
            "WRAPVISION",
            200f,
            52f,
            29f,
            Color.rgb(231, 239, 249),
            true,
            true,
            0.18f
        )

        drawText(
            canvas,
            "P R E M I U M   S T U D I O",
            200f,
            78f,
            9f,
            Color.rgb(91, 167, 230),
            false,
            true
        )

        // Верхняя световая линия
        paint.color = 0x88559DD9.toInt()
        paint.strokeWidth = 1f
        canvas.drawLine(38f, 100f, 362f, 100f, paint)

        // Карточка автомобиля
        val carRect = RectF(18f, 120f, 382f, h * 0.53f)

        paint.setShadowLayer(
            25f,
            0f,
            8f,
            0x77408CD0
        )
        paint.color = Color.rgb(15, 22, 32)
        canvas.drawRoundRect(carRect, 23f, 23f, paint)
        paint.clearShadowLayer()

        drawRound(
            canvas,
            carRect,
            23f,
            Color.rgb(13, 19, 28),
            0xFF456A8B.toInt(),
            1.2f
        )

        // Фотография Mustang
        carBitmap?.let { bitmap ->
            val inner = RectF(
                carRect.left + 8f,
                carRect.top + 8f,
                carRect.right - 8f,
                carRect.bottom - 8f
            )

            val ratio = minOf(
                inner.width() / bitmap.width,
                inner.height() / bitmap.height
            )

            val bw = bitmap.width * ratio
            val bh = bitmap.height * ratio

            val dst = RectF(
                inner.centerX() - bw / 2f,
                inner.centerY() - bh / 2f,
                inner.centerX() + bw / 2f,
                inner.centerY() + bh / 2f
            )

            canvas.save()
            val path = Path().apply {
                addRoundRect(
                    inner,
                    17f,
                    17f,
                    Path.Direction.CW
                )
            }
            canvas.clipPath(path)

            paint.reset()
            paint.isAntiAlias = true
            canvas.drawBitmap(bitmap, null, dst, paint)
            canvas.restore()
        }

        // Подпись под автомобилем
        drawText(
            canvas,
            "YOUR CAR. YOUR STYLE.",
            200f,
            h * 0.56f,
            11f,
            Color.rgb(151, 177, 204),
            true,
            true,
            0.14f
        )

        // Панель образца
        val panel = RectF(18f, h * 0.59f, 382f, h * 0.735f)

        drawRound(
            canvas,
            panel,
            19f,
            Color.rgb(22, 31, 44),
            0xFF344D68.toInt(),
            1f
        )

        drawText(
            canvas,
            "ТЕКУЩИЙ ОБРАЗЕЦ ЦВЕТА",
            35f,
            panel.top + 25f,
            10f,
            Color.rgb(126, 160, 193),
            true
        )

        // Цветовой индикатор
        drawRound(
            canvas,
            RectF(35f, panel.top + 39f, 63f, panel.top + 67f),
            7f,
            color,
            0xFFB7C9DA.toInt(),
            1f
        )

        drawText(
            canvas,
            colorCode,
            76f,
            panel.top + 58f,
            14f,
            Color.rgb(232, 239, 248),
            true
        )

        // Кнопка выбора цвета
        drawButton(
            canvas,
            RectF(24f, panel.bottom - 47f, 376f, panel.bottom - 8f),
            "ВЫБРАТЬ ЦВЕТ ПЛЁНКИ",
            false
        )

        // Основные действия
        drawButton(
            canvas,
            RectF(18f, h * 0.765f, 382f, h * 0.835f),
            "ОТКРЫТЬ КАМЕРУ",
            true
        )

        drawButton(
            canvas,
            RectF(18f, h * 0.855f, 382f, h * 0.92f),
            "МОИ ПРОЕКТЫ",
            false
        )

        drawText(
            canvas,
            "AUTOMOTIVE COLOR VISUALIZATION",
            200f,
            h * 0.96f,
            8f,
            Color.rgb(83, 105, 130),
            false,
            true,
            0.12f
        )
    }

    private fun drawCamera(canvas: Canvas, h: Float) {
        // Камера видна под этим экраном
        drawRound(
            canvas,
            RectF(14f, 25f, 386f, 78f),
            16f,
            0xDD101B29.toInt(),
            0xFF548DC4.toInt()
        )

        drawText(
            canvas,
            "WRAPVISION",
            200f,
            57f,
            19f,
            Color.WHITE,
            true,
            true,
            0.15f
        )

        val panel = RectF(0f, h * 0.65f, 400f, h)

        paint.reset()
        paint.shader = LinearGradient(
            panel.top,
            panel.top,
            panel.left,
            panel.bottom,
            intArrayOf(
                0x00101927,
                0xF20B111C.toInt(),
                0xFF080D15.toInt()
            ),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(panel, paint)
        paint.shader = null

        drawText(
            canvas,
            "ВЫБРАННЫЙ ЦВЕТ: $colorCode",
            200f,
            h * 0.705f,
            11f,
            Color.rgb(210, 226, 243),
            true,
            true
        )

        drawButton(
            canvas,
            RectF(18f, h * 0.73f, 382f, h * 0.795f),
            "ВЫБРАТЬ ОБРАЗЕЦ ЦВЕТА",
            true
        )

        drawButton(
            canvas,
            RectF(18f, h * 0.81f, 191f, h * 0.875f),
            "ВЕСЬ КУЗОВ",
            mode == "ВЕСЬ КУЗОВ"
        )

        drawButton(
            canvas,
            RectF(209f, h * 0.81f, 382f, h * 0.875f),
            "ПО ДЕТАЛЯМ",
            mode == "ПО ДЕТАЛЯМ"
        )

        drawButton(
            canvas,
            RectF(18f, h * 0.89f, 382f, h * 0.95f),
            "НА ГЛАВНЫЙ ЭКРАН",
            false
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_UP) {
            return true
        }

        val scale = width / 400f
        val x = event.x / scale
        val y = event.y / scale
        val h = height / scale

        if (!cameraScreen) {
            when {
                y in (h * 0.59f)..(h * 0.735f) &&
                    y >= h * 0.59f + (h * 0.145f - 47f) -> {
                    onAction?.invoke("color")
                }

                y in (h * 0.765f)..(h * 0.835f) -> {
                    onAction?.invoke("camera")
                }

                y in (h * 0.855f)..(h * 0.92f) -> {
                    onAction?.invoke("projects")
                }
            }
        } else {
            when {
                y in (h * 0.73f)..(h * 0.795f) -> {
                    onAction?.invoke("color")
                }

                y in (h * 0.81f)..(h * 0.875f) -> {
                    if (x < 200f) {
                        onAction?.invoke("body")
                    } else {
                        onAction?.invoke("parts")
                    }
                }

                y in (h * 0.89f)..(h * 0.95f) -> {
                    onAction?.invoke("back")
                }
            }
        }

        return true
    }
}
