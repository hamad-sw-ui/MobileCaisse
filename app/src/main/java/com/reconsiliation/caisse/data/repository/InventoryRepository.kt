package com.reconsiliation.caisse.data.repository

import com.reconsiliation.caisse.data.local.AppDatabase
import com.reconsiliation.caisse.data.local.dao.*
import com.reconsiliation.caisse.data.local.entity.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class InventoryRepository @Inject constructor(
    private val db: AppDatabase,
    private val stockDao: StockDao,
    private val priceHistoryDao: PriceHistoryDao,
    private val movementDao: StockMovementDao,
    private val expenseDao: ExpenseDao,
    private val actionLogDao: ActionLogDao,
    private val subscriptionDao: SubscriptionDao,
    private val auditDao: AuditDao,
    private val supplyDao: SupplyDao,
    private val supplierDao: SupplierDao,
    private val categoryDao: CategoryDao,
    private val recipeDao: RecipeDao,
    private val venteDao: VenteDao,
    private val boutiqueDao: BoutiqueDao
) {

    // --- Stock Core ---

    val allStock: Flow<List<StockEntity>> = stockDao.getAllStock()
    val lowStockAlerts: Flow<List<StockEntity>> = stockDao.getLowStockAlerts()

    fun getStockValuation(): Flow<Pair<Double, Double>> {
        return allStock.map { list ->
            val purchaseVal = list.sumOf { it.quantity * it.purchasePrice }
            val saleVal = list.sumOf { it.quantity * it.unitPrice }
            Pair(purchaseVal, saleVal)
        }
    }

    fun searchStock(query: String) = stockDao.searchStock(query)
    fun getTopProfitableProducts(limit: Int) = stockDao.getTopProfitableProducts(limit)

    suspend fun adjustStock(stock: StockEntity, newQuantity: Double, reason: String, role: String? = null) {
        db.runInTransaction {
            val diff = newQuantity - stock.quantity
            if (diff == 0.0) return@runInTransaction
            stockDao.updateQuantity(stock.id, diff)
            if (diff < 0) {
                val lossAmount = abs(diff) * stock.purchasePrice
                expenseDao.insert(ExpenseEntity(
                    label = "Perte/Casse: ${stock.productName} ($reason)",
                    amount = lossAmount,
                    date = Date(),
                    category = "Pertes"
                ))
            }
            movementDao.insert(StockMovementEntity(
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

    suspend fun updateStock(stock: StockEntity, role: String? = null) {
        db.runInTransaction {
            val oldStock = stockDao.getStockById(stock.id)
            if (oldStock != null) {
                if (oldStock.purchasePrice != stock.purchasePrice) {
                    priceHistoryDao.insert(PriceHistoryEntity(productId = stock.id, oldPrice = oldStock.purchasePrice, newPrice = stock.purchasePrice, date = Date(), type = "PURCHASE"))
                }
                if (oldStock.unitPrice != stock.unitPrice) {
                    priceHistoryDao.insert(PriceHistoryEntity(productId = stock.id, oldPrice = oldStock.unitPrice, newPrice = stock.unitPrice, date = Date(), type = "SALE"))
                    logAction("PRICE_CHANGE", "Prix de vente changé pour ${stock.productName}: ${oldStock.unitPrice} -> ${stock.unitPrice}", role)
                }
            }
            if (stock.barcode != null) {
                val existing = stockDao.getProductByBarcode(stock.barcode)
                if (existing != null && existing.id != stock.id) {
                    throw Exception("Ce code-barres est déjà utilisé par le produit: ${existing.productName}")
                }
            }
            stockDao.insertOrUpdate(stock)
        }
    }

    suspend fun updateStockQuantity(id: Long, delta: Double) = stockDao.updateQuantity(id, delta)
    suspend fun getProductByBarcode(barcode: String) = stockDao.getProductByBarcode(barcode)

    suspend fun deleteStock(stock: StockEntity) {
        db.runInTransaction {
            if (venteDao.isProductUsed(stock.id)) throw Exception("Historique présent")
            stockDao.delete(stock)
        }
    }

    fun getPriceHistory(productId: Long): Flow<List<PriceHistoryEntity>> = priceHistoryDao.getHistoryForProduct(productId)
    fun getStockMovements(productId: Long): Flow<List<StockMovementEntity>> = movementDao.getMovementsForProduct(productId)

    // --- Supply / Arrivages ---

    val allSupplies: Flow<List<SupplyEntity>> = supplyDao.getAllSupplies()

    suspend fun addSupply(supply: SupplyEntity, role: String? = null) {
        db.runInTransaction {
            checkSubscription()
            val currentStock = stockDao.getStockById(supply.productId)
            if (currentStock != null) {
                val totalQty = currentStock.quantity + supply.quantity
                if (totalQty > 0) {
                    val newPmp = ((currentStock.quantity * currentStock.purchasePrice) + (supply.quantity * supply.purchasePrice)) / totalQty
                    stockDao.insertOrUpdate(currentStock.copy(purchasePrice = newPmp))
                }
            }
            supplyDao.insert(supply)
            stockDao.updateQuantity(supply.productId, supply.quantity)
            val after = (stockDao.getStockById(supply.productId)?.quantity ?: 0.0)
            movementDao.insert(StockMovementEntity(
                productId = supply.productId,
                date = Date(),
                type = "IN",
                quantity = supply.quantity,
                reason = "Arrivage ${supply.supplierName ?: ""}",
                balanceAfter = after
            ))

            if (!supply.isPaid && supply.supplierId != null) {
                supplierDao.updateDebt(supply.supplierId, supply.quantity * supply.purchasePrice)
            }

            logAction("STOCK_SUPPLY", "Approvisionnement: ${supply.productName} +${supply.quantity}", role)
        }
    }

    // --- Suppliers ---

    val allSuppliers: Flow<List<SupplierEntity>> = supplierDao.getAllSuppliers()
    suspend fun addSupplier(supplier: SupplierEntity) = supplierDao.insert(supplier)
    suspend fun deleteSupplier(supplier: SupplierEntity) = supplierDao.delete(supplier)
    suspend fun updateSupplierDebt(supplierId: Long, amount: Double) = supplierDao.updateDebt(supplierId, amount)

    // --- Audit ---

    val allAudits: Flow<List<AuditWithItems>> = auditDao.getAllAudits()

    suspend fun performAudit(note: String?, items: List<AuditItemEntity>) {
        db.runInTransaction {
            checkSubscription()
            val totalDiscrepancy = items.sumOf { (it.physicalQuantity - it.systemQuantity) * it.purchasePrice }
            val auditId = auditDao.insertAudit(AuditEntity(date = Date(), note = note, totalDiscrepancy = totalDiscrepancy))
            val itemsWithId = items.map { it.copy(auditId = auditId) }
            auditDao.insertAuditItems(itemsWithId)
            items.forEach { item ->
                val diff = item.physicalQuantity - item.systemQuantity
                if (diff != 0.0) {
                    stockDao.updateQuantity(item.productId, diff)
                    if (diff < 0) {
                        expenseDao.insert(ExpenseEntity(
                            label = "Écart audit: ${item.productName}",
                            amount = abs(diff) * item.purchasePrice,
                            date = Date(),
                            category = "Pertes"
                        ))
                    }
                }
            }
        }
    }

    // --- Categories ---

    fun getCategories(type: String) = categoryDao.getCategoriesByType(type)
    suspend fun addCategory(category: CategoryEntity) = categoryDao.insert(category)
    suspend fun deleteCategory(category: CategoryEntity) = categoryDao.delete(category)

    // --- Recipes ---

    fun getRecipeForProduct(parentId: Long) = recipeDao.getRecipeForProduct(parentId)
    suspend fun addRecipeComponent(recipe: RecipeEntity) = recipeDao.insert(recipe)
    suspend fun deleteRecipeComponent(recipe: RecipeEntity) = recipeDao.delete(recipe)
    suspend fun deleteRecipeForProduct(parentId: Long) = recipeDao.deleteRecipeForProduct(parentId)

    // --- Helpers ---

    internal suspend fun logAction(type: String, details: String, role: String? = null, severity: String = "INFO") {
        actionLogDao.insert(ActionLogEntity(date = Date(), actionType = type, details = details, userRole = role ?: "SYSTEM", severity = severity))
    }

    internal suspend fun checkSubscription() {
        var sub = subscriptionDao.getSubscription().first()
        val now = Date()
        
        if (sub == null) {
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_YEAR, -1)
            sub = SubscriptionEntity(
                startDate = now,
                endDate = calendar.time,
                isActive = false,
                deviceId = "UNKNOWN"
            )
            subscriptionDao.insertOrUpdate(sub)
        }

        if (!sub.isActive || sub.endDate.before(now)) {
            throw Exception("Votre abonnement est expiré. Veuillez payer via l'application pour continuer.")
        }
    }
}
