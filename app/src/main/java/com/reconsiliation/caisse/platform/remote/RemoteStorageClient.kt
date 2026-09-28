package com.reconsiliation.caisse.platform.remote

import com.reconsiliation.caisse.utils.BackupMetadata
import java.io.File

data class RemoteBackupItem(
    val fileId: String,
    val fileName: String,
    val createdAtIso: String,
    val sizeBytes: Long
)

interface RemoteStorageClient {
    suspend fun uploadBackup(file: File, metadata: BackupMetadata): Result<RemoteBackupItem>
    suspend fun listBackups(): Result<List<RemoteBackupItem>>
    suspend fun downloadBackup(fileId: String, destFile: File): Result<Unit>
    suspend fun deleteBackup(fileId: String): Result<Unit>
}
