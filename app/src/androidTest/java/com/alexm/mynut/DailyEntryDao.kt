package com.alexm.mynut.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DailyEntryDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var nutItemDao: NutItemDao
    private lateinit var dailyEntryDao: DailyEntryDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        nutItemDao = db.nutItemDao()
        dailyEntryDao = db.dailyEntryDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    private suspend fun insertItem(name: String = "Yaourt"): Long {
        return nutItemDao.insert(
            NutItem(
                name = name, calories = 95.0, fats = 3.5, saturatedFats = 2.0,
                carbs = 12.0, sugars = 10.0, fiber = 0.0, proteins = 4.0,
                sodium = 50.0, portionLabel = "100g"
            )
        )
    }

    @Test
    fun insertAndGetEntriesForDate() = runTest {
        val itemId = insertItem()
        dailyEntryDao.insert(DailyEntry(nutItemId = itemId, date = "2026-04-09", portionCount = 1.5))
        val entries = dailyEntryDao.getEntriesForDate("2026-04-09").first()
        assertEquals(1, entries.size)
        assertEquals(1.5, entries[0].entry.portionCount, 0.001)
        assertEquals("Yaourt", entries[0].nutItem.name)
    }

    @Test
    fun entriesFilteredByDate() = runTest {
        val itemId = insertItem()
        dailyEntryDao.insert(DailyEntry(nutItemId = itemId, date = "2026-04-09", portionCount = 1.0))
        dailyEntryDao.insert(DailyEntry(nutItemId = itemId, date = "2026-04-10", portionCount = 2.0))
        val entries = dailyEntryDao.getEntriesForDate("2026-04-09").first()
        assertEquals(1, entries.size)
    }

    @Test
    fun deleteEntry() = runTest {
        val itemId = insertItem()
        dailyEntryDao.insert(DailyEntry(nutItemId = itemId, date = "2026-04-09", portionCount = 1.0))
        val entry = dailyEntryDao.getEntriesForDate("2026-04-09").first()[0].entry
        dailyEntryDao.delete(entry)
        val entries = dailyEntryDao.getEntriesForDate("2026-04-09").first()
        assertTrue(entries.isEmpty())
    }

    @Test
    fun cascadeDeleteRemovesEntries() = runTest {
        val itemId = insertItem()
        dailyEntryDao.insert(DailyEntry(nutItemId = itemId, date = "2026-04-09", portionCount = 1.0))
        val item = nutItemDao.getItemById(itemId).first()!!
        nutItemDao.delete(item)
        val entries = dailyEntryDao.getEntriesForDate("2026-04-09").first()
        assertTrue(entries.isEmpty())
    }
}