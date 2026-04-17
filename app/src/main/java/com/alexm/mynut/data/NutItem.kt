package com.alexm.mynut.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "nut_items")
data class NutItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val calories: Double,
    val fats: Double,
    val saturatedFats: Double,
    val carbs: Double,
    val sugars: Double,
    val fiber: Double,
    val proteins: Double,
    val sodium: Double,
    val portionLabel: String,
    val createdAt: Long = System.currentTimeMillis()
)