package com.reconsiliation.caisse.ui.state

sealed interface ReportsUiState {
    object Idle : ReportsUiState
    object Processing : ReportsUiState
    object Success : ReportsUiState
    data class Error(val message: String) : ReportsUiState
}
