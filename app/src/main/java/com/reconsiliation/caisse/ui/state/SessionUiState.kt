package com.reconsiliation.caisse.ui.state

sealed interface SessionUiState {
    object Idle : SessionUiState
    object Processing : SessionUiState
    object Success : SessionUiState
    data class Error(val message: String) : SessionUiState
}
