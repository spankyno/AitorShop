package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "shopping_items")
data class ShoppingItemEntity(
    @PrimaryKey val id: String,
    val name: String,
    val quantity: Double = 1.0,
    val unit: String = "uds.", // uds., kg, l, g, pack
    val price: Double = 0.0,
    val category: String = "Otros",
    val isPurchased: Boolean = false,
    val listId: String = "default",
    val createdAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false,
    val isDeleted: Boolean = false
)

@Entity(tableName = "predefined_items")
data class PredefinedItemEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String = "Otros",
    val defaultPrice: Double = 0.0,
    val defaultUnit: String = "uds."
)

@Entity(tableName = "purchase_history")
data class PurchaseHistoryEntity(
    @PrimaryKey val id: String,
    val listId: String,
    val date: Long = System.currentTimeMillis(),
    val totalCost: Double,
    val itemsCount: Int,
    val itemsSummary: String // Resumen legible (e.g. "Arroz, Pasta, Leche")
)
