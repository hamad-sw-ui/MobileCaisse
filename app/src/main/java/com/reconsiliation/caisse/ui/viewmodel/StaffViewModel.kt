package com.reconsiliation.caisse.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reconsiliation.caisse.data.local.entity.StaffEntity
import com.reconsiliation.caisse.data.repository.MainRepository
import com.reconsiliation.caisse.ui.state.StaffUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StaffViewModel @Inject constructor(
    private val repository: MainRepository
) : ViewModel() {

    val uiState: StateFlow<StaffUiState> = repository.activeStaff
        .map { StaffUiState(activeStaff = it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = StaffUiState()
        )

    fun saveStaff(
        name: String, pin: String, role: String, phone: String? = null,
        existing: StaffEntity? = null, onResult: (String?) -> Unit = {}
    ) = viewModelScope.launch {
        try {
            repository.saveStaff(name, pin, role, phone, existing)
            onResult(null)
        } catch (e: Exception) {
            onResult(e.message ?: "")
        }
    }

    fun deactivateStaff(staff: StaffEntity, onResult: (String?) -> Unit = {}) = viewModelScope.launch {
        try {
            repository.deactivateStaff(staff)
            onResult(null)
        } catch (e: Exception) {
            onResult(e.message ?: "")
        }
    }
}
