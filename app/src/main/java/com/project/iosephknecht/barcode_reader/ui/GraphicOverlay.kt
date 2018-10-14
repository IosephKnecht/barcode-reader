package com.project.iosephknecht.barcode_reader.ui

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import java.util.HashSet

class GraphicOverlay<T : GraphicOverlay.Graphic>
@JvmOverloads constructor(context: Context,
                          attributeSet: AttributeSet? = null,
                          defStyle: Int = 0) : View(context, attributeSet, defStyle) {

    private var previewWidth: Int = 0
    private var previewHeight: Int = 0

    var widthScaleFactor = 1.0f
        private set

    var heightScaleFactor = 1.0f
        private set

    private var facing = CameraSource.CAMERA_FACING_BACK
    val graphics = HashSet<T>()

    private val lockObject = Any()

    abstract class Graphic(private val graphicOverlay: GraphicOverlay<*>) {

        abstract fun draw(canvas: Canvas?)

        fun scaleX(horizontal: Float) = horizontal * graphicOverlay.widthScaleFactor

        fun scaleY(vertical: Float) = vertical * graphicOverlay.heightScaleFactor

        fun translateX(x: Float): Float {
            return graphicOverlay
                .takeIf { it.facing == CameraSource.CAMERA_FACING_FRONT }
                ?.run { graphicOverlay.width - scaleX(x) } ?: scaleX(x)
        }

        fun translateY(y: Float) = scaleY(y)

        fun postInvalidate() = graphicOverlay.postInvalidate()
    }

    fun clear() {
        syncLambda { graphics.clear() }
    }

    fun add(graphics: T) {
        syncLambda { this.graphics.add(graphics) }
    }

    fun remove(graphics: T) {
        syncLambda { this.graphics.remove(graphics) }
    }

    fun setCameraInfo(previewWidth: Int, previewHeight: Int, facing: Int) {
        syncLambda {
            this.previewWidth = previewWidth
            this.previewHeight = previewHeight
            this.facing = facing
        }
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)

        syncLambda {
            if (previewWidth != 0 && previewHeight != 0) {
                widthScaleFactor = canvas!!.width.toFloat() / previewWidth.toFloat()
                heightScaleFactor = canvas.height.toFloat() / previewHeight.toFloat()
            }

            graphics.forEach {
                it.draw(canvas!!)
            }
        }
    }

    private fun syncLambda(block: (() -> Unit)) {
        synchronized(lockObject) {
            block()
        }
        postInvalidate()
    }
}