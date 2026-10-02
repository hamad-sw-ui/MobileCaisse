package com.reconsiliation.caisse.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.reconsiliation.caisse.data.prefs.PreferencesManager
import com.reconsiliation.caisse.data.repository.MainRepository
import com.reconsiliation.caisse.data.repository.RemoteBackupRepository
import com.reconsiliation.caisse.data.work.RemoteBackupWorker
import com.reconsiliation.caisse.platform.ShareManager
import com.reconsiliation.caisse.platform.remote.GoogleDriveAuthManager
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
    private val shareManager: ShareManager,
    private val remoteBackupRepository: RemoteBackupRepository,
    private val authManager: GoogleDriveAuthManager,
    private val preferencesManager: PreferencesManager
) : AndroidViewModel(application) {

    private val _backupState = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val backupState: StateFlow<BackupUiState> = _backupState.asStateFlow()

    private val _isRemoteBackupEnabled = MutableStateFlow(preferencesManager.isRemoteBackupEnabled())
    val isRemoteBackupEnabled: StateFlow<Boolean> = _isRemoteBackupEnabled.asStateFlow()

    private val _isWifiOnlyBackupEnabled = MutableStateFlow(preferencesManager.isWifiOnlyBackupEnabled())
    val isWifiOnlyBackupEnabled: StateFlow<Boolean> = _isWifiOnlyBackupEnabled.asStateFlow()

    private val _lastRemoteBackupTime = MutableStateFlow(preferencesManager.getLastRemoteBackupTime())
    val lastRemoteBackupTime: StateFlow<Long> = _lastRemoteBackupTime.asStateFlow()

    private val _lastRemoteBackupStatus = MutableStateFlow(preferencesManager.getLastRemoteBackupStatus())
    val lastRemoteBackupStatus: StateFlow<String> = _lastRemoteBackupStatus.asStateFlow()

    private val _isGoogleConnected = MutableStateFlow(authManager.isConnected())
    val isGoogleConnected: StateFlow<Boolean> = _isGoogleConnected.asStateFlow()

    private val _googleAccountEmail = MutableStateFlow(authManager.getConnectedAccountEmail())
    val googleAccountEmail: StateFlow<String?> = _googleAccountEmail.asStateFlow()

    init {
        refreshRemoteBackupState()
    }

    fun clearBackupState() {
        _backupState.value = BackupUiState.Idle
    }

    fun refreshRemoteBackupState() {
        _isRemoteBackupEnabled.value = preferencesManager.isRemoteBackupEnabled()
        _isWifiOnlyBackupEnabled.value = preferencesManager.isWifiOnlyBackupEnabled()
        _lastRemoteBackupTime.value = preferencesManager.getLastRemoteBackupTime()
        _lastRemoteBackupStatus.value = preferencesManager.getLastRemoteBackupStatus()
        _isGoogleConnected.value = authManager.isConnected()
        _googleAccountEmail.value = authManager.getConnectedAccountEmail()
    }

    fun toggleRemoteBackup(enabled: Boolean) {
        preferencesManager.setRemoteBackupEnabled(enabled)
        _isRemoteBackupEnabled.value = enabled

        try {
            if (enabled) {
                RemoteBackupWorker.schedulePeriodic(getApplication(), preferencesManager)
            } else {
                RemoteBackupWorker.cancelPeriodic(getApplication())
            }
        } catch (e: Exception) {
            // Uninitialized in pure JVM unit tests
        }
    }

    fun toggleWifiOnlyBackup(wifiOnly: Boolean) {
        preferencesManager.setWifiOnlyBackupEnabled(wifiOnly)
        _isWifiOnlyBackupEnabled.value = wifiOnly

        if (preferencesManager.isRemoteBackupEnabled()) {
            try {
                RemoteBackupWorker.schedulePeriodic(getApplication(), preferencesManager)
            } catch (e: Exception) {
                // Uninitialized in pure JVM unit tests
            }
        }
    }

    fun signOutGoogleDrive() = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            authManager.signOut()
        }
        refreshRemoteBackupState()
    }

    fun triggerManualRemoteBackup() = viewModelScope.launch {
        _backupState.value = BackupUiState.Working("Sauvegarde distante vers Google Drive en cours…")

        val boutique = withContext(Dispatchers.IO) {
            repository.getBoutiqueOnce()
        }

        if (boutique == null) {
            _backupState.value = BackupUiState.Error("Informations de la boutique introuvables")
            return@launch
        }

        val password = RemoteBackupRepository.deriveRemoteBackupPassword(boutique.managerCode)
        val result = withContext(Dispatchers.IO) {
            remoteBackupRepository.performRemoteBackup(password)
        }

        result.fold(
            onSuccess = { remoteItem ->
                refreshRemoteBackupState()
                _backupState.value = BackupUiState.Success(
                    "Sauvegarde distante réussie vers Google Drive (${remoteItem.fileName})."
                )
            },
            onFailure = { error ->
                refreshRemoteBackupState()
                _backupState.value = BackupUiState.Error(
                    error.message ?: "Échec de la sauvegarde distante"
                )
            }
        )
    }

    // ==================== Export / Import Local ====================

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
                PreferencesManager(getApplication()).setSetupComplete(true)
            }
            onResult(ok)
        }

    fun isBackupEncrypted(file: File): Boolean =
        repository.detectBackupFormat(file) is com.reconsiliation.caisse.utils.BackupFormat.Encrypted

    fun backupDatabase(file: File) = repository.backupDatabase(getApplication(), file)
    fun restoreDatabase(file: File) = repository.restoreDatabase(getApplication(), file)
}
