package com.reconsiliation.caisse.ui.viewmodel

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.reconsiliation.caisse.data.local.AppDatabase
import com.reconsiliation.caisse.data.local.entity.*
import com.reconsiliation.caisse.data.repository.MainRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import java.io.File

import java.io.Serializable
import kotlinx.coroutines.flow.first

data class CartItem(
    val product: StockEntity, 
    val quantity: Double, 
    val priceAtSale: Double,
    val isRetail: Boolean = false,
    val parentId: Long? = null,
    val conversionFactor: Double = 1.0
) : Serializable

data class CategoryTotal(val category: String, val total: Double)

class MainViewModel(application: Application, private val savedStateHandle: SavedStateHandle) : AndroidViewModel(application) {
    private val repository: MainRepository
    private val db: AppDatabase = AppDatabase.getDatabase(application)
    
    // User Session (Persistent within app run)
    private val _userRole = savedStateHandle.getStateFlow<String?>("user_role", null)
    val userRole = _userRole
    
    private val _staffId = savedStateHandle.getStateFlow<Long?>("staff_id", null)
    val staffId = _staffId

    fun login(role: String, id: Long? = null) { 
        savedStateHandle["user_role"] = role 
        savedStateHandle["staff_id"] = id
        recordActivity()
    }
    fun logout() { 
        savedStateHandle["user_role"] = null 
        savedStateHandle["staff_id"] = null
        _showPinDialog.value = false
        pendingRoute = null
    }
    fun isManager() = _userRole.value == "MANAGER"

    // Global Access Control
    private val _showPinDialog = MutableStateFlow(false)
    val showPinDialog = _showPinDialog.asStateFlow()
    private var pendingRoute: String? = null
    
    private val _lastActivityTime = MutableStateFlow(System.currentTimeMillis())
    
    fun recordActivity() {
        _lastActivityTime.value = System.currentTimeMillis()
    }

    fun requestAccess(route: String) {
        val currentRole = _userRole.value
        val isManagerRequired = isManagerPageRoute(route)

        if (currentRole == "MANAGER") {
            // Manager has access to everything
        } else if (currentRole == "STAFF" && !isManagerRequired) {
            // Staff accessing non-manager page
        } else {
            // Prompt PIN for access
            pendingRoute = route
            _showPinDialog.value = true
        }
    }

    private fun isManagerPageRoute(route: String): Boolean {
        // List of routes that strictly require MANAGER role
        return route in listOf(
            "settings",
            "reports",
            "audit",
            "action_logs",
            "maintenance",
            "closure_history",
            "edit_boutique",
            "suppliers",
            "categories",
            "restoration_wizard"
        )
    }

    suspend fun handlePinInput(pin: String): Boolean {
        val result = checkPin(pin)
        if (result != null) {
            login(result.first, result.second)
            _showPinDialog.value = false
            return true
        }
        return false
    }

    fun getPendingRouteAndClear(): String? {
        val route = pendingRoute
        pendingRoute = null
        return route
    }

    fun dismissPinDialog() {
        _showPinDialog.value = false
        pendingRoute = null
    }

    val boutique: StateFlow<BoutiqueEntity?>
    val currency: StateFlow<String>

    val allVentes: StateFlow<List<VenteEntity>>
    val allVentesWithItems: StateFlow<List<com.reconsiliation.caisse.data.local.dao.VenteWithItems>>
    val allStock: StateFlow<List<StockEntity>>
    val smsErrors: StateFlow<List<SmsErrorEntity>>
    val debtors: StateFlow<List<CustomerEntity>>
    val allCustomers: StateFlow<List<CustomerEntity>>
    val allExpenses: StateFlow<List<ExpenseEntity>>
    val lowStockAlerts: StateFlow<List<StockEntity>>
    val stockValuation: StateFlow<Pair<Double, Double>>
    val topProducts: StateFlow<List<StockEntity>>
    val subscription: StateFlow<SubscriptionEntity?>
    val allClosures: StateFlow<List<ClosureEntity>>
    val recentLogs: StateFlow<List<ActionLogEntity>>
    val activeSession: StateFlow<SessionEntity?>
    val allSessions: StateFlow<List<SessionEntity>>
    val allSuppliers: StateFlow<List<SupplierEntity>>
    val allSupplies: StateFlow<List<SupplyEntity>>
    val allAudits: StateFlow<List<com.reconsiliation.caisse.data.local.dao.AuditWithItems>>
    val todayProfit: StateFlow<Double>
    val todayRevenue: StateFlow<Double>
    
