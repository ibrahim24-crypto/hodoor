package com.himo.hodoor.ui.screens

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.himo.hodoor.data.excel.ColumnMapping
import com.himo.hodoor.data.excel.ExcelImporter
import com.himo.hodoor.data.excel.ParseOutcome
import com.himo.hodoor.data.excel.ParsedStudents
import com.himo.hodoor.data.entities.ImportReport
import com.himo.hodoor.data.entities.Student
import com.himo.hodoor.data.repository.DataRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ImportUiState(
    val uri: Uri? = null,
    val preview: List<List<String>> = emptyList(),
    val headers: List<String> = emptyList(),
    val needsMapping: Boolean = false,
    val mapping: ColumnMapping = ColumnMapping(),
    val students: List<Student> = emptyList(),
    val skippedRows: Int = 0,
    val progress: Pair<Int, Int>? = null,
    val report: ImportReport? = null,
    val error: String? = null,
    val busy: Boolean = false
)

class ImportViewModel(
    private val repository: DataRepository,
    private val context: Context
) : ViewModel() {
    private val _state = MutableStateFlow(ImportUiState())
    val state: StateFlow<ImportUiState> = _state.asStateFlow()

    fun onFilePicked(uri: Uri) = viewModelScope.launch {
        _state.update {
            it.copy(
                uri = uri,
                busy = true,
                error = null,
                report = null,
                progress = null,
                students = emptyList(),
                needsMapping = false
            )
        }
        when (val outcome = withContext(Dispatchers.IO) { ExcelImporter.parse(context, uri) }) {
            is ParseOutcome.Ready -> _state.update {
                it.copy(
                    busy = false,
                    students = outcome.data.students,
                    preview = outcome.data.preview,
                    skippedRows = outcome.data.skipped,
                    mapping = outcome.data.mapping
                )
            }

            is ParseOutcome.NeedMapping -> _state.update {
                it.copy(
                    busy = false,
                    needsMapping = true,
                    headers = outcome.headers,
                    preview = outcome.preview
                )
            }

            is ParseOutcome.Failed -> _state.update {
                it.copy(busy = false, error = outcome.message)
            }
        }
    }

    fun setMapping(mapping: ColumnMapping) = _state.update { it.copy(mapping = mapping) }

    fun import() {
        val uri = _state.value.uri ?: return
        _state.update { it.copy(busy = true, error = null, report = null, progress = 0 to 0) }
        viewModelScope.launch {
            val parsed: ParsedStudents
            when (val outcome = withContext(Dispatchers.IO) {
                ExcelImporter.parse(context, uri, _state.value.mapping)
            }) {
                is ParseOutcome.Ready -> parsed = outcome.data
                is ParseOutcome.NeedMapping -> {
                    _state.update { it.copy(busy = false, error = "Choisissez les colonnes") }
                    return@launch
                }

                is ParseOutcome.Failed -> {
                    _state.update { it.copy(busy = false, error = outcome.message) }
                    return@launch
                }
            }

            val total = parsed.students.size
            _state.update {
                it.copy(students = parsed.students, mapping = parsed.mapping, progress = 0 to total)
            }
            val report = withContext(Dispatchers.IO) {
                repository.importStudents(parsed.students) { done ->
                    _state.update { it.copy(progress = done.coerceAtMost(total) to total) }
                }
            }
            _state.update {
                it.copy(
                    busy = false,
                    progress = null,
                    report = report.copy(skipped = report.skipped + parsed.skipped),
                    preview = parsed.preview
                )
            }
        }
    }
}
