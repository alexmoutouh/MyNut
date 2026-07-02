package com.alexm.mynut.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [NutItem::class, DailyEntry::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun nutItemDao(): NutItemDao
    abstract fun dailyEntryDao(): DailyEntryDao

    companion object {
        fun build(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "mynut.db"
            ).build()
        }
    }
}
