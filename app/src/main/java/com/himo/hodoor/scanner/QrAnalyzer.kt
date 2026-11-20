package com.himo.hodoor.scanner

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.ceil

/** Fraction of the shorter image / screen side used by the centred scan frame. */
const val SCAN_FRAME_FRACTION = 0.5f

/** Matches a QR payload against imported Massar codes (exact, then substring). */
fun matchStudentCode(payload: String?, codes: List<String>): String? {
    val value = payload?.trim().takeUnless { it.isNullOrEmpty() } ?: return null
    if (value in codes) return value
    val lower = value.lowercase()
    return codes.firstOrNull { code -> code.isNotEmpty() && lower.contains(code.lowercase()) }
}

/** Loads a gallery image downscaled enough to stay safe on low-memory devices. */
private fun loadScaledBitmap(context: Context, uri: Uri, maxDimension: Int = 1600): Bitmap {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri).use { stream ->
        BitmapFactory.decodeStream(stream, null, bounds)
    }
    val largest = maxOf(bounds.outWidth, bounds.outHeight)
    val sample = if (largest <= 0) 1 else maxOf(1, ceil(largest.toFloat() / maxDimension).toInt())
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return resolver.openInputStream(uri).use { stream ->
        BitmapFactory.decodeStream(stream, null, options)
    } ?: error("Could not decode the selected image")
}

/** Reads every QR payload from a still image (single code or a sheet of codes). */
suspend fun decodeQrPayloads(context: Context, uri: Uri): List<String> = withContext(Dispatchers.IO) {
    val bitmap = loadScaledBitmap(context, uri)
    val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
    )
    try {
        val barcodes = Tasks.await(scanner.process(InputImage.fromBitmap(bitmap, 0)))
        barcodes.mapNotNull { it.rawValue?.trim()?.takeIf { value -> value.isNotEmpty() } }
            .distinct()
    } finally {
        scanner.close()
        bitmap.recycle()
    }
}

/**
 * Detects every QR code whose centre lies inside the same centred square shown by the
 * scanner overlay, and reports their raw payloads (deduplicated, in detection order).
 */
class QrAnalyzer(
    private val onResult: (List<String>) -> Unit
) : ImageAnalysis.Analyzer {
    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
    )

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        // ML Kit reports bounding boxes in the upright (rotated) image coordinate space.
        val rotation = imageProxy.imageInfo.rotationDegrees
        val uprightWidth: Int
        val uprightHeight: Int
        if (rotation % 180 == 0) {
            uprightWidth = imageProxy.width
            uprightHeight = imageProxy.height
        } else {
            uprightWidth = imageProxy.height
            uprightHeight = imageProxy.width
        }

        val image = InputImage.fromMediaImage(mediaImage, rotation)
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                val side = minOf(uprightWidth, uprightHeight) * SCAN_FRAME_FRACTION
                val left = (uprightWidth - side) / 2f
                val top = (uprightHeight - side) / 2f
                val right = left + side
                val bottom = top + side

                val payloads = barcodes.mapNotNull { barcode ->
                    val box = barcode.boundingBox ?: return@mapNotNull null
                    val centerX = (box.left + box.right) / 2f
                    val centerY = (box.top + box.bottom) / 2f
                    if (centerX < left || centerX > right || centerY < top || centerY > bottom) {
                        return@mapNotNull null
                    }
                    barcode.rawValue?.trim()?.takeIf { it.isNotEmpty() }
                }.distinct()

                if (payloads.isNotEmpty()) onResult(payloads)
            }
            .addOnCompleteListener { imageProxy.close() }
    }

    fun close() {
        scanner.close()
    }
}
