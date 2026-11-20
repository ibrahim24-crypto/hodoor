package com.himo.hodoor.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.himo.hodoor.R
import com.himo.hodoor.data.entities.Record
import com.himo.hodoor.data.entities.RecordType
import com.himo.hodoor.ui.settings.civilDateFormat
import java.util.Date

private val LateTint = Color(0xFFFFB300)
private val AbsentTint = Color(0xFFE53935)
private val CauseTint = Color(0xFF0F766E)

private enum class StudentPeriod { ALL, TODAY, WEEK }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun StudentScreen(
    viewModel: StudentViewModel,
    onBack: () -> Unit,
    onAddRecord: () -> Unit,
    modifier: Modifier = Modifier
) {
    val student by viewModel.student.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val typeCounts by viewModel.typeCounts.collectAsStateWithLifecycle()
    val topCause by viewModel.topCause.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val causes by viewModel.causes.collectAsStateWithLifecycle()
    val editor by viewModel.editor.collectAsStateWithLifecycle()
    var actionRecord by remember { mutableStateOf<Record?>(null) }
    var query by remember { mutableStateOf("") }
    var period by remember { mutableStateOf(StudentPeriod.ALL) }
    val today = remember { StudentViewModel.todayEpochDay() }

    val visibleHistory = remember(history, query, period, today) {
        val needle = query.trim()
        history.filter { record ->
            val periodOk = when (period) {
                StudentPeriod.ALL -> true
                StudentPeriod.TODAY -> record.date == today
                StudentPeriod.WEEK -> record.date >= today - 6
            }
            val queryOk = needle.isEmpty() || listOf(
                record.cause,
                record.note.orEmpty(),
                record.time.orEmpty()
            ).any { it.contains(needle, ignoreCase = true) }
            periodOk && queryOk
        }
    }

    val lateCount = typeCounts.firstOrNull { it.type == RecordType.LATE }?.count ?: 0
    val absentCount = typeCounts.firstOrNull { it.type == RecordType.ABSENT }?.count ?: 0

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppLogo(size = 32.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(student?.fullName ?: viewModel.studentCode)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddRecord) {
                Icon(
                    painter = painterResource(R.drawable.ic_add),
                    contentDescription = stringResource(R.string.student_add_record)
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        text = student?.fullName.orEmpty(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(text = viewModel.studentCode, style = MaterialTheme.typography.bodyMedium)
                }
            }

            item {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatCard(
                        label = stringResource(R.string.student_total_lates),
                        value = lateCount.toString(),
                        icon = painterResource(R.drawable.ic_clock),
                        tint = LateTint,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        label = stringResource(R.string.student_total_absences),
                        value = absentCount.toString(),
                        icon = painterResource(R.drawable.ic_absent),
                        tint = AbsentTint,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        label = stringResource(R.string.student_top_cause),
                        value = topCause?.let {
                            stringResource(R.string.student_top_cause_count, it.cause, it.count.toString())
                        } ?: "—",
                        icon = painterResource(R.drawable.ic_info),
                        tint = CauseTint,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_search),
                            contentDescription = null
                        )
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_close),
                                    contentDescription = stringResource(R.string.student_cancel)
                                )
                            }
                        }
                    },
                    placeholder = { Text(stringResource(R.string.filter_search_records)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
            }

            item {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_calendar),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    FilterChip(
                        selected = period == StudentPeriod.ALL,
                        onClick = { period = StudentPeriod.ALL },
                        label = { Text(stringResource(R.string.filter_period_all)) }
                    )
                    FilterChip(
                        selected = period == StudentPeriod.TODAY,
                        onClick = { period = StudentPeriod.TODAY },
                        label = { Text(stringResource(R.string.filter_period_today)) }
                    )
                    FilterChip(
                        selected = period == StudentPeriod.WEEK,
                        onClick = { period = StudentPeriod.WEEK },
                        label = { Text(stringResource(R.string.filter_period_week)) }
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = filter == HistoryFilter.ALL,
                        onClick = { viewModel.setFilter(HistoryFilter.ALL) },
                        label = { Text(stringResource(R.string.filter_all)) }
                    )
                    FilterChip(
                        selected = filter == HistoryFilter.LATE,
                        onClick = { viewModel.setFilter(HistoryFilter.LATE) },
                        label = { Text(stringResource(R.string.filter_late)) }
                    )
                    FilterChip(
                        selected = filter == HistoryFilter.ABSENT,
                        onClick = { viewModel.setFilter(HistoryFilter.ABSENT) },
                        label = { Text(stringResource(R.string.filter_absent)) }
                    )
                }
            }

            if (visibleHistory.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.student_no_records),
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            items(items = visibleHistory, key = { it.id }) { record ->
                RecordRow(record = record, onLongClick = { actionRecord = record })
                HorizontalDivider()
            }
        }

        actionRecord?.let { record ->
            AlertDialog(
                onDismissRequest = { actionRecord = null },
                title = { Text(stringResource(R.string.student_edit_record)) },
                text = { Text(record.cause) },
                confirmButton = {
                    IconButton(onClick = {
                        viewModel.startEdit(record)
                        actionRecord = null
                    }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_edit),
                            contentDescription = stringResource(R.string.student_edit_record)
                        )
                    }
                },
                dismissButton = {
                    Row {
                        IconButton(onClick = {
                            viewModel.deleteRecord(record)
                            actionRecord = null
                        }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_delete),
                                contentDescription = stringResource(R.string.student_delete),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                        IconButton(onClick = { actionRecord = null }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_close),
                                contentDescription = stringResource(R.string.student_cancel)
                            )
                        }
                    }
                }
            )
        }

        if (editor.open) {
            RecordEditorDialog(
                record = editor.record,
                causes = causes,
                onDismiss = { viewModel.closeEditor() },
                onSave = { type, date, time, cause, note ->
                    viewModel.saveRecord(type, date, time, cause, note)
                }
            )
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    icon: Painter,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    painter = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(16.dp)
                )
                Text(text = label, style = MaterialTheme.typography.labelMedium)
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecordRow(
    record: Record,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val typeLabel = stringResource(
        if (record.type == RecordType.LATE) R.string.record_type_late else R.string.record_type_absent
    )
    ListItem(
        headlineContent = { Text(record.cause.ifBlank { typeLabel }) },
        supportingContent = {
            val date = civilDateFormat().format(Date(record.date * 86_400_000L))
            Text(listOf(date, typeLabel, record.time).filter { !it.isNullOrBlank() }.joinToString(" · "))
        },
        modifier = modifier.combinedClickable(
            onClick = {},
            onLongClick = onLongClick
        )
    )
}
