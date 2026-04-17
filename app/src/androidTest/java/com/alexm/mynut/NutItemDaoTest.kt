package com.alexm.mynut.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NutItemDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: NutItemDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.nutItemDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    private fun testItem(name: String = "Yaourt", calories: Double = 95.0) = NutItem(
        name = name, calories = calories, fats = 3.5, saturatedFats = 2.0,
        carbs = 12.0, sugars = 10.0, fiber = 0.0, proteins = 4.0,
        sodium = 50.0, portionLabel = "100g"
    )

    @Test
    fun insertAndGetAll() = runTest {
        dao.insert(testItem("Banana"))
        dao.insert(testItem("Apple"))
        val items = dao.getAllItems().first()
        assertEquals(2, items.size)
        assertEquals("Apple", items[0].name)
        assertEquals("Banana", items[1].name)
    }

    @Test
    fun getById() = runTest {
        val id = dao.insert(testItem())
        val item = dao.getItemById(id).first()
        assertEquals("Yaourt", item?.name)
    }

    @Test
    fun update() = runTest {
        val id = dao.insert(testItem())
        val item = dao.getItemById(id).first()!!
        dao.update(item.copy(name = "Yaourt nature"))
        val updated = dao.getItemById(id).first()
        assertEquals("Yaourt nature", updated?.name)
    }

    @Test
    fun delete() = runTest {
        val id = dao.insert(testItem())
        val item = dao.getItemById(id).first()!!
        dao.delete(item)
        val deleted = dao.getItemById(id).first()
        assertNull(deleted)
    }
}