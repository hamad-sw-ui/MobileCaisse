package com.reconsiliation.caisse.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reconsiliation.caisse.data.local.entity.ClosureEntity
import com.reconsiliation.caisse.data.local.entity.SessionEntity
import com.reconsiliation.caisse.data.repository.MainRepository
import com.reconsiliation.caisse.data.repository.SalesRepository
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
        viewModelScope.launch {
            _uiState.value = SessionUiState.Processing
            try {
                salesRepository.openSession(seller, balance, staffId)
                _uiState.value = SessionUiState.Success
            } catch (e: Exception) {
                _uiState.value = SessionUiState.Error(e.message ?: "Erreur d'ouverture")
            }
        }
    }

    fun closeSession(balance: Double, role: String?) {
        viewModelScope.launch {
            _uiState.value = SessionUiState.Processing
            try {
                salesRepository.closeSession(balance, role)
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
