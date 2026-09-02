package com.reconsiliation.caisse.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.reconsiliation.caisse.BuildConfig
import com.reconsiliation.caisse.data.local.entity.ActionLogEntity
import com.reconsiliation.caisse.data.prefs.PreferencesManager
import com.reconsiliation.caisse.data.repository.MainRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MaintenanceViewModel @Inject constructor(
    application: Application,
    private val repository: MainRepository
) : AndroidViewModel(application) {

    val recentLogs: StateFlow<List<ActionLogEntity>> = repository.recentLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun purgeOldData(months: Int, userRole: String?): Int {
        return repository.purgeOldData(months, userRole)
    }

    fun resetApplicationData(onDone: () -> Unit = {}) = viewModelScope.launch(Dispatchers.IO) {
        try {
            repository.deletePhysicalDatabase(getApplication())
            val prefs = PreferencesManager(getApplication())
            prefs.setSetupComplete(false)
            onDone()
        } catch (e: Exception) {
            // Handle error
        }
    }

    fun seedDemoData(onDone: () -> Unit = {}) = viewModelScope.launch(Dispatchers.IO) {
        if (!BuildConfig.DEBUG) {
            onDone()
            return@launch
        }
        try {
            repository.seedDemoData()
        } catch (e: Exception) {
            // Handle error
        }
        onDone()
    }
}
