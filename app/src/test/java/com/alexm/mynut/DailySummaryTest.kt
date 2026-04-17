package com.alexm.mynut.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DailySummaryTest {

    private fun nutItem(
        calories: Double = 0.0, fats: Double = 0.0, saturatedFats: Double = 0.0,
        carbs: Double = 0.0, sugars: Double = 0.0, fiber: Double = 0.0,
        proteins: Double = 0.0, sodium: Double = 0.0
    ) = NutItem(
        id = 1, name = "Test", calories = calories, fats = fats,
        saturatedFats = saturatedFats, carbs = carbs, sugars = sugars,
        fiber = fiber, proteins = proteins, sodium = sodium, portionLabel = "100g"
    )

    private fun entry(nutItem: NutItem, portionCount: Double) =
        DailyEntryWithItem(
            entry = DailyEntry(nutItemId = nutItem.id, date = "2026-04-09", portionCount = portionCount),
            nutItem = nutItem
        )

    @Test
    fun `empty list returns zero summary`() {
        val summary = DailySummary.fromEntries(emptyList())
        assertEquals(0.0, summary.totalCalories, 0.001)
        assertEquals(0.0, summary.totalProteins, 0.001)
    }

    @Test
    fun `single entry with 1 portion returns item values`() {
        val item = nutItem(calories = 250.0, proteins = 12.0, fats = 8.0)
        val summary = DailySummary.fromEntries(listOf(entry(item, 1.0)))
        assertEquals(250.0, summary.totalCalories, 0.001)
        assertEquals(12.0, summary.totalProteins, 0.001)
        assertEquals(8.0, summary.totalFats, 0.001)
    }

    @Test
    fun `single entry with 1_5 portions multiplies values`() {
        val item = nutItem(calories = 100.0, carbs = 20.0)
        val summary = DailySummary.fromEntries(listOf(entry(item, 1.5)))
        assertEquals(150.0, summary.totalCalories, 0.001)
        assertEquals(30.0, summary.totalCarbs, 0.001)
    }

    @Test
    fun `multiple entries are summed`() {
        val yogurt = nutItem(calories = 95.0, proteins = 4.0)
        val bread = nutItem(calories = 265.0, proteins = 9.0)
        val summary = DailySummary.fromEntries(
            listOf(entry(yogurt, 1.0), entry(bread, 2.0))
        )
        assertEquals(625.0, summary.totalCalories, 0.001)
        assertEquals(22.0, summary.totalProteins, 0.001)
    }
}