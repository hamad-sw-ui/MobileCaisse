package com.reconsiliation.caisse.data.local

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.reconsiliation.caisse.data.local.dao.*
import com.reconsiliation.caisse.data.local.entity.*
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory

@Database(
    entities = [
        BoutiqueEntity::class, VenteEntity::class, VenteItemEntity::class,
        StockEntity::class, SubscriptionEntity::class, SmsErrorEntity::class,
        CustomerEntity::class, RepaymentEntity::class, ExpenseEntity::class,
        ProcessedSmsEntity::class, AuditEntity::class, AuditItemEntity::class, 
        PriceHistoryEntity::class, SupplyEntity::class, ClosureEntity::class, 
        ActionLogEntity::class, SupplierEntity::class, SessionEntity::class, 
        StockMovementEntity::class, CategoryEntity::class, RecipeEntity::class, 
        StaffEntity::class
    ],
    version = 28,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun boutiqueDao(): BoutiqueDao
    abstract fun venteDao(): VenteDao
    abstract fun stockDao(): StockDao
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun smsErrorDao(): SmsErrorDao
    abstract fun customerDao(): CustomerDao
    abstract fun repaymentDao(): RepaymentDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun processedSmsDao(): ProcessedSmsDao
    abstract fun auditDao(): AuditDao
    abstract fun priceHistoryDao(): PriceHistoryDao
    abstract fun supplyDao(): SupplyDao
    abstract fun closureDao(): ClosureDao
    abstract fun actionLogDao(): ActionLogDao
    abstract fun supplierDao(): SupplierDao
    abstract fun sessionDao(): SessionDao
    abstract fun stockMovementDao(): StockMovementDao
    abstract fun categoryDao(): CategoryDao
    abstract fun recipeDao(): RecipeDao
    abstract fun staffDao(): StaffDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_27_28 = object : Migration(27, 28) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE boutique ADD COLUMN managerCode TEXT NOT NULL DEFAULT 'SETUP_REQUIRED_12CHARS'")
            }
        }

        val MIGRATION_26_27 = object : Migration(26, 27) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE processed_sms ADD COLUMN type TEXT NOT NULL DEFAULT 'INCOMING'")
            }
        }

        val MIGRATION_25_26 = object : Migration(25, 26) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `staff` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `pinHash` TEXT NOT NULL, `pinSalt` TEXT NOT NULL, `role` TEXT NOT NULL, `permissions` TEXT NOT NULL, `isActive` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)")
            }
        }

        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE stock ADD COLUMN isBulk INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE stock ADD COLUMN retailUnit TEXT")
                db.execSQL("ALTER TABLE stock ADD COLUMN conversionFactor INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE stock ADD COLUMN retailPrice DOUBLE NOT NULL DEFAULT 0.0")
            }
        }

        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `recipes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `description` TEXT NOT NULL, `price` DOUBLE NOT NULL, `category` TEXT NOT NULL, `isAvailable` INTEGER NOT NULL, `imageUrl` TEXT)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `recipe_ingredients` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `recipeId` INTEGER NOT NULL, `stockId` INTEGER NOT NULL, `quantity` DOUBLE NOT NULL, FOREIGN KEY(`recipeId`) REFERENCES `recipes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`stockId`) REFERENCES `stock`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_recipe_ingredients_recipeId` ON `recipe_ingredients` (`recipeId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_recipe_ingredients_stockId` ON `recipe_ingredients` (`stockId`)")
            }
        }

        val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE boutique ADD COLUMN address TEXT")
                db.execSQL("ALTER TABLE boutique ADD COLUMN receiptFooter TEXT")
                db.execSQL("ALTER TABLE boutique ADD COLUMN showCustomerPhoneOnReceipt INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE boutique ADD COLUMN showTaxesOnReceipt INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE boutique ADD COLUMN totalQuantityOnReceipt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE boutique ADD COLUMN printMerchantCopy INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE processed_sms ADD COLUMN status TEXT NOT NULL DEFAULT 'SUCCESS'")
                db.execSQL("ALTER TABLE processed_sms ADD COLUMN errorMessage TEXT")
            }
        }

        val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `categories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `description` TEXT, `color` INTEGER NOT NULL DEFAULT -1, `icon` TEXT)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_categories_name` ON `categories` (`name`)")
            }
        }

        val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `suppliers` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `phoneNumber` TEXT, `address` TEXT, `email` TEXT, `category` TEXT, `notes` TEXT)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_suppliers_name` ON `suppliers` (`name`)")
            }
        }

        val MIGRATION_24_25 = object : Migration(24, 25) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `sessions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `staffId` INTEGER NOT NULL, `staffName` TEXT NOT NULL, `startTime` INTEGER NOT NULL, `endTime` INTEGER, `startCash` DOUBLE NOT NULL, `expectedEndCash` DOUBLE, `actualEndCash` DOUBLE, `status` TEXT NOT NULL, `notes` TEXT, FOREIGN KEY(`staffId`) REFERENCES `staff`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sessions_staffId` ON `sessions` (`staffId`)")
                
                db.execSQL("ALTER TABLE ventes ADD COLUMN sessionId INTEGER")
                db.execSQL("ALTER TABLE expenses ADD COLUMN sessionId INTEGER")
                db.execSQL("ALTER TABLE supplies ADD COLUMN sessionId INTEGER")
                db.execSQL("ALTER TABLE repayments ADD COLUMN sessionId INTEGER")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE?.let { 
                if (it.isOpen) it else {
                    INSTANCE = null
                    null
                }
            } ?: synchronized(this) {
                // Charger les bibliothèques SQLCipher
                SQLiteDatabase.loadLibs(context)

                // Vérifier si une migration de clé est nécessaire
                val dbName = "caisse_database"
                val dbFile = context.getDatabasePath(dbName)
                if (dbFile.exists()) {
                    performRekeyIfNecessary(context, dbName)
                }

                val rawKey = com.reconsiliation.caisse.utils.SecurityUtil.getDatabaseKeySync(context)

                // Mode Standard (Option A) : La clé est utilisée comme une passphrase (String hexadécimale)
                // sur laquelle SQLCipher appliquera PBKDF2.
                val passphrase = if (com.reconsiliation.caisse.utils.SecurityUtil.getMigratedKey(context) != null) {
                    rawKey.joinToString("") { "%02x".format(it) }.toByteArray()
                } else {
                    rawKey
                }

                Log.d("AppDatabase", "Initializing Room with standard passphrase (length=${passphrase.size})...")
                
                val builder = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    dbName
                )
                .openHelperFactory(SupportFactory(passphrase))
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                .addMigrations(
                    MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21, 
                    MIGRATION_21_22, MIGRATION_22_23, MIGRATION_23_24,
                    MIGRATION_24_25, MIGRATION_25_26, MIGRATION_26_27,
                    MIGRATION_27_28
                )
                
                try {
                    val db = builder.build()
                    INSTANCE = db
                    db
                } catch (e: Exception) {
                    Log.e("AppDatabase", "CRITICAL: Room build failed: ${e.message}", e)
                    throw e
                }
            }
        }

        internal fun performRekeyIfNecessary(context: Context, dbName: String = "caisse_database") {
            val dbFile = context.getDatabasePath(dbName)
            if (!dbFile.exists()) return

            if (com.reconsiliation.caisse.utils.SecurityUtil.getMigratedKey(context) != null) return

            Log.i("AppDatabase", "Checking if database '$dbName' needs key migration...")
            val oldKey = com.reconsiliation.caisse.utils.SecurityUtil.getDatabaseKeyCompat(context)
            var db: SQLiteDatabase? = null
            
            try {
                db = SQLiteDatabase.openOrCreateDatabase(dbFile.absolutePath, oldKey, null)
                
                val cursorInfo = db.rawQuery("PRAGMA table_info(boutique)", null)
                var hasManagerCode = false
                while (cursorInfo.moveToNext()) {
                    if (cursorInfo.getString(1) == "managerCode") {
                        hasManagerCode = true
                        break
                    }
                }
                cursorInfo.close()

                if (!hasManagerCode) {
                    Log.d("AppDatabase", "Table 'boutique' or column 'managerCode' not found. Skipping rekey.")
                    db.close()
                    return 
                }

                val cursor = db.rawQuery("SELECT phoneNumber, managerCode FROM boutique LIMIT 1", null)
                if (cursor != null && cursor.moveToFirst()) {
                    val phone = cursor.getString(0)
                    val managerCode = cursor.getString(1)
                    
                    if (managerCode != "SETUP_REQUIRED_12CHARS" && 
                        managerCode.length >= 10 && 
                        managerCode.matches(Regex("^[a-zA-Z0-9_]+$"))) {
                        
                        Log.i("AppDatabase", "Starting physical re-encryption for '$dbName' (standard rekey)...")
                        val newKey = com.reconsiliation.caisse.utils.SecurityUtil.deriveNewKey(phone, managerCode)
                        val newKeyPassphrase = newKey.joinToString("") { "%02x".format(it) }
                        
                        // Action de re-chiffrement standard (PBKDF2 appliqué sur la passphrase hex)
                        db.execSQL("PRAGMA rekey = '$newKeyPassphrase'")
                        
                        // Force le flush sur le disque en fermant
                        db.close()
                        
                        com.reconsiliation.caisse.utils.SecurityUtil.saveMigratedKey(context, newKey)
                        Log.i("AppDatabase", "Database key migration successful for '$dbName'.")
                        db = null
                    } else {
                        Log.d("AppDatabase", "Manager code '$managerCode' not valid for migration. Skipping.")
                    }
                }
                cursor?.close()
            } catch (e: Exception) {
                Log.e("AppDatabase", "Error during migration of '$dbName': ${e.message}", e)
            } finally {
                db?.close()
            }
        }

        fun forceRekey(context: Context, phone: String, newManagerCode: String) {
            synchronized(this) {
                try {
                    INSTANCE?.let { if (it.isOpen) it.close() }
                    INSTANCE = null

                    val dbFile = context.getDatabasePath("caisse_database")
                    if (dbFile.exists()) {
                        val currentKey = com.reconsiliation.caisse.utils.SecurityUtil.getDatabaseKeySync(context)
                        val newKey = com.reconsiliation.caisse.utils.SecurityUtil.deriveNewKey(phone, newManagerCode)
                        val newKeyPassphrase = newKey.joinToString("") { "%02x".format(it) }
                        
                        var db: SQLiteDatabase? = null
                        try {
                            db = SQLiteDatabase.openOrCreateDatabase(dbFile.absolutePath, currentKey, null)
                            db.execSQL("PRAGMA rekey = '$newKeyPassphrase'")
                            com.reconsiliation.caisse.utils.SecurityUtil.saveMigratedKey(context, newKey)
                        } finally {
                            db?.close()
                        }
                    }
                } finally {
                    getDatabase(context)
                }
            }
        }
    }
}
