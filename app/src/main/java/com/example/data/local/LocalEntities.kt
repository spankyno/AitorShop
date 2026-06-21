package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "shopping_items",
    indices = [
        Index(value = ["listId"]),
        Index(value = ["isSynced"]),
        Index(value = ["isDeleted"])
    ]
)
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

@Entity(
    tableName = "purchase_history",
    indices = [
        Index(value = ["listId"])
    ]
)
data class PurchaseHistoryEntity(
    @PrimaryKey val id: String,
    val listId: String,
    val date: Long = System.currentTimeMillis(),
    val totalCost: Double,
    val itemsCount: Int,
    val itemsSummary: String // Resumen legible (e.g. "Arroz, Pasta, Leche")
)

@Entity(
    tableName = "purchase_history_items",
    indices = [
        Index(value = ["historyId"])
    ]
)
data class PurchaseHistoryItemEntity(
    @PrimaryKey val id: String,
    val historyId: String,
    val name: String,
    val quantity: Double,
    val unit: String,
    val price: Double,
    val category: String = "Otros"
)
