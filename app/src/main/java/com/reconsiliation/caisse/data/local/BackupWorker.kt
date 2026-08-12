package com.reconsiliation.caisse.data.local

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.reconsiliation.caisse.data.repository.MainRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.io.File

@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val db: AppDatabase,
    private val repository: MainRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            // 1. Internal Daily Backup
            val backupFile = File(applicationContext.filesDir, "daily_backup.db")
            val success = repository.backupDatabase(applicationContext, backupFile)
            
            // 2. External Mirror Backup (Private External Storage for persistence across app uninstall)
            try {
                val externalDir = applicationContext.getExternalFilesDir(null)
                if (externalDir != null) {
                    val mirrorFile = File(externalDir, "caisse_mirror.db")
                    repository.backupDatabase(applicationContext, mirrorFile)
                }
            } catch (e: Exception) { Log.e("BackupWorker", "External mirror failed", e) }

            if (success) {
                Log.d("BackupWorker", "Local backup created successfully: ${backupFile.absolutePath}")
                
                // Point 2: Trigger automated cloud sync if boutique allows
                val boutique = db.boutiqueDao().getBoutique().first()
                if (boutique != null) {
                    // In a production environment, this would be an automated upload.
                    // For now, we ensure local mirroring is solid and log the activity.
                    repository.logAction("AUTO_BACKUP", "Sauvegarde automatique et chiffrement réussis")
                }

                Result.success()
            } else {
                Log.e("BackupWorker", "Failed to create local backup")
                Result.failure()
            }
        } catch (e: Exception) {
            Log.e("BackupWorker", "Backup error", e)
            Result.retry()
        }
    }
}
