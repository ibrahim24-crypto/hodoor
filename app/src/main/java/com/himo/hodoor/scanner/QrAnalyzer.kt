package com.himo.hodoor.scanner

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.SystemClock
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

/** A QR code found in an image, with its frame in the target view/bitmap coordinates. */
data class QrDetection(
    val payload: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    /** Four corners when ML Kit reports them, so tilted QR frames stay aligned. */
    val corners: List<Pair<Float, Float>> = emptyList()
)

/** Scales a detection (including its corners) from one coordinate space to another. */
fun QrDetection.scaled(sx: Float, sy: Float): QrDetection = copy(
    left = (left * sx).toInt(),
    top = (top * sy).toInt(),
    right = (right * sx).toInt(),
    bottom = (bottom * sy).toInt(),
    corners = corners.map { it.first * sx to it.second * sy }
)

/** Size of the drawing surface, used to scale fallback boxes onto a photo. */
data class FrameInfo(val width: Int, val height: Int)

/** Matches a QR payload against imported Massar codes (exact, then substring). */
fun matchStudentCode(payload: String?, codes: List<String>): String? {
    val value = payload?.trim().takeUnless { it.isNullOrEmpty() } ?: return null
    if (value in codes) return value
    val lower = value.lowercase()
    return codes.firstOrNull { code -> code.isNotEmpty() && lower.contains(code.lowercase()) }
}

/** Loads a gallery image downscaled enough to stay safe on low-memory devices. */
fun loadGalleryBitmap(context: Context, uri: Uri, maxDimension: Int = 2400): Bitmap {
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

/**
 * Rotates a camera/gallery bitmap to the upright orientation and downscales it so the
 * longest side is at most [maxDimension] (2400 by default — small codes need detail).
 */
fun loadScaledBitmap(source: Bitmap, rotationDegrees: Int, maxDimension: Int = 2400): Bitmap {
    val rotated = rotateBitmap(source, rotationDegrees)
    val result = downscaleBitmap(rotated, maxDimension)
    if (rotated !== source && rotated !== result) rotated.recycle()
    return result
}

/** Rotate a camera bitmap upright without changing its resolution. */
fun rotateBitmap(source: Bitmap, rotationDegrees: Int): Bitmap {
    if (rotationDegrees % 360 == 0) return source
    val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
    return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
}

/** Downscale only when the longest side exceeds [maxDimension]. */
fun downscaleBitmap(source: Bitmap, maxDimension: Int = 2400): Bitmap {
    val longest = maxOf(source.width, source.height)
    if (longest <= maxDimension) return source
    val scale = maxDimension.toFloat() / longest
    return Bitmap.createScaledBitmap(
        source,
        (source.width * scale).toInt().coerceAtLeast(1),
        (source.height * scale).toInt().coerceAtLeast(1),
        true
    )
}

/** Reads every QR payload from a still image (kept for the simple gallery path). */
suspend fun decodeQrPayloads(context: Context, uri: Uri): List<String> = withContext(Dispatchers.IO) {
    val bitmap = loadGalleryBitmap(context, uri)
    try {
        detectAllQr(bitmap).map { it.payload }
    } finally {
        bitmap.recycle()
    }
}

/** True only for the same payload printed twice at virtually the same place. */
private fun samePrint(a: QrDetection, b: QrDetection): Boolean {
    val acx = (a.left + a.right) / 2f
    val acy = (a.top + a.bottom) / 2f
    val bcx = (b.left + b.right) / 2f
    val bcy = (b.top + b.bottom) / 2f
    val size = maxOf(
        a.right - a.left, a.bottom - a.top,
        b.right - b.left, b.bottom - b.top
    ).toFloat()
    val half = size / 2f
    val dx = acx - bcx
    val dy = acy - bcy
    return dx * dx + dy * dy < half * half
}

/**
 * Detects all QR codes in a still image with their boxes, in the bitmap's coordinate space.
 * There is deliberately no frame filter here — the whole picture is considered.
 */
suspend fun detectAllQr(bitmap: Bitmap): List<QrDetection> = withContext(Dispatchers.IO) {
    val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
    )
    try {
        val barcodes = Tasks.await(scanner.process(InputImage.fromBitmap(bitmap, 0)))
        val found = barcodes.mapNotNull { barcode ->
            val box = barcode.boundingBox ?: return@mapNotNull null
            val payload = barcode.rawValue?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val corners = barcode.cornerPoints?.mapNotNull { point ->
                point?.let { it.x.toFloat() to it.y.toFloat() }
            }.takeUnless { it.isNullOrEmpty() }
                ?: listOf(
                    box.left.toFloat() to box.top.toFloat(),
                    box.right.toFloat() to box.top.toFloat(),
                    box.right.toFloat() to box.bottom.toFloat(),
                    box.left.toFloat() to box.bottom.toFloat()
                )
            QrDetection(payload, box.left, box.top, box.right, box.bottom, corners)
        }
        val unique = mutableListOf<QrDetection>()
        for (detection in found) {
            if (unique.none { it.payload == detection.payload && samePrint(it, detection) }) {
                unique += detection
            }
        }
        unique
    } finally {
        scanner.close()
    }
}

