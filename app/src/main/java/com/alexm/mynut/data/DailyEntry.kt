package com.alexm.mynut.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(
    tableName = "daily_entries",
    foreignKeys = [
        ForeignKey(
            entity = NutItem::class,
            parentColumns = ["id"],
            childColumns = ["nutItemId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("date"), Index("nutItemId")]
)
data class DailyEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val nutItemId: Long,
    val date: String,
    val portionCount: Double,
    val createdAt: Long = System.currentTimeMillis()
)

data class DailyEntryWithItem(
    @Embedded val entry: DailyEntry,
    @Relation(
        parentColumn = "nutItemId",
        entityColumn = "id"
    )
    val nutItem: NutItem
)

data class DailySummary(
    val totalCalories: Double = 0.0,
    val totalFats: Double = 0.0,
    val totalSaturatedFats: Double = 0.0,
    val totalCarbs: Double = 0.0,
    val totalSugars: Double = 0.0,
    val totalFiber: Double = 0.0,
    val totalProteins: Double = 0.0,
    val totalSodium: Double = 0.0
) {
    companion object {
        fun fromEntries(entries: List<DailyEntryWithItem>): DailySummary {
            return entries.fold(DailySummary()) { acc, item ->
                val n = item.nutItem
                val p = item.entry.portionCount
                acc.copy(
                    totalCalories = acc.totalCalories + n.calories * p,
                    totalFats = acc.totalFats + n.fats * p,
                    totalSaturatedFats = acc.totalSaturatedFats + n.saturatedFats * p,
                    totalCarbs = acc.totalCarbs + n.carbs * p,
                    totalSugars = acc.totalSugars + n.sugars * p,
                    totalFiber = acc.totalFiber + n.fiber * p,
                    totalProteins = acc.totalProteins + n.proteins * p,
                    totalSodium = acc.totalSodium + n.sodium * p
                )
            }
        }
    }
}