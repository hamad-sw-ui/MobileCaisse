package com.reconsiliation.caisse.platform.remote

import com.reconsiliation.caisse.utils.BackupMetadata
import java.io.File

class FakeRemoteStorageClient(
    val items: MutableList<RemoteBackupItem> = mutableListOf(),
    var shouldFailUpload: Boolean = false,
    var shouldFailList: Boolean = false,
    var shouldFailDownload: Boolean = false,
    var shouldFailDelete: Boolean = false,
    var uploadErrorMessage: String = "Erreur d'upload factice",
    val deletedFileIds: MutableList<String> = mutableListOf()
) : RemoteStorageClient {

    override suspend fun uploadBackup(file: File, metadata: BackupMetadata): Result<RemoteBackupItem> {
        if (shouldFailUpload) {
            return Result.failure(RemoteStorageException(uploadErrorMessage))
        }
        val fileId = "fake_drive_id_${System.currentTimeMillis()}_${items.size}"
        val item = RemoteBackupItem(
            fileId = fileId,
            fileName = file.name,
            createdAtIso = metadata.createdAt,
            sizeBytes = file.length()
        )
        items.add(0, item)
        return Result.success(item)
    }

    override suspend fun listBackups(): Result<List<RemoteBackupItem>> {
        if (shouldFailList) {
            return Result.failure(RemoteStorageException("Erreur de listing factice"))
        }
        return Result.success(items.toList())
    }

    override suspend fun downloadBackup(fileId: String, destFile: File): Result<Unit> {
        if (shouldFailDownload) {
            return Result.failure(RemoteStorageException("Erreur de téléchargement factice"))
        }
        val item = items.find { it.fileId == fileId }
            ?: return Result.failure(RemoteStorageException("Fichier introuvable : $fileId"))

        destFile.parentFile?.mkdirs()
        destFile.writeText("RESTORED_DUMMY_CONTENT_FOR_${item.fileName}")
        return Result.success(Unit)
    }

    override suspend fun deleteBackup(fileId: String): Result<Unit> {
        if (shouldFailDelete) {
            return Result.failure(RemoteStorageException("Erreur de suppression factice"))
        }
        deletedFileIds.add(fileId)
        items.removeAll { it.fileId == fileId }
        return Result.success(Unit)
    }
}