    private val _categorySales = MutableStateFlow<List<com.reconsiliation.caisse.data.local.dao.CategoryReport>>(emptyList())
    val categorySales = _categorySales.asStateFlow()
    
    private val _dailySales = MutableStateFlow<List<com.reconsiliation.caisse.data.local.dao.DailySales>>(emptyList())
    val dailySales = _dailySales.asStateFlow()

    private val _hourlySales = MutableStateFlow<List<com.reconsiliation.caisse.data.local.dao.DailySales>>(emptyList())
    val hourlySales = _hourlySales.asStateFlow()

    private val _expenseBreakdown = MutableStateFlow<List<CategoryTotal>>(emptyList())
    val expenseBreakdown = _expenseBreakdown.asStateFlow()

    private val _isDataLoaded = MutableStateFlow(false)
    val isDataLoaded = _isDataLoaded.asStateFlow()

    private val _uiError = MutableStateFlow<String?>(null)
    val uiError = _uiError.asStateFlow()

    init {
        repository = MainRepository(db, application)
        val notificationHelper = com.reconsiliation.caisse.utils.NotificationHelper(application)

        // Auto-logout check every minute
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(60000) // 1 minute
                val inactiveTime = System.currentTimeMillis() - _lastActivityTime.value
                if (_userRole.value != null && inactiveTime > 5 * 60 * 1000) { // 5 minutes
                    logout()
                }
            }
        }

        boutique = repository.boutique.stateIn(viewModelScope, SharingStarted.Eagerly, null)
        currency = boutique.map { it?.currency ?: "FCFA" }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "FCFA")

        allVentes = repository.allVentes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        allVentesWithItems = db.venteDao().getAllVentesWithItems().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        allStock = repository.allStock.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        smsErrors = repository.smsErrors.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        debtors = repository.debtors.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        allCustomers = repository.allCustomers.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        allExpenses = repository.allExpenses.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        lowStockAlerts = repository.lowStockAlerts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        stockValuation = repository.getStockValuation().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Pair(0.0, 0.0))
        topProducts = repository.getTopProfitableProducts(5).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        subscription = repository.getSubscriptionStatus(application).stateIn(viewModelScope, SharingStarted.Eagerly, null)
        allClosures = repository.allClosures.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        recentLogs = db.actionLogDao().getRecentLogs().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        activeSession = repository.activeSession.stateIn(viewModelScope, SharingStarted.Eagerly, null)
        allSessions = db.sessionDao().getAllSessions().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        allSuppliers = repository.allSuppliers.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        allSupplies = repository.allSupplies.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        allAudits = repository.allAudits.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        val start = calendar.time
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        val end = calendar.time

        todayProfit = combine(repository.getProfitFlow(start, end), userRole, boutique) { profit, role, b ->
            if (role == "MANAGER" || b?.hideProfitsFromStaff == false) profit else 0.0
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

        todayRevenue = combine(db.venteDao().getTotalCash(start, end), db.venteDao().getTotalMomo(start, end)) { cash, momo ->
            (cash ?: 0.0) + (momo ?: 0.0)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

        loadCurrentStats()

        viewModelScope.launch {
            // Combine multiple flows to ensure critical data is considered "loaded"
            combine(boutique, subscription, activeSession) { _, _, _ ->
                true 
            }.collect {
                _isDataLoaded.value = true 
            }
        }

        viewModelScope.launch {
            lowStockAlerts.collect { alerts ->
                alerts.forEach { stock ->
                    if (stock.quantity <= stock.alertThreshold) {
                        notificationHelper.showLowStockNotification(stock.productName, stock.quantity)
                    }
                }
            }
        }
        catchUpSms(application)
    }

    fun loadCurrentStats(rangeType: String = "MONTH") {
        val calendar = Calendar.getInstance()
        val end = calendar.time
        
        val start = if (rangeType == "YEAR") {
            calendar.set(Calendar.DAY_OF_YEAR, 1)
            calendar.time
        } else {
            calendar.set(Calendar.DAY_OF_MONTH, 1)
            calendar.time
        }

        viewModelScope.launch {
            repository.getSalesByCategory(start, end).collect { _categorySales.value = it }
        }
        viewModelScope.launch {
            if (rangeType == "YEAR") {
                repository.getMonthlySales(start, end).collect { _dailySales.value = it }
            } else {
                repository.getDailySales(start, end).collect { _dailySales.value = it }
            }
        }
        viewModelScope.launch {
            repository.getHourlySales(start, end).collect { _hourlySales.value = it }
        }
        viewModelScope.launch {
            db.expenseDao().getExpensesByCategory(start, end).collect { _expenseBreakdown.value = it }
        }
    }

    private val _selectedMonthVentes = MutableStateFlow<List<VenteEntity>>(emptyList())
    val selectedMonthVentes = _selectedMonthVentes.asStateFlow()

    fun loadVentesForMonth(date: Date) {
        val calendar = Calendar.getInstance()
        calendar.time = date
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        val start = calendar.time
        calendar.add(Calendar.MONTH, 1)
        val end = calendar.time
        
        viewModelScope.launch {
            repository.getVentesByDateRange(start, end).collect { _selectedMonthVentes.value = it }
        }
    }
    fun catchUpSms(application: Application) {
        viewModelScope.launch(Dispatchers.IO) {
            com.reconsiliation.caisse.sms.SmsSyncManager.syncMissedSms(application)
        }
    }

    // Cart Management (Point 3: Process Death Protection)
    private val _cart = savedStateHandle.getStateFlow<List<CartItem>>("cart_items", emptyList())
    val cart = _cart

    fun addToCart(product: StockEntity, price: Double, isRetail: Boolean = false, parentId: Long? = null, factor: Double = 1.0) {
        val current = _cart.value.toMutableList()
        val existing = current.find { it.product.id == product.id && it.isRetail == isRetail }
        if (existing != null) {
            val updatedList = current.map { 
                if (it.product.id == product.id && it.isRetail == isRetail) 
                    it.copy(quantity = it.quantity + 1, priceAtSale = price) 
                else it
            }
            savedStateHandle["cart_items"] = updatedList
        } else {
            val newList = current + CartItem(product, 1.0, price, isRetail, parentId, factor)
            savedStateHandle["cart_items"] = newList
        }
    }

    fun removeFromCart(item: CartItem) {
        val current = _cart.value.toMutableList()
        current.remove(item)
        savedStateHandle["cart_items"] = current
    }

    fun updateCartItemQuantity(item: CartItem, newQuantity: Double) {
        val current = _cart.value.toMutableList()
        val updatedList = current.map { 
            if (it.product.id == item.product.id && it.isRetail == item.isRetail) 
                it.copy(quantity = newQuantity) 
            else it
        }
        savedStateHandle["cart_items"] = updatedList
    }

    fun clearCart() { 
        savedStateHandle["cart_items"] = emptyList<CartItem>()
    }

    fun updateCartPrices(isVip: Boolean) {
        val current = _cart.value
        val updated = current.map { item ->
            val price = if (isVip && item.product.vipPrice > 0) item.product.vipPrice else item.product.unitPrice
            item.copy(priceAtSale = price)
        }
        savedStateHandle["cart_items"] = updated
    }

    fun addVenteWithItems(vente: VenteEntity, items: List<VenteItemEntity>) = viewModelScope.launch {
        try {
            // ... (keeping existing logic for finalVente calculation)
            val finalVente = if (boutique.value?.isTaxEnabled == true) {
                val tax = vente.amount * (boutique.value?.taxRate ?: 0.0) / 100.0
                vente.copy(taxAmount = tax, amount = vente.amount + tax)
            } else vente

            val processedItems = _cart.value.map { cartItem ->
                if (cartItem.isRetail && cartItem.parentId != null) {
                    VenteItemEntity(
                        venteId = 0,
                        productId = cartItem.parentId, 
                        productName = cartItem.product.productName,
                        quantity = cartItem.quantity / cartItem.conversionFactor, 
                        unitPrice = cartItem.priceAtSale,
                        purchasePrice = cartItem.product.purchasePrice / cartItem.conversionFactor
                    )
                } else {
                    VenteItemEntity(
                        venteId = 0,
                        productId = cartItem.product.id,
                        productName = cartItem.product.productName,
                        quantity = cartItem.quantity,
                        unitPrice = cartItem.priceAtSale,
                        purchasePrice = cartItem.product.purchasePrice
                    )
                }
            }

            val venteId = repository.insertVenteWithItems(finalVente, processedItems, userRole.value)
            clearCart()
            _lastConfirmedVenteId.value = venteId
        } catch (e: Exception) {
            _uiError.value = e.message
        }
    }

    private val _lastConfirmedVenteId = MutableStateFlow<Long?>(null)
    val lastConfirmedVenteId = _lastConfirmedVenteId.asStateFlow()

    fun clearLastVenteId() { _lastConfirmedVenteId.value = null }
    fun updateVente(vente: VenteEntity, items: List<VenteItemEntity>? = null) = viewModelScope.launch {
        try { repository.updateVente(vente, items, userRole.value) } catch (e: Exception) { _uiError.value = e.message }
    }
    fun deleteVente(vente: VenteEntity) = viewModelScope.launch { try { repository.deleteVente(vente, userRole.value) } catch (e: Exception) { _uiError.value = e.message } }
    fun cancelVente(vente: VenteEntity) = viewModelScope.launch { try { repository.cancelVente(vente) } catch (e: Exception) { _uiError.value = e.message } }
    fun confirmOrphanSale(vente: VenteEntity, product: StockEntity) = viewModelScope.launch { try { repository.confirmOrphanSale(vente, product, userRole.value) } catch (e: Exception) { _uiError.value = e.message } }
    fun convertErrorToOrphan(error: SmsErrorEntity, product: StockEntity? = null) = viewModelScope.launch { try { repository.convertErrorToOrphan(error, product, userRole.value) } catch (e: Exception) { _uiError.value = e.message } }
    
    fun adjustStock(stock: StockEntity, newQuantity: Double, reason: String) = viewModelScope.launch { try { repository.adjustStock(stock, newQuantity, reason, userRole.value) } catch (e: Exception) { _uiError.value = e.message } }
    fun updateStock(stock: StockEntity) = viewModelScope.launch { try { repository.updateStock(stock, userRole.value) } catch (e: Exception) { _uiError.value = e.message } }
    fun updateStockQuantity(id: Long, delta: Double) = viewModelScope.launch { repository.updateStockQuantity(id, delta) }
    fun deleteStock(stock: StockEntity) = viewModelScope.launch { try { repository.deleteStock(stock) } catch (e: Exception) { _uiError.value = e.message } }
    suspend fun getProductByBarcode(barcode: String) = repository.getProductByBarcode(barcode)
    
    fun addSupply(supply: SupplyEntity) = viewModelScope.launch { try { repository.addSupply(supply, userRole.value) } catch (e: Exception) { _uiError.value = e.message } }
    fun addSupplier(supplier: SupplierEntity) = viewModelScope.launch { try { repository.addSupplier(supplier) } catch (e: Exception) { _uiError.value = e.message } }
    fun deleteSupplier(supplier: SupplierEntity) = viewModelScope.launch { try { repository.deleteSupplier(supplier) } catch (e: Exception) { _uiError.value = e.message } }
    fun updateSupplierDebt(supplierId: Long, delta: Double) = viewModelScope.launch { try { repository.updateSupplierDebt(supplierId, delta) } catch (e: Exception) { _uiError.value = e.message } }
    fun getExpiringSoon() = repository.getExpiringSoon()

    fun performAudit(note: String?, items: List<AuditItemEntity>) = viewModelScope.launch { try { repository.performAudit(note, items) } catch (e: Exception) { _uiError.value = e.message } }
    fun performClosure(closure: ClosureEntity) = viewModelScope.launch { try { repository.performClosure(closure, userRole.value) } catch (e: Exception) { _uiError.value = e.message } }
    
    fun openSession(seller: String, balance: Double) = viewModelScope.launch { 
        try { repository.openSession(seller, balance, staffId.value) } catch (e: Exception) { _uiError.value = e.message } 
    }
    fun closeSession(balance: Double) = viewModelScope.launch { 
        try { repository.closeSession(balance, userRole.value) } catch (e: Exception) { _uiError.value = e.message } 
    }

    fun addRepayment(customerId: Long, amount: Double, method: String) = viewModelScope.launch { repository.addRepayment(customerId, amount, method, userRole.value) }
    fun addExpense(expense: ExpenseEntity) = viewModelScope.launch { repository.insertExpense(expense) }
    fun deleteExpense(expense: ExpenseEntity) = viewModelScope.launch { repository.deleteExpense(expense) }
    fun sendDebtReminder(context: android.content.Context, customer: CustomerEntity) = repository.sendDebtReminder(context, customer)
    suspend fun getCustomerByPhone(phone: String) = repository.getCustomerByPhone(phone)
    suspend fun findOrCreateCustomer(name: String, phone: String) = repository.findOrCreateCustomer(name, phone)

    fun printTicket(venteId: Long, printerAddress: String) = viewModelScope.launch(Dispatchers.IO) { repository.printTicket(venteId, printerAddress) }
    fun testPrint(address: String) = viewModelScope.launch(Dispatchers.IO) { repository.testPrint(getApplication(), address) }
    
    fun exportSalesData(context: android.content.Context) = viewModelScope.launch { 
        try {
            val ventes = db.venteDao().getAllVentesWithItems().first()
            val file = com.reconsiliation.caisse.utils.ExportUtil.exportSalesToCsv(context, ventes)
            if (file != null) {
                com.reconsiliation.caisse.utils.ExportUtil.shareFile(context, file)
            } else {
                _uiError.value = "Erreur lors de la création du fichier CSV"
            }
        } catch (e: Exception) {
            _uiError.value = "Erreur d'exportation: ${e.message}"
        }
    }
    fun shareDailySummary(context: android.content.Context) = viewModelScope.launch { repository.shareDailySummary(context) }
    
    fun backupDatabase(file: File) = repository.backupDatabase(getApplication(), file)
    fun restoreDatabase(file: File) = repository.restoreDatabase(getApplication(), file)
    
    fun activateSubscription(key: String) = viewModelScope.launch { try { repository.activateSubscription(getApplication(), key) } catch (e: Exception) { _uiError.value = e.message } }
    fun getPaymentUssd(operator: String, amount: Int): String = repository.getPaymentUssd(operator, amount)
    fun getDeviceId() = repository.getDeviceId(getApplication())
    fun isStaffRestricted() = boutique.value?.hideProfitsFromStaff == true
    fun generateCustomerStatement(context: android.content.Context, customer: CustomerEntity) = viewModelScope.launch {
        try {
            val ventes = repository.getVentesByCustomer(customer.id).first()
            val file = repository.generateCustomerStatementPdf(context, customer, ventes)
            if (file != null) {
                val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(android.content.Intent.createChooser(intent, "Partager le relevé"))
            }
        } catch (e: Exception) {
            _uiError.value = "Erreur PDF: ${e.message}"
        }
    }

    fun getPriceHistory(productId: Long) = db.priceHistoryDao().getHistoryForProduct(productId)
    fun getStockMovements(productId: Long) = db.stockMovementDao().getMovementsForProduct(productId)
    fun resolveSmsError(error: SmsErrorEntity, venteId: Long) = viewModelScope.launch {
        try { repository.resolveSmsError(error, venteId) } catch (e: Exception) { _uiError.value = e.message }
    }

    fun syncToCloud(context: android.content.Context) = viewModelScope.launch {
        repository.syncToCloud(context)
    }

    // Category Management
    fun getCategories(type: String) = repository.getCategories(type)
    fun addCategory(name: String, type: String) = viewModelScope.launch {
        repository.addCategory(CategoryEntity(name = name, type = type))
    }
    fun deleteCategory(category: CategoryEntity) = viewModelScope.launch {
        repository.deleteCategory(category)
    }

    suspend fun purgeOldData(months: Int): Int {
        return repository.purgeOldData(months, userRole.value)
    }

    suspend fun getVenteWithItems(venteId: Long) = repository.getVenteWithItems(venteId)
    fun getVentesByCustomer(customerId: Long) = repository.getVentesByCustomer(customerId)
    fun lockSalesForToday() = viewModelScope.launch { repository.lockSalesForToday() }
    fun returnVenteItem(venteId: Long, productId: Long, quantity: Double, refundAmount: Double, method: String = "AVOIR") = viewModelScope.launch {
        try { repository.returnVenteItem(venteId, productId, quantity, refundAmount, method, userRole.value) } catch (e: Exception) { _uiError.value = e.message }
    }
    suspend fun checkPin(pin: String): Pair<String, Long?>? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
        val b = boutique.value ?: return@withContext null
        val sec = com.reconsiliation.caisse.utils.SecurityUtil

        // 1. Vérification Staff
        val allStaff = db.staffDao().getAllActiveStaff().first()
        for (staff in allStaff) {
            if (sec.verifyPin(pin, staff.pinHash, staff.pinSalt)) {
                // Migration paresseuse vers PBKDF2 si nécessaire
                if (staff.pinSalt == null) {
                    viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        try {
                            val salt = sec.generateSalt()
                            val newHash = sec.hashPinPbkdf2(pin, salt)
                            db.staffDao().insertOrUpdate(staff.copy(pinHash = newHash, pinSalt = salt))
                        } catch (e: Exception) {
                            // Si interrompu, le login a quand même réussi, on retentera la migration plus tard
                        }
                    }
                }
                return@withContext Pair(staff.role, staff.id)
            }
        }

        // 2. Vérification Manager Boutique
        if (sec.verifyPin(pin, b.managerPinHash, b.managerPinSalt)) {
            if (b.managerPinSalt == null && b.managerPinHash != null) {
                viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        val salt = sec.generateSalt()
                        val newHash = sec.hashPinPbkdf2(pin, salt)
                        repository.updateBoutique(b.copy(managerPinHash = newHash, managerPinSalt = salt))
                    } catch (e: Exception) { }
                }
            }
            return@withContext Pair("MANAGER", null)
        }

        // 3. Vérification Staff Boutique (Legacy global)
        if (sec.verifyPin(pin, b.pinHash, b.pinSalt)) {
            if (b.pinSalt == null && b.pinHash != null) {
                viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        val salt = sec.generateSalt()
                        val newHash = sec.hashPinPbkdf2(pin, salt)
                        repository.updateBoutique(b.copy(pinHash = newHash, pinSalt = salt))
                    } catch (e: Exception) { }
                }
            }
            return@withContext Pair("STAFF", null)
        }

        return@withContext null
    }
    fun updateBoutique(boutique: BoutiqueEntity) = viewModelScope.launch { repository.updateBoutique(boutique) }
    fun updateVipStatus(customerId: Long, isVip: Boolean) = viewModelScope.launch {
        db.customerDao().updateVipStatus(customerId, isVip)
    }
    @android.annotation.SuppressLint("MissingPermission")
    fun getPairedPrinters(): List<android.bluetooth.BluetoothDevice> {
        val btManager = getApplication<Application>().getSystemService(android.content.Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
        val adapter = btManager.adapter ?: return emptyList()
        return try {
            adapter.bondedDevices.toList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    fun clearError() { _uiError.value = null }
    
    // Recipe Management
    fun getRecipeForProduct(parentId: Long) = repository.getRecipeForProduct(parentId)
    fun addRecipeComponent(parentProductId: Long, ingredientProductId: Long, quantity: Double) = viewModelScope.launch {
        repository.addRecipeComponent(RecipeEntity(parentProductId = parentProductId, ingredientProductId = ingredientProductId, quantityRequired = quantity))
    }
    fun deleteRecipeComponent(recipe: RecipeEntity) = viewModelScope.launch {
        repository.deleteRecipeComponent(recipe)
    }

    fun resetApplicationData() = viewModelScope.launch(Dispatchers.IO) {
        try {
            db.clearAllTables()
            // Reset setup complete preference
            val prefs = com.reconsiliation.caisse.data.prefs.PreferencesManager(getApplication())
            prefs.setSetupComplete(false)
            _isDataLoaded.value = false
        } catch (e: Exception) {
            _uiError.value = "Erreur lors de la réinitialisation: ${e.message}"
        }
    }
}

class MainViewModelFactory(private val application: Application) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(application, androidx.lifecycle.SavedStateHandle()) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
