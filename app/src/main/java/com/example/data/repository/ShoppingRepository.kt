package com.example.data.repository

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.data.local.*
import com.example.data.remote.SupabaseClient
import com.example.data.remote.SupabaseShoppingItemDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withContext
import java.util.UUID

class ShoppingRepository(
    private val context: Context,
    private val shoppingItemDao: ShoppingItemDao,
    private val predefinedItemDao: PredefinedItemDao,
    private val purchaseHistoryDao: PurchaseHistoryDao
) {
    // Flow of custom notifications/alerts to display in UI
    private val _syncAlerts = MutableSharedFlow<String>(extraBufferCapacity = 5)
    val syncAlerts: SharedFlow<String> = _syncAlerts

    init {
        createNotificationChannel()
    }

    // --- Core list getters ---
    fun getItems(listId: String): Flow<List<ShoppingItemEntity>> {
        return shoppingItemDao.getAllItems(listId)
    }

    fun getPredefinedItems(): Flow<List<PredefinedItemEntity>> {
        return predefinedItemDao.getAllPredefined()
    }

    fun getHistory(): Flow<List<PurchaseHistoryEntity>> {
        return purchaseHistoryDao.getAllHistory()
    }

    // --- Insert / Update actions ---
    suspend fun addItem(
        name: String,
        quantity: Double,
        unit: String,
        price: Double,
        category: String,
        listId: String
    ) = withContext(Dispatchers.IO) {
        val item = ShoppingItemEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            quantity = quantity,
            unit = unit,
            price = price,
            category = category,
            isPurchased = false,
            listId = listId,
            isSynced = false
        )
        shoppingItemDao.insert(item)
        triggerSync(listId)
    }

    suspend fun updateItem(item: ShoppingItemEntity) = withContext(Dispatchers.IO) {
        val updated = item.copy(isSynced = false)
        shoppingItemDao.insert(updated)
        triggerSync(item.listId)
    }

    suspend fun deleteItem(id: String, listId: String) = withContext(Dispatchers.IO) {
        shoppingItemDao.softDelete(id)
        triggerSync(listId)
    }

    // --- Predefined Items ---
    suspend fun addPredefinedItem(name: String, category: String, defaultPrice: Double, defaultUnit: String) = withContext(Dispatchers.IO) {
        val predefined = PredefinedItemEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            category = category,
            defaultPrice = defaultPrice,
            defaultUnit = defaultUnit
        )
        predefinedItemDao.insert(predefined)
    }

    suspend fun deletePredefinedItem(item: PredefinedItemEntity) = withContext(Dispatchers.IO) {
        predefinedItemDao.delete(item)
    }

    // Seed initial list of standard food/grocery products so the user has immediate data
    suspend fun seedPredefinedItemsIfNeeded() = withContext(Dispatchers.IO) {
        if (predefinedItemDao.getAllPredefinedSync().isEmpty()) {
            val defaults = listOf(
                PredefinedItemEntity(UUID.randomUUID().toString(), "Pan de molde", "Panadería", 1.50, "uds."),
                PredefinedItemEntity(UUID.randomUUID().toString(), "Leche entera", "Lácteos", 0.90, "l"),
                PredefinedItemEntity(UUID.randomUUID().toString(), "Huevos docena", "Huevos", 2.20, "uds."),
                PredefinedItemEntity(UUID.randomUUID().toString(), "Arroz 1kg", "Despensa", 1.25, "uds."),
                PredefinedItemEntity(UUID.randomUUID().toString(), "Pasta Macarrones", "Despensa", 0.85, "uds."),
                PredefinedItemEntity(UUID.randomUUID().toString(), "Plátanos", "Fruta y Verdura", 1.80, "kg"),
                PredefinedItemEntity(UUID.randomUUID().toString(), "Pechuga de pollo", "Carnicería", 6.50, "kg"),
                PredefinedItemEntity(UUID.randomUUID().toString(), "Detergente líquido", "Limpieza", 4.95, "uds."),
                PredefinedItemEntity(UUID.randomUUID().toString(), "Café molido", "Cafés e Infusiones", 2.20, "uds."),
                PredefinedItemEntity(UUID.randomUUID().toString(), "Aceite de oliva 1L", "Despensa", 7.50, "l"),
                PredefinedItemEntity(UUID.randomUUID().toString(), "Tomates de ensalada", "Fruta y Verdura", 2.10, "kg"),
                PredefinedItemEntity(UUID.randomUUID().toString(), "Agua mineral 6x1.5L", "Bebidas", 1.85, "uds.")
            )
            predefinedItemDao.insertAll(defaults)
        }
    }

    // --- Complete Purchase & Store History ---
    suspend fun completePurchaseAndClear(listId: String) = withContext(Dispatchers.IO) {
        // Retrieve checked-off purchased items
        val purchased = shoppingItemDao.getPurchasedItemsSync(listId)
        if (purchased.isEmpty()) return@withContext

        val totalCost = purchased.sumOf { it.price * it.quantity }
        val itemsCount = purchased.size
        val summary = purchased.joinToString(", ") { "${it.name} (${it.quantity}${it.unit})" }

        // Store in history
        val record = PurchaseHistoryEntity(
            id = UUID.randomUUID().toString(),
            listId = listId,
            date = System.currentTimeMillis(),
            totalCost = totalCost,
            itemsCount = itemsCount,
            itemsSummary = summary
        )
        purchaseHistoryDao.insert(record)

        // Soft-delete purchased items to clear active list
        shoppingItemDao.softDeleteAllPurchased(listId)
        triggerSync(listId)
    }

    suspend fun deleteHistoryRecord(id: String) = withContext(Dispatchers.IO) {
        purchaseHistoryDao.deleteHistoryRecord(id)
    }

    // --- SUPABASE SYNC LOGIC (The core real-time synchronizer) ---
    suspend fun triggerSync(listId: String): Boolean = withContext(Dispatchers.IO) {
        val api = SupabaseClient.getApi() ?: return@withContext false
        val key = SupabaseClient.getApiKey()
        if (key.isBlank()) return@withContext false

        try {
            // 1. Fetch current remote state of items for this list (excluding deleted)
            val remoteItems = api.getItems(
                listIdFilter = "eq.$listId",
                deletedFilter = "eq.false",
                apiKey = key,
                bearer = "Bearer $key"
            )

            // 2. Fetch local active items & raw items (for status comparison)
            val localItems = shoppingItemDao.getAllRawItemsSync()
            val localActiveMap = localItems.filter { !it.isDeleted }.associateBy { it.id }
            val localAllMap = localItems.associateBy { it.id }

            var remoteModificationsFound = false
            val remoteModificationsLogs = mutableListOf<String>()

            // 3. Process Remote -> Local:
            for (remote in remoteItems) {
                val local = localAllMap[remote.id]

                if (local == null) {
                    // Item brand new from another device
                    shoppingItemDao.insert(
                        ShoppingItemEntity(
                            id = remote.id,
                            name = remote.name,
                            quantity = remote.quantity,
                            unit = remote.unit,
                            price = remote.price,
                            category = remote.category,
                            isPurchased = remote.isPurchased,
                            listId = remote.listId,
                            createdAt = remote.createdAt,
                            isSynced = true,
                            isDeleted = false
                        )
                    )
                    remoteModificationsFound = true
                    remoteModificationsLogs.add("Añadido '${remote.name}' por otro usuario")
                } else {
                    // Check if is changed on Supabase
                    val isCheckedDifferent = local.isPurchased != remote.isPurchased
                    val isDataDifferent = local.name != remote.name || local.quantity != remote.quantity || local.price != remote.price

                    if (isCheckedDifferent || isDataDifferent) {
                        shoppingItemDao.insert(
                            local.copy(
                                name = remote.name,
                                quantity = remote.quantity,
                                unit = remote.unit,
                                price = remote.price,
                                category = remote.category,
                                isPurchased = remote.isPurchased,
                                isSynced = true,
                                isDeleted = false
                            )
                        )
                        remoteModificationsFound = true
                        val statusString = if (remote.isPurchased) "marcado" else "desmarcado"
                        remoteModificationsLogs.add("Modificado '${remote.name}' ($statusString) por otro usuario")
                    }
                }
            }

            // Check if remote items lack some of our local records (which means other deleted them)
            val remoteIds = remoteItems.map { it.id }.toSet()
            for (local in localItems) {
                if (!local.isDeleted && local.isSynced && !remoteIds.contains(local.id)) {
                    // Remote deleted this item, so we soft-delete it locally
                    shoppingItemDao.softDelete(local.id)
                    remoteModificationsFound = true
                    remoteModificationsLogs.add("Eliminado '${local.name}' por otro usuario")
                }
            }

            // 4. Process Local -> Remote (Pushed unsynced items, including soft deletes)
            val unsyncedItems = shoppingItemDao.getAllRawItemsSync().filter { !it.isSynced }
            if (unsyncedItems.isNotEmpty()) {
                val dtoList = unsyncedItems.map {
                    SupabaseShoppingItemDto(
                        id = it.id,
                        name = it.name,
                        quantity = it.quantity,
                        unit = it.unit,
                        price = it.price,
                        category = it.category,
                        isPurchased = it.isPurchased,
                        listId = it.listId,
                        createdAt = it.createdAt,
                        isDeleted = it.isDeleted
                    )
                }

                val response = api.upsertItems(
                    items = dtoList,
                    apiKey = key,
                    bearer = "Bearer $key"
                )

                if (response.isSuccessful) {
                    // Mark as synced locally
                    for (unsynced in unsyncedItems) {
                        if (unsynced.isDeleted) {
                            // If it was soft-deleted locally, we can hard-delete it once successfully pushed as deleted
                            shoppingItemDao.hardDelete(unsynced.id)
                        } else {
                            shoppingItemDao.insert(unsynced.copy(isSynced = true))
                        }
                    }
                }
            }

            // 5. Trigger native alert & status notification if there were remote changes
            if (remoteModificationsFound && remoteModificationsLogs.isNotEmpty()) {
                val alertMessage = remoteModificationsLogs.first()
                _syncAlerts.emit(alertMessage)
                showStatusBarNotification(
                    "SuperCompra Compartida",
                    if (remoteModificationsLogs.size == 1) alertMessage else "Se han realizado ${remoteModificationsLogs.size} cambios en la lista compartida."
                )
            }

            // Clean physical vacuum of locally soft-deleted rows once synced
            shoppingItemDao.vacuumDeleted()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // --- Android System Notification Drawer ---
    private fun showStatusBarNotification(title: String, message: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat) // standard android icon that exists on all devices
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)

        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Sincronización de Compra"
            val descriptionText = "Notificaciones cuando un integrante actualiza la lista"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "supercompra_sync_channel"
        private const val NOTIFICATION_ID = 1001
    }
}
