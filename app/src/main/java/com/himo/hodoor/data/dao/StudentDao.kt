package com.himo.hodoor.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Upsert
import androidx.sqlite.db.SupportSQLiteQuery
import com.himo.hodoor.data.entities.Student
import kotlinx.coroutines.flow.Flow

@Dao
interface StudentDao {
    @Query("SELECT COUNT(*) FROM students")
    fun count(): Flow<Int>

    @Query("SELECT * FROM students WHERE code = :code")
    fun get(code: String): Flow<Student?>

    @Query("SELECT code FROM students")
    suspend fun allCodes(): List<String>

    @Query("SELECT * FROM students WHERE code IN (:codes)")
    suspend fun byCodes(codes: List<String>): List<Student>

    @Upsert
    suspend fun upsertAll(students: List<Student>)

    @RawQuery(observedEntities = [Student::class])
    fun search(query: SupportSQLiteQuery): Flow<List<Student>>
}
