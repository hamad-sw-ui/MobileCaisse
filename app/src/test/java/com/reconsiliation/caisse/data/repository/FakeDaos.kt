package com.reconsiliation.caisse.data.repository

import com.reconsiliation.caisse.data.local.AppDatabase
import com.reconsiliation.caisse.data.local.dao.*
import com.reconsiliation.caisse.data.local.entity.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.util.Date

class FakeStockDao : StockDao {
    val stocks = MutableStateFlow<Map<Long, StockEntity>>(emptyMap())
    override fun getAllStock() = stocks.map { it.values.toList() }
    override suspend fun insertOrUpdate(stock: StockEntity) {
        val current = stocks.value.toMutableMap()
        val id = if (stock.id == 0L) (current.keys.maxOrNull() ?: 0L) + 1 else stock.id
        current[id] = stock.copy(id = id)
        stocks.value = current
    }

    override suspend fun delete(stock: StockEntity) {
        val current = stocks.value.toMutableMap()
        current.remove(stock.id)
        stocks.value = current
    }

    override suspend fun updateQuantity(id: Long, delta: Double) {
        val current = stocks.value.toMutableMap()
        current[id]?.let { current[id] = it.copy(quantity = it.quantity + delta) }
        stocks.value = current
    }

    override fun getLowStockAlerts() =
        stocks.map { it.values.filter { s -> s.quantity <= s.alertThreshold } }

    override suspend fun getStockById(id: Long) = stocks.value[id]
    override suspend fun getProductByBarcode(barcode: String) =
        stocks.value.values.find { it.barcode == barcode }

    override fun searchStock(query: String) = stocks.map {
        it.values.filter { s ->
            s.productName.contains(
                query,
                true
            ) || s.category.contains(query, true)
        }
    }

    override fun getTopProfitableProducts(limit: Int) = stocks.map { it.values.take(limit) }
}

class FakeVenteDao : VenteDao {
    val ventes = MutableStateFlow<Map<Long, VenteEntity>>(emptyMap())
    val items = MutableStateFlow<List<VenteItemEntity>>(emptyList())
    override fun getAllVentesWithItems() = ventes.map { vMap ->
        vMap.values.map {
            VenteWithItems(
                it,
                items.value.filter { i -> i.venteId == it.id })
        }
    }

    override suspend fun getVenteWithItemsById(venteId: Long) = ventes.value[venteId]?.let {
        VenteWithItems(
            it,
            items.value.filter { i -> i.venteId == venteId })
    }

    override fun getVentesByCustomerId(customerId: Long) =
        getAllVentesWithItems().map { list -> list.filter { it.vente.customerId == customerId } }

    override fun getAllVentes() = ventes.map { it.values.toList() }
    override fun getVentesPaged(limit: Int, offset: Int) =
        ventes.map { it.values.drop(offset).take(limit) }

    override fun getVentesByDateRange(start: Date, end: Date) =
        ventes.map { it.values.filter { v -> v.date in start..end } }

    override fun getVentesWithItemsByDateRange(start: Date, end: Date) =
        getAllVentesWithItems().map { list -> list.filter { it.vente.date in start..end } }

    override suspend fun getVenteById(id: Long) = ventes.value[id]
    override suspend fun insertVente(vente: VenteEntity): Long {
        val id = (ventes.value.keys.maxOrNull() ?: 0L) + 1
        ventes.value = ventes.value + (id to vente.copy(id = id))
        return id
    }

    override suspend fun insertVenteItems(newItems: List<VenteItemEntity>) {
        items.value = items.value + newItems
    }

    override suspend fun updateVente(vente: VenteEntity) {
        ventes.value = ventes.value + (vente.id to vente)
    }

    override suspend fun deleteVente(vente: VenteEntity) {
        ventes.value = ventes.value - vente.id
    }

    override suspend fun getVenteByTransactionId(transId: String) =
        ventes.value.values.find { it.transactionId == transId }

    override suspend fun findPendingMomoSale(amount: Double) =
        ventes.value.values.find { it.status == "PENDING" && it.amount == amount }

    override suspend fun findPendingMomoSaleWithPhone(amount: Double, phone: String) =
        ventes.value.values.find { it.status == "PENDING" && it.amount == amount && it.customerPhone == phone }

    override fun getTotalByMethod(start: Date, end: Date, method: String) =
        getVentesByDateRange(start, end).map {
            it.filter { v -> v.paymentMethod == method }.sumOf { v -> v.amount }
        }

    override fun getTotalCash(start: Date, end: Date) =
        getVentesByDateRange(start, end).map { it.sumOf { v -> v.amountCash } }

    override fun getTotalMomo(start: Date, end: Date) =
        getVentesByDateRange(start, end).map { it.sumOf { v -> v.amountMomo } }

    override fun getProfitForRange(start: Date, end: Date) = MutableStateFlow(0.0)
    override fun getSalesByCategory(start: Date, end: Date) =
        MutableStateFlow(emptyList<CategoryReport>())

