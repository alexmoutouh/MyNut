package com.alexm.mynut.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyEntryDao {
    @Transaction
    @Query("SELECT * FROM daily_entries WHERE date = :date ORDER BY createdAt ASC")
    fun getEntriesForDate(date: String): Flow<List<DailyEntryWithItem>>

    @Insert
    suspend fun insert(entry: DailyEntry)

    @Delete
    suspend fun delete(entry: DailyEntry)
}