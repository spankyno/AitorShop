package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ShoppingItemEntity::class,
        PredefinedItemEntity::class,
        PurchaseHistoryEntity::class,
        PurchaseHistoryItemEntity::class
    ],
    version = 3,          // ← bumped de 2 a 3
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun shoppingItemDao(): ShoppingItemDao
    abstract fun predefinedItemDao(): PredefinedItemDao
    abstract fun purchaseHistoryDao(): PurchaseHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // ── Migración 1 → 2 (existente, sin cambios) ─────────────────────────
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_shopping_items_listId`   ON `shopping_items` (`listId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_shopping_items_isSynced` ON `shopping_items` (`isSynced`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_shopping_items_isDeleted` ON `shopping_items` (`isDeleted`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_purchase_history_listId` ON `purchase_history` (`listId`)")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `purchase_history_items` (
                        `id`        TEXT    NOT NULL,
                        `historyId` TEXT    NOT NULL,
                        `name`      TEXT    NOT NULL,
                        `quantity`  REAL    NOT NULL,
                        `unit`      TEXT    NOT NULL,
                        `price`     REAL    NOT NULL,
                        `category`  TEXT    NOT NULL DEFAULT 'Otros',
                        PRIMARY KEY(`id`)
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_purchase_history_items_historyId` ON `purchase_history_items` (`historyId`)")
            }
        }

        // ── Migración 2 → 3: añade updatedAt a shopping_items ────────────────
        //
        // Por qué DEFAULT con el valor actual de createdAt en lugar de 0:
        //   · Si usáramos 0, todos los ítems existentes parecerían más antiguos
        //     que cualquier cambio remoto y serían sobreescritos en el primer sync.
        //   · Al inicializar updatedAt = createdAt, la primera sincronización
        //     tratará los ítems locales como si hubieran sido creados/editados
        //     en el mismo momento que se crearon, lo que es la mejor aproximación
        //     posible sin datos históricos reales.
        //   · SQLite no permite referenciar otra columna en DEFAULT, por lo que
        //     se usa strftime para obtener el epoch actual en ms como fallback
        //     y se actualiza en un segundo paso con el valor real de createdAt.
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Añadir columna con valor por defecto = now (epoch ms)
                db.execSQL("""
                    ALTER TABLE `shopping_items`
                    ADD COLUMN `updatedAt` INTEGER NOT NULL
                    DEFAULT 0
                """)

                // 2. Rellenar filas existentes con su createdAt como mejor
                //    aproximación del último momento de modificación conocido
                db.execSQL("""
                    UPDATE `shopping_items`
                    SET    `updatedAt` = `createdAt`
                    WHERE  `updatedAt` = 0
                """)

                // 3. Índice para ordenar/filtrar por versión de forma eficiente
                db.execSQL("""
                    CREATE INDEX IF NOT EXISTS `index_shopping_items_updatedAt`
                    ON `shopping_items` (`updatedAt`)
                """)
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "supercompra_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
