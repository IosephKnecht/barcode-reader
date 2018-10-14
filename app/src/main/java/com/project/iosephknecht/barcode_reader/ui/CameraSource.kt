package com.project.iosephknecht.barcode_reader.ui

import android.Manifest
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.os.Build
import android.os.SystemClock
import android.support.annotation.RequiresPermission
import android.support.annotation.StringDef
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import com.google.android.gms.common.images.Size
import com.google.android.gms.vision.Detector
import com.google.android.gms.vision.Frame
import java.io.IOException
import java.nio.ByteBuffer
import java.util.*

@SuppressWarnings("deprecation")
class CameraSource private constructor() {

    companion object {
        @SuppressLint("InlinedApi")
        val CAMERA_FACING_BACK = Camera.CameraInfo.CAMERA_FACING_BACK

        @SuppressLint("InlinedApi")
        val CAMERA_FACING_FRONT = Camera.CameraInfo.CAMERA_FACING_FRONT
    }

    private val TAG = "OpenCameraSource"

    private val DUMMY_TEXTURE_NAME = 100
    private val ASPECT_RATIO_TOLERANCE = 0.01f

    private var context: Context? = null
    private val cameraLock = Any()
    private var camera: Camera? = null

    var facing = CAMERA_FACING_BACK
        private set

    private var rotation: Int = 0

    var previewSize: Size? = null
        private set

    private var requestedFps = 30.0f
    private var requestedPreviewWidth = 1024
    private var requestedPreviewHeight = 768

    private var focusMode: String? = null
    private var flashMode: String? = null

    private var dummySurfaceView: SurfaceView? = null
    private var dummySurfaceTexture: SurfaceTexture? = null

    private var processingThread: Thread? = null
    private var frameProcessor: FrameProcessingRunnable? = null

    private val bytesToByteBuffer = HashMap<ByteArray, ByteBuffer>()


    class Builder(private val context: Context, private val detector: Detector<*>) {

        private val cameraSource = CameraSource()
        private val maxValue = 1_000_000

        fun setRequestedFps(fps: Float) = apply {
            fps.takeIf { it > 0 }
                ?.let { cameraSource.requestedFps = fps }
                ?: throw IllegalStateException("Invalid fps: $fps")
        }

        fun setFocusMode(@FocusMode focusMode: String?) = apply { cameraSource.focusMode = focusMode }

        fun setFlashMode(@FlashMode flashMode: String?) = apply { cameraSource.flashMode = flashMode }

        fun setRequestedPreviewSize(width: Int, height: Int) = apply {
            if (width <= 0 || width >= maxValue || height <= 0 || height > maxValue)
                throw IllegalStateException("Invalid preview size: $width * $height")

            cameraSource.apply {
                requestedPreviewWidth = width
                requestedPreviewHeight = height
            }
        }

        fun setFacing(facing: Int) = apply {
            facing.takeIf { it == CAMERA_FACING_BACK || it == CAMERA_FACING_FRONT }
                ?.let { cameraSource.facing = facing }
                ?: throw IllegalStateException("Invalid camera: $facing")
        }

        fun build() = cameraSource.apply {
            context = this@Builder.context
            frameProcessor = FrameProcessingRunnable(detector)
        }
    }

    fun release() {
        synchronized(cameraLock) {
            stop()
            frameProcessor!!.release()
        }
    }

