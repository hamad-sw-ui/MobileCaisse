package com.reconsiliation.caisse.ui.viewmodel

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.reconsiliation.caisse.data.local.entity.*
import com.reconsiliation.caisse.data.repository.MainRepository
import com.reconsiliation.caisse.data.repository.SalesRepository
import com.reconsiliation.caisse.notification.NotificationHelper
import com.reconsiliation.caisse.platform.PrinterManager
import com.reconsiliation.caisse.platform.ReportManager
import com.reconsiliation.caisse.platform.ShareManager
import com.reconsiliation.caisse.utils.FormatUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import java.io.File
import javax.inject.Inject

import java.io.Serializable
import kotlinx.coroutines.flow.first

data class CategoryTotal(val category: String, val total: Double)

/** État des opérations de sauvegarde et de restauration. */
sealed interface BackupUiState {
    data object Idle : BackupUiState
    /** Opération en cours : l'UI désactive les boutons et affiche [message]. */
    data class Working(val message: String) : BackupUiState
    data class Success(val message: String) : BackupUiState
    data class Error(val message: String) : BackupUiState
}

@HiltViewModel
class MainViewModel @Inject constructor(
    application: Application,
    private val repository: MainRepository,
    private val salesRepository: SalesRepository,
    private val reportManager: ReportManager,
    private val printerManager: PrinterManager,
    private val shareManager: ShareManager,
    private val savedStateHandle: SavedStateHandle,
    private val notificationHelper: NotificationHelper
) : AndroidViewModel(application) {
    
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
        // Source de vérité centralisée dans Screen (B-027)
        return route in com.reconsiliation.caisse.ui.navigation.Screen.MANAGER_ROUTES
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
    val smsErrors: StateFlow<List<SmsErrorEntity>>
    val allExpenses: StateFlow<List<ExpenseEntity>>
    val subscription: StateFlow<SubscriptionEntity?>
    val allClosures: StateFlow<List<ClosureEntity>>
    val recentLogs: StateFlow<List<ActionLogEntity>>
    val activeSession: StateFlow<SessionEntity?>
    val allSessions: StateFlow<List<SessionEntity>>
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

        allVentes = salesRepository.allVentes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        allVentesWithItems = salesRepository.allVentesWithItems.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        smsErrors = salesRepository.smsErrors.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        allExpenses = repository.allExpenses.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        subscription = repository.getSubscriptionStatus(application).stateIn(viewModelScope, SharingStarted.Eagerly, null)
        allClosures = salesRepository.allClosures.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        recentLogs = repository.recentLogs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        activeSession = salesRepository.activeSession.stateIn(viewModelScope, SharingStarted.Eagerly, null)
        allSessions = salesRepository.allSessions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        val start = calendar.time
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        val end = calendar.time

        todayProfit = combine(salesRepository.getProfitFlow(start, end), userRole, boutique) { profit, role, b ->
            if (role == "MANAGER" || b?.hideProfitsFromStaff == false) profit else 0.0
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

        todayRevenue = combine(salesRepository.getTotalCash(start, end), salesRepository.getTotalMomo(start, end)) { cash, momo ->
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
            salesRepository.getSalesByCategory(start, end).collect { _categorySales.value = it }
        }
        viewModelScope.launch {
            if (rangeType == "YEAR") {
                salesRepository.getMonthlySales(start, end).collect { _dailySales.value = it }
            } else {
                salesRepository.getDailySales(start, end).collect { _dailySales.value = it }
            }
        }
        viewModelScope.launch {
            salesRepository.getHourlySales(start, end).collect { _hourlySales.value = it }
        }
        viewModelScope.launch {
            repository.getExpensesByCategory(start, end).collect { _expenseBreakdown.value = it }
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
            salesRepository.getVentesByDateRange(start, end).collect { _selectedMonthVentes.value = it }
        }
    }
    fun catchUpSms(application: Application) {
        viewModelScope.launch(Dispatchers.IO) {
            com.reconsiliation.caisse.sms.SmsSyncManager.syncMissedSms(application)
        }
    }

    fun addVenteWithItems(vente: VenteEntity, items: List<VenteItemEntity>) = viewModelScope.launch {
        try {
            val finalVente = if (boutique.value?.isTaxEnabled == true) {
                val tax = vente.amount * (boutique.value?.taxRate ?: 0.0) / 100.0
                vente.copy(taxAmount = tax, amount = vente.amount + tax)
            } else vente

            salesRepository.insertVenteWithItems(finalVente, items, userRole.value)
        } catch (e: Exception) {
            _uiError.value = e.message
        }
    }

    fun updateVente(vente: VenteEntity, items: List<VenteItemEntity>? = null) = viewModelScope.launch {
        try { salesRepository.updateVente(vente, items, userRole.value) } catch (e: Exception) { _uiError.value = e.message }
    }
    fun deleteVente(vente: VenteEntity) = viewModelScope.launch { try { salesRepository.deleteVente(vente, userRole.value) } catch (e: Exception) { _uiError.value = e.message } }
    fun cancelVente(vente: VenteEntity) = viewModelScope.launch { try { salesRepository.cancelVente(vente) } catch (e: Exception) { _uiError.value = e.message } }
    fun confirmOrphanSale(vente: VenteEntity, product: StockEntity) = viewModelScope.launch { try { salesRepository.confirmOrphanSale(vente, product, userRole.value) } catch (e: Exception) { _uiError.value = e.message } }
    fun convertErrorToOrphan(error: SmsErrorEntity, product: StockEntity? = null) = viewModelScope.launch { try { salesRepository.convertErrorToOrphan(error, product, userRole.value) } catch (e: Exception) { _uiError.value = e.message } }

    fun addExpense(expense: ExpenseEntity) = viewModelScope.launch { repository.insertExpense(expense) }
    fun deleteExpense(expense: ExpenseEntity) = viewModelScope.launch { repository.deleteExpense(expense) }

    fun openSession(seller: String, balance: Double) = viewModelScope.launch {
        try { salesRepository.openSession(seller, balance, staffId.value) } catch (e: Exception) { _uiError.value = e.message }
    }
    fun closeSession(balance: Double) = viewModelScope.launch {
        try { salesRepository.closeSession(balance, userRole.value) } catch (e: Exception) { _uiError.value = e.message }
    }

    fun printTicket(venteId: Long, printerAddress: String) = viewModelScope.launch(Dispatchers.IO) {
        val venteWithItems = salesRepository.getVenteWithItems(venteId) ?: return@launch
        val boutique = repository.getBoutiqueOnce() ?: return@launch
        printerManager.printReceipt(boutique, venteWithItems.vente, venteWithItems.items, printerAddress)
    }
    fun testPrint(address: String) = viewModelScope.launch(Dispatchers.IO) { printerManager.testConnection(address) }
    
    fun exportSalesData(context: android.content.Context) = viewModelScope.launch {
        try {
            val ventes = salesRepository.allVentesWithItems.first()
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
    fun shareDailySummary(context: android.content.Context) = viewModelScope.launch {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }
        val start = calendar.time
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        val end = calendar.time

        val b = boutique.value
        val cash = salesRepository.getTotalCash(start, end).first() ?: 0.0
        val momo = salesRepository.getTotalMomo(start, end).first() ?: 0.0
        val expenses = repository.getTotalExpenses(start, end).first() ?: 0.0
        val profit = salesRepository.getProfitFlow(start, end).first()

        val text = FormatUtil.generateDailySummary(
            boutiqueName = b?.name ?: "Boutique",
            date = Date(),
            totalSales = cash + momo,
            cashSales = cash,
            momoSales = momo,
            debtsCreated = 0.0,
            expenses = expenses,
            netProfit = profit,
            currency = b?.currency ?: "FCFA"
        )
        shareManager.shareText(text, "Partager le résumé")
    }
    
    fun backupDatabase(file: File) = repository.backupDatabase(getApplication(), file)
    fun restoreDatabase(file: File) = repository.restoreDatabase(getApplication(), file)

    // ---------- Sauvegarde chiffrée par mot de passe ----------

    private val _backupState = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val backupState: StateFlow<BackupUiState> = _backupState.asStateFlow()

    /** Remet l'état à [BackupUiState.Idle] une fois le message consommé par l'UI. */
    fun clearBackupState() { _backupState.value = BackupUiState.Idle }

    /**
     * Exporte la base chiffrée par [password], puis propose le partage.
     *
     * Le mot de passe n'est ni conservé ni journalisé (décision D2-C).
     */
    fun exportEncryptedBackup(context: android.content.Context, password: String) =
        viewModelScope.launch {
            _backupState.value = BackupUiState.Working("Chiffrement de la sauvegarde…")
            val result = kotlinx.coroutines.withContext(Dispatchers.IO) {
                repository.exportEncryptedBackup(context, password, userRole.value)
            }
            _backupState.value = result.fold(
                onSuccess = { file ->
                    shareManager.shareFile(file, "application/zip", "Exporter et partager")
                    BackupUiState.Success(
                        "Sauvegarde créée (${file.length() / 1024} Ko). " +
                            "Conservez précieusement votre mot de passe : il est indispensable " +
                            "pour la restaurer."
                    )
                },
                onFailure = { BackupUiState.Error(it.message ?: "Échec de l'export") }
            )
        }

    /**
     * Restaure [file]. Le format (chiffré ou brut) est détecté automatiquement.
     *
     * @param onRestored appelé si la base a été remplacée : l'appelant doit
     *        relancer l'application, les DAO déjà obtenus étant obsolètes.
     */
    fun importEncryptedBackup(
        context: android.content.Context,
        file: File,
        password: String?,
        onRestored: () -> Unit
    ) = viewModelScope.launch {
        _backupState.value = BackupUiState.Working("Restauration en cours…")
        val result = kotlinx.coroutines.withContext(Dispatchers.IO) {
            repository.importEncryptedBackup(context, file, password, userRole.value)
        }
        result.fold(
            onSuccess = { ok ->
                if (ok) {
                    _backupState.value = BackupUiState.Success("Données restaurées.")
                    onRestored()
                } else {
                    _backupState.value = BackupUiState.Error("La restauration a échoué.")
                }
            },
            onFailure = { _backupState.value = BackupUiState.Error(it.message ?: "Échec de la restauration") }
        )
    }

    /** Indique si [file] exige un mot de passe, pour adapter l'interface. */
    /**
     * Finalise la configuration initiale : hachage du PIN, écriture en base,
     * préférence et journalisation.
     *
     * @param onResult `null` en cas de succès, message d'erreur sinon.
     */
    fun completeSetup(
        name: String, owner: String, phone: String, operator: String,
        momoNumber: String, pin: String,
        onResult: (String?) -> Unit
    ) = viewModelScope.launch {
        try {
            val sec = com.reconsiliation.caisse.utils.SecurityUtil
            val salt = sec.generateSalt()
            val hashed = kotlinx.coroutines.withContext(Dispatchers.Default) {
                sec.hashPinPbkdf2(pin, salt)
            }
            val boutique = BoutiqueEntity(
                id = 1,
                name = name.trim(),
                ownerName = owner.trim(),
                phoneNumber = phone.trim(),
                operator = operator,
                momoNumber = momoNumber.trim(),
                isSetupComplete = true,
                pinHash = hashed,
                pinSalt = salt,
                // Le PIN initial sert aussi de code manager : l'utilisateur
                // pourra les dissocier ensuite depuis les paramètres.
                managerPinHash = hashed,
                managerPinSalt = salt
            )
            repository.completeSetup(boutique)
            com.reconsiliation.caisse.data.prefs.PreferencesManager(getApplication())
                .setSetupComplete(true)
            onResult(null)
        } catch (e: Exception) {
            android.util.Log.e("MainViewModel", "Échec de la configuration initiale", e)
            onResult(e.message ?: "Erreur lors de la configuration")
        }
    }

    // ---------- Gestion du personnel ----------

    /** Employés actifs. */
    val activeStaff: StateFlow<List<StaffEntity>> = repository.activeStaff
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Crée ou met à jour un employé ; [onResult] reçoit `null` si succès. */
    fun saveStaff(
        name: String, pin: String, role: String, phone: String? = null,
        existing: StaffEntity? = null, onResult: (String?) -> Unit = {}
    ) = viewModelScope.launch {
        try {
            repository.saveStaff(name, pin, role, phone, existing)
            onResult(null)
        } catch (e: Exception) {
            onResult(e.message ?: "Enregistrement impossible")
        }
    }

    /** Désactive un employé sans supprimer son historique. */
    fun deactivateStaff(staff: StaffEntity) = viewModelScope.launch {
        try {
            repository.deactivateStaff(staff)
        } catch (e: Exception) {
            _uiError.value = e.message
        }
    }

    /** Une sauvegarde miroir est-elle disponible pour restauration ? */
    fun hasMirrorBackup(context: android.content.Context): Boolean =
        repository.hasMirrorBackup(context)

    /** Restaure la sauvegarde miroir ; [onResult] reçoit true si la base a été remplacée. */
    fun restoreMirrorBackup(context: android.content.Context, onResult: (Boolean) -> Unit) =
        viewModelScope.launch {
            val ok = kotlinx.coroutines.withContext(Dispatchers.IO) {
                try {
                    repository.restoreMirrorBackup(context)
                } catch (e: Exception) {
                    android.util.Log.e("MainViewModel", "Restauration du miroir échouée", e)
                    false
                }
            }
            if (ok) {
                com.reconsiliation.caisse.data.prefs.PreferencesManager(getApplication())
                    .setSetupComplete(true)
            }
            onResult(ok)
        }

    /** Injecte un jeu de données de démonstration. Réservé aux builds debug. */
    fun seedDemoData(onDone: () -> Unit = {}) = viewModelScope.launch(Dispatchers.IO) {
        if (!com.reconsiliation.caisse.BuildConfig.DEBUG) {
            _uiError.value = "Le mode démo est désactivé en production."
            onDone()
            return@launch
        }
        try {
            repository.seedDemoData()
        } catch (e: Exception) {
            _uiError.value = "Injection des données de démo impossible : ${e.message}"
        }
        onDone()
    }

    /**
     * Indique si la configuration initiale a déjà été effectuée.
     *
     * Consulte la préférence locale **et** la base : la préférence peut avoir
     * été effacée (réinstallation, nettoyage) alors que la boutique existe.
     * Resynchronise la préférence le cas échéant.
     *
     * Évite à `SplashScreen` d'ouvrir la base directement (§1 MVVM).
     * 
     * @throws com.reconsiliation.caisse.data.repository.ObsoleteDatabaseException 
     *         si une base incompatible (< v18) est détectée.
     */
    suspend fun isSetupComplete(): Boolean = kotlinx.coroutines.withContext(Dispatchers.IO) {
        // 1. Vérification préventive de la version physique (D1)
        repository.checkObsoleteDatabase(getApplication())

        val prefs = com.reconsiliation.caisse.data.prefs.PreferencesManager(getApplication())
        val fromPrefs = prefs.isSetupComplete()
        val boutique = try {
            repository.getBoutiqueOnce()
        } catch (e: Exception) {
            // Si checkObsoleteDatabase n'a pas levé d'exception mais que Room crash,
            // c'est un échec d'initialisation normal (ex: mauvaise clé).
            android.util.Log.w("MainViewModel", "Lecture boutique impossible au démarrage", e)
            null
        }
        val complete = fromPrefs || boutique?.isSetupComplete == true
        if (complete && !fromPrefs) prefs.setSetupComplete(true)
        complete
    }

    fun isBackupEncrypted(file: File): Boolean =
        repository.detectBackupFormat(file) is com.reconsiliation.caisse.utils.BackupFormat.Encrypted
    
    fun activateSubscription(key: String) = viewModelScope.launch { try { repository.activateSubscription(getApplication(), key) } catch (e: Exception) { _uiError.value = e.message } }
    fun getPaymentUssd(operator: String, amount: Int): String = repository.getPaymentUssd(operator, amount)
    fun getDeviceId() = repository.getDeviceId(getApplication())
    fun isStaffRestricted() = boutique.value?.hideProfitsFromStaff == true

    fun resolveSmsError(error: SmsErrorEntity, venteId: Long) = viewModelScope.launch {
        try { salesRepository.resolveSmsError(error, venteId) } catch (e: Exception) { _uiError.value = e.message }
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

    suspend fun getVenteWithItems(venteId: Long) = salesRepository.getVenteWithItems(venteId)
    fun lockSalesForToday() = viewModelScope.launch { salesRepository.lockSalesForToday() }
    fun returnVenteItem(venteId: Long, productId: Long, quantity: Double, refundAmount: Double, method: String = "AVOIR") = viewModelScope.launch {
        try { salesRepository.returnVenteItem(venteId, productId, quantity, refundAmount, method, userRole.value) } catch (e: Exception) { _uiError.value = e.message }
    }
    suspend fun checkPin(pin: String): Pair<String, Long?>? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
        val b = boutique.value ?: return@withContext null
        val sec = com.reconsiliation.caisse.utils.SecurityUtil

        // 1. Vérification Staff
        val allStaff = repository.getAllActiveStaffOnce()
        for (staff in allStaff) {
            if (sec.verifyPin(pin, staff.pinHash, staff.pinSalt)) {
                // Migration paresseuse vers PBKDF2 (100k) si nécessaire.
                // Se déclenche si pas de sel (legacy SHA-256) ou si sel sans itérations (PBKDF2 5k).
                if (staff.pinSalt == null || !staff.pinSalt.contains(":")) {
                    viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        try {
                            val iterations = 100_000
                            val salt = sec.generateSalt()
                            val newHash = sec.hashPinPbkdf2(pin, salt, iterations)
                            val saltWithIterations = "$salt:$iterations"
                            repository.updateStaffSecurityInfo(staff.copy(pinHash = newHash, pinSalt = saltWithIterations))
                        } catch (e: Exception) {
                            // Migration silencieuse
                        }
                    }
                }
                return@withContext Pair(staff.role, staff.id)
            }
        }

        // 2. Vérification Manager Boutique
        if (sec.verifyPin(pin, b.managerPinHash, b.managerPinSalt)) {
            if (b.managerPinSalt == null || !b.managerPinSalt.contains(":")) {
                viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        val iterations = 100_000
                        val salt = sec.generateSalt()
                        val newHash = sec.hashPinPbkdf2(pin, salt, iterations)
                        val saltWithIterations = "$salt:$iterations"
                        repository.updateBoutique(b.copy(managerPinHash = newHash, managerPinSalt = saltWithIterations))
                    } catch (e: Exception) {
                        android.util.Log.w("MainViewModel", "Migration PBKDF2 du PIN manager échouée", e)
                    }
                }
            }
            return@withContext Pair("MANAGER", null)
        }

        // 3. Vérification Staff Boutique (Legacy global)
        if (sec.verifyPin(pin, b.pinHash, b.pinSalt)) {
            if (b.pinSalt == null || !b.pinSalt.contains(":")) {
                viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        val iterations = 100_000
                        val salt = sec.generateSalt()
                        val newHash = sec.hashPinPbkdf2(pin, salt, iterations)
                        val saltWithIterations = "$salt:$iterations"
                        repository.updateBoutique(b.copy(pinHash = newHash, pinSalt = saltWithIterations))
                    } catch (e: Exception) {
                        android.util.Log.w("MainViewModel", "Migration PBKDF2 du PIN staff échouée", e)
                    }
                }
            }
            return@withContext Pair("STAFF", null)
        }

        return@withContext null
    }
    fun updateBoutique(boutique: BoutiqueEntity) = viewModelScope.launch { repository.updateBoutique(boutique) }
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
    
    fun resetApplicationData() = viewModelScope.launch(Dispatchers.IO) {
        try {
            repository.deletePhysicalDatabase(getApplication())
            // Reset setup complete preference
            val prefs = com.reconsiliation.caisse.data.prefs.PreferencesManager(getApplication())
            prefs.setSetupComplete(false)
            _isDataLoaded.value = false
        } catch (e: Exception) {
            _uiError.value = "Erreur lors de la réinitialisation: ${e.message}"
        }
    }
}
