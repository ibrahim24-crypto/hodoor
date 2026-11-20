package com.himo.hodoor.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.himo.hodoor.data.dao.CauseDao
import com.himo.hodoor.data.dao.RecordDao
import com.himo.hodoor.data.dao.StudentDao
import com.himo.hodoor.data.entities.Cause
import com.himo.hodoor.data.entities.Converters
import com.himo.hodoor.data.entities.Record
import com.himo.hodoor.data.entities.Student

@Database(
    entities = [Student::class, Record::class, Cause::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class HodoorDatabase : RoomDatabase() {
    abstract fun studentDao(): StudentDao
    abstract fun recordDao(): RecordDao
    abstract fun causeDao(): CauseDao

    companion object {
        fun build(context: Context): HodoorDatabase =
            Room.databaseBuilder(context.applicationContext, HodoorDatabase::class.java, "hodoor.db")
                .build()
    }
}
