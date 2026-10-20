package com.reconsiliation.caisse.ui.viewmodel

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.reconsiliation.caisse.data.local.entity.*
import com.reconsiliation.caisse.data.repository.MainRepository
import com.reconsiliation.caisse.data.repository.SalesRepository
import com.reconsiliation.caisse.notification.NotificationHelper
import com.reconsiliation.caisse.permissions.SmsSyncResult
import com.reconsiliation.caisse.sms.SmsSyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CategoryTotal(val category: String, val total: Double)

@HiltViewModel
class MainViewModel @Inject constructor(
    application: Application,
    private val repository: MainRepository,
    private val salesRepository: SalesRepository,
    private val savedStateHandle: SavedStateHandle,
    private val notificationHelper: NotificationHelper
) : AndroidViewModel(application) {
    
    // User Session
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
    
    fun recordActivity() { _lastActivityTime.value = System.currentTimeMillis() }

    fun requestAccess(route: String) {
        val currentRole = _userRole.value
        val isManagerRequired = com.reconsiliation.caisse.ui.navigation.Screen.isManagerRequired(route)
        if (currentRole != "MANAGER" && (currentRole != "STAFF" || isManagerRequired)) {
            pendingRoute = route
            _showPinDialog.value = true
        }
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

    // Boutique & Global State
    val boutique: StateFlow<BoutiqueEntity?> = repository.boutique.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val currency: StateFlow<String> = boutique.map { it?.currency ?: "FCFA" }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "FCFA")
    val subscription: StateFlow<SubscriptionEntity?> = repository.getSubscriptionStatus(application).stateIn(viewModelScope, SharingStarted.Eagerly, null)
    
    private val _isDataLoaded = MutableStateFlow(false)
    val isDataLoaded = _isDataLoaded.asStateFlow()
    private val _uiError = MutableStateFlow<String?>(null)
    val uiError = _uiError.asStateFlow()
    private val _smsCatchUpState = MutableStateFlow<SmsCatchUpState>(SmsCatchUpState.Idle)
    val smsCatchUpState = _smsCatchUpState.asStateFlow()

    // Transversal Sale Primitives
    val allVentes: StateFlow<List<VenteEntity>> = salesRepository.allVentes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allVentesWithItems: StateFlow<List<com.reconsiliation.caisse.data.local.dao.VenteWithItems>> = salesRepository.allVentesWithItems.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val smsErrors: StateFlow<List<SmsErrorEntity>> = salesRepository.smsErrors.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(60000)
                if (_userRole.value != null && System.currentTimeMillis() - _lastActivityTime.value > 5 * 60 * 1000) logout()
            }
        }
        viewModelScope.launch {
            combine(boutique, subscription) { _, _ -> true }.collect { _isDataLoaded.value = true }
        }
        catchUpSms()
    }

    fun catchUpSms(application: Application = getApplication(), userInitiated: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            _smsCatchUpState.value = SmsCatchUpState.Running
            _smsCatchUpState.value = SmsCatchUpState.from(
                SmsSyncManager.syncMissedSms(application),
                userInitiated
            )
        }
    }

    fun clearSmsCatchUpState() {
        _smsCatchUpState.value = SmsCatchUpState.Idle
    }

    fun addVenteWithItems(vente: VenteEntity, items: List<VenteItemEntity>) = viewModelScope.launch {
        try {
            val finalVente = if (boutique.value?.isTaxEnabled == true) {
                val tax = vente.amount * (boutique.value?.taxRate ?: 0.0) / 100.0
                vente.copy(taxAmount = tax, amount = vente.amount + tax)
            } else vente
            salesRepository.insertVenteWithItems(finalVente, items, userRole.value)
        } catch (e: Exception) { _uiError.value = e.message }
    }

    fun updateVente(vente: VenteEntity, items: List<VenteItemEntity>? = null) = viewModelScope.launch {
        try { salesRepository.updateVente(vente, items, userRole.value) } catch (e: Exception) { _uiError.value = e.message }
    }

    fun deleteVente(vente: VenteEntity) = viewModelScope.launch { try { salesRepository.deleteVente(vente, userRole.value) } catch (e: Exception) { _uiError.value = e.message } }
    fun cancelVente(vente: VenteEntity) = viewModelScope.launch { try { salesRepository.cancelVente(vente) } catch (e: Exception) { _uiError.value = e.message } }
    fun confirmOrphanSale(vente: VenteEntity, product: StockEntity) = viewModelScope.launch { try { salesRepository.confirmOrphanSale(vente, product, userRole.value) } catch (e: Exception) { _uiError.value = e.message } }
    fun convertErrorToOrphan(error: SmsErrorEntity, product: StockEntity? = null) = viewModelScope.launch { try { salesRepository.convertErrorToOrphan(error, product, userRole.value) } catch (e: Exception) { _uiError.value = e.message } }
    
    fun completeSetup(name: String, owner: String, phone: String, operator: String, momoNumber: String, pin: String, onResult: (String?) -> Unit) = viewModelScope.launch {
        try {
            val sec = com.reconsiliation.caisse.utils.SecurityUtil
            val salt = sec.generateSalt()
            val hashed = kotlinx.coroutines.withContext(Dispatchers.Default) { sec.hashPinPbkdf2(pin, salt) }
            val b = BoutiqueEntity(id = 1, name = name.trim(), ownerName = owner.trim(), phoneNumber = phone.trim(), operator = operator, momoNumber = momoNumber.trim(), isSetupComplete = true, pinHash = hashed, pinSalt = salt, managerPinHash = hashed, managerPinSalt = salt)
            repository.completeSetup(b)
            com.reconsiliation.caisse.data.prefs.PreferencesManager(getApplication()).setSetupComplete(true)
            onResult(null)
        } catch (e: Exception) { onResult(e.message ?: "Erreur") }
    }

    suspend fun isSetupComplete(): Boolean = kotlinx.coroutines.withContext(Dispatchers.IO) {
        repository.checkObsoleteDatabase(getApplication())
        val prefs = com.reconsiliation.caisse.data.prefs.PreferencesManager(getApplication())
        val complete = prefs.isSetupComplete() || (try { repository.getBoutiqueOnce() } catch (e: Exception) { null })?.isSetupComplete == true
        if (complete && !prefs.isSetupComplete()) prefs.setSetupComplete(true)
        complete
    }

    fun activateSubscription(key: String) = viewModelScope.launch { try { repository.activateSubscription(getApplication(), key) } catch (e: Exception) { _uiError.value = e.message } }
    fun getPaymentUssd(operator: String, amount: Int): String = repository.getPaymentUssd(operator, amount)
    fun getDeviceId() = repository.getDeviceId(getApplication())
    fun resolveSmsError(error: SmsErrorEntity, venteId: Long) = viewModelScope.launch { try { salesRepository.resolveSmsError(error, venteId) } catch (e: Exception) { _uiError.value = e.message } }
    suspend fun getVenteWithItems(venteId: Long) = salesRepository.getVenteWithItems(venteId)
    fun lockSalesForToday() = viewModelScope.launch { salesRepository.lockSalesForToday() }
    fun returnVenteItem(venteId: Long, productId: Long, quantity: Double, refundAmount: Double, method: String = "AVOIR") = viewModelScope.launch {
        try { salesRepository.returnVenteItem(venteId, productId, quantity, refundAmount, method, userRole.value) } catch (e: Exception) { _uiError.value = e.message }
    }

    suspend fun checkPin(pin: String): Pair<String, Long?>? = kotlinx.coroutines.withContext(Dispatchers.Default) {
        val b = boutique.value ?: return@withContext null
        val sec = com.reconsiliation.caisse.utils.SecurityUtil
        repository.getAllActiveStaffOnce().find { sec.verifyPin(pin, it.pinHash, it.pinSalt) }?.let { return@withContext Pair(it.role, it.id) }
        if (sec.verifyPin(pin, b.managerPinHash, b.managerPinSalt)) return@withContext Pair("MANAGER", null)
        if (sec.verifyPin(pin, b.pinHash, b.pinSalt)) return@withContext Pair("STAFF", null)
        null
    }

    suspend fun checkStaffLogin(code: String, pin: String): Pair<String, Long?>? = kotlinx.coroutines.withContext(Dispatchers.Default) {
        val staff = repository.getStaffByEmployeeCode(code.trim()) ?: return@withContext null
        val sec = com.reconsiliation.caisse.utils.SecurityUtil
        if (sec.verifyPin(pin, staff.pinHash, staff.pinSalt)) Pair(staff.role, staff.id) else null
    }

    fun changePin(oldPin: String, newPin: String, onResult: (String) -> Unit) = viewModelScope.launch {
        val error = repository.changePin(oldPin, newPin)
        onResult(error)
    }

    fun updateBoutique(boutique: BoutiqueEntity) = viewModelScope.launch { repository.updateBoutique(boutique) }
    fun clearError() { _uiError.value = null }
    fun resetApplicationData() = viewModelScope.launch(Dispatchers.IO) {
        try {
            repository.deletePhysicalDatabase(getApplication())
            com.reconsiliation.caisse.data.prefs.PreferencesManager(getApplication()).setSetupComplete(false)
            _isDataLoaded.value = false
        } catch (e: Exception) { _uiError.value = "Erreur: ${e.message}" }
    }
}

sealed interface SmsCatchUpState {
    object Idle : SmsCatchUpState
    object Running : SmsCatchUpState
    object PermissionRequired : SmsCatchUpState
    data class Completed(val matchedCount: Int) : SmsCatchUpState
    object Failed : SmsCatchUpState

    companion object {
        fun from(result: SmsSyncResult, userInitiated: Boolean): SmsCatchUpState = when (result) {
            is SmsSyncResult.PermissionDenied -> PermissionRequired
            is SmsSyncResult.Success -> if (userInitiated) Completed(result.matchedCount) else Idle
            is SmsSyncResult.Failed -> if (userInitiated) Failed else Idle
        }
    }
}
