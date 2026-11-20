package com.himo.hodoor.ui.screens

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.himo.hodoor.R
import com.himo.hodoor.data.repository.DataRepository
import com.himo.hodoor.scanner.decodeQrPayloads
import com.himo.hodoor.scanner.matchStudentCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One QR found inside the frame, with the matching student's name when known. */
data class ScanCandidate(
    val code: String,
    val studentName: String?,
    val rawPayload: String
)

class ScannerViewModel(private val repository: DataRepository) : ViewModel() {
    private val _codes = MutableStateFlow<List<String>>(emptyList())
    val codes: StateFlow<List<String>> = _codes.asStateFlow()

    private val _matchedCode = MutableStateFlow<String?>(null)
    val matchedCode: StateFlow<String?> = _matchedCode.asStateFlow()

    private val _notFound = MutableStateFlow(false)
    val notFound: StateFlow<Boolean> = _notFound.asStateFlow()

    private val _scanning = MutableStateFlow(true)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _detected = MutableStateFlow<List<ScanCandidate>>(emptyList())
    val detected: StateFlow<List<ScanCandidate>> = _detected.asStateFlow()

    private val _imageError = MutableStateFlow<String?>(null)
    val imageError: StateFlow<String?> = _imageError.asStateFlow()

    init {
        viewModelScope.launch {
            _codes.value = withContext(Dispatchers.IO) { repository.allStudentCodes() }
        }
    }

    /** Called with every raw QR payload found inside the scan frame. */
    fun onScanResult(payloads: List<String>) {
        if (payloads.isEmpty() || !_scanning.value || _matchedCode.value != null) return
        // Freeze the camera immediately so only this batch of codes is considered.
        _scanning.value = false

        viewModelScope.launch {
            val matches = payloads.distinct().map { it to matchStudentCode(it, _codes.value) }
            val matchedCodes = matches.mapNotNull { it.second }.distinct()
            val students = withContext(Dispatchers.IO) { repository.studentsByCodes(matchedCodes) }
                .associateBy { it.code }
            val candidates = matches.map { (payload, code) ->
                if (code == null) {
                    ScanCandidate(code = payload, studentName = null, rawPayload = payload)
                } else {
                    ScanCandidate(code = code, studentName = students[code]?.fullName, rawPayload = payload)
                }
            }

            val known = candidates.filter { it.studentName != null }
            when {
                candidates.size == 1 && known.size == 1 -> {
                    _matchedCode.value = known.first().code
                    _notFound.value = false
                }

                candidates.size == 1 && known.isEmpty() -> {
                    _notFound.value = true
                }

                else -> {
                    _detected.value = candidates
                    _notFound.value = false
                }
            }
        }
    }

    /** Developer mode: decode a gallery image and reuse the normal scan result flow. */
    fun scanImage(context: Context, uri: Uri) {
        viewModelScope.launch {
            _imageError.value = null
            val payloads = runCatching { decodeQrPayloads(context, uri) }
                .onFailure { _imageError.value = context.getString(R.string.scan_image_error) }
                .getOrNull()
            if (payloads == null) return@launch
            if (payloads.isEmpty()) {
                _imageError.value = context.getString(R.string.scan_image_empty)
            } else {
                onScanResult(payloads)
            }
        }
    }

    /** User chose one of several detected codes. */
    fun pickCandidate(candidate: ScanCandidate) {
        if (candidate.studentName != null) {
            _detected.value = emptyList()
            _matchedCode.value = candidate.code
        } else {
            _detected.value = emptyList()
            _notFound.value = true
        }
    }

    fun retry() {
        _notFound.value = false
        _imageError.value = null
        _detected.value = emptyList()
        _matchedCode.value = null
        _scanning.value = true
    }
}
