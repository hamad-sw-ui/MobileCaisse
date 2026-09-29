package com.reconsiliation.caisse.data.repository

import android.content.Context
import com.reconsiliation.caisse.data.local.AppDatabase
import com.reconsiliation.caisse.data.local.dao.BoutiqueDao
import com.reconsiliation.caisse.data.prefs.PreferencesManager
import com.reconsiliation.caisse.platform.remote.GoogleDriveAuthManager
import com.reconsiliation.caisse.platform.remote.RemoteBackupItem
import com.reconsiliation.caisse.platform.remote.RemoteStorageClient
import com.reconsiliation.caisse.utils.BackupManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemoteBackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val remoteStorageClient: RemoteStorageClient,
    private val authManager: GoogleDriveAuthManager,
    private val boutiqueDao: BoutiqueDao,
    private val preferencesManager: PreferencesManager,
    private val mainRepository: MainRepository
) {

    companion object {
        const val ACTION_TYPE_REMOTE_BACKUP = "REMOTE_BACKUP"
        const val MAX_DAILY_RETENTION_SLOTS = 7
        const val MAX_MONTHLY_RETENTION_SLOTS = 4

        /**
         * Dérive de manière déterministe un mot de passe de sauvegarde robuste à partir du code manager.
         * Applique le suffixe "a1A!" pour garantir la présence simultanée d'une minuscule ('a'),
         * d'un chiffre ('1') et d'une majuscule ('A') exigés par [BackupManager.validatePasswordStrength].
         */
        fun deriveRemoteBackupPassword(managerCode: String): String {
            require(managerCode.isNotBlank()) { "Le code manager ne peut pas être vide" }
            return "${managerCode.trim()}a1A!"
        }
    }

    /**
     * Exécute une sauvegarde distante chiffrée vers Google Drive.
     *
     * @param password Mot de passe de sauvegarde de l'utilisateur.
     * @return Result contenant l'item de sauvegarde distante créé.
     */
    suspend fun performRemoteBackup(password: String): Result<RemoteBackupItem> = withContext(Dispatchers.IO) {
        runCatching {
            if (!authManager.isConnected()) {
                val errorMsg = "Compte Google non connecté ou autorisations manquantes"
                preferencesManager.setLastRemoteBackupStatus("Échec : $errorMsg")
                mainRepository.logAction(
                    type = ACTION_TYPE_REMOTE_BACKUP,
                    details = errorMsg,
                    severity = "CRITICAL"
                )
                throw IllegalStateException(errorMsg)
            }

            val boutique = boutiqueDao.getBoutiqueOnce()
                ?: throw IllegalStateException("Informations de la boutique introuvables")

            val dbFile = context.getDatabasePath("caisse.db")
            if (!dbFile.exists() || dbFile.length() == 0L) {
                val errorMsg = "Fichier de base de données locale introuvable ou vide"
                preferencesManager.setLastRemoteBackupStatus("Échec : $errorMsg")
                mainRepository.logAction(
                    type = ACTION_TYPE_REMOTE_BACKUP,
                    details = errorMsg,
                    severity = "CRITICAL"
                )
                throw IllegalStateException(errorMsg)
            }

            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val tempBackupFile = File(context.cacheDir, "temp_remote_backup_$timeStamp.db.enc")

            try {
                // 1. Export chiffré AES-GCM localement
                BackupManager.exportBackupWithPassword(
                    dbFile = dbFile,
                    outputFile = tempBackupFile,
                    password = password,
                    boutiquePhone = boutique.phoneNumber,
                    boutiqueManagerCode = boutique.managerCode,
                    databaseVersion = 30
                ).getOrThrow()

                // 2. Upload binaire vers le cloud
                val remoteItem = remoteStorageClient.uploadBackup(
                    file = tempBackupFile,
                    metadata = BackupManager.peekMetadata(tempBackupFile, password).getOrThrow()
                ).getOrThrow()

                // 3. Application de la politique de rotation
                cleanOldRemoteBackups()

                // 4. Mise à jour de l'état
                val now = System.currentTimeMillis()
                preferencesManager.setLastRemoteBackupTime(now)
                preferencesManager.setLastRemoteBackupStatus("Succès")

                mainRepository.logAction(
                    type = ACTION_TYPE_REMOTE_BACKUP,
                    details = "Sauvegarde distante réussie vers Google Drive: ${remoteItem.fileName} (${remoteItem.sizeBytes} octets)",
                    severity = "INFO"
                )

                remoteItem
            } catch (e: Exception) {
                val errorDetails = e.message ?: "Erreur inconnue de sauvegarde distante"
                preferencesManager.setLastRemoteBackupStatus("Échec : $errorDetails")
                mainRepository.logAction(
                    type = ACTION_TYPE_REMOTE_BACKUP,
                    details = "Échec de la sauvegarde distante : $errorDetails",
                    severity = "CRITICAL"
                )
                throw e
            } finally {
                if (tempBackupFile.exists()) {
                    tempBackupFile.delete()
                }
            }
        }
    }

    /**
     * Applique la politique de rotation déterministe sur les sauvegardes distantes :
     * - Conserve les 7 jours civils distincts les plus récents ;
     * - Déduplique les sauvegardes du même jour en ne gardant que la plus récente ;
     * - Conserve le 1er fichier de jusqu'à 4 mois civils distincts pour les sauvegardes > 7 jours ;
     * - Supprime les fichiers expirés.
     */
    suspend fun cleanOldRemoteBackups(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val itemsResult = remoteStorageClient.listBackups()
            if (itemsResult.isFailure) return@runCatching 0

            val items = itemsResult.getOrThrow()
            if (items.isEmpty()) return@runCatching 0

            val sdfDay = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            val sdfMonth = SimpleDateFormat("yyyy-MM", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }

            val parsedItems = items.map { item ->
                val date = parseIsoDate(item.createdAtIso)
                val dayKey = sdfDay.format(date)
                val monthKey = sdfMonth.format(date)
                ParsedBackupItem(item, date, dayKey, monthKey)
            }.sortedByDescending { it.date.time }

            // 1. Déduplication quotidienne : conserver uniquement le plus récent par jour
            val newestByDay = mutableMapOf<String, ParsedBackupItem>()
            val duplicatesToDelete = mutableListOf<ParsedBackupItem>()

            for (parsed in parsedItems) {
                if (!newestByDay.containsKey(parsed.dayKey)) {
                    newestByDay[parsed.dayKey] = parsed
                } else {
                    duplicatesToDelete.add(parsed)
                }
            }

            val distinctDailyItems = newestByDay.values.sortedByDescending { it.date.time }

            // 2. Sélection des 7 quotidiennes
            val keepDaily = distinctDailyItems.take(MAX_DAILY_RETENTION_SLOTS).toSet()

            // 3. Sélection des 4 mensuelles pour les mois antérieurs à ceux couverts par les 7 quotidiennes
            val olderItems = distinctDailyItems.drop(MAX_DAILY_RETENTION_SLOTS)
            val keepMonthly = mutableSetOf<ParsedBackupItem>()
            val monthsSeen = keepDaily.map { it.monthKey }.toMutableSet()

            for (parsed in olderItems) {
                if (!monthsSeen.contains(parsed.monthKey) && keepMonthly.size < MAX_MONTHLY_RETENTION_SLOTS) {
                    monthsSeen.add(parsed.monthKey)
                    keepMonthly.add(parsed)
                }
            }

            val totalKeep = keepDaily + keepMonthly
            val itemsToDelete = (parsedItems - totalKeep).toSet()

            var deletedCount = 0
            for (toDelete in itemsToDelete) {
                try {
                    remoteStorageClient.deleteBackup(toDelete.item.fileId)
                    deletedCount++
                } catch (e: Exception) {
                    // Erreur non bloquante lors de la rotation d'un fichier individuel
                }
            }
            deletedCount
        }
    }

    private fun parseIsoDate(isoString: String): Date {
        val clean = isoString.trim().removeSuffix("Z")
        return try {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.parse(clean) ?: Date(0L)
        } catch (e: Exception) {
            Date(0L)
        }
    }

    private data class ParsedBackupItem(
        val item: RemoteBackupItem,
        val date: Date,
        val dayKey: String,
        val monthKey: String
    )
}
