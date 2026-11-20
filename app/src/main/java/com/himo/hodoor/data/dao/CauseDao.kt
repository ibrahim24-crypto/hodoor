package com.himo.hodoor.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.himo.hodoor.data.entities.Cause
import kotlinx.coroutines.flow.Flow

@Dao
interface CauseDao {
    @Query("SELECT * FROM causes ORDER BY id ASC")
    fun all(): Flow<List<Cause>>

    @Query("SELECT * FROM causes ORDER BY id ASC")
    suspend fun allOnce(): List<Cause>

    @Query("SELECT COUNT(*) FROM causes")
    suspend fun count(): Int

    @Insert
    suspend fun insert(cause: Cause): Long

    @Update
    suspend fun update(cause: Cause)

    @Delete
    suspend fun delete(cause: Cause)
}