    override fun getHourlySales(start: Date, end: Date) = MutableStateFlow(emptyList<DailySales>())
    override fun getDailySales(start: Date, end: Date) = MutableStateFlow(emptyList<DailySales>())
    override fun getMonthlySales(start: Date, end: Date) = MutableStateFlow(emptyList<DailySales>())
    override fun getExpiringSupplies(limit: Date) = MutableStateFlow(emptyList<SupplyEntity>())
    override suspend fun lockSalesForDateRange(start: Date, end: Date) = 0
    override suspend fun deleteOldLockedVentes(beforeDate: Date) = 0
    override suspend fun deleteOrphanVenteItems() = 0
    override suspend fun deleteItemsForVente(venteId: Long) {
        items.value = items.value.filter { it.venteId != venteId }
    }

    override suspend fun getItemsForVente(venteId: Long) =
        items.value.filter { it.venteId == venteId }

    override suspend fun getMaxInvoiceNumberForYear(yearPrefix: String) = null
    override suspend fun getRecentVentes(limit: Int) = ventes.value.values.toList()
    override suspend fun isProductUsed(productId: Long) =
        items.value.any { it.productId == productId }
}

class FakeCustomerDao : CustomerDao {
    val customers = MutableStateFlow<Map<Long, CustomerEntity>>(emptyMap())
    override fun getAllCustomers() = customers.map { it.values.toList() }
    override fun getDebtors() = customers.map { it.values.filter { c -> c.totalDebt > 0 } }
    override suspend fun insertOrUpdate(customer: CustomerEntity): Long {
        val id =
            if (customer.id == 0L) (customers.value.keys.maxOrNull() ?: 0L) + 1 else customer.id
        customers.value = customers.value + (id to customer.copy(id = id))
        return id
    }

    override suspend fun updateDebt(id: Long, amount: Double): Int {
        customers.value[id]?.let {
            customers.value = customers.value + (id to it.copy(totalDebt = it.totalDebt + amount))
        }
        return 1
    }

    override suspend fun updateLoyalty(id: Long, points: Int, spent: Double) {}
    override suspend fun getCustomerByPhone(phone: String) =
        customers.value.values.find { it.phone == phone }

    override suspend fun getDebtorByPhone(phone: String) =
        customers.value.values.find { it.phone == phone && it.totalDebt > 0 }

    override suspend fun updateCreditBalance(id: Long, delta: Double): Int = 1
    override suspend fun updateVipStatus(id: Long, isVip: Boolean): Int = 1
    override suspend fun getCustomerById(id: Long) = customers.value[id]
    override suspend fun delete(customer: CustomerEntity) {
        customers.value = customers.value - customer.id
    }
}

class FakeBoutiqueDao : BoutiqueDao {
    private val _boutique = MutableStateFlow<BoutiqueEntity?>(null)
    override fun getBoutique() = _boutique
    override suspend fun getBoutiqueOnce() = _boutique.value
    override suspend fun insertOrUpdate(b: BoutiqueEntity) {
        _boutique.value = b
    }

    override suspend fun updatePin(hash: String) = 1
}

class FakeSubscriptionDao : SubscriptionDao {
    private val _sub = MutableStateFlow<SubscriptionEntity?>(null)
    override fun getSubscription() = _sub
    override suspend fun insertOrUpdate(s: SubscriptionEntity) {
        _sub.value = s
    }
}

class FakeActionLogDao : ActionLogDao {
    override suspend fun insert(log: ActionLogEntity) {}
    override fun getRecentLogs() = MutableStateFlow(emptyList<ActionLogEntity>())
    override suspend fun deleteOldInfoLogs(beforeDate: Date) = 0
    override suspend fun clearLogs() {}
}

class FakeAppDatabase : AppDatabase() {
    private val _boutiqueDao = FakeBoutiqueDao()
    private val _venteDao = FakeVenteDao()
    private val _stockDao = FakeStockDao()
    private val _subscriptionDao = FakeSubscriptionDao()
    private val _customerDao = FakeCustomerDao()
    private val _actionLogDao = FakeActionLogDao()

    override fun boutiqueDao() = _boutiqueDao
    override fun venteDao() = _venteDao
    override fun stockDao() = _stockDao
    override fun subscriptionDao() = _subscriptionDao
    override fun customerDao() = _customerDao
    override fun actionLogDao() = _actionLogDao

    override fun smsErrorDao() = object : SmsErrorDao {
        override fun getUnresolvedErrors() = MutableStateFlow(emptyList<SmsErrorEntity>())
        override suspend fun insert(error: SmsErrorEntity) {}
        override suspend fun delete(error: SmsErrorEntity) {}
        override suspend fun resolve(id: Long) = 1
        override suspend fun update(error: SmsErrorEntity) {}
    }

