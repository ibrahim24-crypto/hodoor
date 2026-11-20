package com.himo.hodoor.ui.screens

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.himo.hodoor.data.backup.csvLineToRecord
import com.himo.hodoor.data.backup.recordsToCsv
import com.himo.hodoor.data.entities.Cause
import com.himo.hodoor.data.repository.DataRepository
import com.himo.hodoor.ui.settings.LocaleHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(private val repository: DataRepository) : ViewModel() {
    val causes: StateFlow<List<Cause>> = repository.causes.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    fun addCause(label: String) {
        if (label.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) { repository.addCause(label) }
    }

    fun updateCause(cause: Cause, label: String) {
        if (label.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) { repository.updateCause(cause.copy(label = label.trim())) }
    }

    fun deleteCause(cause: Cause) {
        viewModelScope.launch(Dispatchers.IO) { repository.deleteCause(cause) }
    }

    suspend fun exportTo(context: Context, uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val records = repository.allRecords()
            val output = context.contentResolver.openOutputStream(uri)
                ?: throw IllegalStateException("Impossible d'écrire le fichier")
            output.use { it.write(recordsToCsv(records).toByteArray(Charsets.UTF_8)) }
            records.size
        }
    }

    suspend fun restoreFrom(context: Context, uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val knownCodes = repository.allStudentCodes().toHashSet()
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Impossible de lire le fichier")
            val records = input.bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.drop(1).mapNotNull(::csvLineToRecord)
                    .filter { it.studentCode in knownCodes }
                    .toList()
            }
            repository.addRecords(records)
            records.size
        }
    }

    fun setLanguage(context: Context, code: String) {
        LocaleHelper.setLanguage(context.applicationContext, code)
    }
}
