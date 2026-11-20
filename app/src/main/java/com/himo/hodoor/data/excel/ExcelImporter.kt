package com.himo.hodoor.data.excel

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Xml
import com.himo.hodoor.data.normalize
import com.himo.hodoor.data.entities.Student
import com.himo.hodoor.data.normalizedStudentName
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.zip.ZipInputStream

data class ColumnMapping(val code: Int = 0, val firstName: Int = 1, val familyName: Int = 2)

data class ParsedStudents(
    val students: List<Student>,
    val preview: List<List<String>>,
    val skipped: Int,
    val mapping: ColumnMapping
)

sealed interface ParseOutcome {
    data class Ready(val data: ParsedStudents) : ParseOutcome
    data class NeedMapping(val headers: List<String>, val preview: List<List<String>>) : ParseOutcome
    data class Failed(val message: String) : ParseOutcome
}

/** Streaming .xlsx / .csv reader. Never keeps the archive in memory, only parsed rows. */
object ExcelImporter {
    fun parse(
        context: Context,
        uri: Uri,
        mapping: ColumnMapping? = null,
        onProgress: (Int) -> Unit = {}
    ): ParseOutcome = try {
        val name = displayName(context, uri)
        val isXlsx = name.endsWith(".xlsx", ignoreCase = true)
        val stream = context.contentResolver.openInputStream(uri)
            ?: return ParseOutcome.Failed("Impossible d'ouvrir le fichier")
        stream.use { input ->
            val rows = if (isXlsx) {
                val shared = context.contentResolver.openInputStream(uri)?.use { readSharedStrings(it) }
                    ?: emptyList()
                readSheet(input, shared)
            } else {
                readCsv(input)
            }
            processRows(rows, mapping, onProgress)
        }
    } catch (e: Exception) {
        ParseOutcome.Failed(e.message ?: "Erreur de lecture")
    }

