package com.himo.hodoor.data.backup

import com.himo.hodoor.data.entities.Record
import com.himo.hodoor.data.entities.RecordType

private const val HEADER = "studentCode,type,date,time,cause,note"

private fun escape(value: String?): String = "\"${value.orEmpty().replace("\"", "\"\"")}\""

fun recordsToCsv(records: List<Record>): String = buildString {
    appendLine(HEADER)
    records.forEach { record ->
        appendLine(
            listOf(
                escape(record.studentCode),
                escape(record.type.name),
                escape(record.date.toString()),
                escape(record.time),
                escape(record.cause),
                escape(record.note)
            ).joinToString(",")
        )
    }
}

/** Returns null for malformed lines. */
fun csvLineToRecord(line: String): Record? {
    val cells = parseCsvLine(line)
    if (cells.size != 6) return null
    val type = runCatching { RecordType.valueOf(cells[1].trim()) }.getOrNull() ?: return null
    val date = cells[2].trim().toLongOrNull() ?: return null
    return Record(
        studentCode = cells[0].trim(),
        type = type,
        date = date,
        time = cells[3].trim().takeIf { it.isNotEmpty() },
        cause = cells[4].trim(),
        note = cells[5].trim().takeIf { it.isNotEmpty() }
    )
}

private fun parseCsvLine(line: String): List<String> {
    val result = mutableListOf<String>()
    val current = StringBuilder()
    var quoted = false
    var i = 0
    while (i < line.length) {
        val char = line[i]
        when {
            char == '"' -> {
                if (quoted && i + 1 < line.length && line[i + 1] == '"') {
                    current.append('"')
                    i++
                } else quoted = !quoted
            }

            char == ',' && !quoted -> {
                result.add(current.toString())
                current.setLength(0)
            }

            else -> current.append(char)
        }
        i++
    }
    result.add(current.toString())
    return result
}
