package com.wrapvision.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/** Displays the transparent segmentation bitmap over CameraX PreviewView. */
class CarMaskOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    @Volatile private var overlay: Bitmap? = null

    fun setOverlay(bitmap: Bitmap?) {
        val old = overlay
        overlay = bitmap
        if (old !== bitmap && old?.isRecycled == false) old.recycle()
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bitmap = overlay ?: return
        if (bitmap.isRecycled || width == 0 || height == 0) return

        // PreviewView uses FILL_CENTER. Match its center-crop transform.
        val scale = maxOf(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height)
        val drawWidth = bitmap.width * scale
        val drawHeight = bitmap.height * scale
        val left = (width - drawWidth) / 2f
        val top = (height - drawHeight) / 2f
        canvas.drawBitmap(bitmap, null,
            android.graphics.RectF(left, top, left + drawWidth, top + drawHeight), paint)
    }

    fun clear() = setOverlay(null)
}
