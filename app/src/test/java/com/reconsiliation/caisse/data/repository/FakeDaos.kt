package com.reconsiliation.caisse.data.repository

import com.reconsiliation.caisse.data.local.AppDatabase
import com.reconsiliation.caisse.data.local.dao.*
import com.reconsiliation.caisse.data.local.entity.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
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
    override fun getAllVentesWithItems() = combine(ventes, items) { vMap, itemList ->
        vMap.values.map {
            VenteWithItems(
                it,
                itemList.filter { i -> i.venteId == it.id })
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
        ventes.value.values
            .filter { it.status == "PENDING" && (it.amount == amount || it.amountMomo == amount) && (it.paymentMethod == "MOMO" || it.paymentMethod == "MIXED") }
            .sortedByDescending { it.date }
            .firstOrNull()

    override suspend fun findPendingMomoSaleWithPhone(amount: Double, phone: String) =
        ventes.value.values
            .filter { it.status == "PENDING" && (it.amount == amount || it.amountMomo == amount) && (it.customerPhone == phone || phone == "") && (it.paymentMethod == "MOMO" || it.paymentMethod == "MIXED") }
            .sortedByDescending { it.date }
            .firstOrNull()

    override fun getTotalByMethod(start: Date, end: Date, method: String) =
        getVentesByDateRange(start, end).map { list ->
            val filtered = list.filter { v -> v.paymentMethod == method && v.status == "CONFIRMED" }
            if (filtered.isEmpty()) null else filtered.sumOf { it.amount }
        }

    override fun getTotalCash(start: Date, end: Date) =
        getVentesByDateRange(start, end).map { list ->
            val filtered = list.filter { it.status == "CONFIRMED" }
            if (filtered.isEmpty()) null else filtered.sumOf { it.amountCash }
        }

    override fun getTotalMomo(start: Date, end: Date) =
        getVentesByDateRange(start, end).map { list ->
            val filtered = list.filter { it.status == "CONFIRMED" }
            if (filtered.isEmpty()) null else filtered.sumOf { it.amountMomo }
        }

    override fun getProfitForRange(start: Date, end: Date) =
        combine(ventes, items) { vMap, itemList ->
            val list = vMap.values.filter { v -> v.date in start..end && v.status == "CONFIRMED" }
            if (list.isEmpty()) null
            else list.sumOf { v ->
                val itemsForVente = itemList.filter { it.venteId == v.id }
                v.amount - v.fees - v.discount - itemsForVente.sumOf { it.quantity * it.purchasePrice }
            }
        }

    override fun getSalesByCategory(start: Date, end: Date) =
        MutableStateFlow(emptyList<CategoryReport>())

    override fun getHourlySales(start: Date, end: Date) = MutableStateFlow(emptyList<DailySales>())
    override fun getDailySales(start: Date, end: Date) = MutableStateFlow(emptyList<DailySales>())
    override fun getMonthlySales(start: Date, end: Date) = MutableStateFlow(emptyList<DailySales>())
    override fun getExpiringSupplies(limit: Date) = MutableStateFlow(emptyList<SupplyEntity>())
    override suspend fun lockSalesForDateRange(start: Date, end: Date): Int {
        var count = 0
        val current = ventes.value.toMutableMap()
        current.forEach { (id, v) ->
            if (v.date in start..end) {
                current[id] = v.copy(isLocked = true)
                count++
            }
        }
        ventes.value = current
        return count
    }

    override suspend fun deleteOldLockedVentes(beforeDate: Date) = 0
    override suspend fun deleteOrphanVenteItems() = 0
    override suspend fun deleteItemsForVente(venteId: Long) {
        items.value = items.value.filter { it.venteId != venteId }
    }

    override suspend fun getItemsForVente(venteId: Long) =
        items.value.filter { it.venteId == venteId }

    override suspend fun getMaxInvoiceNumberForYear(yearPrefix: String): String? {
        return ventes.value.values
            .filter { it.invoiceNumber?.startsWith(yearPrefix) == true }
            .mapNotNull { it.invoiceNumber }
            .maxOrNull()
    }
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
    override suspend fun updateVipStatus(id: Long, isVip: Boolean): Int {
        customers.value[id]?.let {
            customers.value = customers.value + (id to it.copy(isVip = isVip))
        }
        return 1
    }
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
    private val _logs = MutableStateFlow<List<ActionLogEntity>>(emptyList())
    override suspend fun insert(log: ActionLogEntity) {
        _logs.value = _logs.value + log
    }
    override fun getRecentLogs() = _logs
    override suspend fun deleteOldInfoLogs(beforeDate: Date): Int {
        val oldSize = _logs.value.size
        _logs.value = _logs.value.filter { it.date >= beforeDate || it.severity != "INFO" }
        return oldSize - _logs.value.size
    }
    override suspend fun clearLogs() {
        _logs.value = emptyList()
    }
}

class FakeSessionDao : SessionDao {
    val sessions = MutableStateFlow<Map<Long, SessionEntity>>(emptyMap())
    override fun getActiveSession() = sessions.map { it.values.find { s -> s.isActive } }
    override suspend fun openSession(session: SessionEntity): Long {
        val id = (sessions.value.keys.maxOrNull() ?: 0L) + 1
        sessions.value = sessions.value + (id to session.copy(id = id, isActive = true))
        return id
    }

    override suspend fun closeSession(session: SessionEntity) {
        sessions.value = sessions.value + (session.id to session.copy(isActive = false))
    }

    override fun getAllSessions() = sessions.map { it.values.toList() }
}

class FakeExpenseDao : ExpenseDao {
    private val _expenses = MutableStateFlow<List<ExpenseEntity>>(emptyList())
    override fun getAllExpenses() = _expenses
    override fun getExpensesByDateRange(start: Date, end: Date) =
        _expenses.map { it.filter { e -> e.date in start..end } }

    override suspend fun insert(expense: ExpenseEntity) {
        _expenses.value = _expenses.value + expense
    }

    override suspend fun delete(expense: ExpenseEntity) {
        _expenses.value = _expenses.value.filter { it != expense }
    }

    override fun getTotalExpenses(start: Date, end: Date) =
        getExpensesByDateRange(start, end).map { list ->
            if (list.isEmpty()) null else list.sumOf { it.amount }
        }

    override fun getExpensesByCategory(start: Date, end: Date) =
        getExpensesByDateRange(start, end).map { list ->
            list.groupBy { it.category }.map { (cat, items) ->
                com.reconsiliation.caisse.ui.viewmodel.CategoryTotal(cat, items.sumOf { it.amount })
            }
        }
}

class FakeSmsErrorDao : SmsErrorDao {
    private val _errors = MutableStateFlow<List<SmsErrorEntity>>(emptyList())
    override fun getUnresolvedErrors() = _errors.map { it.filter { e -> !e.isResolved } }
    override suspend fun insert(error: SmsErrorEntity) {
        val id = (_errors.value.maxOfOrNull { it.id } ?: 0L) + 1
        _errors.value = _errors.value + error.copy(id = id)
    }

    override suspend fun delete(error: SmsErrorEntity) {
        _errors.value = _errors.value.filter { it != error }
    }

    override suspend fun resolve(id: Long): Int {
        _errors.value = _errors.value.map { if (it.id == id) it.copy(isResolved = true) else it }
        return 1
    }

    override suspend fun update(error: SmsErrorEntity) {
        _errors.value = _errors.value.map { if (it.id == error.id) error else it }
    }
}

class FakeRepaymentDao : RepaymentDao {
    private val _repayments = MutableStateFlow<List<RepaymentEntity>>(emptyList())
    override suspend fun insert(repayment: RepaymentEntity) {
        _repayments.value = _repayments.value + repayment
    }

    override fun getRepaymentsForCustomer(customerId: Long) =
        _repayments.map { it.filter { r -> r.customerId == customerId } }
}

class FakeClosureDao : ClosureDao {
    private val _closures = MutableStateFlow<List<ClosureEntity>>(emptyList())
    override suspend fun insert(closure: ClosureEntity): Long {
        val id = (_closures.value.maxOfOrNull { it.id } ?: 0L) + 1
        _closures.value = _closures.value + closure.copy(id = id)
        return id
    }

    override fun getAllClosures() = _closures
}

class FakeStockMovementDao : StockMovementDao {
    private val _movements = MutableStateFlow<List<StockMovementEntity>>(emptyList())
    override suspend fun insert(movement: StockMovementEntity) {
        _movements.value = _movements.value + movement
    }

    override fun getMovementsForProduct(productId: Long) =
        _movements.map { it.filter { m -> m.productId == productId } }
}

class FakeAuditDao : AuditDao {
    private val _audits = MutableStateFlow<List<AuditWithItems>>(emptyList())
    override suspend fun insertAudit(audit: AuditEntity): Long {
        val id = (_audits.value.size + 1).toLong()
        _audits.value = _audits.value + AuditWithItems(audit.copy(id = id), emptyList())
        return id
    }
    override suspend fun insertAuditItems(items: List<AuditItemEntity>) {
        if (items.isEmpty()) return
        val auditId = items[0].auditId
        _audits.value = _audits.value.map {
            if (it.audit.id == auditId) it.copy(items = it.items + items) else it
        }
    }
    override fun getAllAudits() = _audits
    override suspend fun getAuditById(id: Long) = _audits.value.find { it.audit.id == id }
}

class FakePriceHistoryDao : PriceHistoryDao {
    private val _history = MutableStateFlow<List<PriceHistoryEntity>>(emptyList())
    override suspend fun insert(history: PriceHistoryEntity) {
        _history.value = _history.value + history
    }
    override fun getHistoryForProduct(productId: Long) =
        _history.map { it.filter { h -> h.productId == productId } }
    override fun getHistoryByType(type: String) =
        _history.map { it.filter { h -> h.type == type } }
}

class FakeSupplyDao : SupplyDao {
    private val _supplies = MutableStateFlow<List<SupplyEntity>>(emptyList())
    override suspend fun insert(supply: SupplyEntity): Long {
        val id = (_supplies.value.size + 1).toLong()
        _supplies.value = _supplies.value + supply.copy(id = id)
        return id
    }
    override fun getAllSupplies() = _supplies
    override fun getSuppliesForProduct(productId: Long) =
        _supplies.map { it.filter { s -> s.productId == productId } }
}

class FakeSupplierDao : SupplierDao {
    private val _suppliers = MutableStateFlow<List<SupplierEntity>>(emptyList())
    override fun getAllSuppliers() = _suppliers
    override suspend fun insert(supplier: SupplierEntity): Long {
        val id = (_suppliers.value.size + 1).toLong()
        _suppliers.value = _suppliers.value + supplier.copy(id = id)
        return id
    }
    override suspend fun updateDebt(id: Long, amount: Double) {
        _suppliers.value = _suppliers.value.map {
            if (it.id == id) it.copy(totalDebt = it.totalDebt + amount) else it
        }
    }
    override suspend fun delete(supplier: SupplierEntity) {
        _suppliers.value = _suppliers.value.filter { it.id != supplier.id }
    }
}

class FakeCategoryDao : CategoryDao {
    private val _categories = MutableStateFlow<List<CategoryEntity>>(emptyList())
    override fun getCategoriesByType(type: String) =
        _categories.map { it.filter { c -> c.type == type } }
    override suspend fun insert(category: CategoryEntity): Long {
        val id = (_categories.value.size + 1).toLong()
        _categories.value = _categories.value + category.copy(id = id)
        return id
    }
    override suspend fun delete(category: CategoryEntity) {
        _categories.value = _categories.value.filter { it.id != category.id }
    }
    override suspend fun exists(name: String, type: String) =
        _categories.value.any { it.name == name && it.type == type }
}

class FakeRecipeDao : RecipeDao {
    private val _recipes = MutableStateFlow<List<RecipeEntity>>(emptyList())
    override fun getRecipeForProduct(parentId: Long) = MutableStateFlow(emptyList<RecipeWithIngredient>()) // Simple mock
    override suspend fun getRecipeForProductOnce(parentId: Long) =
        _recipes.value.filter { it.parentProductId == parentId }
    override suspend fun insert(recipe: RecipeEntity) {
        _recipes.value = _recipes.value + recipe
    }
    override suspend fun delete(recipe: RecipeEntity) {
        _recipes.value = _recipes.value.filter { it != recipe }
    }
    override suspend fun deleteRecipeForProduct(parentId: Long) {
        _recipes.value = _recipes.value.filter { it.parentProductId != parentId }
    }
}

class FakeStaffDao : StaffDao {
    private val _staff = MutableStateFlow<List<StaffEntity>>(emptyList())
    override fun getAllActiveStaff() = _staff.map { it.filter { s -> s.isActive } }
    override suspend fun getStaffById(id: Long) = _staff.value.find { it.id == id }
    override suspend fun getStaffByPin(pinHash: String) = _staff.value.find { it.pinHash == pinHash }
    override suspend fun insertOrUpdate(staff: StaffEntity): Long {
        val id = if (staff.id == 0L) (_staff.value.size + 1).toLong() else staff.id
        _staff.value = _staff.value.filter { it.id != id } + staff.copy(id = id)
        return id
    }
    override suspend fun delete(staff: StaffEntity) {
        _staff.value = _staff.value.filter { it.id != staff.id }
    }
}

class FakeProcessedSmsDao : ProcessedSmsDao {
    private val _sms = MutableStateFlow<Set<String>>(emptySet())
    override suspend fun insert(sms: ProcessedSmsEntity) {
        _sms.value = _sms.value + sms.transactionId
    }
    override suspend fun exists(id: String) = _sms.value.contains(id)
}

class FakeAppDatabase : AppDatabase() {
    private val _boutiqueDao = FakeBoutiqueDao()
    private val _venteDao = FakeVenteDao()
    private val _stockDao = FakeStockDao()
    private val _subscriptionDao = FakeSubscriptionDao()
    private val _customerDao = FakeCustomerDao()
    private val _actionLogDao = FakeActionLogDao()
    private val _sessionDao = FakeSessionDao()
    private val _expenseDao = FakeExpenseDao()
    private val _smsErrorDao = FakeSmsErrorDao()
    private val _repaymentDao = FakeRepaymentDao()
    private val _closureDao = FakeClosureDao()
    private val _movementDao = FakeStockMovementDao()
    private val _auditDao = FakeAuditDao()
    private val _priceHistoryDao = FakePriceHistoryDao()
    private val _supplyDao = FakeSupplyDao()
    private val _supplierDao = FakeSupplierDao()
    private val _categoryDao = FakeCategoryDao()
    private val _recipeDao = FakeRecipeDao()
    private val _staffDao = FakeStaffDao()
    private val _processedSmsDao = FakeProcessedSmsDao()

    override fun boutiqueDao() = _boutiqueDao
    override fun venteDao() = _venteDao
    override fun stockDao() = _stockDao
    override fun subscriptionDao() = _subscriptionDao
    override fun customerDao() = _customerDao
    override fun actionLogDao() = _actionLogDao
    override fun sessionDao() = _sessionDao
    override fun expenseDao() = _expenseDao
    override fun smsErrorDao() = _smsErrorDao
    override fun repaymentDao() = _repaymentDao
    override fun closureDao() = _closureDao
    override fun stockMovementDao() = _movementDao
    override fun auditDao() = _auditDao
    override fun priceHistoryDao() = _priceHistoryDao
    override fun supplyDao() = _supplyDao
    override fun supplierDao() = _supplierDao
    override fun categoryDao() = _categoryDao
    override fun recipeDao() = _recipeDao
    override fun staffDao() = _staffDao
    override fun processedSmsDao() = _processedSmsDao

    override suspend fun <R> runInTransaction(block: suspend () -> R): R {
        return block()
    }

    override fun clearAllTables() {}
    override fun createInvalidationTracker() = throw UnsupportedOperationException()
    override fun createOpenHelper(config: androidx.room.DatabaseConfiguration) =
        throw UnsupportedOperationException()
}

/** Dummy Context for JVM tests */
class FakeContext : android.content.ContextWrapper(null) {
    private val tempDir = java.io.File(System.getProperty("java.io.tmpdir"), "fake_context_${System.nanoTime()}")
    init { tempDir.mkdirs() }
    override fun getCacheDir(): java.io.File = tempDir
    override fun getFilesDir(): java.io.File = tempDir
    override fun getDatabasePath(name: String): java.io.File = java.io.File(tempDir, name)
    override fun getPackageName(): String = "com.reconsiliation.caisse"
    override fun getExternalFilesDir(type: String?): java.io.File? = tempDir
    override fun startActivity(intent: android.content.Intent?) {}
}
