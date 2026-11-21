
package com.wrapvision.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
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
    private var selectedColorCode = "ЦВЕТ НЕ ВЫБРАН"
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
            returnHome()
        }
    }

    private val colorPicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) readSampleColor(uri)
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
            onAction = { action -> handleAction(action) }
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

            val image = bitmap ?: throw IllegalArgumentException("No bitmap")

            val left = image.width / 3
            val right = (image.width * 2) / 3
            val top = image.height / 3
            val bottom = (image.height * 2) / 3

            var red = 0L
            var green = 0L
            var blue = 0L
            var count = 0L

            val step = maxOf(1, minOf(image.width, image.height) / 100)

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

            studioView.setSelectedColor(
                selectedColor,
                selectedColorCode
            )

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

    @Deprecated("Deprecated in Android")
    override fun onBackPressed() {
        if (studioView.isCameraScreen()) {
            returnHome()
        } else {
            super.onBackPressed()
        }
    }
}

class StudioView(context: android.content.Context) : View(context) {

    var onAction: ((String) -> Unit)? = null

    private var cameraScreen = false
    private var selectedColor = Color.rgb(40, 45, 55)
    private var colorCode = "ЦВЕТ НЕ ВЫБРАН"
    private var mode = "ВЕСЬ КУЗОВ"

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var carBitmap: Bitmap? = null

