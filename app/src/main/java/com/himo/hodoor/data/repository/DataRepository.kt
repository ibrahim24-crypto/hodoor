package com.himo.hodoor.data.repository

import android.content.Context
import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import com.himo.hodoor.R
import com.himo.hodoor.data.db.HodoorDatabase
import com.himo.hodoor.data.entities.Cause
import com.himo.hodoor.data.entities.CauseCount
import com.himo.hodoor.data.entities.ImportReport
import com.himo.hodoor.data.entities.Record
import com.himo.hodoor.data.entities.Student
import com.himo.hodoor.data.entities.TypeCount
import com.himo.hodoor.data.normalize
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class DataRepository(context: Context) {
    private val db = HodoorDatabase.build(context)
    private val studentDao = db.studentDao()
    private val recordDao = db.recordDao()
    private val causeDao = db.causeDao()

    val studentCount: Flow<Int> = studentDao.count()
    val causes: Flow<List<Cause>> = causeDao.all()

    fun searchStudents(query: String): Flow<List<Student>> {
        val words = normalize(query).split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return flowOf(emptyList())

        val conditions = words.map { "searchName LIKE ?" }
        val sql = """
            SELECT * FROM students
            WHERE ${conditions.joinToString(" AND ")}
            ORDER BY familyName ASC, firstName ASC
            LIMIT 50
        """.trimIndent()
        val args = words.map { "%$it%" }.toTypedArray()
        return studentDao.search(SimpleSQLiteQuery(sql, args))
    }

    fun student(code: String): Flow<Student?> = studentDao.get(code)

    suspend fun allStudentCodes(): List<String> = studentDao.allCodes()

    suspend fun studentsByCodes(codes: List<String>): List<Student> = studentDao.byCodes(codes)

    suspend fun upsertStudents(students: List<Student>) = studentDao.upsertAll(students)

    /** Batch upsert inside one transaction; never deletes existing students or their records. */
    suspend fun importStudents(
        students: List<Student>,
        onProgress: (Int) -> Unit = {}
    ): ImportReport {
        // A single unparameterised query avoids SQLite's variable limit for large imports.
        val existing = studentDao.allCodes().toHashSet()
        val added = students.count { it.code !in existing }
        db.withTransaction {
            students.chunked(500).forEachIndexed { index, chunk ->
                studentDao.upsertAll(chunk)
                onProgress((index + 1) * 500)
            }
        }
        return ImportReport(added = added, updated = students.size - added, skipped = 0)
    }

    fun history(studentCode: String): Flow<List<Record>> = recordDao.history(studentCode)

    fun typeCounts(studentCode: String): Flow<List<TypeCount>> = recordDao.typeCounts(studentCode)

    fun topCause(studentCode: String): Flow<CauseCount?> = recordDao.topCause(studentCode)

    suspend fun allRecords(): List<Record> = recordDao.all()

    suspend fun addRecord(record: Record) = recordDao.insert(record)

    suspend fun addRecords(records: List<Record>) = recordDao.insertAll(records)

    suspend fun updateRecord(record: Record) = recordDao.update(record)

    suspend fun deleteRecord(record: Record) = recordDao.delete(record)

    suspend fun addCause(label: String) = causeDao.insert(Cause(label = label.trim()))

    suspend fun updateCause(cause: Cause) = causeDao.update(cause)

    suspend fun deleteCause(cause: Cause) = causeDao.delete(cause)

    /** Called once on app start so the preset causes exist even before Settings is opened. */
    suspend fun ensureDefaultCauses(context: Context) {
        val localized = listOf(
            context.getString(R.string.cause_illness),
            context.getString(R.string.cause_transport),
            context.getString(R.string.cause_family),
            context.getString(R.string.cause_medical),
            context.getString(R.string.cause_unjustified),
            context.getString(R.string.cause_other)
        )
        if (causeDao.count() == 0) {
            localized.forEach { addCause(it) }
            return
        }
        // Causes saved under an earlier language are translated in place; custom labels are kept.
        val translation = legacyFrenchCauses.zip(localized).toMap()
        causeDao.allOnce().forEach { cause ->
            translation[cause.label.trim()]?.let { updateCause(cause.copy(label = it)) }
        }
        recordDao.all().forEach { record ->
            translation[record.cause.trim()]?.let { updateRecord(record.copy(cause = it)) }
        }
    }

    private val legacyFrenchCauses = listOf(
        "Maladie",
        "Transport",
        "Motif familial",
        "Rendez-vous médical",
        "Non justifié",
        "Autre"
    )
}
