package com.example.moderntodo.util

import android.content.Context
import android.widget.Toast
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

object QrScannerHelper {

    fun startScan(
        context: Context,
        onSuccess: (String) -> Unit,
        onFailure: (Exception) -> Unit = {}
    ) {
        try {
            val options = GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .enableAutoZoom()
                .build()

            val scanner = GmsBarcodeScanning.getClient(context, options)
            scanner.startScan()
                .addOnSuccessListener { barcode ->
                    val rawValue = barcode.rawValue ?: ""
                    if (rawValue.isNotBlank()) {
                        onSuccess(rawValue)
                    } else {
                        Toast.makeText(context, "No QR data found", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnCanceledListener {
                    // User dismissed scanner
                }
                .addOnFailureListener { e ->
                    e.printStackTrace()
                    Toast.makeText(context, "Scan error: ${e.message}", Toast.LENGTH_SHORT).show()
                    onFailure(e)
                }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Scanner unavailable: ${e.message}", Toast.LENGTH_SHORT).show()
            onFailure(e)
        }
    }

    fun parseListId(input: String): Int? {
        val trimmed = input.trim()
        val listRegex = Regex("""list_(\d+)""")
        val match = listRegex.find(trimmed)
        if (match != null) {
            return match.groupValues[1].toIntOrNull()
        }
        val doitRegex = Regex("""DOIT(\d+)""", RegexOption.IGNORE_CASE)
        val matchDoit = doitRegex.find(trimmed)
        if (matchDoit != null) {
            return matchDoit.groupValues[1].toIntOrNull()
        }
        return trimmed.toIntOrNull()
    }
}
