package com.reconsiliation.caisse.ui.state

sealed interface CustomerUiState {
    object Idle : CustomerUiState
    object Processing : CustomerUiState
    object Success : CustomerUiState
    data class Error(val message: String) : CustomerUiState
}
