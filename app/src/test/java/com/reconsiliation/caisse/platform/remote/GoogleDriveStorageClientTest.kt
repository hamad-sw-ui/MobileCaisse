package com.reconsiliation.caisse.platform.remote

import com.reconsiliation.caisse.utils.BackupMetadata
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class GoogleDriveStorageClientTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var mockWebServer: MockWebServer
    private lateinit var authManager: FakeGoogleDriveAuthManager
    private lateinit var storageClient: GoogleDriveStorageClient

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        authManager = FakeGoogleDriveAuthManager()
        val okHttpClient = OkHttpClient.Builder().build()
        val baseUrl = mockWebServer.url("/").toString().removeSuffix("/")

        storageClient = GoogleDriveStorageClient(
            authManager = authManager,
            okHttpClient = okHttpClient,
            baseUrl = baseUrl
        )
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    private fun createDummyBackupFile(fileName: String = "test_backup.db.enc"): File {
        val file = tempFolder.newFile(fileName)
        file.writeText("DUMMY_ENCRYPTED_BACKUP_CONTENT")
        return file
    }

    private fun createSampleMetadata() = BackupMetadata(
        formatVersion = 2,
        createdAt = "2026-09-28T00:00:00Z",
        boutiquePhone = "677112233",
        boutiqueManagerCode = "MANAGER123456",
        checksum = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
        databaseVersion = 28
    )

    @Test
    fun `uploadBackup sends multipart request to appDataFolder and returns RemoteBackupItem`() = runBlocking {
        val jsonResponse = """
            {
              "id": "drive_file_999",
              "name": "test_backup.db.enc",
              "createdTime": "2026-09-28T12:00:00Z",
              "size": "30"
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(jsonResponse))

        val dummyFile = createDummyBackupFile()
        val result = storageClient.uploadBackup(dummyFile, createSampleMetadata())

        assertTrue("Upload failed: ${result.exceptionOrNull()?.message}", result.isSuccess)
        val item = result.getOrThrow()
        assertEquals("drive_file_999", item.fileId)
        assertEquals("test_backup.db.enc", item.fileName)
        assertEquals("2026-09-28T12:00:00Z", item.createdAtIso)

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("POST", recordedRequest.method)
        assertTrue(recordedRequest.path!!.contains("/upload/drive/v3/files"))
        assertTrue(recordedRequest.path!!.contains("uploadType=multipart"))
        assertEquals("Bearer fake_access_token_12345", recordedRequest.getHeader("Authorization"))

        val bodyText = recordedRequest.body.readUtf8()
        assertTrue("Request body must specify appDataFolder parent", bodyText.contains("appDataFolder"))
        assertTrue("Request body must include file name", bodyText.contains("test_backup.db.enc"))
    }

    @Test
    fun `listBackups queries appDataFolder and parses file list`() = runBlocking {
        val jsonResponse = """
            {
              "files": [
                {
                  "id": "file_1",
                  "name": "backup1.db.enc",
                  "createdTime": "2026-09-28T10:00:00Z",
                  "size": "2048"
                },
                {
                  "id": "file_2",
                  "name": "backup2.db.enc",
                  "createdTime": "2026-09-27T10:00:00Z",
                  "size": "4096"
                }
              ]
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(jsonResponse))

        val result = storageClient.listBackups()

        assertTrue(result.isSuccess)
        val files = result.getOrThrow()
        assertEquals(2, files.size)
        assertEquals("file_1", files[0].fileId)
        assertEquals("backup1.db.enc", files[0].fileName)
        assertEquals(2048L, files[0].sizeBytes)

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("GET", recordedRequest.method)
        assertTrue(recordedRequest.path!!.contains("spaces=appDataFolder"))
        assertEquals("Bearer fake_access_token_12345", recordedRequest.getHeader("Authorization"))
    }

    @Test
    fun `downloadBackup streams payload into destFile via temporary file`() = runBlocking {
        val expectedPayload = "SQLITE_CIPHER_BINARY_DATA_STREAM"
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(expectedPayload))

        val destFile = File(tempFolder.root, "restored_database.db.enc")
        val result = storageClient.downloadBackup("file_123", destFile)

        assertTrue(result.isSuccess)
        assertTrue(destFile.exists())
        assertEquals(expectedPayload, destFile.readText())

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("GET", recordedRequest.method)
        assertTrue(recordedRequest.path!!.contains("/drive/v3/files/file_123?alt=media"))
    }

    @Test
    fun `downloadBackup cleans up temp file when download fails`() = runBlocking {
        mockWebServer.enqueue(MockResponse().setResponseCode(404).setBody("Not Found"))

        val destFile = File(tempFolder.root, "failed_database.db.enc")
        val result = storageClient.downloadBackup("missing_id", destFile)

        assertTrue(result.isFailure)
        assertFalse("Destination file must not exist on failure", destFile.exists())
        val tempFile = File(destFile.parentFile, "${destFile.name}.tmp")
        assertFalse("Temp file must be cleaned up on failure", tempFile.exists())
    }

    @Test
    fun `deleteBackup sends DELETE request for fileId`() = runBlocking {
        mockWebServer.enqueue(MockResponse().setResponseCode(204))

        val result = storageClient.deleteBackup("file_to_delete_55")

        assertTrue(result.isSuccess)

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("DELETE", recordedRequest.method)
        assertTrue(recordedRequest.path!!.contains("/drive/v3/files/file_to_delete_55"))
    }

    @Test
    fun `HTTP 401 Unauthorized returns failure Result`() = runBlocking {
        mockWebServer.enqueue(MockResponse().setResponseCode(401).setBody("Unauthorized Token Expired"))

        val result = storageClient.listBackups()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("401"))
    }

    @Test
    fun `HTTP 500 Internal Server Error returns failure Result`() = runBlocking {
        mockWebServer.enqueue(MockResponse().setResponseCode(500).setBody("Internal Server Error"))

        val result = storageClient.listBackups()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("500"))
    }

    @Test
    fun `auth failure in AuthManager propagates failure`() = runBlocking {
        authManager.shouldFailToken = true

        val result = storageClient.listBackups()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("Échec token"))
    }
}
