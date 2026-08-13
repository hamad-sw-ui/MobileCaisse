package com.reconsiliation.caisse.data.repository

import com.reconsiliation.caisse.data.local.AppDatabase
import com.reconsiliation.caisse.data.local.dao.AuditWithItems
import com.reconsiliation.caisse.data.local.entity.*
import com.reconsiliation.caisse.utils.FormatUtil
import android.content.Context
import android.net.Uri
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Date
import java.util.Calendar
import javax.inject.Inject
import kotlin.math.abs

class MainRepository @Inject constructor(
    private val db: com.reconsiliation.caisse.data.local.AppDatabase,
    @ApplicationContext private val context: android.content.Context,
    private val inventoryRepository: InventoryRepository
) {

    companion object {
        /**
         * Version du schéma Room, inscrite dans les métadonnées de sauvegarde
         * (BUG-021). Doit rester alignée sur `@Database(version = …)`.
         */
        const val DATABASE_VERSION = 28
    }
    val boutique: Flow<BoutiqueEntity?> = db.boutiqueDao().getBoutique()
    val allStock: Flow<List<StockEntity>> get() = inventoryRepository.allStock
    
    fun getSubscriptionStatus(context: Context): Flow<SubscriptionEntity?> {
        return db.subscriptionDao().getSubscription().map { sub ->
            if (sub != null && sub.deviceId == "UNKNOWN") {
                val currentDevice = getDeviceId(context)
                val updated = sub.copy(deviceId = currentDevice)
                updated
            } else sub
        }
    }
    
    val allExpenses: Flow<List<ExpenseEntity>> = db.expenseDao().getAllExpenses()
    val lowStockAlerts: Flow<List<StockEntity>> get() = inventoryRepository.lowStockAlerts
    val allAudits: Flow<List<AuditWithItems>> = db.auditDao().getAllAudits()
    val allSupplies: Flow<List<SupplyEntity>> = db.supplyDao().getAllSupplies()
    val allSuppliers: Flow<List<SupplierEntity>> = db.supplierDao().getAllSuppliers()
    val recentLogs: Flow<List<ActionLogEntity>> = db.actionLogDao().getRecentLogs()

    fun getExpensesByCategory(start: Date, end: Date): Flow<List<com.reconsiliation.caisse.ui.viewmodel.CategoryTotal>> = db.expenseDao().getExpensesByCategory(start, end)

    fun getDeviceId(context: Context): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "UNKNOWN"
    }

    internal suspend fun checkSubscription() {
        var sub = db.subscriptionDao().getSubscription().first()
        val now = Date()
        
        if (sub == null) {
            val calendar = java.util.Calendar.getInstance()
            calendar.add(java.util.Calendar.DAY_OF_YEAR, -1)
            sub = SubscriptionEntity(
                startDate = now,
                endDate = calendar.time,
                isActive = false,
                deviceId = "UNKNOWN"
            )
            db.subscriptionDao().insertOrUpdate(sub)
        }

        if (!sub.isActive || sub.endDate.before(now)) {
            throw Exception("Votre abonnement est expiré. Veuillez payer via l'application pour continuer.")
        }
    }

    suspend fun activateSubscription(context: Context, key: String) {
        val boutique = db.boutiqueDao().getBoutique().first() ?: throw Exception("Boutique non configurée")
        
        // Use the new LicenseUtil for offline validation tied to the boutique's phone number
        val expiryDate = com.reconsiliation.caisse.utils.LicenseUtil.verifyKey(boutique.phoneNumber, key)
        
        if (expiryDate != null) {
            val sub = SubscriptionEntity(
                id = 1,
                startDate = Date(),
                endDate = expiryDate,
                isActive = true,
                type = "PRO",
                deviceId = getDeviceId(context),
                activationKey = key
            )
            db.subscriptionDao().insertOrUpdate(sub)
            logAction("SUBSCRIPTION_ACTIVATE", "Abonnement activé jusqu'au ${FormatUtil.formatDate(expiryDate)}")
        } else {
            throw Exception("Clé d'activation invalide pour ce numéro (${boutique.phoneNumber})")
        }
    }

    suspend fun processSubscriptionSms(context: Context, amount: Double, transactionId: String) {
        val boutique = db.boutiqueDao().getBoutique().first() ?: return
        val monthsToAdd = when {
            amount >= 45000 -> 12
            amount >= 25000 -> 6
            amount >= 5000 -> 1
            else -> return
        }
        
        // Le générateur de clé a été retiré de l'APK client pour sécurité (B-023).
        // L'auto-activation n'est plus possible en local sans secret.
        logAction("AUTO_SUB_PAYMENT", "Paiement de $amount FCFA reçu (équivalent $monthsToAdd mois). En attente de clé manuelle.", severity = "WARNING")
        markSmsAsProcessed(transactionId)
    }

    fun getPaymentUssd(operator: String, amount: Int): String {
        val myNumber = "692971991"
        return when (operator.uppercase()) {
            "MTN" -> "tel:*126*9*$myNumber*$amount${android.net.Uri.encode("#")}"
            "ORANGE" -> "tel:#150*47*$myNumber*$amount${android.net.Uri.encode("#")}"
            else -> ""
        }
    }

    suspend fun adjustStock(stock: StockEntity, newQuantity: Double, reason: String, role: String? = null) {
        inventoryRepository.adjustStock(stock, newQuantity, reason, role)
    }

    fun getStockValuation(): Flow<Pair<Double, Double>> = inventoryRepository.getStockValuation()

    suspend fun updateStock(stock: StockEntity, role: String? = null) {
        inventoryRepository.updateStock(stock, role)
    }

    suspend fun updateStockQuantity(id: Long, delta: Double) = db.stockDao().updateQuantity(id, delta)
    suspend fun getProductByBarcode(barcode: String) = db.stockDao().getProductByBarcode(barcode)
    suspend fun updateBoutique(boutique: BoutiqueEntity) = db.boutiqueDao().insertOrUpdate(boutique)

    fun getExpensesByDateRange(start: Date, end: Date) = db.expenseDao().getExpensesByDateRange(start, end)
    fun getTotalExpenses(start: Date, end: Date) = db.expenseDao().getTotalExpenses(start, end)


    suspend fun insertExpense(expense: ExpenseEntity) = db.expenseDao().insert(expense)
    suspend fun deleteExpense(expense: ExpenseEntity) = db.expenseDao().delete(expense)


    suspend fun insertSmsError(body: String, sender: String?) = db.smsErrorDao().insert(SmsErrorEntity(body = body, date = Date(), sender = sender))

    suspend fun addSupplier(supplier: SupplierEntity) = db.supplierDao().insert(supplier)
    suspend fun deleteSupplier(supplier: SupplierEntity) = db.supplierDao().delete(supplier)
    suspend fun updateSupplierDebt(supplierId: Long, amount: Double) = db.supplierDao().updateDebt(supplierId, amount)

    suspend fun addSupply(supply: SupplyEntity, role: String? = null) {
        db.runInTransaction {
            checkSubscription()
            val currentStock = db.stockDao().getStockById(supply.productId)
            if (currentStock != null) {
                val totalQty = currentStock.quantity + supply.quantity
                if (totalQty > 0) {
                    val newPmp = ((currentStock.quantity * currentStock.purchasePrice) + (supply.quantity * supply.purchasePrice)) / totalQty
                    db.stockDao().insertOrUpdate(currentStock.copy(purchasePrice = newPmp))
                }
            }
            db.supplyDao().insert(supply)
            db.stockDao().updateQuantity(supply.productId, supply.quantity)
            val after = (db.stockDao().getStockById(supply.productId)?.quantity ?: 0.0)
            db.stockMovementDao().insert(StockMovementEntity(
                productId = supply.productId,
                date = Date(),
                type = "IN",
                quantity = supply.quantity,
                reason = "Arrivage ${supply.supplierName ?: ""}",
                balanceAfter = after
            ))
            
            // Handle Supplier Debt (Point 4)
            if (!supply.isPaid && supply.supplierId != null) {
                db.supplierDao().updateDebt(supply.supplierId, supply.quantity * supply.purchasePrice)
            }

            logAction("STOCK_SUPPLY", "Approvisionnement: ${supply.productName} +${supply.quantity}", role)
        }
    }


    fun sendDebtReminder(context: android.content.Context, customer: CustomerEntity) {
        val boutiqueName = "votre boutique" // In a real case, fetch from db.boutiqueDao().getBoutique().first()
        val message = "Bonjour ${customer.name}, un petit rappel concernant votre dette de ${FormatUtil.formatCurrency(customer.totalDebt)} chez $boutiqueName. Merci de régulariser dès que possible."
        
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, message)
            // Optionally target WhatsApp if installed
            `package` = "com.whatsapp"
        }
        
        try {
            val chooser = android.content.Intent.createChooser(intent, "Envoyer le rappel")
            chooser.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            // Fallback to generic SMS/Share if WhatsApp not found or failed
            val genericIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_TEXT, message)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(android.content.Intent.createChooser(genericIntent, "Envoyer via"))
        }
    }
    suspend fun testPrint(context: android.content.Context, address: String) {
        if (address.isBlank() || address == "NONE") return
        val printer = com.reconsiliation.caisse.printing.EscPosPrinter(context)
        if (printer.connect(address)) {
            printer.testPrint()
            printer.disconnect()
        }
    }
    suspend fun logAction(type: String, details: String, role: String? = null, severity: String = "INFO") {
        db.actionLogDao().insert(ActionLogEntity(date = Date(), actionType = type, details = details, userRole = role ?: "SYSTEM", severity = severity))
    }

    // Recipe Management
    fun getRecipeForProduct(parentId: Long) = db.recipeDao().getRecipeForProduct(parentId)
    suspend fun addRecipeComponent(recipe: RecipeEntity) = db.recipeDao().insert(recipe)
    suspend fun deleteRecipeComponent(recipe: RecipeEntity) = db.recipeDao().delete(recipe)
    suspend fun deleteRecipeForProduct(parentId: Long) = db.recipeDao().deleteRecipeForProduct(parentId)

    // ==================== Sauvegarde chiffrée (BackupManager) ====================

    /**
     * Exporte la base dans une archive chiffrée par [password].
     *
     * Le mot de passe est saisi par l'utilisateur à chaque export et n'est
     * jamais conservé (décision D2-C). Un checkpoint WAL est effectué au
     * préalable : sans lui, les dernières transactions seraient absentes de
     * l'archive (BUG-011).
     *
     * @return le fichier produit, prêt à être partagé.
     */
    suspend fun exportEncryptedBackup(
        context: Context,
        password: String,
        role: String? = null
    ): Result<File> = runCatching {
        val boutique = db.boutiqueDao().getBoutique().first()
            ?: throw com.reconsiliation.caisse.utils.BackupException("Boutique non configurée")

        if (!checkpointWal()) {
            throw com.reconsiliation.caisse.utils.BackupException(
                "Impossible de finaliser la base avant l'export. Réessayez dans un instant."
            )
        }

        val dbFile = context.getDatabasePath("caisse_database")
        val out = File(context.cacheDir, "caisse_backup_${System.currentTimeMillis()}.zip")

        val result = com.reconsiliation.caisse.utils.BackupManager.exportBackupWithPassword(
            dbFile = dbFile,
            outputFile = out,
            password = password,
            boutiquePhone = boutique.phoneNumber,
            boutiqueManagerCode = boutique.managerCode,
            databaseVersion = DATABASE_VERSION
        )
        val file = result.getOrThrow()
        logAction("BACKUP_EXPORT", "Sauvegarde chiffrée créée (${file.length() / 1024} Ko)", role)
        file
    }

    /**
     * Restaure une sauvegarde, chiffrée ou brute.
     *
     * Le format est déterminé par la **signature binaire** du fichier et non par
     * son extension (risque R1) : une archive renommée en `.db` reste détectée
     * comme chiffrée.
     *
     * @param password requis pour une archive chiffrée ; ignoré pour un `.db` brut.
     * @return `true` si la base a été remplacée. L'appelant doit alors relancer
     *         l'application (navigation vers `Splash`).
     */
    suspend fun importEncryptedBackup(
        context: Context,
        backupFile: File,
        password: String?,
        role: String? = null
    ): Result<Boolean> = runCatching {
        when (val format = com.reconsiliation.caisse.utils.BackupFormat.detect(backupFile)) {
            is com.reconsiliation.caisse.utils.BackupFormat.Encrypted -> {
                val pwd = password?.takeIf { it.isNotBlank() }
                    ?: throw com.reconsiliation.caisse.utils.BackupException(
                        "Cette sauvegarde est protégée : saisissez son mot de passe."
                    )
                val boutique = db.boutiqueDao().getBoutique().first()
                val staged = File(context.cacheDir, "restore_decrypted.db")

                val imported = com.reconsiliation.caisse.utils.BackupManager
                    .importBackupWithPassword(
                        backupFile = backupFile,
                        destFile = staged,
                        password = pwd,
                        expectedPhone = boutique?.phoneNumber,
                        expectedManagerCode = boutique?.managerCode
                    ).getOrThrow()

                // Un écart d'identité est signalé, pas bloquant : restaurer la
                // sauvegarde d'une autre boutique est un cas légitime (changement
                // d'appareil). La couche UI avertit l'utilisateur.
                if (!imported.identityMatches) {
                    logAction(
                        "BACKUP_IMPORT",
                        "Restauration d'une sauvegarde d'origine différente " +
                            "(${imported.metadata.boutiquePhone})",
                        role,
                        severity = "WARNING"
                    )
                }

                val ok = restoreDatabase(context, staged)
                staged.delete()
                if (ok) logAction("BACKUP_IMPORT", "Sauvegarde chiffrée restaurée", role)
                ok
            }

            is com.reconsiliation.caisse.utils.BackupFormat.LegacyRaw -> {
                val ok = restoreDatabase(context, backupFile)
                if (ok) logAction("BACKUP_IMPORT", "Sauvegarde non chiffrée restaurée", role)
                ok
            }

            is com.reconsiliation.caisse.utils.BackupFormat.Unknown ->
                throw com.reconsiliation.caisse.utils.BackupException(
                    "Fichier non reconnu : ${format.reason}"
                )
        }
    }

    /** Lit les métadonnées d'une archive sans restaurer quoi que ce soit. */
    suspend fun peekBackupMetadata(
        backupFile: File,
        password: String
    ): Result<com.reconsiliation.caisse.utils.BackupMetadata> =
        com.reconsiliation.caisse.utils.BackupManager.peekMetadata(backupFile, password)

    /**
     * Propose le partage d'un fichier de sauvegarde via le sélecteur système.
     *
     * L'autorité `FileProvider` doit correspondre exactement à celle déclarée
     * au manifeste : une divergence provoque une `IllegalArgumentException`
     * (BUG-025).
     */
    /**
     * Enregistre la configuration initiale de la boutique.
     *
     * Regroupe l'écriture en base et la journalisation : l'écran de
     * configuration n'a plus à connaître ni le DAO ni `logAction`.
     */
    suspend fun completeSetup(boutique: BoutiqueEntity) {
        db.boutiqueDao().insertOrUpdate(boutique)
        logAction("SETUP_COMPLETE", "Configuration initiale terminée")
    }

    /** Restaure la sauvegarde miroir produite par `BackupWorker`, si elle existe. */
    suspend fun restoreMirrorBackup(context: Context): Boolean {
        val mirror = File(context.getExternalFilesDir(null), "caisse_mirror.db")
        if (!mirror.exists() || mirror.length() == 0L) return false
        val restored = restoreDatabase(context, mirror)
        if (restored) logAction("BACKUP_IMPORT", "Restauration depuis la sauvegarde miroir")
        return restored
    }

    /** Présence d'une sauvegarde miroir exploitable. */
    fun hasMirrorBackup(context: Context): Boolean {
        val mirror = File(context.getExternalFilesDir(null), "caisse_mirror.db")
        return mirror.exists() && mirror.length() > 0
    }

    // ==================== Personnel ====================

    /** Employés actifs, flux réactif pour l'écran de gestion. */
    val activeStaff: Flow<List<StaffEntity>> = db.staffDao().getAllActiveStaff()

    /**
     * Crée ou met à jour un employé.
     *
     * Le PIN est haché en PBKDF2 avec un sel unique avant toute écriture : il
     * ne transite jamais en clair vers la base (SECURITY.md).
     */
    suspend fun saveStaff(
        name: String, pin: String, role: String, phone: String? = null,
        existing: StaffEntity? = null
    ) {
        require(name.isNotBlank()) { "Le nom de l'employé est obligatoire" }
        val sec = com.reconsiliation.caisse.utils.SecurityUtil

        val entity = if (pin.isBlank() && existing != null) {
            // Modification sans changement de PIN : on conserve le hash existant.
            existing.copy(name = name.trim(), role = role, phone = phone?.trim())
        } else {
            require(pin.length == 4) { "Le code PIN doit comporter 4 chiffres" }
            val iterations = 100_000
            val salt = sec.generateSalt()
            val hash = sec.hashPinPbkdf2(pin, salt, iterations)
            val saltWithIterations = "$salt:$iterations"
            existing?.copy(
                name = name.trim(), role = role, phone = phone?.trim(),
                pinHash = hash, pinSalt = saltWithIterations
            ) ?: StaffEntity(
                name = name.trim(), pinHash = hash, pinSalt = saltWithIterations,
                phone = phone?.trim(), role = role, isActive = true
            )
        }
        db.staffDao().insertOrUpdate(entity)
        logAction("STAFF_SAVE", "Employé enregistré : ${entity.name} (${entity.role})")
    }

    /**
     * Désactive un employé au lieu de le supprimer.
     *
     * Les ventes et sessions référencent `staffId` : une suppression physique
     * rendrait l'historique incohérent. La désactivation préserve la traçabilité
     * tout en bloquant la connexion.
     */
    suspend fun deactivateStaff(staff: StaffEntity) {
        db.staffDao().insertOrUpdate(staff.copy(isActive = false))
        logAction("STAFF_DEACTIVATE", "Employé désactivé : ${staff.name}", severity = "WARNING")
    }

    suspend fun seedDemoData() = com.reconsiliation.caisse.data.seed.DataSeeder(db).seedSampleData()

    /** Lecture ponctuelle de la boutique, hors flux réactif. */
    suspend fun getBoutiqueOnce(): BoutiqueEntity? = db.boutiqueDao().getBoutiqueOnce()
    suspend fun getAllActiveStaffOnce(): List<StaffEntity> = db.staffDao().getAllActiveStaff().first()
    suspend fun updateStaffSecurityInfo(staff: StaffEntity) = db.staffDao().insertOrUpdate(staff)
    suspend fun updateCustomerVipStatus(customerId: Long, isVip: Boolean) = db.customerDao().updateVipStatus(customerId, isVip)

    fun shareBackupFile(context: Context, file: File) {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(
            android.content.Intent.createChooser(intent, "Exporter et partager")
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** Format d'un fichier de sauvegarde, pour adapter l'interface. */
    fun detectBackupFormat(file: File): com.reconsiliation.caisse.utils.BackupFormat =
        com.reconsiliation.caisse.utils.BackupFormat.detect(file)

    /**
     * Force l'écriture du journal WAL dans le fichier principal de la base.
     *
     * Sans ce point de contrôle, une copie du seul fichier `caisse_database`
     * omet les transactions encore présentes dans `-wal` : la sauvegarde est
     * silencieusement incomplète (BUG-011). Room ouvre la base en
     * `WRITE_AHEAD_LOGGING`, ce cas est donc la règle, pas l'exception.
     *
     * @return true si le checkpoint a réussi ; false en cas d'échec, auquel cas
     *         la sauvegarde ne doit PAS être considérée comme fiable.
     */
    private fun checkpointWal(): Boolean {
        return try {
            db.query("PRAGMA wal_checkpoint(FULL)", emptyArray()).use { cursor ->
                // La colonne 0 vaut 0 si le checkpoint a abouti, 1 s'il a été bloqué
                // par une transaction concurrente.
                if (cursor.moveToFirst()) cursor.getInt(0) == 0 else true
            }
        } catch (e: Exception) {
            android.util.Log.e("MainRepository", "Échec du checkpoint WAL", e)
            false
        }
    }

    /**
     * Copie le fichier de base vers [backupFile], après point de contrôle WAL.
     *
     * La copie passe par un fichier temporaire renommé en fin d'opération : une
     * interruption ne laisse jamais une sauvegarde partielle à la place d'une
     * sauvegarde valide.
     */
    fun backupDatabase(context: Context, backupFile: File): Boolean {
        return try {
            val dbFile = context.getDatabasePath("caisse_database")
            if (!dbFile.exists()) return false

            if (!checkpointWal()) {
                android.util.Log.w(
                    "MainRepository",
                    "Checkpoint WAL non abouti : sauvegarde potentiellement incomplète"
                )
                return false
            }

            val tmp = File(backupFile.parentFile, "${backupFile.name}.tmp")
            FileInputStream(dbFile).channel.use { src ->
                FileOutputStream(tmp).channel.use { dst ->
                    dst.transferFrom(src, 0, src.size())
                    dst.force(true)
                }
            }
            if (backupFile.exists()) backupFile.delete()
            val renamed = tmp.renameTo(backupFile)
            if (!renamed) tmp.delete()
            renamed
        } catch (e: Exception) {
            android.util.Log.e("MainRepository", "Échec de la sauvegarde", e)
            false
        }
    }

    /**
     * Remplace la base courante par le contenu de [backupFile].
     *
     * La copie est d'abord écrite dans un fichier temporaire ; la base n'est
     * fermée qu'une fois cette copie **intégralement réussie** (B-140).
     * L'ancienne implémentation appelait `db.close()` en premier : un échec de
     * copie laissait alors l'application sans base exploitable.
     *
     * Les fichiers `-wal` et `-shm` résiduels sont supprimés : conservés, ils
     * appartiendraient à l'ancienne base et corrompraient celle qui est restaurée.
     *
     * ⚠️ L'appelant doit relancer l'application (navigation vers `Splash`) après
     * un retour `true` : les instances de DAO déjà obtenues pointent vers
     * l'ancien fichier.
     */
    fun restoreDatabase(context: Context, backupFile: File): Boolean {
        val dbFile = context.getDatabasePath("caisse_database")
        val staging = File(context.cacheDir, "restore_staging.db")
        return try {
            if (!backupFile.exists() || backupFile.length() == 0L) return false

            // 1. Copier d'abord : aucune destruction tant que ceci n'a pas abouti.
            FileInputStream(backupFile).channel.use { src ->
                FileOutputStream(staging).channel.use { dst ->
                    dst.transferFrom(src, 0, src.size())
                    dst.force(true)
                }
            }
            if (staging.length() != backupFile.length()) {
                staging.delete()
                return false
            }

            // 2. Seulement maintenant : fermer et remplacer.
            db.close()
            FileInputStream(staging).channel.use { src ->
                FileOutputStream(dbFile).channel.use { dst ->
                    dst.transferFrom(src, 0, src.size())
                    dst.force(true)
                }
            }

            // 3. Purger les journaux de l'ancienne base.
            File(dbFile.parentFile, "${dbFile.name}-wal").delete()
            File(dbFile.parentFile, "${dbFile.name}-shm").delete()

            staging.delete()
            true
        } catch (e: Exception) {
            android.util.Log.e("MainRepository", "Échec de la restauration", e)
            staging.delete()
            false
        }
    }

    fun getPriceHistory(productId: Long): Flow<List<PriceHistoryEntity>> = db.priceHistoryDao().getHistoryForProduct(productId)
    fun getStockMovements(productId: Long): Flow<List<StockMovementEntity>> = db.stockMovementDao().getMovementsForProduct(productId)

    suspend fun deleteStock(stock: StockEntity) {
        db.runInTransaction {
            if (db.venteDao().isProductUsed(stock.id)) throw Exception("Historique présent")
            db.stockDao().delete(stock)
        }
    }

    fun searchStock(query: String) = inventoryRepository.searchStock(query)
    fun getTopProfitableProducts(limit: Int) = inventoryRepository.getTopProfitableProducts(limit)

    // Category Management
    fun getCategories(type: String) = db.categoryDao().getCategoriesByType(type)
    suspend fun addCategory(category: CategoryEntity) = db.categoryDao().insert(category)
    suspend fun deleteCategory(category: CategoryEntity) = db.categoryDao().delete(category)

    suspend fun performAudit(note: String?, items: List<AuditItemEntity>) {
        db.runInTransaction {
            checkSubscription()
            val totalDiscrepancy = items.sumOf { (it.physicalQuantity - it.systemQuantity) * it.purchasePrice }
            val auditId = db.auditDao().insertAudit(AuditEntity(date = Date(), note = note, totalDiscrepancy = totalDiscrepancy))
            val itemsWithId = items.map { it.copy(auditId = auditId) }
            db.auditDao().insertAuditItems(itemsWithId)
            items.forEach { item ->
                val diff = item.physicalQuantity - item.systemQuantity
                if (diff != 0.0) {
                    db.stockDao().updateQuantity(item.productId, diff)
                    if (diff < 0) {
                        db.expenseDao().insert(ExpenseEntity(label = "Écart audit: ${item.productName}", amount = abs(diff) * item.purchasePrice, date = Date(), category = "Pertes"))
                    }
                }
            }
        }
    }

    suspend fun markSmsAsProcessed(transactionId: String) = db.processedSmsDao().insert(ProcessedSmsEntity(transactionId))
    suspend fun isSmsProcessed(transactionId: String) = db.processedSmsDao().exists(transactionId)


    suspend fun purgeOldData(months: Int, role: String? = null): Int {
        return db.runInTransaction {
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.MONTH, -months)
            val beforeDate = calendar.time
            
            var deletedCount = 0
            deletedCount += db.venteDao().deleteOldLockedVentes(beforeDate)
            deletedCount += db.venteDao().deleteOrphanVenteItems()
            deletedCount += db.actionLogDao().deleteOldInfoLogs(beforeDate)
            
            logAction("DATA_PURGE", "Purge des données de plus de $months mois effectuée ($deletedCount entrées supprimées)", role, "CRITICAL")
            deletedCount
        }
    }

    // syncToCloud() a été supprimé (étape 6) : il partageait une copie brute de
    // la base, sans mot de passe utilisateur ni contrôle d'intégrité.
    // Remplacé par exportEncryptedBackup(), qui chiffre l'archive par un mot de
    // passe saisi à chaque export (décision D2-C).
    // Les chemins sans interface — BackupWorker et le miroir de SetupScreen —
    // conservent la copie brute, protégée par SQLCipher : aucun mot de passe
    // n'y est saisissable.


    /**
     * Vérifie si la base de données existante est trop ancienne (< v18).
     *
     * Room ne permet pas de migrer depuis des versions très anciennes sans
     * fournir toutes les migrations intermédiaires. Selon la décision D1,
     * ces bases sont recréées après accord utilisateur.
     *
     * @throws ObsoleteDatabaseException si la version est comprise entre 1 et 17.
     */
    fun checkObsoleteDatabase(context: Context) {
        val dbFile = context.getDatabasePath("caisse_database")
        if (!dbFile.exists()) return

        // Ouverture en SQLite brut (SQLCipher) pour lire la version sans Room.
        // On utilise la clé legacy car une base < v18 utilise forcément celle-ci.
        val keyBytes = com.reconsiliation.caisse.utils.SecurityUtil.getDatabaseKeyCompat(context)
        var sqlDb: net.sqlcipher.database.SQLiteDatabase? = null
        try {
            // SQLCipher attend souvent la clé sous forme de String ou CharArray.
            // Le format exact dépend de comment elle a été initialisée historiquement.
            sqlDb = net.sqlcipher.database.SQLiteDatabase.openDatabase(
                dbFile.absolutePath, keyBytes.joinToString("") { "%02x".format(it) }, 
                null, net.sqlcipher.database.SQLiteDatabase.OPEN_READONLY
            )
            val version = sqlDb?.version ?: 0
            if (version in 1..17) {
                throw ObsoleteDatabaseException(version)
            }
        } catch (e: Exception) {
            if (e is ObsoleteDatabaseException) throw e
            // Les erreurs de déchiffrement (mauvaise clé) ou fichiers corrompus 
            // sont ignorées ici car elles ne permettent pas d'affirmer que 
            // la base est obsolète. Room les gérera ensuite.
        } finally {
            sqlDb?.close()
        }
    }

    /** Supprime physiquement les fichiers de la base de données. */
    fun deletePhysicalDatabase(context: Context) {
        db.close()
        context.deleteDatabase("caisse_database")
    }
}

/** Exception levée lorsqu'une base de données est trop ancienne pour être migrée. */
class ObsoleteDatabaseException(val version: Int) : Exception("Base de données obsolète (v$version)")
