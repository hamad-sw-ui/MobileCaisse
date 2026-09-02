package com.reconsiliation.caisse.ui.state

/** État des opérations de sauvegarde et de restauration. */
sealed interface BackupUiState {
    data object Idle : BackupUiState
    /** Opération en cours : l'UI désactive les boutons et affiche [message]. */
    data class Working(val message: String) : BackupUiState
    data class Success(val message: String) : BackupUiState
    data class Error(val message: String) : BackupUiState
}