/**
 * Detect on the downscaled display bitmap; if that finds fewer than two codes, retry
 * once on the original full-resolution capture and scale boxes back to display size.
 * The whole image is always used — there is no frame filter here.
 */
suspend fun detectAllQrWithRetry(
    displayBitmap: Bitmap,
    fullBitmap: Bitmap? = null
): List<QrDetection> {
    val first = detectAllQr(displayBitmap)
    if (first.size >= 2 || fullBitmap == null || fullBitmap === displayBitmap) return first
    val second = detectAllQr(fullBitmap)
    if (second.isEmpty()) return first
    val sx = displayBitmap.width.toFloat() / fullBitmap.width.coerceAtLeast(1)
    val sy = displayBitmap.height.toFloat() / fullBitmap.height.coerceAtLeast(1)
    val scaled = second.map { it.scaled(sx, sy) }
    val merged = mutableListOf<QrDetection>()
    for (detection in first + scaled) {
        if (merged.none { it.payload == detection.payload && samePrint(it, detection) }) {
            merged += detection
        }
    }
    return merged
}

/**
 * Maps an ML Kit box (already rotated by InputImage) back through the sensor to PreviewView
 * pixels. CameraX supplies both matrices, so this also respects the shared FILL_CENTER
 * ViewPort crop — the usual cause of boxes drifting away from the codes on screen.
 */
private fun viewMapping(imageProxy: ImageProxy, sensorToView: Matrix): Matrix? {
    val rotation = ((imageProxy.imageInfo.rotationDegrees % 360) + 360) % 360
    val width = imageProxy.width
    val height = imageProxy.height
    val rotated = rotation == 90 || rotation == 270
    val mlWidth = if (rotated) height else width
    val mlHeight = if (rotated) width else height
    val source = floatArrayOf(
        0f, 0f,
        mlWidth.toFloat(), 0f,
        mlWidth.toFloat(), mlHeight.toFloat(),
        0f, mlHeight.toFloat()
    )
    // Inverse of the clockwise rotation InputImage applied to the buffer.
    val destination = when (rotation) {
        0 -> floatArrayOf(
            0f, 0f,
            width.toFloat(), 0f,
            width.toFloat(), height.toFloat(),
            0f, height.toFloat()
        )
        90 -> floatArrayOf(
            0f, height.toFloat(),
            0f, 0f,
            width.toFloat(), 0f,
            width.toFloat(), height.toFloat()
        )
        180 -> floatArrayOf(
            width.toFloat(), height.toFloat(),
            0f, height.toFloat(),
            0f, 0f,
            width.toFloat(), 0f
        )
        270 -> floatArrayOf(
            width.toFloat(), 0f,
            width.toFloat(), height.toFloat(),
            0f, height.toFloat(),
            0f, 0f
        )
        else -> return null
    }
    val mlToBuffer = Matrix()
    if (!mlToBuffer.setPolyToPoly(source, 0, destination, 0, 4)) return null
    val bufferToSensor = Matrix()
    if (!imageProxy.imageInfo.sensorToBufferTransformMatrix.invert(bufferToSensor)) return null
    val mlToSensor = Matrix().apply { setConcat(bufferToSensor, mlToBuffer) }
    return Matrix().apply { setConcat(sensorToView, mlToSensor) }
}