    @RequiresPermission(Manifest.permission.CAMERA)
    @Throws(IOException::class)
    fun start(): CameraSource {
        synchronized(cameraLock) {
            if (camera != null) {
                return this
            }

            camera = createCamera()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                dummySurfaceTexture = SurfaceTexture(DUMMY_TEXTURE_NAME)
                camera!!.setPreviewTexture(dummySurfaceTexture)
            } else {
                dummySurfaceView = SurfaceView(context)
                camera!!.setPreviewDisplay(dummySurfaceView!!.getHolder())
            }
            camera!!.startPreview()

            processingThread = Thread(frameProcessor)
            frameProcessor!!.setActive(true)
            processingThread!!.start()
        }
        return this
    }

    @RequiresPermission(Manifest.permission.CAMERA)
    @Throws(IOException::class)
    fun start(surfaceHolder: SurfaceHolder): CameraSource {
        synchronized(cameraLock) {
            if (camera != null) {
                return this
            }

            camera = createCamera()
            camera!!.setPreviewDisplay(surfaceHolder)
            camera!!.startPreview()

            processingThread = Thread(frameProcessor)
            frameProcessor!!.setActive(true)
            processingThread!!.start()
        }
        return this
    }

    fun stop() {
        synchronized(cameraLock) {
            frameProcessor!!.setActive(false)
            if (processingThread != null) {
                try {
                    processingThread!!.join()
                } catch (e: InterruptedException) {
                    Log.d(TAG, "Frame processing thread interrupted on release.")
                }

                processingThread = null
            }

            bytesToByteBuffer.clear()

            if (camera != null) {
                camera!!.stopPreview()
                camera!!.setPreviewCallbackWithBuffer(null)
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                        camera!!.setPreviewTexture(null)
                    } else {
                        camera!!.setPreviewDisplay(null)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to clear camera preview: $e")
                }

                camera!!.release()
                camera = null
            }
        }
    }

    fun doZoom(scale: Float): Int {
        synchronized(cameraLock) {
            if (camera == null) {
                return 0
            }
            var currentZoom = 0
            val maxZoom: Int
            val parameters = camera!!.parameters
            if (!parameters.isZoomSupported) {
                Log.w(TAG, "Zoom is not supported on this device")
                return currentZoom
            }
            maxZoom = parameters.getMaxZoom()

            currentZoom = parameters.getZoom() + 1
            val newZoom: Float
            newZoom = if (scale > 1) {
                currentZoom + scale * (maxZoom / 10)
            } else {
                currentZoom * scale
            }
            currentZoom = Math.round(newZoom) - 1
            if (currentZoom < 0) {
                currentZoom = 0
            } else if (currentZoom > maxZoom) {
                currentZoom = maxZoom
            }
            parameters.zoom = currentZoom
            camera!!.parameters = parameters
            return currentZoom
        }
    }

    fun takePicture(shutter: ShutterCallback, jpeg: PictureCallback) {
        synchronized(cameraLock) {
            if (camera != null) {
                val startCallback = PictureStartCallback()
                startCallback.delegate = shutter
                val doneCallback = PictureDoneCallback()
                doneCallback.delegate = jpeg
                camera!!.takePicture(startCallback, null, null, doneCallback)
            }
        }
    }

    fun setFocusMode(@FocusMode focusMode: String): Boolean {
        synchronized(cameraLock) {
            return camera?.takeIf { it.parameters.supportedFocusModes.contains(focusMode) }
                ?.let {
                    it.parameters = it.parameters.apply { setFocusMode(focusMode) }
                    this@CameraSource.focusMode = focusMode
                    true
                } ?: false
        }
    }

    fun setFlashMode(@FlashMode flashMode: String): Boolean {
        synchronized(cameraLock) {
            return camera?.takeIf { it.parameters.supportedFlashModes.contains(flashMode) }
                ?.let {
                    it.parameters = it.parameters.apply { setFlashMode(flashMode) }
                    this@CameraSource.focusMode = focusMode
                    true
                } ?: false
        }
    }

    fun autoFocus(callback: AutoFocusCallback?) {
        synchronized(cameraLock) {
            camera?.apply {
                autoFocus(if (callback != null) CameraAutoFocusCallback().apply {
                    delegate = callback
                }
                else null)
            }
        }
    }

    fun cancelAutoFocus() {
        synchronized(cameraLock) {
            camera?.cancelAutoFocus()
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    fun setAutoFocusMoveCallback(callback: AutoFocusMoveCallback?): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            return false
        }

        synchronized(cameraLock) {
            camera?.apply {
                setAutoFocusMoveCallback(if (callback != null) CameraAutoFocusMoveCallback().apply {
                    delegate = callback
                }
                else null)
            }
        }

        return true
    }


    interface ShutterCallback {
        fun onShutter()
    }

    interface PictureCallback {
        fun onPictureTaken(data: ByteArray)
    }

    interface AutoFocusCallback {
        fun onAutoFocus(success: Boolean)
    }

    interface AutoFocusMoveCallback {
        fun onAutoFocusMoving(start: Boolean)
    }

    @StringDef(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE,
        Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO,
        Camera.Parameters.FOCUS_MODE_AUTO,
        Camera.Parameters.FOCUS_MODE_EDOF,
        Camera.Parameters.FOCUS_MODE_FIXED,
        Camera.Parameters.FOCUS_MODE_INFINITY,
        Camera.Parameters.FOCUS_MODE_MACRO)
    @kotlin.annotation.Retention(AnnotationRetention.SOURCE)
    private annotation class FocusMode

    @StringDef(Camera.Parameters.FLASH_MODE_ON,
        Camera.Parameters.FLASH_MODE_OFF,
        Camera.Parameters.FLASH_MODE_AUTO,
        Camera.Parameters.FLASH_MODE_RED_EYE,
        Camera.Parameters.FLASH_MODE_TORCH)
    @Retention(AnnotationRetention.SOURCE)
    private annotation class FlashMode

    private inner class FrameProcessingRunnable internal constructor(private var detector: Detector<*>?) : Runnable {
        private val startTimeMillis = SystemClock.elapsedRealtime()

        private val lock = java.lang.Object()
        private var active = true

        private var pendingTimeMillis: Long = 0
        private var pendingFrameId = 0
        private var pendingFrameData: ByteBuffer? = null

        @SuppressLint("Assert")
        fun release() {

            //TODO: dirty hack
            if (processingThread != null)
                assert(processingThread!!.state == Thread.State.TERMINATED)

            detector!!.release()
            detector = null
        }

        fun setActive(active: Boolean) {
            synchronized(lock) {
                this.active = active
                lock.notifyAll()
            }
        }

        fun setNextFrame(data: ByteArray, camera: Camera) {
            synchronized(lock) {
                if (pendingFrameData != null) {
                    camera.addCallbackBuffer(pendingFrameData!!.array())
                    pendingFrameData = null
                }

                if (!bytesToByteBuffer.containsKey(data)) {
                    Log.d(TAG,
                        "Skipping frame.  Could not find ByteBuffer associated with the image " + "data from the camera.")
                    return
                }

                pendingTimeMillis = SystemClock.elapsedRealtime() - startTimeMillis
                pendingFrameId++
                pendingFrameData = bytesToByteBuffer.get(data)

                lock.notifyAll()
            }
        }

        override fun run() {
            var outputFrame: Frame? = null
            var data: ByteBuffer? = null

            while (true) {
                synchronized(lock) {
                    while (active && pendingFrameData == null) {
                        try {
                            lock.wait()
                        } catch (e: InterruptedException) {
                            Log.d(TAG, "Frame processing loop terminated.", e)
                            return
                        }

                    }

                    if (!active) {
                        return
                    }

                    outputFrame = Frame.Builder()
                        .setImageData(pendingFrameData!!, previewSize!!.width,
                            previewSize!!.height, ImageFormat.NV21)
                        .setId(pendingFrameId)
                        .setTimestampMillis(pendingTimeMillis)
                        .setRotation(rotation)
                        .build()

                    data = pendingFrameData!!
                    pendingFrameData = null
                }

                try {
                    detector!!.receiveFrame(outputFrame)
                } catch (t: Throwable) {
                    Log.e(TAG, "Exception thrown from receiver.", t)
                } finally {
                    camera!!.addCallbackBuffer(data!!.array())
                }
            }
        }
    }

    private class PictureStartCallback : Camera.ShutterCallback {
        var delegate: ShutterCallback? = null

        override fun onShutter() {
            delegate?.onShutter()
        }
    }

    private inner class PictureDoneCallback : Camera.PictureCallback {
        var delegate: PictureCallback? = null

        override fun onPictureTaken(data: ByteArray, camera: Camera) {
            delegate?.onPictureTaken(data)
            synchronized(cameraLock) {
                camera.startPreview()
            }
        }
    }

    private class CameraAutoFocusCallback : Camera.AutoFocusCallback {
        var delegate: AutoFocusCallback? = null

        override fun onAutoFocus(success: Boolean, camera: Camera) {
            delegate?.onAutoFocus(success)
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private class CameraAutoFocusMoveCallback : Camera.AutoFocusMoveCallback {
        var delegate: AutoFocusMoveCallback? = null

        override fun onAutoFocusMoving(start: Boolean, camera: Camera) {
            delegate?.onAutoFocusMoving(start)
        }
    }

    private data class SizePair(private val previewSize: android.hardware.Camera.Size,
                                private val pictureSize: android.hardware.Camera.Size?) {
        val preview: Size = Size(previewSize.width, previewSize.height)
        val picture: Size? = if (pictureSize != null) Size(pictureSize.width, pictureSize.height) else null
    }

    private inner class CameraPreviewCallback : Camera.PreviewCallback {
        override fun onPreviewFrame(data: ByteArray, camera: Camera) {
            frameProcessor!!.setNextFrame(data, camera)
        }
    }

    private fun getIdForRequestedCamera(facing: Int): Int {
        val cameraInfo = Camera.CameraInfo()
        for (i in 0 until Camera.getNumberOfCameras()) {
            Camera.getCameraInfo(i, cameraInfo)
            if (cameraInfo.facing == facing) {
                return i
            }
        }
        return -1
    }

    private fun selectPreviewFpsRange(camera: Camera, desiredPreviewFps: Float): IntArray? {
        val desiredPreviewFpsScaled = (desiredPreviewFps * 1000.0f).toInt()

        var selectedFpsRange: IntArray? = null
        var minDiff = Integer.MAX_VALUE
        val previewFpsRangeList = camera.parameters.supportedPreviewFpsRange
        for (range in previewFpsRangeList) {
            val deltaMin = desiredPreviewFpsScaled - range[Camera.Parameters.PREVIEW_FPS_MIN_INDEX]
            val deltaMax = desiredPreviewFpsScaled - range[Camera.Parameters.PREVIEW_FPS_MAX_INDEX]
            val diff = Math.abs(deltaMin) + Math.abs(deltaMax)
            if (diff < minDiff) {
                selectedFpsRange = range
                minDiff = diff
            }
        }
        return selectedFpsRange
    }

    private fun selectSizePair(camera: Camera, desiredWidth: Int, desiredHeight: Int): SizePair? {
        val validPreviewSizes = generateValidPreviewSizeList(camera)

        var selectedPair: SizePair? = null
        var minDiff = Integer.MAX_VALUE
        for (sizePair in validPreviewSizes) {
            val size = sizePair.preview
            val diff = Math.abs(size.width - desiredWidth) + Math.abs(size.height - desiredHeight)
            if (diff < minDiff) {
                selectedPair = sizePair
                minDiff = diff
            }
        }

        return selectedPair
    }

    private fun generateValidPreviewSizeList(camera: Camera): List<SizePair> {
        val parameters = camera.parameters
        val supportedPreviewSizes = parameters.supportedPreviewSizes
        val supportedPictureSizes = parameters.supportedPictureSizes
        val validPreviewSizes = ArrayList<SizePair>()
        for (previewSize in supportedPreviewSizes) {
            val previewAspectRatio = previewSize.width.toFloat() / previewSize.height.toFloat()

            for (pictureSize in supportedPictureSizes) {
                val pictureAspectRatio = pictureSize.width.toFloat() / pictureSize.height.toFloat()
                if (Math.abs(previewAspectRatio - pictureAspectRatio) < ASPECT_RATIO_TOLERANCE) {
                    validPreviewSizes.add(SizePair(previewSize, pictureSize))
                    break
                }
            }
        }

        if (validPreviewSizes.size == 0) {
            Log.w(TAG, "No preview sizes have a corresponding same-aspect-ratio picture size")
            for (previewSize in supportedPreviewSizes) {
                // The null picture size will let us know that we shouldn't set a picture size.
                validPreviewSizes.add(SizePair(previewSize, null))
            }
        }

        return validPreviewSizes
    }

    private fun setRotation(camera: Camera, parameters: Camera.Parameters, cameraId: Int) {
        val windowManager = context!!.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        var degrees = 0
        val rotation = windowManager.defaultDisplay.rotation
        when (rotation) {
            Surface.ROTATION_0 -> degrees = 0
            Surface.ROTATION_90 -> degrees = 90
            Surface.ROTATION_180 -> degrees = 180
            Surface.ROTATION_270 -> degrees = 270
            else -> Log.e(TAG, "Bad rotation value: $rotation")
        }

        val cameraInfo = Camera.CameraInfo()
        Camera.getCameraInfo(cameraId, cameraInfo)

        val angle: Int
        val displayAngle: Int
        if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            angle = (cameraInfo.orientation + degrees) % 360
            displayAngle = (360 - angle) % 360 // compensate for it being mirrored
        } else {  // back-facing
            angle = (cameraInfo.orientation - degrees + 360) % 360
            displayAngle = angle
        }

        this.rotation = angle / 90

        camera.setDisplayOrientation(displayAngle)
        parameters.setRotation(angle)
    }

    private fun createPreviewBuffer(previewSize: Size): ByteArray {
        val bitsPerPixel = ImageFormat.getBitsPerPixel(ImageFormat.NV21)
        val sizeInBits = (previewSize.height * previewSize.width * bitsPerPixel).toLong()
        val bufferSize = Math.ceil(sizeInBits / 8.0).toInt() + 1

        val byteArray = ByteArray(bufferSize)
        val buffer = ByteBuffer.wrap(byteArray)
        if (!buffer.hasArray() || !buffer.array()!!.contentEquals(byteArray)) {
            throw IllegalStateException("Failed to create valid buffer for camera source.")
        }

        bytesToByteBuffer[byteArray] = buffer
        return byteArray
    }

    @SuppressLint("InlinedApi")
    private fun createCamera(): Camera {
        val requestedCameraId = getIdForRequestedCamera(facing)
        if (requestedCameraId == -1) {
            throw RuntimeException("Could not find requested camera.")
        }
        val camera = Camera.open(requestedCameraId)

        val sizePair = selectSizePair(camera, requestedPreviewWidth, requestedPreviewHeight)
            ?: throw RuntimeException("Could not find suitable preview size.")
        previewSize = sizePair.preview

        val previewFpsRange = selectPreviewFpsRange(camera, requestedFps)
            ?: throw RuntimeException("Could not find suitable preview frames per second range.")

        val parameters = camera.parameters

        if (sizePair.picture != null) {
            parameters.setPictureSize(sizePair.picture.width, sizePair.picture.height)
        }

        parameters.setPreviewSize(previewSize!!.width, previewSize!!.height)
        parameters.setPreviewFpsRange(
            previewFpsRange[Camera.Parameters.PREVIEW_FPS_MIN_INDEX],
            previewFpsRange[Camera.Parameters.PREVIEW_FPS_MAX_INDEX])
        parameters.previewFormat = ImageFormat.NV21

        setRotation(camera, parameters, requestedCameraId)

        if (focusMode != null) {
            if (parameters.supportedFocusModes.contains(
                    focusMode)) {
                parameters.focusMode = focusMode
            } else {
                Log.i(TAG, "Camera focus mode: $focusMode is not supported on this device.")
            }
        }

        focusMode = parameters.focusMode

        if (flashMode != null) {
            if (parameters.supportedFlashModes != null) {
                if (parameters.supportedFlashModes.contains(
                        flashMode)) {
                    parameters.flashMode = flashMode
                } else {
                    Log.i(TAG, "Camera flash mode: $flashMode is not supported on this device.")
                }
            }
        }

        flashMode = parameters.flashMode

        camera.parameters = parameters

        camera.setPreviewCallbackWithBuffer(CameraPreviewCallback())
        camera.addCallbackBuffer(createPreviewBuffer(previewSize!!))
        camera.addCallbackBuffer(createPreviewBuffer(previewSize!!))
        camera.addCallbackBuffer(createPreviewBuffer(previewSize!!))
        camera.addCallbackBuffer(createPreviewBuffer(previewSize!!))

        return camera
    }
}