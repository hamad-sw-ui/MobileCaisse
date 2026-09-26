package com.reconsiliation.caisse.data.repository

import com.reconsiliation.caisse.data.local.AppDatabase
import com.reconsiliation.caisse.data.local.dao.*
import com.reconsiliation.caisse.data.local.entity.*
import com.reconsiliation.caisse.sms.SmsParser
import com.reconsiliation.caisse.utils.FormatUtil
import com.reconsiliation.caisse.utils.PhoneUtil
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SalesRepository @Inject constructor(
    private val db: AppDatabase,
    private val mainRepository: MainRepository,
    private val venteDao: VenteDao,
    private val stockDao: StockDao,
    private val customerDao: CustomerDao,
    private val sessionDao: SessionDao,
    private val boutiqueDao: BoutiqueDao,
    private val recipeDao: RecipeDao,
    private val movementDao: StockMovementDao,
    private val smsErrorDao: SmsErrorDao,
    private val expenseDao: ExpenseDao
) {
    private val openSessionMutex = Mutex()
    // 1. Core Sales Flows
    val allVentes: Flow<List<VenteEntity>> = venteDao.getAllVentes()
    val allVentesWithItems: Flow<List<VenteWithItems>> = venteDao.getAllVentesWithItems()
    val allCustomers: Flow<List<CustomerEntity>> = customerDao.getAllCustomers()
    val debtors: Flow<List<CustomerEntity>> = customerDao.getDebtors()
    val activeSession: Flow<SessionEntity?> = sessionDao.getActiveSession()
    val allSessions: Flow<List<SessionEntity>> = sessionDao.getAllSessions()
    val allClosures: Flow<List<ClosureEntity>> = db.closureDao().getAllClosures()
    val smsErrors: Flow<List<SmsErrorEntity>> = smsErrorDao.getUnresolvedErrors()

    // 2. Sales Statistics
    fun getSalesByCategory(start: Date, end: Date) = venteDao.getSalesByCategory(start, end)
    fun getHourlySales(start: Date, end: Date) = venteDao.getHourlySales(start, end)
    fun getDailySales(start: Date, end: Date) = venteDao.getDailySales(start, end)
    fun getMonthlySales(start: Date, end: Date) = venteDao.getMonthlySales(start, end)
    fun getTotalByMethod(start: Date, end: Date, method: String) = venteDao.getTotalByMethod(start, end, method)
    fun getTotalCash(start: Date, end: Date) = venteDao.getTotalCash(start, end)
    fun getTotalMomo(start: Date, end: Date) = venteDao.getTotalMomo(start, end)

    fun getProfitFlow(start: Date, end: Date): Flow<Double> = combine(
        venteDao.getProfitForRange(start, end),
        expenseDao.getTotalExpenses(start, end)
    ) { salesProfit, totalExpenses ->
        (salesProfit ?: 0.0) - (totalExpenses ?: 0.0)
    }

    fun getExpiringSoon() = venteDao.getExpiringSupplies(Date())

    // 3. Sales Operations (CUD)
    suspend fun insertVenteWithItems(vente: VenteEntity, items: List<VenteItemEntity>, role: String? = null): Long {
        return db.runInTransaction {
            mainRepository.checkSubscription()
            val activeSession = sessionDao.getActiveSession().first()
            val boutique = boutiqueDao.getBoutique().first()
            
            // Generate Sequential Invoice Number (Year-XXXX)
            val year = Calendar.getInstance().get(Calendar.YEAR).toString()
            val lastInvoice = venteDao.getMaxInvoiceNumberForYear(year)
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

            // Handle Loyalty & Customer Credit
            if (finalVente.customerId != null) {
                if (finalVente.description.contains("Points Fidélité")) {
                    customerDao.updateLoyalty(finalVente.customerId!!, -100, 0.0)
                }
                
                if (finalVente.paymentMethod != "CREDIT") {
                    val paid = finalVente.amountCash + finalVente.amountMomo
                    val creditUsed = finalVente.amount - paid
                    if (creditUsed > 0.1) {
                        customerDao.updateCreditBalance(finalVente.customerId!!, -creditUsed)
                    }
                }
            }

            val totalPaid = finalVente.amountCash + finalVente.amountMomo
            val creditRemainder = if (finalVente.paymentMethod == "CREDIT") finalVente.amount - totalPaid else 0.0

            // Anomaly Detection
            val history = venteDao.getRecentVentes(10)
            val anomaly = com.reconsiliation.caisse.utils.AnomalyEngine.analyzeVente(finalVente, history)
            if (anomaly.blocked) throw Exception(anomaly.message)
            if (anomaly.severity != "INFO") mainRepository.logAction("ANOMALY_DETECTED", anomaly.message, role, anomaly.severity)

            if (finalVente.status == "CONFIRMED") {
                if (finalVente.paymentMethod == "MOMO" && finalVente.transactionId.isNullOrBlank() && role == "STAFF") {
                    throw Exception("Validation manuelle MoMo interdite pour le personnel. Attendez la confirmation SMS.")
                }
                for (item in items) {
                    val stock = stockDao.getStockById(item.productId)
                    if (stock != null && (boutique?.allowNegativeStock == false) && stock.quantity < item.quantity) {
                        throw Exception("Stock insuffisant pour ${stock.productName} (Disponible: ${stock.quantity})")
                    }
                }
            }

            val venteId = venteDao.insertVente(finalVente)
            val itemsWithId = items.map { it.copy(venteId = venteId) }
            venteDao.insertVenteItems(itemsWithId)
            
            if (finalVente.status == "CONFIRMED") {
                items.forEach { item ->
                    val recipe = recipeDao.getRecipeForProductOnce(item.productId)
                    if (recipe.isNotEmpty()) {
                        recipe.forEach { component ->
                            val qtyToDeduct = item.quantity * component.quantityRequired
                            stockDao.updateQuantity(component.ingredientProductId, -qtyToDeduct)
                            
                            val after = (stockDao.getStockById(component.ingredientProductId)?.quantity ?: 0.0)
                            movementDao.insert(StockMovementEntity(
                                productId = component.ingredientProductId,
                                date = Date(),
                                type = "OUT",
                                quantity = qtyToDeduct,
                                reason = "Composant de Vente #${venteId} (${item.productName})",
                                balanceAfter = after
                            ))
                        }
                    } else {
                        stockDao.updateQuantity(item.productId, -item.quantity)
                        val after = (stockDao.getStockById(item.productId)?.quantity ?: 0.0)
                        movementDao.insert(StockMovementEntity(
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
                    customerDao.updateLoyalty(finalVente.customerId, points, finalVente.amount)
                }
            }
            
            if (creditRemainder > 0 && finalVente.customerId != null) {
                customerDao.updateDebt(finalVente.customerId, creditRemainder)
            }

            mainRepository.logAction("SALE_CREATE", "Vente #$venteId créée (${vente.amount} FCFA, ${vente.paymentMethod})", role)
            return@runInTransaction venteId
        }
    }

    suspend fun updateVente(vente: VenteEntity, newItems: List<VenteItemEntity>? = null, role: String? = null) {
        db.runInTransaction {
            mainRepository.checkSubscription()
            val oldVenteWithItems = venteDao.getVenteWithItemsById(vente.id) ?: return@runInTransaction
            val oldVente = oldVenteWithItems.vente
            val oldItems = oldVenteWithItems.items
            if (oldVente.isLocked) throw Exception("Cette vente est clôturée et ne peut plus être modifiée.")

            // 1. Revert Stock
            if (oldVente.status == "CONFIRMED") {
                oldItems.forEach { item ->
                    stockDao.updateQuantity(item.productId, item.quantity)
                    val after = (stockDao.getStockById(item.productId)?.quantity ?: 0.0)
                    movementDao.insert(StockMovementEntity(productId = item.productId, date = Date(), type = "IN", quantity = item.quantity, reason = "Annulation/Modif Vente #${vente.id}", balanceAfter = after))
                }
            }

            val oldPaid = oldVente.amountCash + oldVente.amountMomo
            val oldCredit = oldVente.amount - oldPaid
            
            // 2. Revert old debt/points
            if (oldVente.customerId != null) {
                if (oldCredit > 0) customerDao.updateDebt(oldVente.customerId, -oldCredit)
                val oldPoints = (oldVente.amount / 1000).toInt()
                customerDao.updateLoyalty(oldVente.customerId, -oldPoints, -oldVente.amount)
            }

            val itemsToProcess = if (newItems != null) {
                venteDao.deleteItemsForVente(vente.id)
                val itemsWithId = newItems.map { it.copy(venteId = vente.id) }
                venteDao.insertVenteItems(itemsWithId)
                itemsWithId
            } else {
                oldItems
            }

            // 3. Apply new state
            if (vente.status == "CONFIRMED") {
                val boutique = boutiqueDao.getBoutique().first()
                for (item in itemsToProcess) {
                    val stock = stockDao.getStockById(item.productId)
                    if (stock != null && (boutique?.allowNegativeStock == false) && stock.quantity < item.quantity) {
                        throw Exception("Stock insuffisant pour ${stock.productName}")
                    }
                }
                itemsToProcess.forEach { item ->
                    stockDao.updateQuantity(item.productId, -item.quantity)
                    val after = (stockDao.getStockById(item.productId)?.quantity ?: 0.0)
                    movementDao.insert(StockMovementEntity(productId = item.productId, date = Date(), type = "OUT", quantity = item.quantity, reason = "Mise à jour Vente #${vente.id}", balanceAfter = after))
                }
            }

            // 4. Apply new debt/points or Avoir
            if (vente.customerId != null) {
                val newPaid = vente.amountCash + vente.amountMomo
                val newCredit = vente.amount - newPaid
                
                if (newCredit > 0) {
                    customerDao.updateDebt(vente.customerId, newCredit)
                } else if (newCredit < 0) {
                    customerDao.updateCreditBalance(vente.customerId, -newCredit)
                }
                
                if (vente.status == "CONFIRMED") {
                    val newPoints = (vente.amount / 1000).toInt()
                    customerDao.updateLoyalty(vente.customerId, newPoints, vente.amount)
                }
            }

            val normalizedVente = vente.copy(customerPhone = PhoneUtil.normalize(vente.customerPhone))
            venteDao.updateVente(normalizedVente)
            
            mainRepository.logAction("SALE_UPDATE", "Vente #${vente.id} modifiée", role, "WARNING")
        }
    }

    suspend fun deleteVente(vente: VenteEntity, role: String? = null) {
        db.runInTransaction {
            val venteWithItems = venteDao.getVenteWithItemsById(vente.id) ?: return@runInTransaction
            if (venteWithItems.vente.isLocked) throw Exception("Impossible de supprimer une vente clôturée.")
            
            if (role == "STAFF") {
                venteDao.updateVente(venteWithItems.vente.copy(status = "PENDING_DELETION"))
                mainRepository.logAction("SALE_DELETE_REQUEST", "Demande de suppression pour Vente #${vente.id} par STAFF", role, "WARNING")
            } else {
                if (venteWithItems.vente.status == "CONFIRMED") {
                    venteWithItems.items.forEach { item ->
                        stockDao.updateQuantity(item.productId, item.quantity)
                    }
                }
                if (venteWithItems.vente.paymentMethod == "CREDIT" && venteWithItems.vente.customerId != null) {
                    customerDao.updateDebt(venteWithItems.vente.customerId, -venteWithItems.vente.amount)
                }
                venteDao.deleteVente(vente)
                mainRepository.logAction("SALE_DELETE", "Vente #${vente.id} supprimée (Montant: ${vente.amount})", role)
            }
        }
    }

    suspend fun cancelVente(vente: VenteEntity) {
        val cancelledVente = vente.copy(status = "CANCELLED", amount = 0.0)
        updateVente(cancelledVente, emptyList())
    }

    suspend fun confirmOrphanSale(vente: VenteEntity, product: StockEntity, role: String? = null) {
        db.runInTransaction {
            val existing = venteDao.getVenteWithItemsById(vente.id)
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

    suspend fun returnVenteItem(venteId: Long, productId: Long, quantity: Double, refundAmount: Double, refundMethod: String = "AVOIR", role: String? = null) {
        db.runInTransaction {
            val venteWithItems = venteDao.getVenteWithItemsById(venteId) ?: return@runInTransaction
            val item = venteWithItems.items.find { it.productId == productId } ?: return@runInTransaction
            if (quantity > item.quantity) throw Exception("Quantité invalide")
            
            // 1. Stock Return
            stockDao.updateQuantity(productId, quantity)
            val after = (stockDao.getStockById(productId)?.quantity ?: 0.0)
            movementDao.insert(StockMovementEntity(
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
                expenseDao.insert(ExpenseEntity(
                    label = "Remboursement Cash: ${item.productName} (Vente #$venteId)",
                    amount = refundAmount,
                    date = Date(),
                    category = "Remboursements"
                ))
                mainRepository.logAction("SALE_RETURN_CASH", "Remboursement Cash de $refundAmount pour Vente #$venteId", role, "WARNING")
            } else {
                val debtRemainder = vente.amount - (vente.amountCash + vente.amountMomo)
                if (debtRemainder > 0) {
                    if (debtRemainder >= refundAmount) {
                        if (vente.customerId != null) customerDao.updateDebt(vente.customerId, -refundAmount)
                    } else {
                        if (vente.customerId != null) {
                            customerDao.updateDebt(vente.customerId, -debtRemainder)
                            customerDao.updateCreditBalance(vente.customerId, refundAmount - debtRemainder)
                        }
                    }
                } else {
                    if (vente.customerId != null) customerDao.updateCreditBalance(vente.customerId, refundAmount)
                }
                mainRepository.logAction("SALE_RETURN_AVOIR", "Retour avec Avoir de $refundAmount pour Vente #$venteId", role, "INFO")
            }

            // 3. Update Vente totals
            val updatedVente = vente.copy(amount = vente.amount - refundAmount)
            venteDao.updateVente(updatedVente)
            
            if (updatedVente.customerId != null) {
                customerDao.updateLoyalty(updatedVente.customerId, 0, -refundAmount)
            }
        }
    }

    suspend fun getVenteWithItems(id: Long) = venteDao.getVenteWithItemsById(id)
    fun getVentesByCustomer(customerId: Long) = venteDao.getVentesByCustomerId(customerId)
    fun getVentesByDateRange(start: Date, end: Date) = venteDao.getVentesByDateRange(start, end)
    suspend fun findPendingMomoSale(amount: Double) = venteDao.findPendingMomoSale(amount)
    suspend fun findPendingMomoSaleWithPhone(amount: Double, phone: String) = venteDao.findPendingMomoSaleWithPhone(amount, phone)
    suspend fun getVenteByTransactionId(transactionId: String) = venteDao.getVenteByTransactionId(transactionId)

    // 4. Sessions
    /**
     * Ouvre une session seulement s'il n'en existe aucune d'active.
     * @return false si une session est déjà active : rien n'est créé.
     */
    suspend fun openSession(seller: String, balance: Double, staffId: Long? = null): Boolean {
        return openSessionMutex.withLock {
            db.runInTransaction {
                if (sessionDao.getActiveSession().first() != null) return@runInTransaction false
                sessionDao.openSession(SessionEntity(
                    sellerName = seller,
                    startTime = Date(),
                    openingBalance = balance,
                    staffId = staffId
                ))
                true
            }
        }
    }

    suspend fun previewActiveSession(): SessionPreview? {
        val active = sessionDao.getActiveSession().first() ?: return null
        val endTime = Date()
        val cashSales = venteDao.getTotalCash(active.startTime, endTime).first() ?: 0.0
        val expenses = expenseDao.getTotalExpenses(active.startTime, endTime).first() ?: 0.0
        return SessionPreview(
            sellerName = active.sellerName,
            openingBalance = active.openingBalance,
            cashSales = cashSales,
            expenses = expenses,
            expectedCash = expectedSessionCash(active.openingBalance, cashSales, expenses)
        )
    }

    suspend fun closeSession(balance: Double, role: String? = null) {
        db.runInTransaction {
            val active = sessionDao.getActiveSession().first()
            if (active != null) {
                val start = active.startTime
                val end = Date()
                
                val cashSales = venteDao.getTotalCash(start, end).first() ?: 0.0
                val momoSales = venteDao.getTotalMomo(start, end).first() ?: 0.0
                val expenses = expenseDao.getTotalExpenses(start, end).first() ?: 0.0
                
                val expected = expectedSessionCash(active.openingBalance, cashSales, expenses)
                
                sessionDao.closeSession(active.copy(
                    endTime = end,
                    closingBalance = balance,
                    expectedBalance = expected,
                    totalCashSales = cashSales,
                    totalMomoSales = momoSales,
                    totalExpenses = expenses,
                    isActive = false
                ))
                mainRepository.logAction("SESSION_CLOSE", "Session de ${active.sellerName} clôturée. Cash réel: $balance, Attendu: $expected", role)
            }
        }
    }

    suspend fun performClosure(closure: ClosureEntity, role: String? = null) {
        db.runInTransaction {
            mainRepository.checkSubscription()
            db.closureDao().insert(closure)
            lockSalesForToday()
            mainRepository.logAction("DAY_CLOSURE", "Clôture de journée effectuée (Cash réel: ${closure.actualCash})", role)
        }
    }

    suspend fun lockSalesForToday() {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        val start = calendar.time
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        val end = calendar.time
        venteDao.lockSalesForDateRange(start, end)
    }

    // 5. Customer & Repayment (Sales related)
    suspend fun findOrCreateCustomer(name: String, phone: String): Long {
        val existing = customerDao.getCustomerByPhone(phone)
        return existing?.id ?: customerDao.insertOrUpdate(CustomerEntity(name = name, phone = phone))
    }

    suspend fun getCustomerByPhone(phone: String) = customerDao.getCustomerByPhone(phone)

    suspend fun addRepayment(customerId: Long, amount: Double, method: String, role: String? = null) {
        db.runInTransaction {
            mainRepository.checkSubscription()
            val customer = customerDao.getCustomerById(customerId) ?: return@runInTransaction
            
            if (amount > customer.totalDebt) {
                val excess = amount - customer.totalDebt
                customerDao.updateDebt(customerId, -customer.totalDebt)
                customerDao.updateCreditBalance(customerId, excess)
            } else {
                customerDao.updateDebt(customerId, -amount)
            }

            db.repaymentDao().insert(RepaymentEntity(customerId = customerId, amount = amount, date = Date(), paymentMethod = method))
            mainRepository.logAction("REPAYMENT", "Remboursement de ${customer.name}: $amount FCFA ($method)", role)
        }
    }

    suspend fun processMoMoRepayment(phone: String, amount: Double, transactionId: String) {
        db.runInTransaction {
            val debtor = customerDao.getDebtorByPhone(phone)
            if (debtor != null) {
                addRepayment(debtor.id, amount, "MOMO")
                mainRepository.markSmsAsProcessed(transactionId)
            } else throw Exception("Debtor not found")
        }
    }

    fun getRepayments(customerId: Long) = db.repaymentDao().getRepaymentsForCustomer(customerId)
    suspend fun updateCustomerVipStatus(customerId: Long, isVip: Boolean) = customerDao.updateVipStatus(customerId, isVip)

    // 7. SMS Reconciliation
    suspend fun resolveSmsError(error: SmsErrorEntity, venteId: Long, role: String? = null) {
        db.runInTransaction {
            val vente = venteDao.getVenteById(venteId) ?: throw Exception("Vente non trouvée")
            val parsed = SmsParser.parse(error.body, error.sender) ?: throw Exception("Impossible de lire ce SMS")
            
            val updatedVente = vente.copy(
                status = "CONFIRMED",
                transactionId = parsed.transactionId,
                smsBody = error.body,
                reconciliationStatus = "OK"
            )
            venteDao.updateVente(updatedVente)
            
            val items = venteDao.getItemsForVente(venteId)
            items.forEach { stockDao.updateQuantity(it.productId, -it.quantity) }
            
            smsErrorDao.resolve(error.id)
            mainRepository.markSmsAsProcessed(parsed.transactionId)
            mainRepository.logAction("SMS_RESOLVE", "Réconciliation manuelle: SMS ${parsed.transactionId} lié à Vente #$venteId", role)
        }
    }

    suspend fun convertErrorToOrphan(error: SmsErrorEntity, product: StockEntity? = null, role: String? = null): Long {
        return db.runInTransaction {
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
            val venteId = venteDao.insertVente(vente)
            if (product != null) {
                val item = VenteItemEntity(
                    venteId = venteId,
                    productId = product.id,
                    productName = product.productName,
                    quantity = 1.0,
                    unitPrice = amount,
                    purchasePrice = product.purchasePrice
                )
                venteDao.insertVenteItems(listOf(item))
                stockDao.updateQuantity(product.id, -1.0)
            }
            smsErrorDao.delete(error)
            mainRepository.logAction("SMS_ORPHAN", "Conversion erreur SMS en vente orpheline: ${parsed?.transactionId}", role)
            venteId
        }
    }

    private fun expectedSessionCash(openingBalance: Double, cashSales: Double, expenses: Double): Double =
        openingBalance + cashSales - expenses
}

data class SessionPreview(
    val sellerName: String,
    val openingBalance: Double,
    val cashSales: Double,
    val expenses: Double,
    val expectedCash: Double
)
