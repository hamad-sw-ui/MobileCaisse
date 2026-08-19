package com.reconsiliation.caisse.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reconsiliation.caisse.data.local.entity.CustomerEntity
import com.reconsiliation.caisse.data.repository.SalesRepository
import com.reconsiliation.caisse.platform.ReportManager
import com.reconsiliation.caisse.platform.ShareManager
import com.reconsiliation.caisse.ui.state.CustomerUiState
import com.reconsiliation.caisse.utils.FormatUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CustomerViewModel @Inject constructor(
    private val salesRepository: SalesRepository,
    private val reportManager: ReportManager,
    private val shareManager: ShareManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<CustomerUiState>(CustomerUiState.Idle)
    val uiState: StateFlow<CustomerUiState> = _uiState.asStateFlow()

    val allCustomers: StateFlow<List<CustomerEntity>> = salesRepository.allCustomers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val debtors: StateFlow<List<CustomerEntity>> = salesRepository.debtors
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addRepayment(customerId: Long, amount: Double, method: String, role: String?) {
        viewModelScope.launch {
            _uiState.value = CustomerUiState.Processing
            try {
                salesRepository.addRepayment(customerId, amount, method, role)
                _uiState.value = CustomerUiState.Success
            } catch (e: Exception) {
                _uiState.value = CustomerUiState.Error(e.message ?: "Erreur de remboursement")
            }
        }
    }

    fun updateVipStatus(customerId: Long, isVip: Boolean) {
        viewModelScope.launch {
            salesRepository.updateCustomerVipStatus(customerId, isVip)
        }
    }

    fun getVentesByCustomer(customerId: Long) = salesRepository.getVentesByCustomer(customerId)

    suspend fun getCustomerByPhone(phone: String) = salesRepository.getCustomerByPhone(phone)
    suspend fun findOrCreateCustomer(name: String, phone: String) = salesRepository.findOrCreateCustomer(name, phone)

    fun sendDebtReminder(customer: CustomerEntity, boutiqueName: String?, currency: String) {
        val name = boutiqueName ?: "votre boutique"
        val message = "Bonjour ${customer.name}, un petit rappel concernant votre dette de ${FormatUtil.formatCurrency(customer.totalDebt, currency)} chez $name. Merci de régulariser dès que possible."
        shareManager.sendSms(customer.phone, message)
    }

    fun generateCustomerStatement(customer: CustomerEntity) {
        viewModelScope.launch {
            _uiState.value = CustomerUiState.Processing
            try {
                val ventes = salesRepository.getVentesByCustomer(customer.id).first()
                val file = reportManager.generateCustomerStatementPdf(customer, ventes)
                if (file != null) {
                    shareManager.shareFile(file, "application/pdf", "Partager le relevé")
                    _uiState.value = CustomerUiState.Success
                } else {
                    _uiState.value = CustomerUiState.Error("Échec de génération du PDF")
                }
            } catch (e: Exception) {
                _uiState.value = CustomerUiState.Error(e.message ?: "Erreur PDF")
            }
        }
    }

    fun clearUiState() {
        _uiState.value = CustomerUiState.Idle
    }
}