private fun mapDetection(
    payload: String,
    box: android.graphics.Rect,
    cornerPoints: Array<android.graphics.Point?>?,
    matrix: Matrix,
    viewWidth: Int,
    viewHeight: Int
): QrDetection {
    val rectPoints = floatArrayOf(
        box.left.toFloat(), box.top.toFloat(),
        box.right.toFloat(), box.top.toFloat(),
        box.right.toFloat(), box.bottom.toFloat(),
        box.left.toFloat(), box.bottom.toFloat()
    )
    matrix.mapPoints(rectPoints)
    val xs = listOf(rectPoints[0], rectPoints[2], rectPoints[4], rectPoints[6])
    val ys = listOf(rectPoints[1], rectPoints[3], rectPoints[5], rectPoints[7])
    val left = minOf(xs[0], xs[1], xs[2], xs[3]).coerceAtLeast(0f).toInt()
    val top = minOf(ys[0], ys[1], ys[2], ys[3]).coerceAtLeast(0f).toInt()
    val right = maxOf(xs[0], xs[1], xs[2], xs[3]).coerceAtMost(viewWidth.toFloat()).toInt()
        .coerceAtLeast(left)
    val bottom = maxOf(ys[0], ys[1], ys[2], ys[3]).coerceAtMost(viewHeight.toFloat()).toInt()
        .coerceAtLeast(top)

    fun mapPoint(x: Int, y: Int): Pair<Float, Float> {
        val point = floatArrayOf(x.toFloat(), y.toFloat())
        matrix.mapPoints(point)
        return point[0] to point[1]
    }
    val corners = cornerPoints?.mapNotNull { point -> point?.let { mapPoint(it.x, it.y) } }
        .takeUnless { it.isNullOrEmpty() }
        ?: listOf(
            rectPoints[0] to rectPoints[1],
            rectPoints[2] to rectPoints[3],
            rectPoints[4] to rectPoints[5],
            rectPoints[6] to rectPoints[7]
        )
    return QrDetection(payload, left, top, right, bottom, corners)
}

/**
 * Detects QR codes in camera frames. Boxes are transformed into PreviewView pixels;
 * Single mode additionally applies the centred scan-frame filter.
 */
class QrAnalyzer(
    private val multiMode: Boolean,
    private val onResult: ((FrameInfo, List<QrDetection>) -> Unit)? = null,
    private val onFrame: ((Bitmap, Int) -> Unit)? = null,
    private val previewSize: () -> Pair<Int, Int> = { 0 to 0 },
    private val previewTransform: () -> Matrix? = { null }
) : ImageAnalysis.Analyzer {
    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
    )
    private var lastFrameMs = 0L

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        // ML Kit reports boxes in the upright (rotated) image coordinate space.
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
        val (rawWidth, rawHeight) = previewSize()
        val viewWidth = rawWidth.takeIf { it > 0 } ?: uprightWidth
        val viewHeight = rawHeight.takeIf { it > 0 } ?: uprightHeight
        val transform = previewTransform()
        val mapping = if (transform != null && rawWidth > 0 && rawHeight > 0) {
            viewMapping(imageProxy, transform)
        } else {
            null
        } ?: Matrix().apply {
            setScale(viewWidth.toFloat() / uprightWidth, viewHeight.toFloat() / uprightHeight)
        }
        val frameInfo = FrameInfo(viewWidth, viewHeight)

        val image = InputImage.fromMediaImage(mediaImage, rotation)
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                val detections = barcodes.mapNotNull { barcode ->
                    val box = barcode.boundingBox ?: return@mapNotNull null
                    val payload = barcode.rawValue?.trim()?.takeIf { it.isNotEmpty() }
                        ?: return@mapNotNull null
                    mapDetection(payload, box, barcode.cornerPoints, mapping, viewWidth, viewHeight)
                }

                val visible = if (multiMode) {
                    detections
                } else {
                    val side = minOf(viewWidth, viewHeight) * SCAN_FRAME_FRACTION
                    val left = (viewWidth - side) / 2f
                    val top = (viewHeight - side) / 2f
                    val right = left + side
                    val bottom = top + side
                    detections.filter { d ->
                        val centerX = (d.left + d.right) / 2f
                        val centerY = (d.top + d.bottom) / 2f
                        centerX in left..right && centerY in top..bottom
                    }
                }.distinctBy { it.payload }

                // Keep an occasional upright frame as a fallback if ImageCapture is
                // unavailable. Multi mode needs a frame even with zero detections.
                val now = SystemClock.elapsedRealtime()
                if (onFrame != null && now - lastFrameMs >= 500L &&
                    (multiMode || visible.size >= 2)
                ) {
                    runCatching { imageProxy.toBitmap() }.getOrNull()?.let { frame ->
                        lastFrameMs = now
                        onFrame(frame, rotation)
                    }
                }

                // Always report boxes (for the live highlight) and let the screen decide:
                // Single mode may open a student; Multi mode only the shutter does.
                onResult?.invoke(frameInfo, visible)
            }
            .addOnCompleteListener { imageProxy.close() }
    }

    fun close() {
        scanner.close()
    }
}
