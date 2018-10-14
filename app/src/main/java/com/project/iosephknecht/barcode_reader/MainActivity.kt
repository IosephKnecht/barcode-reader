package com.project.iosephknecht.barcode_reader

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.samples.vision.barcodereader.R
import com.google.android.gms.vision.barcode.Barcode
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    private val RC_BARCODE_CAPTURE = 9001
    private val TAG = "BarcodeMain"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    override fun onStart() {
        super.onStart()

        read_barcode.setOnClickListener {
            val intent = Intent(this, BarcodeCaptureActivity::class.java).apply {
                putExtra(BarcodeCaptureActivity.AutoFocus, auto_focus.isChecked)
                putExtra(BarcodeCaptureActivity.UseFlash, use_flash.isChecked)
            }

            startActivityForResult(intent, RC_BARCODE_CAPTURE)
        }
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == RC_BARCODE_CAPTURE && resultCode == CommonStatusCodes.SUCCESS) {
            if (data != null) {
                val barcode = data.getParcelableExtra<Barcode>(BarcodeCaptureActivity.BarcodeObject)
                status_message.setText(R.string.barcode_success)
                barcode_value.text = barcode.displayValue
                Log.d(TAG, "Barcode read: ${barcode.displayValue}")
            } else {
                status_message.setText(R.string.barcode_failure)
                Log.d(TAG, "No barcode captured, intent data is null")
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }

    }
}