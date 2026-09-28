package com.reconsiliation.caisse.platform.remote

import com.reconsiliation.caisse.utils.BackupMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
internal data class DriveFileResponse(
    val id: String,
    val name: String,
    val createdTime: String? = null,
    val size: String? = null
)

@Serializable
internal data class DriveFileListResponse(
    val files: List<DriveFileResponse> = emptyList()
)

@Serializable
internal data class DriveFileMetadataRequest(
    val name: String,
    val parents: List<String> = listOf("appDataFolder")
)

@Singleton
class GoogleDriveStorageClient @Inject constructor(
    private val authManager: GoogleDriveAuthManager,
    private val okHttpClient: OkHttpClient,
    private val baseUrl: String = DEFAULT_BASE_URL
) : RemoteStorageClient {

    companion object {
        const val DEFAULT_BASE_URL = "https://www.googleapis.com"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val OCTET_STREAM_MEDIA_TYPE = "application/octet-stream".toMediaType()
        private val MULTIPART_RELATED_MEDIA_TYPE = "multipart/related".toMediaType()

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }

    override suspend fun uploadBackup(file: File, metadata: BackupMetadata): Result<RemoteBackupItem> = withContext(Dispatchers.IO) {
        runCatching {
            require(file.exists() && file.length() > 0) { "Fichier de sauvegarde introuvable ou vide : ${file.name}" }

            val token = authManager.getAccessToken().getOrThrow()
            val url = "$baseUrl/upload/drive/v3/files?uploadType=multipart"

            val metadataJson = json.encodeToString(
                DriveFileMetadataRequest.serializer(),
                DriveFileMetadataRequest(name = file.name, parents = listOf("appDataFolder"))
            )

            val multipartBody = MultipartBody.Builder()
                .setType(MULTIPART_RELATED_MEDIA_TYPE)
                .addPart(metadataJson.toRequestBody(JSON_MEDIA_TYPE))
                .addPart(file.asRequestBody(OCTET_STREAM_MEDIA_TYPE))
                .build()

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .post(multipartBody)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val code = response.code
                    val errorBody = response.body?.string() ?: ""
                    throw RemoteStorageException("Échec de l'upload vers Google Drive (HTTP $code): $errorBody")
                }

                val bodyString = response.body?.string()
                    ?: throw RemoteStorageException("Réponse vide de Google Drive après upload")

                val driveFile = json.decodeFromString(DriveFileResponse.serializer(), bodyString)
                RemoteBackupItem(
                    fileId = driveFile.id,
                    fileName = driveFile.name,
                    createdAtIso = driveFile.createdTime ?: metadata.createdAt,
                    sizeBytes = driveFile.size?.toLongOrNull() ?: file.length()
                )
            }
        }
    }

    override suspend fun listBackups(): Result<List<RemoteBackupItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val token = authManager.getAccessToken().getOrThrow()
            val url = "$baseUrl/drive/v3/files?spaces=appDataFolder&fields=files(id,name,createdTime,size)&orderBy=createdTime%20desc"

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val code = response.code
                    val errorBody = response.body?.string() ?: ""
                    throw RemoteStorageException("Échec du listing des sauvegardes (HTTP $code): $errorBody")
                }

                val bodyString = response.body?.string() ?: "{}"
                val listResponse = json.decodeFromString(DriveFileListResponse.serializer(), bodyString)

                listResponse.files.map { file ->
                    RemoteBackupItem(
                        fileId = file.id,
                        fileName = file.name,
                        createdAtIso = file.createdTime ?: "",
                        sizeBytes = file.size?.toLongOrNull() ?: 0L
                    )
                }
            }
        }
    }

    override suspend fun downloadBackup(fileId: String, destFile: File): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(fileId.isNotBlank()) { "fileId ne peut pas être vide" }

            val token = authManager.getAccessToken().getOrThrow()
            val url = "$baseUrl/drive/v3/files/$fileId?alt=media"

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val tempFile = File(destFile.parentFile, "${destFile.name}.tmp")
            try {
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val code = response.code
                        val errorBody = response.body?.string() ?: ""
                        throw RemoteStorageException("Échec du téléchargement (HTTP $code): $errorBody")
                    }

                    val body = response.body
                        ?: throw RemoteStorageException("Réponse vide de Google Drive au téléchargement")

                    tempFile.parentFile?.mkdirs()
                    body.byteStream().use { input ->
                        tempFile.outputStream().buffered().use { output ->
                            input.copyTo(output, 8 * 1024)
                        }
                    }
                }

                if (destFile.exists()) {
                    destFile.delete()
                }
                if (!tempFile.renameTo(destFile)) {
                    // Fallback copy if renameTo fails
                    tempFile.copyTo(destFile, overwrite = true)
                    tempFile.delete()
                }
            } catch (e: Exception) {
                if (tempFile.exists()) {
                    tempFile.delete()
                }
                throw e
            }
            Unit
        }
    }

    override suspend fun deleteBackup(fileId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(fileId.isNotBlank()) { "fileId ne peut pas être vide" }

            val token = authManager.getAccessToken().getOrThrow()
            val url = "$baseUrl/drive/v3/files/$fileId"

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .delete()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful && response.code != 404) {
                    val code = response.code
                    val errorBody = response.body?.string() ?: ""
                    throw RemoteStorageException("Échec de suppression sur Google Drive (HTTP $code): $errorBody")
                }
            }
            Unit
        }
    }
}

class RemoteStorageException(message: String) : Exception(message)
