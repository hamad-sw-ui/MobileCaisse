package com.reconsiliation.caisse.ui.state

sealed interface ExpenseUiState {
    object Idle : ExpenseUiState
    object Loading : ExpenseUiState
    object Success : ExpenseUiState
    data class Error(val message: String) : ExpenseUiState
}
