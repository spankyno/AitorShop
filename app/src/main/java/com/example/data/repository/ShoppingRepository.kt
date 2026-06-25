package com.example.data.repository

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.BuildConfig
import com.example.data.local.*
import com.example.data.remote.AuthResponse
import com.example.sync.SyncWorker
import com.example.data.remote.RefreshTokenRequest
import com.example.data.remote.SupabaseClient
import com.example.data.remote.SupabaseShoppingItemDto
import com.example.security.SecureSessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

class ShoppingRepository(
    private val context: Context,
    private val session: SecureSessionManager,
    private val shoppingItemDao: ShoppingItemDao,
    private val predefinedItemDao: PredefinedItemDao,
    private val purchaseHistoryDao: PurchaseHistoryDao
) {
    private val _syncAlerts = MutableSharedFlow<String>(extraBufferCapacity = 5)
    val syncAlerts: SharedFlow<String> = _syncAlerts

    init {
        createNotificationChannel()
        SupabaseClient.setSession(session)
        SupabaseClient.setAuthenticator(TokenAuthenticator(session))
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    fun getItems(listId: String): Flow<List<ShoppingItemEntity>> =
        shoppingItemDao.getAllItems(listId)

    fun getPredefinedItems(): Flow<List<PredefinedItemEntity>> =
        predefinedItemDao.getAllPredefined()

    fun getHistory(): Flow<List<PurchaseHistoryEntity>> =
        purchaseHistoryDao.getAllHistory()

    // ── Escrituras ───────────────────────────────────────────────────────────

    suspend fun addItem(
        name: String,
        quantity: Double,
        unit: String,
        price: Double,
        category: String,
        listId: String
    ) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val item = ShoppingItemEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            quantity = quantity,
            unit = unit,
            price = price,
            category = category,
            isPurchased = false,
            listId = listId,
            createdAt = now,
            updatedAt = now,
            isSynced = false
        )
        shoppingItemDao.insert(item)
        triggerSync(listId)
    }

    suspend fun updateItem(item: ShoppingItemEntity) = withContext(Dispatchers.IO) {
        shoppingItemDao.insert(item.copy(isSynced = false, updatedAt = System.currentTimeMillis()))
        triggerSync(item.listId)
    }

    suspend fun deleteItem(id: String, listId: String) = withContext(Dispatchers.IO) {
        shoppingItemDao.softDelete(id)
        triggerSync(listId)
    }

    // ── Predefinidos ─────────────────────────────────────────────────────────

    suspend fun addPredefinedItem(
        name: String,
        category: String,
        defaultPrice: Double,
        defaultUnit: String
    ) = withContext(Dispatchers.IO) {
        predefinedItemDao.insert(
            PredefinedItemEntity(
                id = UUID.randomUUID().toString(),
                name = name,
                category = category,
                defaultPrice = defaultPrice,
                defaultUnit = defaultUnit
            )
        )
    }

    suspend fun deletePredefinedItem(item: PredefinedItemEntity) =
        withContext(Dispatchers.IO) { predefinedItemDao.delete(item) }

    suspend fun seedPredefinedItemsIfNeeded() = withContext(Dispatchers.IO) {
        if (predefinedItemDao.getAllPredefinedSync().isEmpty()) {
            predefinedItemDao.insertAll(defaultPredefinedItems())
        }
    }

    // ── Historial ────────────────────────────────────────────────────────────

    suspend fun completePurchaseAndClear(listId: String) = withContext(Dispatchers.IO) {
        val purchased = shoppingItemDao.getPurchasedItemsSync(listId)
        if (purchased.isEmpty()) return@withContext

        val totalCost = purchased.sumOf { it.price * it.quantity }
        val summary = purchased.joinToString(", ") { "${it.name} (${it.quantity}${it.unit})" }
        val historyId = UUID.randomUUID().toString()

        purchaseHistoryDao.insert(
            PurchaseHistoryEntity(
                id = historyId,
                listId = listId,
                date = System.currentTimeMillis(),
                totalCost = totalCost,
                itemsCount = purchased.size,
                itemsSummary = summary
            )
        )
        purchaseHistoryDao.insertItems(purchased.map { item ->
            PurchaseHistoryItemEntity(
                id = UUID.randomUUID().toString(),
                historyId = historyId,
                name = item.name,
                quantity = item.quantity,
                unit = item.unit,
                price = item.price,
                category = item.category
            )
        })
        shoppingItemDao.softDeleteAllPurchased(listId)
        triggerSync(listId)
    }

    suspend fun deleteHistoryRecord(id: String) = withContext(Dispatchers.IO) {
        purchaseHistoryDao.deleteHistoryRecord(id)
        purchaseHistoryDao.deleteHistoryItems(id)
    }

    // ── Sincronización con Supabase ──────────────────────────────────────────

    suspend fun triggerSync(listId: String): Boolean = withContext(Dispatchers.IO) {
        if (!isNetworkAvailable()) {
            // Sin red → encolar para reintento automático cuando vuelva la conexión
            SyncWorker.enqueueOfflineSync(context, listId)
            return@withContext false
        }
        val api = SupabaseClient.getApi() ?: return@withContext false

        try {
            val itemsResponse = api.getItems(
                listIdFilter = "eq.$listId",
                deletedFilter = "eq.false"
            )

            if (!itemsResponse.isSuccessful) {
                val errorBody = itemsResponse.errorBody()?.string() ?: "HTTP ${itemsResponse.code()}"
                android.util.Log.e("ShoppingRepository", "getItems failed: $errorBody")
                SyncWorker.enqueueOfflineSync(context, listId)
                return@withContext false
            }

            val remoteItems = itemsResponse.body() ?: emptyList()

            val localItems = shoppingItemDao.getAllRawItemsSync()
            val localAllMap = localItems.associateBy { it.id }

            var remoteModificationsFound = false
            val remoteModificationsLogs = mutableListOf<String>()

            for (remote in remoteItems) {
                val local = localAllMap[remote.id]
                if (local == null) {
                    // Ítem nuevo de otro dispositivo → insertamos siempre
                    shoppingItemDao.insert(remote.toEntity())
                    remoteModificationsFound = true
                    remoteModificationsLogs.add("Añadido '${remote.name}' por otro usuario")
                } else {
                    // ── Resolución de conflicto LWW ──────────────────────────
                    // Solo aplicamos el cambio remoto si:
                    //   (a) el registro local ya está sincronizado (sin cambios
                    //       pendientes locales), O
                    //   (b) el remoto es más reciente (updatedAt mayor), lo que
                    //       significa que otro dispositivo editó DESPUÉS que nosotros.
                    //
                    // Si el local tiene isSynced=false Y su updatedAt >= remoto,
                    // el cambio local gana y se empujará al servidor en el paso 4.
                    val remoteUpdatedAt = remote.updatedAt ?: remote.createdAt
                    val remoteWins = local.isSynced || (remoteUpdatedAt > local.updatedAt)

                    if (remoteWins) {
                        val changed = local.isPurchased != remote.isPurchased
                                || local.name != remote.name
                                || local.quantity != remote.quantity
                                || local.price != remote.price
                                || local.unit != remote.unit
                                || local.category != remote.category

                        if (changed) {
                            shoppingItemDao.insert(
                                local.copy(
                                    name        = remote.name,
                                    quantity    = remote.quantity,
                                    unit        = remote.unit,
                                    price       = remote.price,
                                    category    = remote.category,
                                    isPurchased = remote.isPurchased,
                                    updatedAt   = remote.updatedAt ?: remote.createdAt,
                                    isSynced    = true,
                                    isDeleted   = false
                                )
                            )
                            remoteModificationsFound = true
                            val status = if (remote.isPurchased) "marcado" else "desmarcado"
                            remoteModificationsLogs.add("Modificado '${remote.name}' ($status) por otro usuario")
                        }
                    }
                    // Si remoteWins == false, el cambio local (más reciente) se
                    // mantendrá y se subirá a Supabase en el paso 4 de esta misma sync.
                }
            }

            val remoteIds = remoteItems.map { it.id }.toSet()
            for (local in localItems) {
                if (!local.isDeleted && local.isSynced && !remoteIds.contains(local.id)) {
                    shoppingItemDao.softDelete(local.id)
                    remoteModificationsFound = true
                    remoteModificationsLogs.add("Eliminado '${local.name}' por otro usuario")
                }
            }

            val unsyncedItems = shoppingItemDao.getAllRawItemsSync().filter { !it.isSynced }
            if (unsyncedItems.isNotEmpty()) {
                val response = api.upsertItems(unsyncedItems.map { it.toDto() })
                if (response.isSuccessful) {
                    for (item in unsyncedItems) {
                        if (item.isDeleted) shoppingItemDao.hardDelete(item.id)
                        else shoppingItemDao.insert(item.copy(isSynced = true))
                    }
                }
            }

            if (remoteModificationsFound && remoteModificationsLogs.isNotEmpty()) {
                val alertMessage = remoteModificationsLogs.first()
                _syncAlerts.emit(alertMessage)
                showStatusBarNotification(
                    "SuperCompra Compartida",
                    if (remoteModificationsLogs.size == 1) alertMessage
                    else "Se han realizado ${remoteModificationsLogs.size} cambios en la lista compartida."
                )
            }

            shoppingItemDao.vacuumDeleted()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            // Petición fallida (timeout, servidor caído…) → encolar reintento
            SyncWorker.enqueueOfflineSync(context, listId)
            false
        }
    }

    private fun showStatusBarNotification(title: String, message: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()
        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Sincronización de Compra",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Notificaciones cuando un integrante actualiza la lista" }
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE)
                as? android.net.ConnectivityManager ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        return caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    // ── Helpers de conversión ────────────────────────────────────────────────

    private fun SupabaseShoppingItemDto.toEntity() = ShoppingItemEntity(
        id = id, name = name, quantity = quantity, unit = unit, price = price,
        category = category, isPurchased = isPurchased, listId = listId,
        createdAt = createdAt,
        // Si updatedAt no existe aún en Supabase, usamos createdAt como fallback
        updatedAt = updatedAt ?: createdAt,
        isSynced = true, isDeleted = false
    )

    private fun ShoppingItemEntity.toDto() = SupabaseShoppingItemDto(
        id = id, name = name, quantity = quantity, unit = unit, price = price,
        category = category, isPurchased = isPurchased, listId = listId,
        createdAt = createdAt, updatedAt = updatedAt, isDeleted = isDeleted
    )

    private fun defaultPredefinedItems() = listOf(
        PredefinedItemEntity(UUID.randomUUID().toString(), "Pan de molde",       "Panadería",        1.50, "uds."),
        PredefinedItemEntity(UUID.randomUUID().toString(), "Leche entera",       "Lácteos",          0.90, "l"),
        PredefinedItemEntity(UUID.randomUUID().toString(), "Huevos docena",      "Huevos",           2.20, "uds."),
        PredefinedItemEntity(UUID.randomUUID().toString(), "Arroz 1kg",          "Despensa",         1.25, "uds."),
        PredefinedItemEntity(UUID.randomUUID().toString(), "Pasta Macarrones",   "Despensa",         0.85, "uds."),
        PredefinedItemEntity(UUID.randomUUID().toString(), "Plátanos",           "Fruta y Verdura",  1.80, "kg"),
        PredefinedItemEntity(UUID.randomUUID().toString(), "Pechuga de pollo",   "Carnicería",       6.50, "kg"),
        PredefinedItemEntity(UUID.randomUUID().toString(), "Detergente líquido", "Limpieza",         4.95, "uds."),
        PredefinedItemEntity(UUID.randomUUID().toString(), "Café molido",        "Cafés e Infusiones",2.20,"uds."),
        PredefinedItemEntity(UUID.randomUUID().toString(), "Aceite de oliva 1L", "Despensa",         7.50, "l"),
        PredefinedItemEntity(UUID.randomUUID().toString(), "Tomates de ensalada","Fruta y Verdura",  2.10, "kg"),
        PredefinedItemEntity(UUID.randomUUID().toString(), "Agua mineral 6x1.5L","Bebidas",          1.85, "uds.")
    )

    companion object {
        private const val CHANNEL_ID       = "supercompra_sync_channel"
        private const val NOTIFICATION_ID  = 1001
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MEJORA 2 — TokenAuthenticator refactorizado:
//
//  · El Mutex de corrutinas garantiza que dos peticiones 401 simultáneas
//    NO lancen dos refrescos en paralelo: la segunda espera el resultado de
//    la primera y reutiliza el token ya renovado.
//
//  · performTokenRefresh() ahora usa el SupabaseAuthApi (Retrofit) en lugar
//    de construir un OkHttpClient nuevo para cada refresco, eliminando la
//    fuga de recursos y el bloqueo en el hilo de red de OkHttp.
//
//  · runBlocking es necesario porque okhttp3.Authenticator es sincrónico,
//    pero todo el trabajo real (la llamada de red) ocurre en una corrutina
//    que delega en Dispatchers.IO, por lo que no bloquea el thread pool de
//    OkHttp más allá del tiempo de la petición HTTP.
// ─────────────────────────────────────────────────────────────────────────────
class TokenAuthenticator(
    private val session: SecureSessionManager
) : okhttp3.Authenticator {

    // Un único Mutex evita el doble refresco cuando varias peticiones
    // reciben 401 de forma simultánea.
    private val refreshMutex = Mutex()

    override fun authenticate(route: okhttp3.Route?, response: okhttp3.Response): okhttp3.Request? {
        if (response.code != 401) return null

        // No reintentar en los propios endpoints de auth para evitar bucles
        val url = response.request.url.toString()
        if (url.contains("/auth/v1/") || url.contains("/token")) return null

        return runBlocking {
            refreshMutex.withLock {
                // Si otro hilo ya renovó el token mientras esperábamos, lo
                // reutilizamos directamente sin lanzar otra petición de refresco.
                val currentToken = session.accessToken
                val requestToken = response.request.header("Authorization")
                    ?.removePrefix("Bearer ")

                if (currentToken != null && currentToken != requestToken) {
                    // Token ya renovado por otra corrutina — simplemente reintentamos
                    return@withLock response.request.newBuilder()
                        .header("Authorization", "Bearer $currentToken")
                        .build()
                }

                val refreshToken = session.refreshToken
                if (refreshToken.isNullOrBlank()) {
                    session.clearSession()
                    return@withLock null
                }

                val success = performTokenRefresh(refreshToken)
                if (success) {
                    val newToken = session.accessToken
                    if (newToken != null) {
                        response.request.newBuilder()
                            .header("Authorization", "Bearer $newToken")
                            .build()
                    } else null
                } else {
                    session.clearSession()
                    null
                }
            }
        }
    }

    /**
     * Llama al endpoint de refresh usando el SupabaseAuthApi de Retrofit
     * (que ya tiene el ApiKeyInterceptor configurado) en lugar de crear
     * un OkHttpClient nuevo por cada refresco.
     */
    private suspend fun performTokenRefresh(refreshToken: String): Boolean {
        val authApi = SupabaseClient.getAuthApi() ?: return false
        return try {
            val response = authApi.refreshToken(
                request = RefreshTokenRequest(refreshToken)
            )
            val body: AuthResponse? = response.body()
            if (response.isSuccessful
                && !body?.accessToken.isNullOrBlank()
                && !body?.refreshToken.isNullOrBlank()
            ) {
                session.updateTokens(body!!.accessToken!!, body.refreshToken!!)
                true
            } else false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
