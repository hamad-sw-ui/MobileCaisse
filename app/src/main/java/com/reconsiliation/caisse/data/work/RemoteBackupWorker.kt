package com.reconsiliation.caisse.data.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.reconsiliation.caisse.data.prefs.PreferencesManager
import com.reconsiliation.caisse.data.repository.MainRepository
import com.reconsiliation.caisse.data.repository.RemoteBackupRepository
import com.reconsiliation.caisse.platform.remote.GoogleDriveAuthManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class RemoteBackupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: RemoteBackupRepository,
    private val authManager: GoogleDriveAuthManager,
    private val preferencesManager: PreferencesManager,
    private val mainRepository: MainRepository
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "remote_backup_periodic_work"
        private const val TAG = "RemoteBackupWorker"

        fun schedulePeriodic(context: Context, preferencesManager: PreferencesManager) {
            val networkType = if (preferencesManager.isWifiOnlyBackupEnabled()) {
                NetworkType.UNMETERED
            } else {
                NetworkType.CONNECTED
            }

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(networkType)
                .setRequiresBatteryNotLow(true)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<RemoteBackupWorker>(24, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }

        fun cancelPeriodic(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }

    override suspend fun doWork(): Result {
        return try {
            if (!preferencesManager.isRemoteBackupEnabled()) {
                Log.d(TAG, "Sauvegarde distante désactivée dans les préférences")
                return Result.success()
            }

            if (!authManager.isConnected()) {
                val errorMsg = "Compte Google non connecté ou autorisations manquantes pour la sauvegarde distante"
                Log.w(TAG, errorMsg)
                mainRepository.logAction(
                    type = RemoteBackupRepository.ACTION_TYPE_REMOTE_BACKUP,
                    details = errorMsg,
                    severity = "WARNING"
                )
                return Result.failure()
            }

            val boutique = mainRepository.getBoutiqueOnce()
            if (boutique == null) {
                Log.e(TAG, "Boutique non configurée")
                return Result.failure()
            }

            val password = RemoteBackupRepository.deriveRemoteBackupPassword(boutique.managerCode)
            val result = repository.performRemoteBackup(password)

            if (result.isSuccess) {
                Log.d(TAG, "Sauvegarde distante réussie via WorkManager")
                Result.success()
            } else {
                val exception = result.exceptionOrNull()
                Log.e(TAG, "Échec de la sauvegarde distante via WorkManager: ${exception?.message}")
                if (exception is java.io.IOException) {
                    Result.retry()
                } else {
                    Result.failure()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur imprévue dans RemoteBackupWorker: ${e.message}", e)
            Result.retry()
        }
    }
}
