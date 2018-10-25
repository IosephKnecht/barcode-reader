package com.project.iosephknecht.barcode_reader

import com.google.android.gms.vision.MultiProcessor
import com.google.android.gms.vision.Tracker
import com.google.android.gms.vision.barcode.Barcode
import com.project.iosephknecht.barcode_reader.ui.GraphicOverlay

class BarcodeTrackerFactory(private val graphicOverlay: GraphicOverlay<BarcodeGraphic>,
                            private val listener: BarcodeGraphicTracker.BarcodeUpdateListener) : MultiProcessor.Factory<Barcode> {
    override fun create(barcode: Barcode?): Tracker<Barcode> {
        val graphic = BarcodeGraphic(graphicOverlay)
        return BarcodeGraphicTracker(graphicOverlay, graphic, listener)
    }
}