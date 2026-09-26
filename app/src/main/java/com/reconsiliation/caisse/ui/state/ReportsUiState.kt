package com.reconsiliation.caisse.ui.state

import androidx.annotation.StringRes

sealed interface ReportsUiState {
    object Idle : ReportsUiState
    object Processing : ReportsUiState
    object Success : ReportsUiState
    data class Error(@StringRes val messageRes: Int) : ReportsUiState
}
