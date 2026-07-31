package com.reconsiliation.caisse.data.repository

import com.reconsiliation.caisse.data.local.dao.VenteWithItems
import com.reconsiliation.caisse.data.local.AppDatabase
import com.reconsiliation.caisse.data.local.dao.AuditWithItems
import com.reconsiliation.caisse.data.local.entity.*
import com.reconsiliation.caisse.sms.SmsParser
import com.reconsiliation.caisse.utils.PhoneUtil
import com.reconsiliation.caisse.utils.FormatUtil
import androidx.room.withTransaction
import android.content.Context
import android.net.Uri
import android.provider.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Date
import java.util.Calendar
import kotlin.math.abs

class MainRepository(private val db: com.reconsiliation.caisse.data.local.AppDatabase, private val context: android.content.Context) {

    companion object {
        /**
         * Version du schéma Room, inscrite dans les métadonnées de sauvegarde
         * (BUG-021). Doit rester alignée sur `@Database(version = …)`.
         */
        const val DATABASE_VERSION = 28
    }
    val boutique: Flow<BoutiqueEntity?> = db.boutiqueDao().getBoutique()
    val allVentes: Flow<List<VenteEntity>> = db.venteDao().getAllVentes()
    val allStock: Flow<List<StockEntity>> = db.stockDao().getAllStock()
    
    fun getSubscriptionStatus(context: Context): Flow<SubscriptionEntity?> {
        return db.subscriptionDao().getSubscription().map { sub ->
            if (sub != null && sub.deviceId == "UNKNOWN") {
                val currentDevice = getDeviceId(context)
                val updated = sub.copy(deviceId = currentDevice)
                updated
            } else sub
        }
    }
    
    val smsErrors: Flow<List<SmsErrorEntity>> = db.smsErrorDao().getUnresolvedErrors()
    val allExpenses: Flow<List<ExpenseEntity>> = db.expenseDao().getAllExpenses()
    val allCustomers: Flow<List<CustomerEntity>> = db.customerDao().getAllCustomers()
    val debtors: Flow<List<CustomerEntity>> = db.customerDao().getDebtors()
    val lowStockAlerts: Flow<List<StockEntity>> = db.stockDao().getLowStockAlerts()
    val allAudits: Flow<List<AuditWithItems>> = db.auditDao().getAllAudits()
    val allSupplies: Flow<List<SupplyEntity>> = db.supplyDao().getAllSupplies()
    val allClosures: Flow<List<ClosureEntity>> = db.closureDao().getAllClosures()
    val allSuppliers: Flow<List<SupplierEntity>> = db.supplierDao().getAllSuppliers()
    val activeSession: Flow<SessionEntity?> = db.sessionDao().getActiveSession()

