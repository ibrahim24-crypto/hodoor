package com.himo.hodoor.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.himo.hodoor.R
import com.himo.hodoor.data.repository.DataRepository
import com.himo.hodoor.scanner.FrameInfo
import com.himo.hodoor.scanner.QrDetection
import com.himo.hodoor.scanner.detectAllQr
import com.himo.hodoor.scanner.detectAllQrWithRetry
import com.himo.hodoor.scanner.loadGalleryBitmap
import com.himo.hodoor.scanner.matchStudentCode
import com.himo.hodoor.scanner.scaled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One labelled QR box in the still photo, with its matching student when known. */
data class SnapshotItem(
    val detection: QrDetection,
    val code: String?,
    val studentName: String?
)

/** A captured photo plus every QR box found in it, in bitmap coordinates. */
data class SnapshotUi(
    val bitmap: Bitmap,
    val items: List<SnapshotItem>
)

class ScannerViewModel(private val repository: DataRepository) : ViewModel() {
    private val _codes = MutableStateFlow<List<String>>(emptyList())
    val codes: StateFlow<List<String>> = _codes.asStateFlow()

    private val _matchedCode = MutableStateFlow<String?>(null)
    val matchedCode: StateFlow<String?> = _matchedCode.asStateFlow()

    private val _notFound = MutableStateFlow(false)
    val notFound: StateFlow<Boolean> = _notFound.asStateFlow()

    private val _notFoundMessage = MutableStateFlow<String?>(null)
    val notFoundMessage: StateFlow<String?> = _notFoundMessage.asStateFlow()

    private val _scanning = MutableStateFlow(true)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _snapshot = MutableStateFlow<SnapshotUi?>(null)
    val snapshot: StateFlow<SnapshotUi?> = _snapshot.asStateFlow()

    private val _imageError = MutableStateFlow<String?>(null)
    val imageError: StateFlow<String?> = _imageError.asStateFlow()

    init {
        viewModelScope.launch {
            _codes.value = withContext(Dispatchers.IO) { repository.allStudentCodes() }
        }
    }

    /** Live single-code flow: one known code inside the frame still navigates straight away. */
    fun onScanResult(payloads: List<String>) {
        if (payloads.isEmpty() || !_scanning.value || _matchedCode.value != null) return
        // Freeze the camera immediately so only this batch of codes is considered.
        _scanning.value = false

        viewModelScope.launch {
            val code = payloads.distinct().firstNotNullOfOrNull { matchStudentCode(it, _codes.value) }
            if (code == null) {
                _notFound.value = true
                return@launch
            }
            val student = withContext(Dispatchers.IO) { repository.studentsByCodes(listOf(code)) }
                .firstOrNull()
            if (student != null) {
                _matchedCode.value = code
                _notFound.value = false
            } else {
                _notFound.value = true
            }
        }
    }

    /** Camera capture (or fallback frame). forcePicker keeps multi/2+ flows in the picker. */
    fun onPhoto(
        bitmap: Bitmap,
        fullBitmap: Bitmap? = null,
        forcePicker: Boolean = false,
        fallbackDetections: List<QrDetection> = emptyList(),
        frameInfo: FrameInfo? = null,
        emptyMessage: String? = null
    ) {
        if (_matchedCode.value != null) return
        _scanning.value = false
        _notFound.value = false
        _notFoundMessage.value = null
        _imageError.value = null

        viewModelScope.launch {
            Log.d("HodoorScanner", "onPhoto ${bitmap.width}x${bitmap.height} forcePicker=$forcePicker")
            val detections = runCatching { detectAllQrWithRetry(bitmap, fullBitmap) }
                .getOrElse { emptyList() }
                .ifEmpty {
                    if (frameInfo == null || frameInfo.width <= 0 || frameInfo.height <= 0) {
                        fallbackDetections
                    } else {
                        val sx = bitmap.width.toFloat() / frameInfo.width
                        val sy = bitmap.height.toFloat() / frameInfo.height
                        fallbackDetections.map { it.scaled(sx, sy) }
                    }
                }
            Log.d("HodoorScanner", "onPhoto detections=${detections.size}")
            if (detections.isEmpty()) {
                _notFound.value = true
                _notFoundMessage.value = emptyMessage
                return@launch
            }
            showOrNavigate(snapshotFor(bitmap, detections), forcePicker)
        }
    }

    /** Capture/fallback failed before any bitmap could be decoded. */
    fun onCaptureFailed(message: String?) {
        _scanning.value = false
        _snapshot.value = null
        _notFound.value = true
        _notFoundMessage.value = message
    }

    /** Developer image mode: the gallery photo uses the exact same labelled picker. */
    fun scanImage(context: Context, uri: Uri) {
        viewModelScope.launch {
            _imageError.value = null
            _notFoundMessage.value = null
            _scanning.value = false
            val bitmap = runCatching { loadGalleryBitmap(context, uri) }
                .onFailure { _imageError.value = context.getString(R.string.scan_image_error) }
                .getOrNull()
                ?: return@launch
            val detections = runCatching { detectAllQr(bitmap) }.getOrElse { emptyList() }
            if (detections.isEmpty()) {
                _imageError.value = context.getString(R.string.scan_image_empty)
                return@launch
            }
            showOrNavigate(snapshotFor(bitmap, detections))
        }
    }

    private suspend fun snapshotFor(bitmap: Bitmap, detections: List<QrDetection>): SnapshotUi {
        val matches = detections.map { it to matchStudentCode(it.payload, _codes.value) }
        val matchedCodes = matches.mapNotNull { it.second }.distinct()
        val students = if (matchedCodes.isEmpty()) {
            emptyList()
        } else {
            withContext(Dispatchers.IO) { repository.studentsByCodes(matchedCodes) }
        }.associateBy { it.code }
        return SnapshotUi(
            bitmap = bitmap,
            items = matches.map { (detection, code) ->
                SnapshotItem(
                    detection = detection,
                    code = code,
                    studentName = code?.let { students[it]?.fullName }
                )
            }
        )
    }

    private fun showOrNavigate(snapshot: SnapshotUi, forcePicker: Boolean = false) {
        if (!forcePicker && snapshot.items.size == 1) {
            val only = snapshot.items.first()
            if (only.code != null && only.studentName != null) {
                _matchedCode.value = only.code
                _notFound.value = false
            } else {
                _notFound.value = true
            }
            return
        }
        _snapshot.value = snapshot
        _notFound.value = false
        _notFoundMessage.value = null
    }

    /** User tapped one of the labelled boxes in the photo. */
    fun onPickSnapshotItem(item: SnapshotItem) {
        _snapshot.value = null
        _notFoundMessage.value = null
        if (item.code != null && item.studentName != null) {
            _matchedCode.value = item.code
            _notFound.value = false
        } else {
            _notFound.value = true
        }
    }

    /** Close or rescan: drop the photo and start the camera again. */
    fun closeSnapshot() {
        _snapshot.value = null
        retry()
    }

    fun retry() {
        _notFound.value = false
        _notFoundMessage.value = null
        _imageError.value = null
        _snapshot.value = null
        _matchedCode.value = null
        _scanning.value = true
    }
}
