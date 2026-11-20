package com.himo.hodoor.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.himo.hodoor.data.entities.CauseCount
import com.himo.hodoor.data.entities.Record
import com.himo.hodoor.data.entities.TypeCount
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordDao {
    @Query("SELECT * FROM records WHERE studentCode = :studentCode ORDER BY date DESC, id DESC")
    fun history(studentCode: String): Flow<List<Record>>

    @Query("SELECT type, COUNT(*) AS count FROM records WHERE studentCode = :studentCode GROUP BY type")
    fun typeCounts(studentCode: String): Flow<List<TypeCount>>

    @Query("SELECT cause, COUNT(*) AS count FROM records WHERE studentCode = :studentCode GROUP BY cause ORDER BY count DESC, cause ASC LIMIT 1")
    fun topCause(studentCode: String): Flow<CauseCount?>

    @Query("SELECT * FROM records")
    suspend fun all(): List<Record>

    @Insert
    suspend fun insert(record: Record): Long

    @Insert
    suspend fun insertAll(records: List<Record>)

    @Update
    suspend fun update(record: Record)

    @Delete
    suspend fun delete(record: Record)

    @Query("DELETE FROM records WHERE studentCode = :studentCode")
    suspend fun deleteForStudent(studentCode: String)
}