    // 5. Subscription & Security Logic
    fun getDeviceId(context: Context): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "UNKNOWN"
    }

    private suspend fun checkSubscription() {
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
        
        val key = com.reconsiliation.caisse.utils.LicenseUtil.generateActivationKey(boutique.phoneNumber, monthsToAdd)
        activateSubscription(context, key)
        logAction("AUTO_SUB", "Abonnement auto-activé ($monthsToAdd mois) via SMS MoMo")
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

    suspend fun insertVenteWithItems(vente: VenteEntity, items: List<VenteItemEntity>, role: String? = null): Long {
        return db.withTransaction {
            checkSubscription()
            val activeSession = db.sessionDao().getActiveSession().first()
            val boutique = db.boutiqueDao().getBoutique().first()
            // Generate Sequential Invoice Number (Year-XXXX)
            val year = Calendar.getInstance().get(Calendar.YEAR).toString()
            val lastInvoice = db.venteDao().getMaxInvoiceNumberForYear(year)
            val nextNumber = if (lastInvoice != null) {
                val parts = lastInvoice.split("-")
                if (parts.size == 2) {
                    val num = parts[1].toIntOrNull() ?: 0
                    (num + 1).toString().padStart(4, '0')
                } else "0001"
            } else "0001"
            
            val normalizedPhone = PhoneUtil.normalize(vente.customerPhone)
            var finalVente = vente.copy(
                customerPhone = normalizedPhone,
                sessionId = activeSession?.id,
                invoiceNumber = "$year-$nextNumber"
            )

            // Auto-create/find customer if phone is provided to track loyalty/debt
            if (!normalizedPhone.isNullOrBlank() && finalVente.customerId == null) {
                val customerId = findOrCreateCustomer("Client $normalizedPhone", normalizedPhone)
                finalVente = finalVente.copy(customerId = customerId)
            }

            // Handle Loyalty & Customer Credit (Point 1 & 3)
            if (finalVente.customerId != null) {
                // Deduct loyalty points if used (indicated by a specific discount amount or description)
                if (finalVente.description.contains("Points Fidélité")) {
                    db.customerDao().updateLoyalty(finalVente.customerId!!, -100, 0.0)
                }
                
                // Deduct Customer Credit (Avoir) if used
                // If paymentMethod is NOT CREDIT, and total paid (cash+momo) is less than amount,
                // the difference is assumed to be the Avoir credit used.
                if (finalVente.paymentMethod != "CREDIT") {
                    val paid = finalVente.amountCash + finalVente.amountMomo
                    // Total total should be: Paid + CreditUsed + Debt(if any)
                    // But here NewSaleScreen ensures: Total = Paid + CreditUsed
                    val creditUsed = finalVente.amount - paid
                    if (creditUsed > 0.1) {
                        db.customerDao().updateCreditBalance(finalVente.customerId!!, -creditUsed)
                    }
                }
            }

            val totalPaid = finalVente.amountCash + finalVente.amountMomo
            val creditRemainder = if (finalVente.paymentMethod == "CREDIT") finalVente.amount - totalPaid else 0.0

            // Anomaly Detection (Point 1)
            val history = db.venteDao().getRecentVentes(10)
            val anomaly = com.reconsiliation.caisse.utils.AnomalyEngine.analyzeVente(finalVente, history)
            if (anomaly.blocked) throw Exception(anomaly.message)
            if (anomaly.severity != "INFO") logAction("ANOMALY_DETECTED", anomaly.message, role, anomaly.severity)

            if (finalVente.status == "CONFIRMED") {
                if (finalVente.paymentMethod == "MOMO" && finalVente.transactionId.isNullOrBlank() && role == "STAFF") {
                    throw Exception("Validation manuelle MoMo interdite pour le personnel. Attendez la confirmation SMS.")
                }
                for (item in items) {
                    val stock = db.stockDao().getStockById(item.productId)
                    if (stock != null && (boutique?.allowNegativeStock == false) && stock.quantity < item.quantity) {
                        throw Exception("Stock insuffisant pour ${stock.productName} (Disponible: ${stock.quantity})")
                    }
                }
            }

            val venteId = db.venteDao().insertVente(finalVente)
            val itemsWithId = items.map { it.copy(venteId = venteId) }
            db.venteDao().insertVenteItems(itemsWithId)
            
            if (finalVente.status == "CONFIRMED") {
                items.forEach { item ->
                    // Logic for Composite Products (Recipes)
                    val recipe = db.recipeDao().getRecipeForProductOnce(item.productId)
                    if (recipe.isNotEmpty()) {
                        // It's a composite product, deduct ingredients
                        recipe.forEach { component ->
                            val qtyToDeduct = item.quantity * component.quantityRequired
                            db.stockDao().updateQuantity(component.ingredientProductId, -qtyToDeduct)
                            
                            val after = (db.stockDao().getStockById(component.ingredientProductId)?.quantity ?: 0.0)
                            db.stockMovementDao().insert(StockMovementEntity(
                                productId = component.ingredientProductId,
                                date = Date(),
                                type = "OUT",
                                quantity = qtyToDeduct,
                                reason = "Composant de Vente #${venteId} (${item.productName})",
                                balanceAfter = after
                            ))
                        }
                    } else {
                        // Standard product
                        db.stockDao().updateQuantity(item.productId, -item.quantity)
                        val after = (db.stockDao().getStockById(item.productId)?.quantity ?: 0.0)
                        db.stockMovementDao().insert(StockMovementEntity(
                            productId = item.productId,
                            date = Date(),
                            type = "OUT",
                            quantity = item.quantity,
                            reason = "Vente #$venteId",
                            balanceAfter = after
                        ))
                    }
                }
                if (finalVente.customerId != null) {
                    val points = (finalVente.amount / 1000).toInt()
                    db.customerDao().updateLoyalty(finalVente.customerId, points, finalVente.amount)
                }
            }
            
            if (creditRemainder > 0 && finalVente.customerId != null) {
                db.customerDao().updateDebt(finalVente.customerId, creditRemainder)
            }

            logAction("SALE_CREATE", "Vente #$venteId créée (${vente.amount} FCFA, ${vente.paymentMethod})", role)
            return@withTransaction venteId
        }
    }

    suspend fun updateVente(vente: VenteEntity, newItems: List<VenteItemEntity>? = null, role: String? = null) {
        db.withTransaction {
            checkSubscription()
            val oldVenteWithItems = db.venteDao().getVenteWithItemsById(vente.id) ?: return@withTransaction
            val oldVente = oldVenteWithItems.vente
            val oldItems = oldVenteWithItems.items
            if (oldVente.isLocked) throw Exception("Cette vente est clôturée et ne peut plus être modifiée.")

            // 1. Revert Stock
            if (oldVente.status == "CONFIRMED") {
                oldItems.forEach { item ->
                    db.stockDao().updateQuantity(item.productId, item.quantity)
                    val after = (db.stockDao().getStockById(item.productId)?.quantity ?: 0.0)
                    db.stockMovementDao().insert(StockMovementEntity(productId = item.productId, date = Date(), type = "IN", quantity = item.quantity, reason = "Annulation/Modif Vente #${vente.id}", balanceAfter = after))
                }
            }

            val oldPaid = oldVente.amountCash + oldVente.amountMomo
            val oldCredit = oldVente.amount - oldPaid
            
            // 2. Revert old debt/points
            if (oldVente.customerId != null) {
                if (oldCredit > 0) db.customerDao().updateDebt(oldVente.customerId, -oldCredit)
                val oldPoints = (oldVente.amount / 1000).toInt()
                db.customerDao().updateLoyalty(oldVente.customerId, -oldPoints, -oldVente.amount)
            }

            val itemsToProcess = if (newItems != null) {
                db.venteDao().deleteItemsForVente(vente.id)
                val itemsWithId = newItems.map { it.copy(venteId = vente.id) }
                db.venteDao().insertVenteItems(itemsWithId)
                itemsWithId
            } else {
                oldItems
            }

            // 3. Apply new state
            if (vente.status == "CONFIRMED") {
                val boutique = db.boutiqueDao().getBoutique().first()
                for (item in itemsToProcess) {
                    val stock = db.stockDao().getStockById(item.productId)
                    if (stock != null && (boutique?.allowNegativeStock == false) && stock.quantity < item.quantity) {
                        throw Exception("Stock insuffisant pour ${stock.productName}")
                    }
                }
                itemsToProcess.forEach { item ->
                    db.stockDao().updateQuantity(item.productId, -item.quantity)
                    val after = (db.stockDao().getStockById(item.productId)?.quantity ?: 0.0)
                    db.stockMovementDao().insert(StockMovementEntity(productId = item.productId, date = Date(), type = "OUT", quantity = item.quantity, reason = "Mise à jour Vente #${vente.id}", balanceAfter = after))
                }
            }

            // 4. Apply new debt/points or Avoir
            if (vente.customerId != null) {
                val newPaid = vente.amountCash + vente.amountMomo
                val newCredit = vente.amount - newPaid
                
                if (newCredit > 0) {
                    db.customerDao().updateDebt(vente.customerId, newCredit)
                } else if (newCredit < 0) {
                    db.customerDao().updateCreditBalance(vente.customerId, -newCredit)
                }
                
                if (vente.status == "CONFIRMED") {
                    val newPoints = (vente.amount / 1000).toInt()
                    db.customerDao().updateLoyalty(vente.customerId, newPoints, vente.amount)
                }
            }

            val normalizedVente = vente.copy(customerPhone = PhoneUtil.normalize(vente.customerPhone))
            db.venteDao().updateVente(normalizedVente)
            
            logAction("SALE_UPDATE", "Vente #${vente.id} modifiée", role, "WARNING")
        }
    }

    suspend fun deleteVente(vente: VenteEntity, role: String? = null) {
        db.withTransaction {
            val venteWithItems = db.venteDao().getVenteWithItemsById(vente.id) ?: return@withTransaction
            if (venteWithItems.vente.isLocked) throw Exception("Impossible de supprimer une vente clôturée.")
            
            if (role == "STAFF") {
                // Instead of deleting, mark as pending deletion for manager approval
                db.venteDao().updateVente(venteWithItems.vente.copy(status = "PENDING_DELETION"))
                logAction("SALE_DELETE_REQUEST", "Demande de suppression pour Vente #${vente.id} par STAFF", role, "WARNING")
            } else {
                if (venteWithItems.vente.status == "CONFIRMED") {
                    venteWithItems.items.forEach { item ->
                        db.stockDao().updateQuantity(item.productId, item.quantity)
                    }
                }
                if (venteWithItems.vente.paymentMethod == "CREDIT" && venteWithItems.vente.customerId != null) {
                    db.customerDao().updateDebt(venteWithItems.vente.customerId, -venteWithItems.vente.amount)
                }
                db.venteDao().deleteVente(vente)
                logAction("SALE_DELETE", "Vente #${vente.id} supprimée (Montant: ${vente.amount})", role)
            }
        }
    }

    suspend fun cancelVente(vente: VenteEntity) {
        val cancelledVente = vente.copy(status = "CANCELLED", amount = 0.0)
        updateVente(cancelledVente, emptyList())
    }

    suspend fun confirmOrphanSale(vente: VenteEntity, product: StockEntity, role: String? = null) {
        db.withTransaction {
            val existing = db.venteDao().getVenteWithItemsById(vente.id)
            if (existing != null && existing.vente.isLocked) throw Exception("Vente clôturée.")
            val updatedVente = vente.copy(
                description = product.productName,
                reconciliationStatus = "OK",
                status = "CONFIRMED",
                customerPhone = PhoneUtil.normalize(vente.customerPhone)
            )
            val item = VenteItemEntity(
                venteId = vente.id,
                productId = product.id,
                productName = product.productName,
                quantity = 1.0,
                unitPrice = product.unitPrice,
                purchasePrice = product.purchasePrice
            )
            updateVente(updatedVente, listOf(item), role)
        }
    }

    suspend fun resolveSmsError(error: SmsErrorEntity, venteId: Long, role: String? = null) {
        db.withTransaction {
            val vente = db.venteDao().getVenteById(venteId) ?: throw Exception("Vente non trouvée")
            val parsed = SmsParser.parse(error.body, error.sender) ?: throw Exception("Impossible de lire ce SMS")
            
            val updatedVente = vente.copy(
                status = "CONFIRMED",
                transactionId = parsed.transactionId,
                smsBody = error.body,
                reconciliationStatus = "OK"
            )
            db.venteDao().updateVente(updatedVente)
            
            // Deduct stock if not already deducted
            val items = db.venteDao().getItemsForVente(venteId)
            items.forEach { db.stockDao().updateQuantity(it.productId, -it.quantity) }
            
            db.smsErrorDao().resolve(error.id)
            markSmsAsProcessed(parsed.transactionId)
            logAction("SMS_RESOLVE", "Réconciliation manuelle: SMS ${parsed.transactionId} lié à Vente #$venteId", role)
        }
    }
    suspend fun convertErrorToOrphan(error: SmsErrorEntity, product: StockEntity? = null, role: String? = null): Long {
        return db.withTransaction {
            val parsed = SmsParser.parse(error.body)
            val amount = parsed?.amount ?: 0.0
            val vente = VenteEntity(
                amount = amount,
                amountMomo = amount,
                description = product?.productName ?: "MoMo Manuel (depuis erreur)",
                date = error.date,
                paymentMethod = "MOMO",
                status = "CONFIRMED",
                smsBody = error.body,
                reconciliationStatus = if (product != null) "OK" else "ORPHAN",
                customerPhone = PhoneUtil.normalize(parsed?.sender)
            )
            val venteId = db.venteDao().insertVente(vente)
            if (product != null) {
                val item = VenteItemEntity(
                    venteId = venteId,
                    productId = product.id,
                    productName = product.productName,
                    quantity = 1.0,
                    unitPrice = amount,
                    purchasePrice = product.purchasePrice
                )
                db.venteDao().insertVenteItems(listOf(item))
                db.stockDao().updateQuantity(product.id, -1.0)
            }
            db.smsErrorDao().delete(error)
            logAction("SMS_ORPHAN", "Conversion erreur SMS en vente orpheline: ${parsed?.transactionId}", role)
            venteId
        }
    }

    suspend fun adjustStock(stock: StockEntity, newQuantity: Double, reason: String, role: String? = null) {
        db.withTransaction {
            val diff = newQuantity - stock.quantity
            if (diff == 0.0) return@withTransaction
            db.stockDao().updateQuantity(stock.id, diff)
            if (diff < 0) {
                val lossAmount = abs(diff) * stock.purchasePrice
                db.expenseDao().insert(ExpenseEntity(
                    label = "Perte/Casse: ${stock.productName} ($reason)",
                    amount = lossAmount,
                    date = Date(),
                    category = "Pertes"
                ))
            }
            db.stockMovementDao().insert(StockMovementEntity(
                productId = stock.id,
                date = Date(),
                type = if (diff > 0) "IN" else "LOSS",
                quantity = abs(diff),
                reason = "Ajustement: $reason",
                balanceAfter = newQuantity
            ))
            logAction("STOCK_ADJUST", "Ajustement: ${stock.productName} de ${stock.quantity} à $newQuantity ($reason)", role)
        }
    }

    suspend fun getVenteWithItems(id: Long) = db.venteDao().getVenteWithItemsById(id)

    fun getStockValuation(): Flow<Pair<Double, Double>> {
        return allStock.map { list ->
            val purchaseVal = list.sumOf { it.quantity * it.purchasePrice }
            val saleVal = list.sumOf { it.quantity * it.unitPrice }
            Pair(purchaseVal, saleVal)
        }
    }

    suspend fun updateStock(stock: StockEntity, role: String? = null) {
        db.withTransaction {
            val oldStock = db.stockDao().getStockById(stock.id)
            if (oldStock != null) {
                if (oldStock.purchasePrice != stock.purchasePrice) {
                    db.priceHistoryDao().insert(PriceHistoryEntity(productId = stock.id, oldPrice = oldStock.purchasePrice, newPrice = stock.purchasePrice, date = Date(), type = "PURCHASE"))
                }
                if (oldStock.unitPrice != stock.unitPrice) {
                    db.priceHistoryDao().insert(PriceHistoryEntity(productId = stock.id, oldPrice = oldStock.unitPrice, newPrice = stock.unitPrice, date = Date(), type = "SALE"))
                    logAction("PRICE_CHANGE", "Prix de vente changé pour ${stock.productName}: ${oldStock.unitPrice} -> ${stock.unitPrice}", role)
                }
            }
            if (stock.barcode != null) {
                val existing = db.stockDao().getProductByBarcode(stock.barcode)
                if (existing != null && existing.id != stock.id) {
                    throw Exception("Ce code-barres est déjà utilisé par le produit: ${existing.productName}")
                }
            }
            db.stockDao().insertOrUpdate(stock)
        }
    }

    suspend fun updateStockQuantity(id: Long, delta: Double) = db.stockDao().updateQuantity(id, delta)
    suspend fun getProductByBarcode(barcode: String) = db.stockDao().getProductByBarcode(barcode)
    suspend fun updateBoutique(boutique: BoutiqueEntity) = db.boutiqueDao().insertOrUpdate(boutique)

    suspend fun findOrCreateCustomer(name: String, phone: String): Long {
        val existing = db.customerDao().getCustomerByPhone(phone)
        return existing?.id ?: db.customerDao().insertOrUpdate(CustomerEntity(name = name, phone = phone))
    }

    suspend fun getCustomerByPhone(phone: String) = db.customerDao().getCustomerByPhone(phone)

    suspend fun addRepayment(customerId: Long, amount: Double, method: String, role: String? = null) {
        db.withTransaction {
            checkSubscription()
            val customer = db.customerDao().getCustomerById(customerId) ?: return@withTransaction
            
            // Check if amount exceeds debt
            if (amount > customer.totalDebt) {
                val excess = amount - customer.totalDebt
                db.customerDao().updateDebt(customerId, -customer.totalDebt)
                db.customerDao().updateCreditBalance(customerId, excess)
            } else {
                db.customerDao().updateDebt(customerId, -amount)
            }

            db.repaymentDao().insert(RepaymentEntity(customerId = customerId, amount = amount, date = Date(), paymentMethod = method))
            logAction("REPAYMENT", "Remboursement de ${customer.name}: $amount FCFA ($method)", role)
        }
    }

    suspend fun processMoMoRepayment(phone: String, amount: Double, transactionId: String) {
        db.withTransaction {
            val debtor = db.customerDao().getDebtorByPhone(phone)
            if (debtor != null) {
                addRepayment(debtor.id, amount, "MOMO")
                markSmsAsProcessed(transactionId)
            } else throw Exception("Debtor not found")
        }
    }

    fun getRepayments(customerId: Long) = db.repaymentDao().getRepaymentsForCustomer(customerId)
    fun getVentesByCustomer(customerId: Long) = db.venteDao().getVentesByCustomerId(customerId)
    fun getVentesByDateRange(start: Date, end: Date) = db.venteDao().getVentesByDateRange(start, end)
    fun getTotalByMethod(start: Date, end: Date, method: String) = db.venteDao().getTotalByMethod(start, end, method)
    fun getTotalCash(start: Date, end: Date) = db.venteDao().getTotalCash(start, end)
    fun getTotalMomo(start: Date, end: Date) = db.venteDao().getTotalMomo(start, end)
    fun getExpensesByDateRange(start: Date, end: Date) = db.expenseDao().getExpensesByDateRange(start, end)
    fun getTotalExpenses(start: Date, end: Date) = db.expenseDao().getTotalExpenses(start, end)

    fun getProfitFlow(start: Date, end: Date): Flow<Double> = combine(
        db.venteDao().getProfitForRange(start, end),
        db.expenseDao().getTotalExpenses(start, end)
    ) { salesProfit, totalExpenses ->
        (salesProfit ?: 0.0) - (totalExpenses ?: 0.0)
    }

    fun getSalesByCategory(start: Date, end: Date) = db.venteDao().getSalesByCategory(start, end)
    fun getHourlySales(start: Date, end: Date) = db.venteDao().getHourlySales(start, end)
    fun getDailySales(start: Date, end: Date) = db.venteDao().getDailySales(start, end)
    fun getMonthlySales(start: Date, end: Date) = db.venteDao().getMonthlySales(start, end)
    fun getExpiringSoon() = db.venteDao().getExpiringSupplies(Date())

    suspend fun insertExpense(expense: ExpenseEntity) = db.expenseDao().insert(expense)
    suspend fun deleteExpense(expense: ExpenseEntity) = db.expenseDao().delete(expense)

    suspend fun lockSalesForToday() {
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        val start = calendar.time
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 23)
        calendar.set(java.util.Calendar.MINUTE, 59)
        val end = calendar.time
        db.venteDao().lockSalesForDateRange(start, end)
    }

    suspend fun findPendingMomoSale(amount: Double) = db.venteDao().findPendingMomoSale(amount)
    suspend fun findPendingMomoSaleWithPhone(amount: Double, phone: String) = db.venteDao().findPendingMomoSaleWithPhone(amount, phone)
    suspend fun insertSmsError(body: String, sender: String?) = db.smsErrorDao().insert(SmsErrorEntity(body = body, date = Date(), sender = sender))

    suspend fun addSupplier(supplier: SupplierEntity) = db.supplierDao().insert(supplier)
    suspend fun deleteSupplier(supplier: SupplierEntity) = db.supplierDao().delete(supplier)
    suspend fun updateSupplierDebt(supplierId: Long, amount: Double) = db.supplierDao().updateDebt(supplierId, amount)

    suspend fun addSupply(supply: SupplyEntity, role: String? = null) {
        db.withTransaction {
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

    suspend fun closeSession(balance: Double, role: String? = null) {
        db.withTransaction {
            val active = db.sessionDao().getActiveSession().first()
            if (active != null) {
                val start = active.startTime
                val end = Date()
                
                val cashSales = db.venteDao().getTotalCash(start, end).first() ?: 0.0
                val momoSales = db.venteDao().getTotalMomo(start, end).first() ?: 0.0
                val expenses = db.expenseDao().getTotalExpenses(start, end).first() ?: 0.0
                
                val expected = active.openingBalance + cashSales - expenses
                
                db.sessionDao().closeSession(active.copy(
                    endTime = end,
                    closingBalance = balance,
                    expectedBalance = expected,
                    totalCashSales = cashSales,
                    totalMomoSales = momoSales,
                    totalExpenses = expenses,
                    isActive = false
                ))
                logAction("SESSION_CLOSE", "Session de ${active.sellerName} clôturée. Cash réel: $balance, Attendu: $expected", role)
            }
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
    suspend fun printTicket(venteId: Long, printerAddress: String) {
        val venteWithItems = db.venteDao().getVenteWithItemsById(venteId) ?: return
        val boutique = db.boutiqueDao().getBoutique().first() ?: return
        
        if (printerAddress.isBlank() || printerAddress == "NONE") return

        val printer = com.reconsiliation.caisse.printing.EscPosPrinter(context)
        
        if (printer.connect(printerAddress)) {
            val text = com.reconsiliation.caisse.utils.FormatUtil.generateReceiptText(boutique, venteWithItems.vente, venteWithItems.items, boutique.currency)
            printer.printText(text)
            printer.feed(3)
            printer.disconnect()
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
    suspend fun exportSalesData(context: android.content.Context) {}
    suspend fun shareDailySummary(context: android.content.Context) {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }
        val start = calendar.time
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        val end = calendar.time

        val boutique = db.boutiqueDao().getBoutique().first()
        val cash = db.venteDao().getTotalCash(start, end).first() ?: 0.0
        val momo = db.venteDao().getTotalMomo(start, end).first() ?: 0.0
        val expenses = db.expenseDao().getTotalExpenses(start, end).first() ?: 0.0
        val profit = getProfitFlow(start, end).first()

        val text = FormatUtil.generateDailySummary(
            boutiqueName = boutique?.name ?: "Boutique",
            date = Date(),
            totalSales = cash + momo,
            cashSales = cash,
            momoSales = momo,
            debtsCreated = 0.0, // Need to implement debt tracking for today
            expenses = expenses,
            netProfit = profit,
            currency = boutique?.currency ?: "FCFA"
        )

        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, text)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(android.content.Intent.createChooser(intent, "Partager le résumé"))
    }

    suspend fun logAction(type: String, details: String, role: String? = null, severity: String = "INFO") {
        db.actionLogDao().insert(ActionLogEntity(date = Date(), actionType = type, details = details, userRole = role ?: "SYSTEM", severity = severity))
    }

    suspend fun returnVenteItem(venteId: Long, productId: Long, quantity: Double, refundAmount: Double, refundMethod: String = "AVOIR", role: String? = null) {
        db.withTransaction {
            val venteWithItems = db.venteDao().getVenteWithItemsById(venteId) ?: return@withTransaction
            val item = venteWithItems.items.find { it.productId == productId } ?: return@withTransaction
            if (quantity > item.quantity) throw Exception("Quantité invalide")
            
            // 1. Stock Return
            db.stockDao().updateQuantity(productId, quantity)
            val after = (db.stockDao().getStockById(productId)?.quantity ?: 0.0)
            db.stockMovementDao().insert(StockMovementEntity(
                productId = productId,
                date = Date(),
                type = "IN",
                quantity = quantity,
                reason = "Retour sur Vente #$venteId",
                balanceAfter = after
            ))
            
            val vente = venteWithItems.vente
            
            // 2. Financial Refund Logic
            if (refundMethod == "CASH") {
                // Record as an expense to balance the cash
                db.expenseDao().insert(ExpenseEntity(
                    label = "Remboursement Cash: ${item.productName} (Vente #$venteId)",
                    amount = refundAmount,
                    date = Date(),
                    category = "Remboursements"
                ))
                logAction("SALE_RETURN_CASH", "Remboursement Cash de $refundAmount pour Vente #$venteId", role, "WARNING")
            } else {
                // Logic for credit (Avoir) distribution
                val debtRemainder = vente.amount - (vente.amountCash + vente.amountMomo)
                if (debtRemainder > 0) {
                    if (debtRemainder >= refundAmount) {
                        if (vente.customerId != null) db.customerDao().updateDebt(vente.customerId, -refundAmount)
                    } else {
                        if (vente.customerId != null) {
                            db.customerDao().updateDebt(vente.customerId, -debtRemainder)
                            db.customerDao().updateCreditBalance(vente.customerId, refundAmount - debtRemainder)
                        }
                    }
                } else {
                    if (vente.customerId != null) db.customerDao().updateCreditBalance(vente.customerId, refundAmount)
                }
                logAction("SALE_RETURN_AVOIR", "Retour avec Avoir de $refundAmount pour Vente #$venteId", role, "INFO")
            }

            // 3. Update Vente totals
            val updatedVente = vente.copy(amount = vente.amount - refundAmount)
            db.venteDao().updateVente(updatedVente)
            
            if (updatedVente.customerId != null) {
                db.customerDao().updateLoyalty(updatedVente.customerId, 0, -refundAmount)
            }
        }
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
    /** Lecture ponctuelle de la boutique, hors flux réactif. */
    suspend fun getBoutiqueOnce(): BoutiqueEntity? = db.boutiqueDao().getBoutiqueOnce()

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

    suspend fun deleteStock(stock: StockEntity) {
        db.withTransaction {
            if (db.venteDao().isProductUsed(stock.id)) throw Exception("Historique présent")
            db.stockDao().delete(stock)
        }
    }

    fun searchStock(query: String) = db.stockDao().searchStock(query)
    fun getTopProfitableProducts(limit: Int) = db.stockDao().getTopProfitableProducts(limit)

    // Category Management
    fun getCategories(type: String) = db.categoryDao().getCategoriesByType(type)
    suspend fun addCategory(category: CategoryEntity) = db.categoryDao().insert(category)
    suspend fun deleteCategory(category: CategoryEntity) = db.categoryDao().delete(category)

    suspend fun performAudit(note: String?, items: List<AuditItemEntity>) {
        db.withTransaction {
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

    fun getPriceHistory(productId: Long) = db.priceHistoryDao().getHistoryForProduct(productId)
    suspend fun markSmsAsProcessed(transactionId: String) = db.processedSmsDao().insert(ProcessedSmsEntity(transactionId))
    suspend fun isSmsProcessed(transactionId: String) = db.processedSmsDao().exists(transactionId)

    suspend fun performClosure(closure: ClosureEntity, role: String? = null) {
        db.withTransaction {
            checkSubscription()
            db.closureDao().insert(closure)
            lockSalesForToday()
            logAction("DAY_CLOSURE", "Clôture de journée effectuée (Cash réel: ${closure.actualCash})", role)
        }
    }

    suspend fun openSession(seller: String, balance: Double, staffId: Long? = null) {
        db.sessionDao().openSession(SessionEntity(
            sellerName = seller,
            startTime = Date(),
            openingBalance = balance,
            staffId = staffId
        ))
    }

    suspend fun purgeOldData(months: Int, role: String? = null): Int {
        return db.withTransaction {
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

    // Export manuel de la base : copie puis partage via Intent.
    // ⚠️ Ce n'est PAS une synchronisation cloud : aucun envoi automatique.
    // Une vraie sauvegarde distante est planifiée (B-112).
    suspend fun syncToCloud(context: Context, role: String? = null) {
        val dbFile = context.getDatabasePath("caisse_database")
        if (dbFile.exists()) {
            val backupFile = File(context.cacheDir, "caisse_export_${System.currentTimeMillis()}.db")
            dbFile.copyTo(backupFile, overwrite = true)
            
            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", backupFile)
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "application/octet-stream"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(android.content.Intent.createChooser(intent, "Exporter et partager"))
            logAction("EXPORT_SHARE", "Export manuel de la base initié", role)
        }
    }

    // PDF Statement Generation (Multi-page Support)
    fun generateCustomerStatementPdf(context: Context, customer: CustomerEntity, ventes: List<VenteWithItems>): File? {
        val fileName = "Releve_${customer.name}_${System.currentTimeMillis()}.pdf"
        val file = File(context.cacheDir, fileName)
        
        return try {
            val pdfDocument = android.graphics.pdf.PdfDocument()
            val paint = android.graphics.Paint()
            val titlePaint = android.graphics.Paint().apply {
                textSize = 18f
                isFakeBoldText = true
            }
            val textPaint = android.graphics.Paint().apply {
                textSize = 12f
            }
            val headerPaint = android.graphics.Paint().apply {
                textSize = 12f
                isFakeBoldText = true
            }

            var currentPageNumber = 1
            var y = 50f
            val margin = 50f
            val pageWidth = 595
            val pageHeight = 842

            fun createNewPage(): android.graphics.pdf.PdfDocument.Page {
                val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNumber++).create()
                val page = pdfDocument.startPage(pageInfo)
                y = 50f
                return page
            }

            var page = createNewPage()
            var canvas = page.canvas

            // Header on first page
            canvas.drawText("RELEVÉ DE COMPTE CLIENT", 180f, y, titlePaint)
            y += 40f
            canvas.drawText("Client: ${customer.name}", margin, y, textPaint)
            y += 20f
            canvas.drawText("Téléphone: ${customer.phone}", margin, y, textPaint)
            y += 20f
            canvas.drawText("Dette Totale: ${com.reconsiliation.caisse.utils.FormatUtil.formatCurrency(customer.totalDebt)}", margin, y, textPaint)
            y += 40f

            // Table Headers
            canvas.drawText("Date", margin, y, headerPaint)
            canvas.drawText("Détail", 150f, y, headerPaint)
            canvas.drawText("Montant", 450f, y, headerPaint)
            y += 10f
            canvas.drawLine(margin, y, pageWidth - margin, y, textPaint)
            y += 25f

            ventes.forEach { v ->
                if (y > pageHeight - 50) {
                    pdfDocument.finishPage(page)
                    page = createNewPage()
                    canvas = page.canvas
                    // Repeat headers on new page
                    canvas.drawText("Date", margin, y, headerPaint)
                    canvas.drawText("Détail", 150f, y, headerPaint)
                    canvas.drawText("Montant", 450f, y, headerPaint)
                    y += 30f
                }

                canvas.drawText(com.reconsiliation.caisse.utils.FormatUtil.formatShortDate(v.vente.date), margin, y, textPaint)
                
                // Truncate description if too long for the column
                val desc = if (v.vente.description.length > 35) v.vente.description.take(32) + "..." else v.vente.description
                canvas.drawText(desc, 150f, y, textPaint)
                
                canvas.drawText(com.reconsiliation.caisse.utils.FormatUtil.formatCurrency(v.vente.amount), 450f, y, textPaint)
                y += 25f
            }

            pdfDocument.finishPage(page)
            FileOutputStream(file).use { pdfDocument.writeTo(it) }
            pdfDocument.close()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