    override fun repaymentDao() = object : RepaymentDao {
        override suspend fun insert(repayment: RepaymentEntity) {}
        override fun getRepaymentsForCustomer(customerId: Long) =
            MutableStateFlow(emptyList<RepaymentEntity>())
    }

    override fun expenseDao() = object : ExpenseDao {
        override fun getAllExpenses() = MutableStateFlow(emptyList<ExpenseEntity>())
        override fun getExpensesByDateRange(start: Date, end: Date) =
            MutableStateFlow(emptyList<ExpenseEntity>())

        override suspend fun insert(expense: ExpenseEntity) {}
        override suspend fun delete(expense: ExpenseEntity) {}
        override fun getTotalExpenses(start: Date, end: Date) = MutableStateFlow(0.0)
        override fun getExpensesByCategory(start: Date, end: Date) =
            MutableStateFlow(emptyList<com.reconsiliation.caisse.ui.viewmodel.CategoryTotal>())
    }

    override fun processedSmsDao() = object : ProcessedSmsDao {
        override suspend fun insert(sms: ProcessedSmsEntity) {}
        override suspend fun exists(id: String) = false
    }

    override fun auditDao() = object : AuditDao {
        override suspend fun insertAudit(audit: AuditEntity) = 0L
        override suspend fun insertAuditItems(items: List<AuditItemEntity>) {}
        override fun getAllAudits() = MutableStateFlow(emptyList<AuditWithItems>())
        override suspend fun getAuditById(id: Long) = null
    }

    override fun priceHistoryDao() = object : PriceHistoryDao {
        override suspend fun insert(history: PriceHistoryEntity) {}
        override fun getHistoryForProduct(productId: Long) =
            MutableStateFlow(emptyList<PriceHistoryEntity>())

        override fun getHistoryByType(type: String) =
            MutableStateFlow(emptyList<PriceHistoryEntity>())
    }

    override fun supplyDao() = object : SupplyDao {
        override suspend fun insert(supply: SupplyEntity) = 0L
        override fun getAllSupplies() = MutableStateFlow(emptyList<SupplyEntity>())
        override fun getSuppliesForProduct(productId: Long) =
            MutableStateFlow(emptyList<SupplyEntity>())
    }

    override fun closureDao() = object : ClosureDao {
        override suspend fun insert(closure: ClosureEntity) = 0L
        override fun getAllClosures() = MutableStateFlow(emptyList<ClosureEntity>())
    }

    override fun supplierDao() = object : SupplierDao {
        override fun getAllSuppliers() = MutableStateFlow(emptyList<SupplierEntity>())
        override suspend fun insert(supplier: SupplierEntity) = 0L
        override suspend fun updateDebt(id: Long, amount: Double) {}
        override suspend fun delete(supplier: SupplierEntity) {}
    }

    override fun sessionDao() = object : SessionDao {
        override fun getActiveSession() = MutableStateFlow(null)
        override suspend fun openSession(session: SessionEntity) = 0L
        override suspend fun closeSession(session: SessionEntity) {}
        override fun getAllSessions() = MutableStateFlow(emptyList<SessionEntity>())
    }

    override fun stockMovementDao() = object : StockMovementDao {
        override suspend fun insert(movement: StockMovementEntity) {}
        override fun getMovementsForProduct(productId: Long) =
            MutableStateFlow(emptyList<StockMovementEntity>())
    }

    override fun categoryDao() = object : CategoryDao {
        override fun getCategoriesByType(type: String) =
            MutableStateFlow(emptyList<CategoryEntity>())

        override suspend fun insert(category: CategoryEntity) = 0L
        override suspend fun delete(category: CategoryEntity) {}
        override suspend fun exists(name: String, type: String) = false
    }

    override fun recipeDao() = object : RecipeDao {
        override fun getRecipeForProduct(parentId: Long) =
            MutableStateFlow(emptyList<RecipeWithIngredient>())

        override suspend fun getRecipeForProductOnce(parentId: Long) = emptyList<RecipeEntity>()
        override suspend fun insert(recipe: RecipeEntity) {}
        override suspend fun delete(recipe: RecipeEntity) {}
        override suspend fun deleteRecipeForProduct(parentId: Long) {}
    }

    override fun staffDao() = object : StaffDao {
        override fun getAllActiveStaff() = MutableStateFlow(emptyList<StaffEntity>())
        override suspend fun getStaffById(id: Long) = null
        override suspend fun getStaffByPin(pinHash: String) = null
        override suspend fun insertOrUpdate(staff: StaffEntity) = 0L
        override suspend fun delete(staff: StaffEntity) {}
    }

    override suspend fun <R> runInTransaction(block: suspend () -> R): R {
        return block()
    }

    override fun clearAllTables() {}
    override fun createInvalidationTracker() = throw UnsupportedOperationException()
    override fun createOpenHelper(config: androidx.room.DatabaseConfiguration) =
        throw UnsupportedOperationException()
}
