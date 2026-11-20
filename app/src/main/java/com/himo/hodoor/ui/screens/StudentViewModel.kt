package com.himo.hodoor.ui.screens

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.himo.hodoor.data.entities.Cause
import com.himo.hodoor.data.entities.CauseCount
import com.himo.hodoor.data.entities.Record
import com.himo.hodoor.data.entities.RecordType
import com.himo.hodoor.data.entities.Student
import com.himo.hodoor.data.entities.TypeCount
import com.himo.hodoor.data.repository.DataRepository
import com.himo.hodoor.ui.navigation.Route
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.TimeUnit

enum class HistoryFilter { ALL, LATE, ABSENT }

data class RecordEditorState(val open: Boolean = false, val record: Record? = null)

class StudentViewModel(
    private val repository: DataRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    val studentCode: String = savedStateHandle.get<String>(Route.ArgStudentCode).orEmpty()

    val student: StateFlow<Student?> = repository.student(studentCode).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null
    )

    private val _filter = MutableStateFlow(HistoryFilter.ALL)
    val filter: StateFlow<HistoryFilter> = _filter

    val history: StateFlow<List<Record>> = combine(repository.history(studentCode), _filter) { records, filter ->
        when (filter) {
            HistoryFilter.ALL -> records
            HistoryFilter.LATE -> records.filter { it.type == RecordType.LATE }
            HistoryFilter.ABSENT -> records.filter { it.type == RecordType.ABSENT }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val typeCounts: StateFlow<List<TypeCount>> = repository.typeCounts(studentCode).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val topCause: StateFlow<CauseCount?> = repository.topCause(studentCode).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null
    )

    val causes: StateFlow<List<Cause>> = repository.causes.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    private val _editor = MutableStateFlow(RecordEditorState())
    val editor: StateFlow<RecordEditorState> = _editor

    fun setFilter(value: HistoryFilter) {
        _filter.value = value
    }

    fun startAdd() {
        _editor.value = RecordEditorState(open = true)
    }

    fun startEdit(record: Record) {
        _editor.value = RecordEditorState(open = true, record = record)
    }

    fun closeEditor() {
        _editor.value = RecordEditorState()
    }

    fun saveRecord(type: RecordType, date: Long, time: String?, cause: String, note: String?) {
        val existing = _editor.value.record
        val record = existing?.copy(
            type = type,
            date = date,
            time = time?.takeIf { it.isNotBlank() },
            cause = cause.trim(),
            note = note?.takeIf { it.isNotBlank() }
        ) ?: Record(
            studentCode = studentCode,
            type = type,
            date = date,
            time = time?.takeIf { it.isNotBlank() },
            cause = cause.trim(),
            note = note?.takeIf { it.isNotBlank() }
        )
        viewModelScope.launch {
            if (existing == null) repository.addRecord(record) else repository.updateRecord(record)
        }
        closeEditor()
    }

    fun deleteRecord(record: Record) {
        viewModelScope.launch { repository.deleteRecord(record) }
    }

    companion object {
        fun todayEpochDay(): Long {
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            return TimeUnit.MILLISECONDS.toDays(calendar.timeInMillis)
        }
    }
}
