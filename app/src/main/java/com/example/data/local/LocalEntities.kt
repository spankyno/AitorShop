package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

// ─────────────────────────────────────────────────────────────────────────────
// MEJORA: Conflicto de sincronización sin timestamp de versión
//
// Se añade el campo `updatedAt` (epoch ms) a ShoppingItemEntity.
// Este timestamp se actualiza en CADA operación local (insert, toggle,
// edición, soft-delete) y se propaga a Supabase dentro del DTO.
//
// Estrategia de resolución de conflictos: "Last Write Wins" (LWW)
//   · Si isSynced == false  →  el dispositivo tiene cambios locales pendientes
//     y el campo updatedAt indica cuándo se hicieron.
//   · En triggerSync(), antes de sobreescribir un ítem local con datos remotos,
//     se compara local.updatedAt vs remote.updatedAt.
//   · Solo se aplica el cambio remoto si remote.updatedAt > local.updatedAt,
//     evitando que una sync tardía machaque ediciones más recientes.
// ─────────────────────────────────────────────────────────────────────────────

@Entity(
    tableName = "shopping_items",
    indices = [
        Index(value = ["listId"]),
        Index(value = ["isSynced"]),
        Index(value = ["isDeleted"]),
        Index(value = ["updatedAt"])   // Nuevo índice para ordenar por versión
    ]
)
data class ShoppingItemEntity(
    @PrimaryKey val id: String,
    val name: String,
    val quantity: Double = 1.0,
    val unit: String = "uds.",
    val price: Double = 0.0,
    val category: String = "Otros",
    val isPurchased: Boolean = false,
    val listId: String = "default",
    val createdAt: Long = System.currentTimeMillis(),
    /** Timestamp de la última modificación local (epoch ms).
     *  Se usa como vector de versión para resolver conflictos LWW. */
    val updatedAt: Long = System.currentTimeMillis(),
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
    val itemsSummary: String
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
