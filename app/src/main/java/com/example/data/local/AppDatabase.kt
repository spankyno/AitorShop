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
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun shoppingItemDao(): ShoppingItemDao
    abstract fun predefinedItemDao(): PredefinedItemDao
    abstract fun purchaseHistoryDao(): PurchaseHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Create indices on shopping_items
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_shopping_items_listId` ON `shopping_items` (`listId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_shopping_items_isSynced` ON `shopping_items` (`isSynced`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_shopping_items_isDeleted` ON `shopping_items` (`isDeleted`)")

                // 2. Create index on purchase_history
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_purchase_history_listId` ON `purchase_history` (`listId`)")

                // 3. Create purchase_history_items table and its index
                db.execSQL("CREATE TABLE IF NOT EXISTS `purchase_history_items` (`id` TEXT NOT NULL, `historyId` TEXT NOT NULL, `name` TEXT NOT NULL, `quantity` REAL NOT NULL, `unit` TEXT NOT NULL, `price` REAL NOT NULL, `category` TEXT NOT NULL DEFAULT 'Otros', PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_purchase_history_items_historyId` ON `purchase_history_items` (`historyId`)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "supercompra_database"
                )
                .addMigrations(MIGRATION_1_2)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
