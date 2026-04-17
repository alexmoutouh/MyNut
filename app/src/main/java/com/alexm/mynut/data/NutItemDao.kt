package com.alexm.mynut.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NutItemDao {
    @Query("SELECT * FROM nut_items ORDER BY name ASC")
    fun getAllItems(): Flow<List<NutItem>>

    @Query("SELECT * FROM nut_items WHERE id = :id")
    fun getItemById(id: Long): Flow<NutItem?>

    @Insert
    suspend fun insert(item: NutItem): Long

    @Update
    suspend fun update(item: NutItem)

    @Delete
    suspend fun delete(item: NutItem)
}