package com.project.iosephknecht.barcode_reader

import android.Manifest
import android.annotation.SuppressLint
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Camera
import android.os.Build
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v4.app.ActivityCompat
import android.support.v4.content.PermissionChecker
import android.support.v4.content.PermissionChecker.PERMISSION_GRANTED
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.Toast
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.samples.vision.barcodereader.R
import com.google.android.gms.vision.MultiProcessor
import com.google.android.gms.vision.barcode.Barcode
import com.google.android.gms.vision.barcode.BarcodeDetector
import com.project.iosephknecht.barcode_reader.ui.CameraSource
import com.project.iosephknecht.barcode_reader.ui.CameraSourcePreview
import com.project.iosephknecht.barcode_reader.ui.GraphicOverlay
import kotlinx.android.synthetic.main.barcode_capture.*
import java.io.IOException

class BarcodeCaptureActivity : AppCompatActivity(), BarcodeGraphicTracker.BarcodeUpdateListener {

    private val TAG = "Barcode-reader"

    private val RC_HANDLE_GMS = 9001
    private val RC_HANDLE_CAMERA_PERM = 2

    companion object {
        const val AutoFocus = "AutoFocus"
        const val UseFlash = "UseFlash"
        const val BarcodeObject = "Barcode"
    }

    private var cameraSource: CameraSource? = null
    private lateinit var preview: CameraSourcePreview
    private lateinit var graphicOverlay: GraphicOverlay<BarcodeGraphic>

    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private lateinit var gestureDetector: GestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.barcode_capture)

        preview = preview_view
        graphicOverlay = graphic_overlay as GraphicOverlay<BarcodeGraphic>

        val autoFocus = intent.getBooleanExtra(AutoFocus, false)
        val useFlash = intent.getBooleanExtra(UseFlash, false)

        val rc = PermissionChecker.checkSelfPermission(this, Manifest.permission.CAMERA)

        if (rc == PERMISSION_GRANTED) {
            createCameraSource(autoFocus, useFlash)
        } else {
            requestCameraPermission()
        }

        gestureDetector = GestureDetector(this, CaptureGestureListener())
        scaleGestureDetector = ScaleGestureDetector(this, ScaleListener())

        Snackbar.make(graphicOverlay, "Tap to capture. Pinch/Stretch to zoom",
            Snackbar.LENGTH_LONG)
            .show()
    }

    override fun onResume() {
        super.onResume()
        startCameraSource()
    }

    override fun onPause() {
        super.onPause()
        preview.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        preview.release()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode != RC_HANDLE_CAMERA_PERM) {
            Log.d(TAG, "Got unexpected permission result: $requestCode")
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
            return
        }

        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Camera permission granted - initialize the camera source")
            // we have permission, so create the camerasource
            val autoFocus = intent.getBooleanExtra(AutoFocus, false)
            val useFlash = intent.getBooleanExtra(UseFlash, false)
            createCameraSource(autoFocus, useFlash)
            return
        }

        Log.e(TAG, "Permission not granted: results len = " + grantResults.size +
            " Result code = " + if (grantResults.isNotEmpty()) grantResults[0] else "(empty)")

        val listener = DialogInterface.OnClickListener { dialog, id -> finish() }

        val builder = AlertDialog.Builder(this)
        builder.setTitle("Multitracker sample")
            .setMessage(R.string.no_camera_permission)
            .setPositiveButton(R.string.ok, listener)
            .show()
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        val b = scaleGestureDetector.onTouchEvent(event)
        val c = gestureDetector.onTouchEvent(event)
        return b || c || super.onTouchEvent(event)
    }

    @SuppressLint("InlinedApi")
    private fun createCameraSource(autoFocus: Boolean, useFlash: Boolean) {
        val barcodeDetector = BarcodeDetector.Builder(this).build()
        val barcodeFactory = BarcodeTrackerFactory(graphicOverlay!!, this)
        barcodeDetector.setProcessor(MultiProcessor.Builder<Barcode>(barcodeFactory).build())

        if (!barcodeDetector.isOperational) {
            Log.w(TAG, "Detector dependencies are not yet available.")

            val lowstorageFilter = IntentFilter(Intent.ACTION_DEVICE_STORAGE_LOW)
            val hasLowStorage = registerReceiver(null, lowstorageFilter) != null

            if (hasLowStorage) {
                Toast.makeText(this, R.string.low_storage_error, Toast.LENGTH_LONG).show()
                Log.w(TAG, getString(R.string.low_storage_error))
            }
        }

        var cameraSourceBuilder = CameraSource.Builder(this, barcodeDetector)
            .setFacing(CameraSource.CAMERA_FACING_BACK)
            .setRequestedPreviewSize(1024, 768)
            .setRequestedFps(15f)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            cameraSourceBuilder = cameraSourceBuilder.setFocusMode(
                if (autoFocus) Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE else null)
        }

        cameraSource = cameraSourceBuilder
            .setFlashMode(if (useFlash) Camera.Parameters.FLASH_MODE_TORCH else null)
            .build()
    }

    private fun requestCameraPermission() {
        Log.w(TAG, "Camera permission is not granted. Requesting permission")

        val permissions = arrayOf(Manifest.permission.CAMERA)

        if (!ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.CAMERA)) {
            ActivityCompat.requestPermissions(this, permissions, RC_HANDLE_CAMERA_PERM)
            return
        }

        val thisActivity = this

        val listener = View.OnClickListener {
            ActivityCompat.requestPermissions(thisActivity, permissions,
                RC_HANDLE_CAMERA_PERM)
        }

        findViewById<View>(R.id.topLayout).setOnClickListener(listener)
        Snackbar.make(graphicOverlay!!, R.string.permission_camera_rationale,
            Snackbar.LENGTH_INDEFINITE)
            .setAction(R.string.ok, listener)
            .show()
    }

    @Throws(SecurityException::class)
    private fun startCameraSource() {
        val code = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(
            applicationContext)
        if (code != ConnectionResult.SUCCESS) {
            val dlg = GoogleApiAvailability.getInstance().getErrorDialog(this, code, RC_HANDLE_GMS)
            dlg.show()
        }

        if (cameraSource != null) {
            try {
                preview.start(cameraSource!!, graphicOverlay)
            } catch (e: IOException) {
                Log.e(TAG, "Unable to start camera source.", e)
                cameraSource!!.release()
                cameraSource = null
            }

        }
    }

    private fun onTap(rawX: Float, rawY: Float): Boolean {
        val location = IntArray(2)
        graphicOverlay.getLocationOnScreen(location)
        val x = (rawX - location[0]) / graphicOverlay.widthScaleFactor
        val y = (rawY - location[1]) / graphicOverlay.heightScaleFactor

        var best: Barcode? = null
        var bestDistance = java.lang.Float.MAX_VALUE
        for (graphic in graphicOverlay.graphics) {
            val barcode = graphic.barcode
            if (barcode!!.boundingBox.contains(x.toInt(), y.toInt())) {
                best = barcode
                break
            }
            val dx = x - barcode.boundingBox.centerX()
            val dy = y - barcode.boundingBox.centerY()
            val distance = dx * dx + dy * dy  // actually squared distance
            if (distance < bestDistance) {
                best = barcode
                bestDistance = distance
            }
        }

        if (best != null) {
            val data = Intent()
            data.putExtra(BarcodeObject, best)
            setResult(RequestCode.SUCCESS, data)
            finish()
            return true
        }
        return false
    }

    override fun onBarcodeDetected(barcode: Barcode?) {
    }

    private inner class CaptureGestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapConfirmed(e: MotionEvent?): Boolean {
            return onTap(e!!.rawX, e.rawY) || super.onSingleTapConfirmed(e)
        }
    }

    private inner class ScaleListener : ScaleGestureDetector.OnScaleGestureListener {
        override fun onScaleBegin(detector: ScaleGestureDetector?) = true

        override fun onScaleEnd(detector: ScaleGestureDetector?) {
            cameraSource!!.doZoom(detector!!.scaleFactor)
        }

        override fun onScale(detector: ScaleGestureDetector?) = false

    }
}