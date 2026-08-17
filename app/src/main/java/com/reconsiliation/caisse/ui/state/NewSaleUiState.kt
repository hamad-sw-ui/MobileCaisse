package com.reconsiliation.caisse.ui.state

sealed interface NewSaleUiState {
    object Idle : NewSaleUiState
    object Processing : NewSaleUiState
    data class Success(val venteId: Long) : NewSaleUiState
    data class Error(val message: String) : NewSaleUiState
}
