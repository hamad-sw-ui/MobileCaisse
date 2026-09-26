package com.reconsiliation.caisse.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reconsiliation.caisse.data.local.entity.ClosureEntity
import com.reconsiliation.caisse.data.local.entity.SessionEntity
import com.reconsiliation.caisse.data.repository.MainRepository
import com.reconsiliation.caisse.data.repository.SalesRepository
import com.reconsiliation.caisse.data.repository.SessionPreview
import com.reconsiliation.caisse.ui.state.SessionUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

@HiltViewModel
class SessionViewModel @Inject constructor(
    private val salesRepository: SalesRepository,
    private val mainRepository: MainRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<SessionUiState>(SessionUiState.Idle)
    val uiState: StateFlow<SessionUiState> = _uiState.asStateFlow()
    private val _sessionPreview = MutableStateFlow<SessionPreview?>(null)
    val sessionPreview: StateFlow<SessionPreview?> = _sessionPreview.asStateFlow()
    private val _suggestedSeller = MutableStateFlow<String?>(null)
    val suggestedSeller: StateFlow<String?> = _suggestedSeller.asStateFlow()

    private val _isLoaded = MutableStateFlow(false)
    val isLoaded = _isLoaded.asStateFlow()

    private val _userRole = MutableStateFlow<String?>(null)

    val activeSession: StateFlow<SessionEntity?> = salesRepository.activeSession
        .onEach { _isLoaded.value = true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val allSessions: StateFlow<List<SessionEntity>> = salesRepository.allSessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allClosures: StateFlow<List<ClosureEntity>> = salesRepository.allClosures
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val todayRange: Pair<Date, Date>
        get() {
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val start = calendar.time
            calendar.set(Calendar.HOUR_OF_DAY, 23)
            calendar.set(Calendar.MINUTE, 59)
            calendar.set(Calendar.SECOND, 59)
            calendar.set(Calendar.MILLISECOND, 999)
            val end = calendar.time
            return Pair(start, end)
        }

    val todayProfit: StateFlow<Double> = combine(
        salesRepository.getProfitFlow(todayRange.first, todayRange.second),
        _userRole,
        mainRepository.boutique
    ) { profit, role, boutique ->
        if (role == "MANAGER" || boutique?.hideProfitsFromStaff == false) profit else 0.0
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val todayRevenue: StateFlow<Double> = combine(
        salesRepository.getTotalCash(todayRange.first, todayRange.second),
        salesRepository.getTotalMomo(todayRange.first, todayRange.second)
    ) { cash, momo ->
        (cash ?: 0.0) + (momo ?: 0.0)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    fun setUserRole(role: String?) {
        _userRole.value = role
    }

    fun openSession(seller: String, balance: Double, staffId: Long?) {
        if (_uiState.value is SessionUiState.Processing) return
        _uiState.value = SessionUiState.Processing
        viewModelScope.launch {
            try {
                val opened = salesRepository.openSession(seller, balance, staffId)
                _uiState.value = if (opened) SessionUiState.Success else SessionUiState.AlreadyOpen
            } catch (e: Exception) {
                _uiState.value = SessionUiState.Error(e.message ?: "Erreur d'ouverture")
            }
        }
    }

    fun loadSellerSuggestion(staffId: Long?) {
        if (staffId == null) {
            _suggestedSeller.value = null
            return
        }
        viewModelScope.launch {
            _suggestedSeller.value = mainRepository.getStaffName(staffId)
        }
    }

    fun loadSessionPreview() {
        viewModelScope.launch {
            _sessionPreview.value = salesRepository.previewActiveSession()
        }
    }

    fun closeSession(balance: Double, role: String?, afterClose: suspend () -> Unit = {}) {
        viewModelScope.launch {
            _uiState.value = SessionUiState.Processing
            try {
                salesRepository.closeSession(balance, role)
                afterClose()
                _uiState.value = SessionUiState.Success
            } catch (e: Exception) {
                _uiState.value = SessionUiState.Error(e.message ?: "Erreur de clôture")
            }
        }
    }

    fun clearUiState() {
        _uiState.value = SessionUiState.Idle
    }
}

internal object SessionAmounts {
    fun openingBalance(text: String): Double? = parseNonNegative(text)

    fun countedCash(text: String): Double? = parseNonNegative(text)

    fun cashGap(countedCash: Double?, expectedCash: Double?): Double? {
        if (countedCash == null || expectedCash == null) return null
        return countedCash - expectedCash
    }

    fun sellerName(text: String): String? = text.trim().takeIf { it.isNotEmpty() }

    private fun parseNonNegative(text: String): Double? {
        val normalized = text.trim().replace(',', '.')
        if (normalized.isEmpty()) return null
        val value = normalized.toDoubleOrNull() ?: return null
        return value.takeIf { it >= 0.0 }
    }
}