    private val dark = Color.rgb(7, 10, 15)
    private val panel = Color.rgb(18, 23, 31)
    private val silver = Color.rgb(210, 218, 228)
    private val blue = Color.rgb(35, 155, 255)

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
        selectedColor = value
        colorCode = code
        invalidate()
    }

    fun setMode(value: String) {
        mode = value
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val scale = width / 400f
        if (scale <= 0f) return

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

    private fun drawHome(canvas: Canvas, h: Float) {
        drawBackground(canvas, h)

        drawText(
            canvas,
            "WRAPVISION",
            200f,
            48f,
            29f,
            silver,
            true
        )

        drawText(
            canvas,
            "P R E M I U M   S T U D I O",
            200f,
            76f,
            10f,
            blue,
            true
        )

        paint.color = Color.rgb(52, 65, 82)
        paint.strokeWidth = 1f
        canvas.drawLine(45f, 96f, 355f, 96f, paint)

        drawText(
            canvas,
            "ПРЕОБРАЗИ СВОЙ АВТОМОБИЛЬ",
            200f,
            120f,
            11f,
            Color.rgb(155, 167, 183),
            true
        )

        val carTop = 138f
        val carBottom = h - 320f
        val carRect = RectF(18f, carTop, 382f, maxOf(carTop + 100f, carBottom))

        drawCarPanel(canvas, carRect)

        val panelTop = h - 300f
        drawPanel(
            canvas,
            RectF(14f, panelTop, 386f, h - 15f),
            22f
        )

        drawText(
            canvas,
            "ВЫБЕРИ ЦВЕТ БУДУЩЕЙ ПЛЁНКИ",
            32f,
            panelTop + 32f,
            11f,
            silver,
            true,
            Paint.Align.LEFT
        )

        drawRoundRect(
            canvas,
            RectF(30f, panelTop + 47f, 370f, panelTop + 88f),
            12f,
            Color.rgb(28, 35, 45),
            Color.rgb(58, 72, 90)
        )

        paint.color = selectedColor
        canvas.drawRoundRect(
            RectF(42f, panelTop + 57f, 70f, panelTop + 78f),
            6f,
            6f,
            paint
        )

        drawText(
            canvas,
            colorCode,
            85f,
            panelTop + 74f,
            12f,
            silver,
            true,
            Paint.Align.LEFT
        )

        drawButton(
            canvas,
            RectF(30f, panelTop + 101f, 370f, panelTop + 155f),
            "НАЧАТЬ ПРОСМОТР",
            true
        )

        drawButton(
            canvas,
            RectF(30f, panelTop + 166f, 194f, panelTop + 215f),
            "ВЫБРАТЬ ЦВЕТ",
            false
        )

        drawButton(
            canvas,
            RectF(206f, panelTop + 166f, 370f, panelTop + 215f),
            "МОИ ПРОЕКТЫ",
            false
        )
    }

    private fun drawCamera(canvas: Canvas, h: Float) {
        // Прозрачный экран поверх живого изображения камеры.
        paint.color = Color.argb(185, 7, 10, 15)
        canvas.drawRoundRect(
            RectF(12f, 18f, 388f, 91f),
            18f,
            18f,
            paint
        )

        drawText(
            canvas,
            "WRAPVISION",
            200f,
            49f,
            23f,
            silver,
            true
        )

        drawText(
            canvas,
            "LIVE STUDIO",
            200f,
            72f,
            9f,
            blue,
            true
        )

        drawButton(
            canvas,
            RectF(24f, 110f, 142f, 153f),
            "НАЗАД",
            false
        )

        val bottom = h - 16f

        paint.color = Color.argb(235, 12, 17, 25)
        canvas.drawRoundRect(
            RectF(12f, bottom - 220f, 388f, bottom),
            22f,
            22f,
            paint
        )

        paint.color = Color.rgb(45, 58, 75)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRoundRect(
            RectF(12f, bottom - 220f, 388f, bottom),
            22f,
            22f,
            paint
        )
        paint.style = Paint.Style.FILL

        drawText(
            canvas,
            "РЕЖИМ НАНЕСЕНИЯ",
            32f,
            bottom - 190f,
            11f,
            silver,
            true,
            Paint.Align.LEFT
        )

        drawButton(
            canvas,
            RectF(25f, bottom - 172f, 194f, bottom - 119f),
            "ВЕСЬ КУЗОВ",
            mode == "ВЕСЬ КУЗОВ"
        )

        drawButton(
            canvas,
            RectF(206f, bottom - 172f, 375f, bottom - 119f),
            "ПО ДЕТАЛЯМ",
            mode == "ПО ДЕТАЛЯМ"
        )

        drawButton(
            canvas,
            RectF(25f, bottom - 105f, 375f, bottom - 53f),
            "ВЫБРАТЬ ЦВЕТ ПЛЁНКИ",
            true
        )

        drawText(
            canvas,
            colorCode,
            200f,
            bottom - 27f,
            10f,
            silver,
            true
        )
    }

    private fun drawBackground(canvas: Canvas, h: Float) {
        paint.shader = LinearGradient(
            0f,
            0f,
            400f,
            h,
            intArrayOf(
                Color.rgb(13, 19, 28),
                Color.rgb(5, 8, 13),
                Color.rgb(13, 18, 26)
            ),
            null,
            Shader.TileMode.CLAMP
        )

        canvas.drawRect(0f, 0f, 400f, h, paint)
        paint.shader = null

        paint.color = Color.argb(18, 45, 140, 255)
        canvas.drawCircle(200f, h * 0.39f, 190f, paint)
    }

    private fun drawCarPanel(canvas: Canvas, rect: RectF) {
        paint.color = Color.rgb(17, 23, 32)
        canvas.drawRoundRect(rect, 20f, 20f, paint)

        paint.color = Color.rgb(47, 64, 82)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRoundRect(rect, 20f, 20f, paint)
        paint.style = Paint.Style.FILL

        val bitmap = carBitmap

        if (bitmap != null && !bitmap.isRecycled) {
            val save = canvas.save()

            val path = android.graphics.Path().apply {
                addRoundRect(
                    rect,
                    20f,
                    20f,
                    android.graphics.Path.Direction.CW
                )
            }

            canvas.clipPath(path)

            val imageWidth = bitmap.width.toFloat()
            val imageHeight = bitmap.height.toFloat()

            val ratio = minOf(
                (rect.width() - 12f) / imageWidth,
                (rect.height() - 12f) / imageHeight
            )

            val dstWidth = imageWidth * ratio
            val dstHeight = imageHeight * ratio

            val dst = RectF(
                rect.centerX() - dstWidth / 2f,
                rect.centerY() - dstHeight / 2f,
                rect.centerX() + dstWidth / 2f,
                rect.centerY() + dstHeight / 2f
            )

            paint.alpha = 255
            canvas.drawBitmap(bitmap, null, dst, paint)
            canvas.restoreToCount(save)
        } else {
            drawText(
                canvas,
                "WRAPVISION",
                rect.centerX(),
                rect.centerY(),
                22f,
                silver,
                true
            )
        }
    }

    private fun drawPanel(
        canvas: Canvas,
        rect: RectF,
        radius: Float
    ) {
        paint.shader = LinearGradient(
            rect.left,
            rect.top,
            rect.right,
            rect.bottom,
            Color.rgb(30, 38, 50),
            Color.rgb(12, 17, 25),
            Shader.TileMode.CLAMP
        )

        canvas.drawRoundRect(rect, radius, radius, paint)
        paint.shader = null

        paint.color = Color.rgb(57, 72, 91)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRoundRect(rect, radius, radius, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawButton(
        canvas: Canvas,
        rect: RectF,
        text: String,
        primary: Boolean
    ) {
        val radius = 13f

        if (primary) {
            paint.shader = LinearGradient(
                rect.left,
                rect.top,
                rect.right,
                rect.bottom,
                intArrayOf(
                    Color.rgb(218, 228, 240),
                    Color.rgb(130, 151, 176),
                    Color.rgb(190, 204, 221)
                ),
                null,
                Shader.TileMode.CLAMP
            )
        } else {
            paint.shader = LinearGradient(
                rect.left,
                rect.top,
                rect.right,
                rect.bottom,
                Color.rgb(38, 48, 62),
                Color.rgb(19, 26, 37),
                Shader.TileMode.CLAMP
            )
        }

        canvas.drawRoundRect(rect, radius, radius, paint)
        paint.shader = null

        paint.color = if (primary) {
            Color.rgb(225, 238, 252)
        } else {
            Color.rgb(72, 100, 132)
        }

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRoundRect(rect, radius, radius, paint)
        paint.style = Paint.Style.FILL

        drawText(
            canvas,
            text,
            rect.centerX(),
            rect.centerY() + 4f,
            if (text.length > 18) 10f else 12f,
            if (primary) Color.rgb(12, 20, 30) else silver,
            true
        )
    }

    private fun drawRoundRect(
        canvas: Canvas,
        rect: RectF,
        radius: Float,
        color: Int,
        stroke: Int
    ) {
        paint.color = color
        canvas.drawRoundRect(rect, radius, radius, paint)

        paint.color = stroke
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRoundRect(rect, radius, radius, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawText(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        size: Float,
        color: Int,
        bold: Boolean = false,
        align: Paint.Align = Paint.Align.CENTER
    ) {
        paint.shader = null
        paint.color = color
        paint.textSize = size
        paint.textAlign = align
        paint.typeface = if (bold) {
            android.graphics.Typeface.create(
                "sans-serif",
                android.graphics.Typeface.BOLD
            )
        } else {
            android.graphics.Typeface.create(
                "sans-serif",
                android.graphics.Typeface.NORMAL
            )
        }

        paint.style = Paint.Style.FILL
        canvas.drawText(text, x, y, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_UP) {
            return true
        }

        val scale = width / 400f
        if (scale <= 0f) return true

        val x = event.x / scale
        val y = event.y / scale
        val h = height / scale

        if (!cameraScreen) {
            val panelTop = h - 300f

            when {
                y in (panelTop + 101f)..(panelTop + 155f) &&
                    x in 30f..370f -> onAction?.invoke("camera")

                y in (panelTop + 166f)..(panelTop + 215f) &&
                    x in 30f..194f -> onAction?.invoke("color")

                y in (panelTop + 166f)..(panelTop + 215f) &&
                    x in 206f..370f -> onAction?.invoke("projects")
            }
        } else {
            val bottom = h - 16f

            when {
                y in 110f..153f && x in 24f..142f ->
                    onAction?.invoke("back")

                y in (bottom - 172f)..(bottom - 119f) &&
                    x in 25f..194f -> onAction?.invoke("body")

                y in (bottom - 172f)..(bottom - 119f) &&
                    x in 206f..375f -> onAction?.invoke("parts")

                y in (bottom - 105f)..(bottom - 53f) &&
                    x in 25f..375f -> onAction?.invoke("color")
            }
        }

        return true
    }
}
