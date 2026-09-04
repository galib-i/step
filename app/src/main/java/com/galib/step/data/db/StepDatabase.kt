package com.galib.step.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "daily_summary")
data class DailySummaryEntity(
    @PrimaryKey val epochDay: Long,
    val steps: Long,
    val goal: Int,
    val source: String
)

@Dao
interface SummaryDao {
    @Upsert
    suspend fun upsert(entity: DailySummaryEntity)

    @Upsert
    suspend fun upsertAll(entities: List<DailySummaryEntity>)

    @Query("SELECT * FROM daily_summary WHERE epochDay = :epochDay")
    fun observeDay(epochDay: Long): Flow<DailySummaryEntity?>

    @Query("SELECT * FROM daily_summary WHERE epochDay BETWEEN :from AND :to ORDER BY epochDay ASC")
    fun observeRange(from: Long, to: Long): Flow<List<DailySummaryEntity>>

    @Query("SELECT * FROM daily_summary ORDER BY epochDay ASC")
    suspend fun getAll(): List<DailySummaryEntity>

    @Query("SELECT * FROM daily_summary WHERE epochDay BETWEEN :from AND :to ORDER BY epochDay ASC")
    suspend fun getRange(from: Long, to: Long): List<DailySummaryEntity>

    @Query("SELECT * FROM daily_summary WHERE epochDay = :epochDay")
    suspend fun getDay(epochDay: Long): DailySummaryEntity?
}

@Database(
    entities = [DailySummaryEntity::class],
    version = 1,
    exportSchema = false
)
abstract class StepDatabase : RoomDatabase() {
    abstract fun summaryDao(): SummaryDao

    companion object {
        fun build(context: Context): StepDatabase =
            Room.databaseBuilder(context, StepDatabase::class.java, "stride.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
