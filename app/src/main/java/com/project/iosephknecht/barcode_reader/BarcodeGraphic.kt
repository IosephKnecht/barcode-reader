package com.project.iosephknecht.barcode_reader

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.google.android.gms.vision.barcode.Barcode
import com.project.iosephknecht.barcode_reader.ui.GraphicOverlay

class BarcodeGraphic(barcodeOverlay: GraphicOverlay<*>) : GraphicOverlay.Graphic(barcodeOverlay) {

    var id: Int? = null

    private val COLOR_CHOICES = arrayOf(Color.BLUE, Color.CYAN, Color.GREEN)
    private var currentColorIndex = 0

    private val rectPaint: Paint
    private val textPaint: Paint
    @Volatile
    var barcode: Barcode? = null
        private set

    init {
        currentColorIndex = (currentColorIndex + 1) % COLOR_CHOICES.size
        val choicesColor = COLOR_CHOICES[currentColorIndex]

        rectPaint = Paint().apply {
            color = choicesColor
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }

        textPaint = Paint().apply {
            color = choicesColor
            textSize = 36f
        }
    }

    fun updateItem(barcode: Barcode?) {
        this.barcode = barcode
        postInvalidate()
    }

    override fun draw(canvas: Canvas?) {
        val mutableBarcode = barcode ?: return

        val rect = RectF(mutableBarcode.boundingBox).apply {
            left = translateX(left)
            top = translateY(top)
            right = translateX(right)
            bottom = translateY(bottom)
        }

        canvas!!.drawRect(rect, rectPaint)
        canvas.drawText(mutableBarcode.rawValue, rect.left, rect.bottom, textPaint)
    }
}