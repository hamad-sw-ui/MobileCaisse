package com.reconsiliation.caisse.data.local

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.room.withTransaction
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
    version = 30,
    exportSchema = true
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

    /**
     * Exécute un bloc de code dans une transaction Room.
     * Cette méthode permet de mocker/faker le comportement des transactions dans les tests JVM (B-094A).
     */
    open suspend fun <R> runInTransaction(block: suspend () -> R): R {
        return withTransaction { block() }
    }

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_29_30 = object : Migration(29, 30) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Index sur vente_items.venteId
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_vente_items_venteId` ON `vente_items` (`venteId`)")

                // 2. AuditItemEntity (Ajout FK et Index via table temporaire)
                db.execSQL("CREATE TABLE IF NOT EXISTS `audit_items_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `auditId` INTEGER NOT NULL, `productId` INTEGER NOT NULL, `productName` TEXT NOT NULL, `systemQuantity` REAL NOT NULL, `physicalQuantity` REAL NOT NULL, `unitPrice` REAL NOT NULL, `purchasePrice` REAL NOT NULL, FOREIGN KEY(`auditId`) REFERENCES `audits`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("INSERT INTO audit_items_new SELECT * FROM audit_items")
                db.execSQL("DROP TABLE audit_items")
                db.execSQL("ALTER TABLE audit_items_new RENAME TO audit_items")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_audit_items_auditId` ON `audit_items` (`auditId`)")
            }
        }

        val MIGRATION_28_29 = object : Migration(28, 29) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Correction table 'boutique' (totalQuantityOnReceipt -> showTotalQuantityOnReceipt)
                db.execSQL("CREATE TABLE IF NOT EXISTS `boutique_new` (`id` INTEGER NOT NULL, `name` TEXT NOT NULL, `ownerName` TEXT NOT NULL, `phoneNumber` TEXT NOT NULL, `operator` TEXT NOT NULL, `momoNumber` TEXT NOT NULL, `currency` TEXT NOT NULL, `pinHash` TEXT, `pinSalt` TEXT, `isSetupComplete` INTEGER NOT NULL, `allowNegativeStock` INTEGER NOT NULL, `printerAddress` TEXT, `hideProfitsFromStaff` INTEGER NOT NULL, `managerPinHash` TEXT, `managerPinSalt` TEXT, `taxName` TEXT NOT NULL, `taxRate` REAL NOT NULL, `isTaxEnabled` INTEGER NOT NULL, `address` TEXT, `receiptFooter` TEXT, `showCustomerPhoneOnReceipt` INTEGER NOT NULL, `showTaxesOnReceipt` INTEGER NOT NULL, `showTotalQuantityOnReceipt` INTEGER NOT NULL, `printMerchantCopy` INTEGER NOT NULL, `managerCode` TEXT NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("INSERT INTO boutique_new (id, name, ownerName, phoneNumber, operator, momoNumber, currency, pinHash, pinSalt, isSetupComplete, allowNegativeStock, printerAddress, hideProfitsFromStaff, managerPinHash, managerPinSalt, taxName, taxRate, isTaxEnabled, address, receiptFooter, showCustomerPhoneOnReceipt, showTaxesOnReceipt, showTotalQuantityOnReceipt, printMerchantCopy, managerCode) SELECT id, name, ownerName, phoneNumber, operator, momoNumber, currency, pinHash, pinSalt, isSetupComplete, allowNegativeStock, printerAddress, hideProfitsFromStaff, managerPinHash, managerPinSalt, taxName, taxRate, isTaxEnabled, address, receiptFooter, showCustomerPhoneOnReceipt, showTaxesOnReceipt, totalQuantityOnReceipt, printMerchantCopy, managerCode FROM boutique")
                db.execSQL("DROP TABLE boutique")
                db.execSQL("ALTER TABLE boutique_new RENAME TO boutique")

                // 2. Correction table 'processed_sms' (Suppression colonnes en trop)
                db.execSQL("CREATE TABLE IF NOT EXISTS `processed_sms_new` (`transactionId` TEXT NOT NULL, `processedDate` INTEGER NOT NULL, PRIMARY KEY(`transactionId`))")
                // On ne garde que les colonnes qui existent dans l'entité v29
                db.execSQL("INSERT INTO processed_sms_new (transactionId, processedDate) SELECT transactionId, processedDate FROM processed_sms")
                db.execSQL("DROP TABLE processed_sms")
                db.execSQL("ALTER TABLE processed_sms_new RENAME TO processed_sms")

                // 3. Correction table 'categories' (Ajout 'type', suppression autres)
                db.execSQL("CREATE TABLE IF NOT EXISTS `categories_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `type` TEXT NOT NULL)")
                db.execSQL("INSERT INTO categories_new (id, name, type) SELECT id, name, 'PRODUCT' FROM categories")
                db.execSQL("DROP TABLE categories")
                db.execSQL("ALTER TABLE categories_new RENAME TO categories")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_categories_name` ON `categories` (`name`)")

                // 4. Correction table 'suppliers' (Divergence totale)
                db.execSQL("CREATE TABLE IF NOT EXISTS `suppliers_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `phone` TEXT, `address` TEXT, `totalDebt` REAL NOT NULL)")
                db.execSQL("INSERT INTO suppliers_new (id, name, phone, address, totalDebt) SELECT id, name, phoneNumber, address, 0.0 FROM suppliers")
                db.execSQL("DROP TABLE suppliers")
                db.execSQL("ALTER TABLE suppliers_new RENAME TO suppliers")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_suppliers_name` ON `suppliers` (`name`)")

                // 5. Correction table 'sessions' (Divergence totale)
                db.execSQL("CREATE TABLE IF NOT EXISTS `sessions_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `staffId` INTEGER, `sellerName` TEXT NOT NULL, `startTime` INTEGER NOT NULL, `endTime` INTEGER, `openingBalance` REAL NOT NULL, `closingBalance` REAL, `expectedBalance` REAL, `totalCashSales` REAL NOT NULL, `totalMomoSales` REAL NOT NULL, `totalExpenses` REAL NOT NULL, `isActive` INTEGER NOT NULL)")
                db.execSQL("INSERT INTO sessions_new (id, staffId, sellerName, startTime, endTime, openingBalance, closingBalance, expectedBalance, totalCashSales, totalMomoSales, totalExpenses, isActive) SELECT id, staffId, staffName, startTime, endTime, startCash, actualEndCash, expectedEndCash, 0.0, 0.0, 0.0, CASE WHEN status = 'OPEN' THEN 1 ELSE 0 END FROM sessions")
                db.execSQL("DROP TABLE sessions")
                db.execSQL("ALTER TABLE sessions_new RENAME TO sessions")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sessions_staffId` ON `sessions` (`staffId`)")

                // 6. Correction table 'staff' (pinSalt nullable, phone ajouté)
                db.execSQL("CREATE TABLE IF NOT EXISTS `staff_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `pinHash` TEXT NOT NULL, `pinSalt` TEXT, `phone` TEXT, `isActive` INTEGER NOT NULL, `role` TEXT NOT NULL)")
                db.execSQL("INSERT INTO staff_new (id, name, pinHash, pinSalt, phone, isActive, role) SELECT id, name, pinHash, pinSalt, NULL, isActive, role FROM staff")
                db.execSQL("DROP TABLE staff")
                db.execSQL("ALTER TABLE staff_new RENAME TO staff")
            }
        }

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
                // Schéma aligné sur StaffEntity (BUG-022).
                // `pinSalt` est NULLABLE : sa nullité est le marqueur d'un hash
                // SHA-256 legacy que MainViewModel.checkPin re-hache en PBKDF2
                // (migration paresseuse). Un NOT NULL rendrait ce mécanisme
                // de sécurité inopérant.
                db.execSQL("CREATE TABLE IF NOT EXISTS `staff` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `pinHash` TEXT NOT NULL, `pinSalt` TEXT, `phone` TEXT, `isActive` INTEGER NOT NULL, `role` TEXT NOT NULL)")
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
                db.execSQL("ALTER TABLE boutique ADD COLUMN showTotalQuantityOnReceipt INTEGER NOT NULL DEFAULT 0")
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
                db.execSQL("CREATE TABLE IF NOT EXISTS `categories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `type` TEXT NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_categories_name` ON `categories` (`name`)")
            }
        }

        val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `suppliers` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `phone` TEXT, `address` TEXT, `totalDebt` REAL NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_suppliers_name` ON `suppliers` (`name`)")
            }
        }

        val MIGRATION_24_25 = object : Migration(24, 25) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `sessions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `staffId` INTEGER, `sellerName` TEXT NOT NULL, `startTime` INTEGER NOT NULL, `endTime` INTEGER, `openingBalance` REAL NOT NULL, `closingBalance` REAL, `expectedBalance` REAL, `totalCashSales` REAL NOT NULL, `totalMomoSales` REAL NOT NULL, `totalExpenses` REAL NOT NULL, `isActive` INTEGER NOT NULL, FOREIGN KEY(`staffId`) REFERENCES `staff`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
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
                    MIGRATION_27_28, MIGRATION_28_29, MIGRATION_29_30
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
                        
                        // Action de re-chiffrement sécurisée (B-025)
                        executeRekey(db, newKeyPassphrase)
                        
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
                            executeRekey(db, newKeyPassphrase)
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

        /**
         * Centralise et sécurise l'exécution de PRAGMA rekey (B-025).
         * Valide que la passphrase est une chaîne hexadécimale de 64 caractères (256 bits).
         */
        private fun executeRekey(db: SQLiteDatabase?, hexPassphrase: String) {
            requireNotNull(db) { "Base de données non initialisée" }
            require(hexPassphrase.matches(Regex("^[0-9a-fA-F]{64}$"))) {
                "Passphrase de re-chiffrement invalide (doit être 64 caractères hex)"
            }
            db.execSQL("PRAGMA rekey = '$hexPassphrase'")
        }
    }
}
