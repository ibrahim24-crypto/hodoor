package com.himo.hodoor.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.himo.hodoor.R
import com.himo.hodoor.data.entities.Cause
import com.himo.hodoor.data.entities.Record
import com.himo.hodoor.data.entities.RecordType
import com.himo.hodoor.ui.settings.AppSettings
import com.himo.hodoor.ui.settings.civilDateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

private const val TIME_RANGE_SEPARATOR = " - "
private const val TIME_HINT = "HH:MM"

private enum class TimeMode { AT, RANGE }

/** Formats typed digits as HH:MM, e.g. 1400 -> 14:00. */
private fun formatTimeInput(raw: String): String {
    val digits = raw.filter { it.isDigit() }.take(4)
    if (digits.isEmpty()) return ""
    if (raw.contains(':') && raw.length <= 5 && raw.all { it.isDigit() || it == ':' }) return raw
    return if (digits.length <= 2) digits else "${digits.take(2)}:${digits.drop(2)}"
}

/** Normalizes both AT and RANGE values, including values saved before auto-formatting. */
private fun normalizeStoredTime(stored: String): String =
    stored.split(TIME_RANGE_SEPARATOR, limit = 2)
        .joinToString(TIME_RANGE_SEPARATOR) { formatTimeInput(it.trim()) }

/** Splits a stored time into its editing mode and values. */
private fun splitStoredTime(stored: String?): Pair<TimeMode, Pair<String, String>> {
    if (stored.isNullOrBlank()) return TimeMode.AT to ("" to "")
    val parts = normalizeStoredTime(stored).split(TIME_RANGE_SEPARATOR, limit = 2)
    return if (parts.size == 2) TimeMode.RANGE to (parts[0] to parts[1])
    else TimeMode.AT to (parts[0] to "")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordEditorDialog(
    record: Record?,
    causes: List<Cause>,
    onDismiss: () -> Unit,
    onSave: (RecordType, Long, String?, String, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val dateFormat = remember { civilDateFormat() }
    val today = remember { StudentViewModel.todayEpochDay() }
    val initialTime = remember(record) { splitStoredTime(record?.time) }

    var type by remember(record) { mutableStateOf(record?.type ?: RecordType.LATE) }
    var dateText by remember(record) {
        val lastPicked = AppSettings.lastRecordDate(context).takeIf { it >= 0 }
        mutableStateOf(dateFormat.format(Date((record?.date ?: lastPicked ?: today) * 86_400_000L)))
    }
    var timeMode by remember(record) { mutableStateOf(initialTime.first) }
    var timeAt by remember(record) {
        mutableStateOf(if (initialTime.first == TimeMode.AT) initialTime.second.first else "")
    }
    var timeFrom by remember(record) {
        mutableStateOf(if (initialTime.first == TimeMode.RANGE) initialTime.second.first else "")
    }
    var timeTo by remember(record) {
        mutableStateOf(if (initialTime.first == TimeMode.RANGE) initialTime.second.second else "")
    }
    var cause by remember(record) { mutableStateOf(record?.cause.orEmpty()) }
    var note by remember(record) { mutableStateOf(record?.note.orEmpty()) }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (record == null) R.string.student_add_record else R.string.student_edit_record
                )
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row {
                    FilterChip(
                        selected = type == RecordType.LATE,
                        onClick = { type = RecordType.LATE },
                        label = { Text(stringResource(R.string.record_type_late)) }
                    )
                    FilterChip(
                        selected = type == RecordType.ABSENT,
                        onClick = { type = RecordType.ABSENT },
                        label = { Text(stringResource(R.string.record_type_absent)) }
                    )
                }

                OutlinedTextField(
                    value = dateText,
                    onValueChange = { dateText = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.record_date)) },
                    placeholder = { Text("dd/MM/yyyy") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = timeMode == TimeMode.AT,
                        onClick = { timeMode = TimeMode.AT },
                        label = { Text(stringResource(R.string.record_time_at)) }
                    )
                    FilterChip(
                        selected = timeMode == TimeMode.RANGE,
                        onClick = { timeMode = TimeMode.RANGE },
                        label = { Text(stringResource(R.string.record_time_range)) }
                    )
                }

                if (timeMode == TimeMode.AT) {
                    OutlinedTextField(
                        value = timeAt,
                        onValueChange = { timeAt = formatTimeInput(it) },
                        singleLine = true,
                        label = { Text(stringResource(R.string.student_time)) },
                        placeholder = { Text(TIME_HINT) },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = timeFrom,
                            onValueChange = { timeFrom = formatTimeInput(it) },
                            singleLine = true,
                            label = { Text(stringResource(R.string.record_time_from)) },
                            placeholder = { Text(TIME_HINT) },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = timeTo,
                            onValueChange = { timeTo = formatTimeInput(it) },
                            singleLine = true,
                            label = { Text(stringResource(R.string.record_time_to)) },
                            placeholder = { Text(TIME_HINT) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    TextField(
                        value = cause,
                        onValueChange = { cause = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.student_cause)) },
                        modifier = Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, enabled = true)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        causes.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(item.label) },
                                onClick = {
                                    cause = item.label
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.student_notes)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            IconButton(
                onClick = {
                    val epochDay = runCatching {
                        TimeUnit.MILLISECONDS.toDays(dateFormat.parse(dateText)!!.time)
                    }.getOrDefault(today)
                    AppSettings.setLastRecordDate(context, epochDay)
                    val storedTime = when (timeMode) {
                        TimeMode.AT -> timeAt.trim().ifBlank { null }
                        TimeMode.RANGE -> {
                            val from = timeFrom.trim()
                            val to = timeTo.trim()
                            if (from.isBlank() && to.isBlank()) null
                            else "$from$TIME_RANGE_SEPARATOR$to"
                        }
                    }?.let(::normalizeStoredTime)
                    onSave(type, epochDay, storedTime, cause, note)
                },
                enabled = cause.isNotBlank()
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = stringResource(R.string.student_save)
                )
            }
        },
        dismissButton = {
            IconButton(onClick = onDismiss) {
                Icon(
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = stringResource(R.string.student_cancel)
                )
            }
        }
    )
}
