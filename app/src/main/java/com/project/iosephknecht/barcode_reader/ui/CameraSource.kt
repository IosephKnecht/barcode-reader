//package com.project.iosephknecht.barcode_reader.ui
//
//import android.content.Context
//import android.graphics.ImageFormat
//import android.hardware.Camera
//import android.os.SystemClock
//import android.support.annotation.StringDef
//import android.util.Log
//import android.view.SurfaceView
//import com.google.android.gms.common.images.Size
//import com.google.android.gms.vision.Detector
//import com.google.android.gms.vision.Frame
//import java.nio.ByteBuffer
//import java.util.concurrent.locks.ReentrantLock
//
//
//class CameraSource {
//    companion object {
//        val CAMERA_FACING_BACK = Camera.CameraInfo.CAMERA_FACING_BACK
//        val CAMERA_FACING_FRONT = Camera.CameraInfo.CAMERA_FACING_FRONT
//    }
//
//    private val TAG = "OpenCameraSource"
//    private val DUMMY_TEXTURE_NAME = 100
//    private val ASPECT_RATIO_TOLERANCE = 0.01f
//
//    private var context: Context? = null
//
//    private val cameraLock = ReentrantLock()
//
//    private val camera: Camera? = null
//
//    private var facing = CAMERA_FACING_BACK
//
//    private val rotation = 0
//
//    private val previewSize: Size? = null
//
//    private var requestedFps = 30f
//    private var requestedPreviewWidth = 1024
//    private var requestedPreviewHeight = 768
//
//    private var focusMode: String? = null
//    private var flashMode: String? = null
//
//    private val dummySurfaceView: SurfaceView? = null
//    private val dummySurfaceTexture: SurfaceView? = null
//
//    private val processingThread: Thread? = null
//    private val frameProcessor: FrameProcessingRunnable? = null
//
//    private val bytesToByteBuffer = mutableMapOf<ByteArray, ByteBuffer>()
//
//    fun release() {
//        cameraLock.lock()
//        stop()
//        frameProcessor!!.release()
//        cameraLock.unlock()
//    }
//
//    fun start
//
//    class Builder(private val context: Context,
//                  private val detector: Detector<*>) {
//
//        private val cameraSource = CameraSource().apply {
//            context = this@Builder.context
//        }
//
//        fun setRequestedFps(fps: Float): Builder {
//            cameraSource.requestedFps = fps
//            return this
//        }
//
//        fun setFocusMode(@FocusMode mode: String): Builder {
//            cameraSource.focusMode = mode
//            return this
//        }
//
//        fun setFlashMode(@FlashMode mode: String): Builder {
//            cameraSource.flashMode = mode
//            return this
//        }
//
//        fun setRequestedPreviewSize(width: Int, height: Int): Builder {
//            val maxValue = 1_000_000
//
//            if (width <= 0 || width > maxValue || height <= 0 || height > maxValue) {
//                throw IllegalStateException("Not correct size width = $width height = $height")
//            }
//
//            cameraSource.requestedPreviewWidth = width
//            cameraSource.requestedPreviewHeight = height
//
//            return this
//        }
//
//        fun setFacing(facing: Int): Builder {
//            if (facing != CAMERA_FACING_BACK && facing != CAMERA_FACING_BACK) {
//                throw  IllegalStateException("Invalid camera = $facing")
//            }
//            cameraSource.facing = facing
//            return this
//        }
//
//        fun build(): CameraSource {
//            cameraSource.frameProcessor =
//                return cameraSource
//        }
//
//    }
//
//    private inner class FrameProcessingRunnable(private var detector: Detector<*>?) : Runnable {
//        private val startTimeMillis = SystemClock.elapsedRealtime()
//        private val lock = ReentrantLock()
//        private var active = true
//
//        private var pendingTimeMillis: Long? = null
//        private var pendingFrameId = 0
//        private var pendingFrameData: ByteBuffer? = null
//
//        fun release() {
//            assert(processingThread!!.state == Thread.State.TERMINATED)
//            detector!!.release()
//            detector = null
//        }
//
//        fun setActive(active: Boolean) {
//            val condition = lock.newCondition()
//            lock.lock()
//            try {
//                this.active = active
//                condition.signalAll()
//            } finally {
//                lock.unlock()
//            }
//        }
//
//        fun setNextFrame(data: ByteArray, camera: Camera) {
//            lock.lock()
//            val condition = lock.newCondition()
//            try {
//                if (pendingFrameData != null) {
//                    camera.addCallbackBuffer(pendingFrameData!!.array())
//                    pendingFrameData = null
//                }
//
//                if (!bytesToByteBuffer.containsKey(data)) {
//                    Log.d(TAG, "Skipping frame.  Could not find ByteBuffer associated with the image " +
//                        "data from the camera.")
//                    return
//                }
//
//                pendingTimeMillis = SystemClock.elapsedRealtime() - startTimeMillis
//                pendingFrameId++
//                pendingFrameData = bytesToByteBuffer[data]
//
//                condition.signalAll()
//            } finally {
//                lock.unlock()
//            }
//        }
//
//        override fun run() {
//            var outputFrame: Frame? = null
//            var data: ByteBuffer? = null
//
//            while (true) {
//                lock.lock()
//                val condition = lock.newCondition()
//
//                while (active && (pendingFrameData == null)) {
//                    try {
//                        condition.await()
//                    } catch (e: InterruptedException) {
//                        Log.d(TAG, "Frame processing loop terminated.", e)
//                        return
//                    } finally {
//                        lock.unlock()
//                    }
//                }
//
//                if (!active) {
//                    return
//                }
//
//                outputFrame = Frame.Builder()
//                    .setImageData(pendingFrameData,
//                        previewSize!!.width,
//                        previewSize!!.height,
//                        ImageFormat.NV21)
//                    .setId(pendingFrameId)
//                    .setTimestampMillis(pendingTimeMillis!!)
//                    .setRotation(rotation)
//                    .build()
//
//                data = pendingFrameData
//                pendingFrameData = null
//            }
//
//            try {
//                detector!!.receiveFrame(outputFrame)
//            } catch (e: Exception) {
//                Log.e(TAG, "Exception thrown from receive.", e)
//            } finally {
//                lock.unlock()
//                camera!!.addCallbackBuffer(data!!.array())
//            }
//        }
//
//    }
//
//    @StringDef(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE,
//        Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO,
//        Camera.Parameters.FOCUS_MODE_AUTO,
//        Camera.Parameters.FOCUS_MODE_EDOF,
//        Camera.Parameters.FOCUS_MODE_FIXED,
//        Camera.Parameters.FOCUS_MODE_INFINITY,
//        Camera.Parameters.FOCUS_MODE_MACRO)
//    @Retention(AnnotationRetention.SOURCE)
//    private annotation class FocusMode
//
//    @StringDef(Camera.Parameters.FLASH_MODE_ON,
//        Camera.Parameters.FLASH_MODE_OFF,
//        Camera.Parameters.FLASH_MODE_AUTO,
//        Camera.Parameters.FLASH_MODE_RED_EYE,
//        Camera.Parameters.FLASH_MODE_TORCH)
//    @Retention(AnnotationRetention.SOURCE)
//    private annotation class FlashMode
//
//    interface ShutterCallback {
//        fun onShutter()
//    }
//
//    interface PictureCallback {
//        fun onPictureTaken(data: ByteArray)
//    }
//
//    interface AutoFocusCallback {
//        fun onAutoFocus(success: Boolean)
//    }
//
//    interface AutoFocusMoveCallback {
//        fun onAutoFocusMoving(start: Boolean)
//    }
//
//
//}