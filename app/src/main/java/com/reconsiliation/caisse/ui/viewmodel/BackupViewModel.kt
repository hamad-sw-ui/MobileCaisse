package com.reconsiliation.caisse.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.reconsiliation.caisse.data.repository.MainRepository
import com.reconsiliation.caisse.platform.ShareManager
import com.reconsiliation.caisse.ui.state.BackupUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class BackupViewModel @Inject constructor(
    application: Application,
    private val repository: MainRepository,
    private val shareManager: ShareManager
) : AndroidViewModel(application) {

    private val _backupState = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val backupState: StateFlow<BackupUiState> = _backupState.asStateFlow()

    fun clearBackupState() {
        _backupState.value = BackupUiState.Idle
    }

    fun exportEncryptedBackup(context: Context, password: String, userRole: String?) =
        viewModelScope.launch {
            _backupState.value = BackupUiState.Working("Chiffrement de la sauvegarde…")
            val result = withContext(Dispatchers.IO) {
                repository.exportEncryptedBackup(context, password, userRole)
            }
            _backupState.value = result.fold(
                onSuccess = { file ->
                    shareManager.shareFile(file, "application/zip", "Exporter et partager")
                    BackupUiState.Success(
                        "Sauvegarde créée (${file.length() / 1024} Ko). " +
                            "Conservez précieusement votre mot de passe : il est indispensable " +
                            "pour la restaurer."
                    )
                },
                onFailure = { BackupUiState.Error(it.message ?: "Échec de l'export") }
            )
        }

    fun importEncryptedBackup(
        context: Context,
        file: File,
        password: String?,
        userRole: String?,
        onRestored: () -> Unit
    ) = viewModelScope.launch {
        _backupState.value = BackupUiState.Working("Restauration en cours…")
        val result = withContext(Dispatchers.IO) {
            repository.importEncryptedBackup(context, file, password, userRole)
        }
        result.fold(
            onSuccess = { ok ->
                if (ok) {
                    _backupState.value = BackupUiState.Success("Données restaurées.")
                    onRestored()
                } else {
                    _backupState.value = BackupUiState.Error("La restauration a échoué.")
                }
            },
            onFailure = { _backupState.value = BackupUiState.Error(it.message ?: "Échec de la restauration") }
        )
    }

    fun hasMirrorBackup(context: Context): Boolean =
        repository.hasMirrorBackup(context)

    fun restoreMirrorBackup(context: Context, onResult: (Boolean) -> Unit) =
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    repository.restoreMirrorBackup(context)
                } catch (e: Exception) {
                    false
                }
            }
            if (ok) {
                com.reconsiliation.caisse.data.prefs.PreferencesManager(getApplication())
                    .setSetupComplete(true)
            }
            onResult(ok)
        }

    fun isBackupEncrypted(file: File): Boolean =
        repository.detectBackupFormat(file) is com.reconsiliation.caisse.utils.BackupFormat.Encrypted

    fun backupDatabase(file: File) = repository.backupDatabase(getApplication(), file)
    fun restoreDatabase(file: File) = repository.restoreDatabase(getApplication(), file)
}
