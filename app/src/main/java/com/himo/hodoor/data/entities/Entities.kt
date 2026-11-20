package com.himo.hodoor.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

@Entity(
    tableName = "students",
    indices = [Index(value = ["searchName"])]
)
data class Student(
    @PrimaryKey val code: String,
    val firstName: String,
    val familyName: String,
    val searchName: String
) {
    val fullName: String get() = "$firstName $familyName".trim()
}

enum class RecordType {
    LATE,
    ABSENT
}

@Entity(
    tableName = "records",
    foreignKeys = [
        ForeignKey(
            entity = Student::class,
            parentColumns = ["code"],
            childColumns = ["studentCode"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["studentCode"])]
)
data class Record(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val studentCode: String,
    val type: RecordType,
    val date: Long,
    val time: String? = null,
    val cause: String,
    val note: String? = null
)

@Entity(tableName = "causes")
data class Cause(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String
)

data class TypeCount(val type: RecordType, val count: Int)
data class CauseCount(val cause: String, val count: Int)
data class ImportReport(val added: Int, val updated: Int, val skipped: Int)

class Converters {
    @TypeConverter
    fun recordTypeToValue(value: RecordType?): String? = value?.name

    @TypeConverter
    fun valueToRecordType(value: String?): RecordType? = value?.let { runCatching { RecordType.valueOf(it) }.getOrNull() }
}
