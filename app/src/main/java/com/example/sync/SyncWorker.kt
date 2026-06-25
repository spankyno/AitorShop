package com.example.sync

import android.content.Context
import androidx.work.*
import com.example.data.local.AppDatabase
import com.example.data.repository.ShoppingRepository
import com.example.security.SecureSessionManager
import java.util.concurrent.TimeUnit

/**
 * Worker que ejecuta la sincronización con Supabase.
 *
 * Se usa para DOS propósitos:
 *
 * 1. COLA OFFLINE  — se encola con OneTimeWorkRequest cada vez que hay ítems
 *    sin sincronizar (isSynced = false). WorkManager lo reintenta
 *    automáticamente cuando la red vuelve (NetworkType.CONNECTED), con
 *    backoff exponencial, sobreviviendo a reinicios del proceso.
 *
 * 2. POLLING PERIÓDICO — se registra como PeriodicWorkRequest con un
 *    intervalo de 15 minutos (el mínimo permitido por WorkManager) para
 *    detectar cambios de otros usuarios incluso con la app en background.
 *    En primer plano, el ViewModel mantiene su propio polling de 30 s.
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val listId = inputData.getString(KEY_LIST_ID)
            ?: return Result.failure()

        val session = SecureSessionManager(applicationContext)

        // No sincronizar si no hay sesión activa
        if (!session.isLoggedIn) return Result.success()

        val db = AppDatabase.getDatabase(applicationContext)
        val repository = ShoppingRepository(
            context    = applicationContext,
            session    = session,
            shoppingItemDao      = db.shoppingItemDao(),
            predefinedItemDao    = db.predefinedItemDao(),
            purchaseHistoryDao   = db.purchaseHistoryDao()
        )

        return if (repository.triggerSync(listId)) Result.success()
        else Result.retry()   // WorkManager aplicará backoff exponencial
    }

    companion object {
        const val KEY_LIST_ID = "list_id"

        // ── Tags para identificar los trabajos en WorkManager ────────────────
        const val TAG_OFFLINE_QUEUE = "sync_offline_queue"
        const val TAG_PERIODIC      = "sync_periodic"

        /**
         * Encola una sincronización puntual para cuando la red esté disponible.
         * Usar después de cualquier operación local que quede pendiente de sync.
         *
         * · ExistingWorkPolicy.KEEP → si ya hay uno encolado no duplicamos.
         * · BackoffPolicy.EXPONENTIAL → 10 s → 20 s → 40 s … hasta 5 min.
         * · NetworkType.CONNECTED → WorkManager espera red antes de ejecutar.
         */
        fun enqueueOfflineSync(context: Context, listId: String) {
            val data = workDataOf(KEY_LIST_ID to listId)

            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setInputData(data)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    10, TimeUnit.SECONDS
                )
                .addTag(TAG_OFFLINE_QUEUE)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "$TAG_OFFLINE_QUEUE-$listId",
                ExistingWorkPolicy.KEEP,
                request
            )
        }

        /**
         * Registra el polling periódico en background (mínimo 15 min por SO).
         * Llamar una sola vez al arrancar la app (p.ej. desde Application o init
         * del ViewModel). ExistingPeriodicWorkPolicy.KEEP evita duplicados al
         * rotar pantalla o recrear el ViewModel.
         */
        fun schedulePeriodicSync(context: Context, listId: String) {
            val data = workDataOf(KEY_LIST_ID to listId)

            val request = PeriodicWorkRequestBuilder<SyncWorker>(
                15, TimeUnit.MINUTES
            )
                .setInputData(data)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .addTag(TAG_PERIODIC)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                TAG_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        /** Cancela el polling periódico (p.ej. al cerrar sesión). */
        fun cancelPeriodicSync(context: Context) {
            WorkManager.getInstance(context).cancelAllWorkByTag(TAG_PERIODIC)
        }

        /** Cancela las operaciones pendientes de la cola offline. */
        fun cancelOfflineQueue(context: Context) {
            WorkManager.getInstance(context).cancelAllWorkByTag(TAG_OFFLINE_QUEUE)
        }
    }
}
