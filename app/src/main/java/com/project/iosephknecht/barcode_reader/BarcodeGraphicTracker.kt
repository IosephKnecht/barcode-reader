package com.project.iosephknecht.barcode_reader

import android.support.annotation.UiThread
import com.google.android.gms.vision.Detector
import com.google.android.gms.vision.Tracker
import com.google.android.gms.vision.barcode.Barcode
import com.project.iosephknecht.barcode_reader.ui.GraphicOverlay

class BarcodeGraphicTracker(private val graphicOverlay: GraphicOverlay<BarcodeGraphic>,
                            private val barcodeGraphic: BarcodeGraphic,
                            private val listener: BarcodeUpdateListener) : Tracker<Barcode>() {

    override fun onNewItem(id: Int, barcode: Barcode?) {
        barcodeGraphic.id = id
        listener.onBarcodeDetected(barcode)
    }

    override fun onUpdate(detector: Detector.Detections<Barcode>?, barcode: Barcode?) {
        graphicOverlay.add(barcodeGraphic)
        barcodeGraphic.updateItem(barcode)
    }

    override fun onMissing(detector: Detector.Detections<Barcode>?) {
        graphicOverlay.remove(barcodeGraphic)
    }

    override fun onDone() {
        graphicOverlay.remove(barcodeGraphic)
    }

    public interface BarcodeUpdateListener {
        @UiThread
        fun onBarcodeDetected(barcode: Barcode?)
    }
}