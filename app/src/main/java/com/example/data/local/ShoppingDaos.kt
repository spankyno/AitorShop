package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ShoppingItemDao {
    @Query("SELECT * FROM shopping_items WHERE listId = :listId AND isDeleted = 0 ORDER BY createdAt ASC")
    fun getAllItems(listId: String): Flow<List<ShoppingItemEntity>>

    @Query("SELECT * FROM shopping_items WHERE listId = :listId AND isDeleted = 0 AND isPurchased = 1")
    suspend fun getPurchasedItemsSync(listId: String): List<ShoppingItemEntity>

    @Query("SELECT * FROM shopping_items WHERE isDeleted = 0")
    suspend fun getActiveItemsSync(): List<ShoppingItemEntity>

    @Query("SELECT * FROM shopping_items")
    suspend fun getAllRawItemsSync(): List<ShoppingItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ShoppingItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ShoppingItemEntity>)

    @Query("UPDATE shopping_items SET isDeleted = 1, isSynced = 0 WHERE id = :id")
    suspend fun softDelete(id: String)

    @Query("DELETE FROM shopping_items WHERE id = :id")
    suspend fun hardDelete(id: String)

    @Query("UPDATE shopping_items SET isDeleted = 1, isSynced = 0 WHERE listId = :listId AND isPurchased = 1")
    suspend fun softDeleteAllPurchased(listId: String)

    @Query("DELETE FROM shopping_items WHERE isDeleted = 1")
    suspend fun vacuumDeleted()
}

@Dao
interface PredefinedItemDao {
    @Query("SELECT * FROM predefined_items ORDER BY name ASC")
    fun getAllPredefined(): Flow<List<PredefinedItemEntity>>

    @Query("SELECT * FROM predefined_items ORDER BY name ASC")
    suspend fun getAllPredefinedSync(): List<PredefinedItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: PredefinedItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<PredefinedItemEntity>)

    @Delete
    suspend fun delete(item: PredefinedItemEntity)
}

@Dao
interface PurchaseHistoryDao {
    @Query("SELECT * FROM purchase_history ORDER BY date DESC")
    fun getAllHistory(): Flow<List<PurchaseHistoryEntity>>

    @Query("SELECT * FROM purchase_history ORDER BY date DESC LIMIT :limit OFFSET :offset")
    suspend fun getHistoryPaged(limit: Int, offset: Int): List<PurchaseHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: PurchaseHistoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<PurchaseHistoryItemEntity>)

    @Query("SELECT * FROM purchase_history_items WHERE historyId = :historyId")
    suspend fun getItemsForHistory(historyId: String): List<PurchaseHistoryItemEntity>

    @Query("DELETE FROM purchase_history WHERE id = :id")
    suspend fun deleteHistoryRecord(id: String)

    @Query("DELETE FROM purchase_history_items WHERE historyId = :id")
    suspend fun deleteHistoryItems(id: String)
}
