package com.reconsiliation.caisse.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reconsiliation.caisse.data.local.entity.CategoryEntity
import com.reconsiliation.caisse.data.local.entity.ExpenseEntity
import com.reconsiliation.caisse.data.repository.MainRepository
import com.reconsiliation.caisse.ui.state.ExpenseUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExpenseViewModel @Inject constructor(
    private val repository: MainRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ExpenseUiState>(ExpenseUiState.Idle)
    val uiState: StateFlow<ExpenseUiState> = _uiState.asStateFlow()

    val allExpenses: StateFlow<List<ExpenseEntity>> = repository.allExpenses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addExpense(label: String, amount: Double, category: String) {
        if (label.isBlank() || amount <= 0) {
            _uiState.value = ExpenseUiState.Error("Libellé obligatoire et montant positif requis.")
            return
        }
        
        viewModelScope.launch {
            _uiState.value = ExpenseUiState.Loading
            try {
                repository.insertExpense(ExpenseEntity(label = label, amount = amount, category = category, date = java.util.Date()))
                _uiState.value = ExpenseUiState.Success
            } catch (e: Exception) {
                _uiState.value = ExpenseUiState.Error(e.message ?: "Erreur d'ajout de dépense")
            }
        }
    }

    fun deleteExpense(expense: ExpenseEntity) {
        viewModelScope.launch {
            try {
                repository.deleteExpense(expense)
            } catch (e: Exception) {
                _uiState.value = ExpenseUiState.Error(e.message ?: "Erreur de suppression")
            }
        }
    }

    fun getCategories(): Flow<List<CategoryEntity>> = repository.getCategories("EXPENSE")

    fun addCategory(name: String) {
        viewModelScope.launch {
            repository.addCategory(CategoryEntity(name = name, type = "EXPENSE"))
        }
    }

    fun clearUiState() {
        _uiState.value = ExpenseUiState.Idle
    }
}
