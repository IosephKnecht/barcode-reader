package com.project.iosephknecht.barcode_reader.ui

import android.Manifest
import android.content.Context
import android.content.res.Configuration
import android.support.annotation.RequiresPermission
import android.util.AttributeSet
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import java.io.IOException

class CameraSourcePreview @JvmOverloads constructor(context: Context,
                                                    attrs: AttributeSet? = null,
                                                    defStyle: Int = 0) : ViewGroup(context, attrs, defStyle) {
    private val TAG = "CameraSourcePreview"

    private val surfaceView: SurfaceView = SurfaceView(context).apply {
        holder.addCallback(SurfaceCallback())
    }
    private var startRequested: Boolean = false
    private var surfaceAvailable: Boolean = false
    private var cameraSource: CameraSource? = null

    private var overlayGrapic: GraphicOverlay<*>? = null

    init {
        addView(surfaceView)
    }

    @RequiresPermission(Manifest.permission.CAMERA)
    @Throws(IOException::class, SecurityException::class)
    fun start(cameraSource: CameraSource?) {
        if (cameraSource == null) {
            stop()
        }

        this.cameraSource = cameraSource

        if (cameraSource != null) {
            startRequested = true
            startIfReady()
        }
    }

    @RequiresPermission(Manifest.permission.CAMERA)
    @Throws(IOException::class, SecurityException::class)
    fun start(cameraSource: CameraSource, overlay: GraphicOverlay<*>) {
        overlayGrapic = overlay
        start(cameraSource)
    }

    fun stop() = cameraSource?.stop()

    fun release() {
        cameraSource?.release()
        cameraSource = null
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        var width = 320
        var height = 240
        if (cameraSource != null) {
            val size = cameraSource!!.previewSize
            if (size != null) {
                width = size.width
                height = size.height
            }
        }

        if (isPortraitMode()) {
            val tmp = width

            width = height
            height = tmp
        }

        val layoutWidth = right - left
        val layoutHeight = bottom - top

        var childWidth = layoutWidth
        var childHeight = (layoutWidth.toFloat() / width.toFloat() * height).toInt()

        if (childHeight > layoutHeight) {
            childHeight = layoutHeight
            childWidth = (layoutHeight.toFloat() / height.toFloat() * width).toInt()
        }

        for (i in 0 until childCount) {
            getChildAt(i).layout(0, 0, childWidth, childHeight)
        }

        try {
            startIfReady()
        } catch (se: SecurityException) {
            Log.e(TAG, "Do not have permission to start the camera", se)
        } catch (e: IOException) {
            Log.e(TAG, "Could not start camera source.", e)
        }
    }

    @RequiresPermission(Manifest.permission.CAMERA)
    @Throws(IOException::class, SecurityException::class)
    private fun startIfReady() {
        if (startRequested && surfaceAvailable) {
            cameraSource!!.start(surfaceView.holder)
            if (overlayGrapic != null) {
                val size = cameraSource!!.previewSize
                val min = Math.min(size.width, size.height)
                val max = Math.max(size.width, size.height)
                if (isPortraitMode()) {
                    // Swap width and height sizes when in portrait, since it will be rotated by
                    // 90 degrees
                    overlayGrapic!!.setCameraInfo(min, max, cameraSource!!.cameraFacing)
                } else {
                    overlayGrapic!!.setCameraInfo(max, min, cameraSource!!.cameraFacing)
                }
                overlayGrapic!!.clear()
            }
            startRequested = false
        }
    }

    private fun isPortraitMode(): Boolean {
        val orientation = context.resources.configuration.orientation
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            return false
        }
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            return true
        }

        Log.d(TAG, "isPortraitMode returning false by default")
        return false
    }

    private inner class SurfaceCallback : SurfaceHolder.Callback {
        override fun surfaceCreated(surface: SurfaceHolder) {
            surfaceAvailable = true
            try {
                startIfReady()
            } catch (se: SecurityException) {
                Log.e(TAG, "Do not have permission to start the camera", se)
            } catch (e: IOException) {
                Log.e(TAG, "Could not start camera source.", e)
            }

        }

        override fun surfaceDestroyed(surface: SurfaceHolder) {
            surfaceAvailable = false
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
    }
}