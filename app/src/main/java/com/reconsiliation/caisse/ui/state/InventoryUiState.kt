package com.reconsiliation.caisse.ui.state

sealed interface InventoryUiState {
    object Idle : InventoryUiState
    object Loading : InventoryUiState
    object Processing : InventoryUiState
    object Success : InventoryUiState
    data class Error(val message: String) : InventoryUiState
}