    private fun displayName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) return cursor.getString(index) ?: ""
            }
        }
        return uri.lastPathSegment ?: ""
    }

    private fun readSharedStrings(input: InputStream): List<String> {
        val result = mutableListOf<String>()
        val zip = ZipInputStream(input)
        var entry = zip.nextEntry
        while (entry != null) {
            if (entry.name == "xl/sharedStrings.xml") {
                result.addAll(parseSharedStrings(zip))
                break
            }
            zip.closeEntry()
            entry = zip.nextEntry
        }
        return result
    }

    private fun readSheet(input: InputStream, sharedStrings: List<String>): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val zip = ZipInputStream(input)
        var entry = zip.nextEntry
        var sheetRead = false
        while (entry != null && !sheetRead) {
            if (entry.name.startsWith("xl/worksheets/sheet") && entry.name.endsWith(".xml")) {
                parseSheet(zip, sharedStrings) { row -> rows.add(row) }
                sheetRead = true
            }
            zip.closeEntry()
            if (!sheetRead) entry = zip.nextEntry
        }
        return rows
    }

    private fun parseSharedStrings(input: InputStream): List<String> {
        val result = mutableListOf<String>()
        val parser = Xml.newPullParser()
        parser.setInput(input, null)
        var inText = false
        val text = StringBuilder()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> if (parser.name == "t") {
                    inText = true
                    text.setLength(0)
                }

                XmlPullParser.TEXT -> if (inText) text.append(parser.text)

                XmlPullParser.END_TAG -> when (parser.name) {
                    "t" -> inText = false
                    "si" -> result.add(text.toString())
                }
            }
            event = parser.next()
        }
        return result
    }

    private fun parseSheet(
        input: InputStream,
        sharedStrings: List<String>,
        onRow: (List<String>) -> Unit
    ) {
        val parser = Xml.newPullParser()
        parser.setInput(input, null)
        var row = mutableListOf<String>()
        var cellIndex = -1
        var cellType = ""
        var inValue = false
        val cellText = StringBuilder()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "row" -> {
                        row = mutableListOf()
                        cellIndex = -1
                    }

                    "c" -> {
                        cellType = parser.getAttributeValue(null, "t") ?: ""
                        cellIndex = columnOf(parser.getAttributeValue(null, "r"), cellIndex)
                        cellText.setLength(0)
                    }

                    "v", "t" -> inValue = true
                }

                XmlPullParser.TEXT -> if (inValue) cellText.append(parser.text)

                XmlPullParser.END_TAG -> when (parser.name) {
                    "v", "t" -> inValue = false

                    "c" -> {
                        val raw = cellText.toString()
                        val value = if (cellType == "s") {
                            raw.trim().toIntOrNull()?.let { sharedStrings.getOrNull(it) } ?: ""
                        } else raw
                        while (row.size <= cellIndex) row.add("")
                        row[cellIndex] = value
                    }

                    "row" -> onRow(row.toList())
                }
            }
            event = parser.next()
        }
    }

    private fun columnOf(reference: String?, fallback: Int): Int {
        if (reference.isNullOrEmpty()) return fallback + 1
        var result = 0
        for (char in reference) {
            if (!char.isLetter()) break
            result = result * 26 + (char.uppercaseChar() - 'A' + 1)
        }
        return result - 1
    }

    private fun readCsv(input: InputStream): List<List<String>> {
        val reader = BufferedReader(InputStreamReader(input, Charsets.UTF_8))
        val lines = reader.readLines()
        if (lines.isEmpty()) return emptyList()
        val delimiter = if (lines.first().count { it == ';' } >= lines.first().count { it == ',' }) ';' else ','
        return lines.filter { it.isNotBlank() }.map { parseCsvLine(it, delimiter) }
    }

    private fun parseCsvLine(line: String, delimiter: Char): List<String> {
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

                char == delimiter && !quoted -> {
                    result.add(current.toString().trim())
                    current.setLength(0)
                }

                else -> current.append(char)
            }
            i++
        }
        result.add(current.toString().trim())
        return result
    }

    private fun processRows(
        rows: List<List<String>>,
        mapping: ColumnMapping?,
        onProgress: (Int) -> Unit
    ): ParseOutcome {
        val headerIndex = rows.indexOfFirst { row -> row.any { it.isNotBlank() } }
        if (headerIndex < 0) return ParseOutcome.Failed("Fichier vide")
        val header = rows[headerIndex]
        val dataRows = rows.drop(headerIndex + 1)
        val resolved = mapping ?: detectColumns(header)
        if (resolved == null) {
            return ParseOutcome.NeedMapping(header, dataRows.take(5))
        }

        val students = mutableListOf<Student>()
        val seen = mutableSetOf<String>()
        var skipped = 0
        dataRows.forEachIndexed { index, row ->
            onProgress(index + 1)
            fun cell(index: Int) = row.getOrNull(index)?.trim().orEmpty()
            val code = cell(resolved.code)
            if (code.isEmpty() || !seen.add(normalize(code))) {
                skipped++
                return@forEachIndexed
            }
            val firstName = cell(resolved.firstName)
            val familyName = cell(resolved.familyName)
            students.add(
                Student(
                    code = code,
                    firstName = firstName,
                    familyName = familyName,
                    searchName = normalizedStudentName(firstName, familyName)
                )
            )
        }
        return ParseOutcome.Ready(
            ParsedStudents(students, dataRows.take(5), skipped, resolved)
        )
    }

    private fun detectColumns(header: List<String>): ColumnMapping? {
        val normalized = header.map(::normalize)
        fun find(vararg names: String): Int = normalized.indexOfFirst { it in names }

        val code = find("code", "massar", "code massar", "numero", "numéro")
            .takeIf { it >= 0 } ?: normalized.indexOfFirst { it.contains("massar") }
        val first = find("prenom", "first name", "firstname", "prénom")
        val family = find("nom", "nom de famille", "family name", "lastname", "surname", "nom famille")
        return if (code >= 0 && first >= 0 && family >= 0) {
            ColumnMapping(code, first, family)
        } else null
    }
}
