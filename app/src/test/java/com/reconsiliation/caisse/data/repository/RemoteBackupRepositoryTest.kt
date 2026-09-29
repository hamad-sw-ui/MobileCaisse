package com.reconsiliation.caisse.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.reconsiliation.caisse.data.local.entity.BoutiqueEntity
import com.reconsiliation.caisse.data.prefs.PreferencesManager
import com.reconsiliation.caisse.platform.remote.FakeGoogleDriveAuthManager
import com.reconsiliation.caisse.platform.remote.FakeRemoteStorageClient
import com.reconsiliation.caisse.platform.remote.RemoteBackupItem
import com.reconsiliation.caisse.utils.BackupManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TestContext : android.app.Application() {
    private val tempDir = File(System.getProperty("java.io.tmpdir"), "test_context_${System.nanoTime()}").apply { mkdirs() }
    private val prefsMap = mutableMapOf<String, Any>()

    override fun getCacheDir(): File = tempDir
    override fun getFilesDir(): File = tempDir
    override fun getDatabasePath(name: String): File = File(tempDir, name)
    override fun getPackageName(): String = "com.reconsiliation.caisse"
    override fun getExternalFilesDir(type: String?): File? = tempDir

    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
        return object : SharedPreferences {
            override fun getAll(): MutableMap<String, *> = prefsMap
            override fun getString(key: String, defValue: String?): String? = (prefsMap[key] as? String) ?: defValue
            override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? = defValues
            override fun getInt(key: String, defValue: Int): Int = (prefsMap[key] as? Int) ?: defValue
            override fun getLong(key: String, defValue: Long): Long = (prefsMap[key] as? Long) ?: defValue
            override fun getFloat(key: String, defValue: Float): Float = (prefsMap[key] as? Float) ?: defValue
            override fun getBoolean(key: String, defValue: Boolean): Boolean = (prefsMap[key] as? Boolean) ?: defValue
            override fun contains(key: String): Boolean = prefsMap.containsKey(key)
            override fun edit(): SharedPreferences.Editor = TestEditor(prefsMap)
            override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
            override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        }
    }

    private class TestEditor(private val map: MutableMap<String, Any>) : SharedPreferences.Editor {
        override fun putString(key: String, value: String?): SharedPreferences.Editor { if (value != null) map[key] = value else map.remove(key); return this }
        override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor = this
        override fun putInt(key: String, value: Int): SharedPreferences.Editor { map[key] = value; return this }
        override fun putLong(key: String, value: Long): SharedPreferences.Editor { map[key] = value; return this }
        override fun putFloat(key: String, value: Float): SharedPreferences.Editor { map[key] = value; return this }
        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor { map[key] = value; return this }
        override fun remove(key: String): SharedPreferences.Editor { map.remove(key); return this }
        override fun clear(): SharedPreferences.Editor { map.clear(); return this }
        override fun commit(): Boolean = true
        override fun apply() {}
    }
}

class RemoteBackupRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: TestContext
    private lateinit var db: FakeAppDatabase
    private lateinit var inventoryRepository: InventoryRepository
    private lateinit var mainRepository: MainRepository
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var fakeRemoteStorageClient: FakeRemoteStorageClient
    private lateinit var fakeAuthManager: FakeGoogleDriveAuthManager
    private lateinit var repository: RemoteBackupRepository

    private lateinit var dummyDbFile: File

    @Before
    fun setup() {
        context = TestContext()
        db = FakeAppDatabase()

        runBlocking {
            db.boutiqueDao().insertOrUpdate(
                BoutiqueEntity(
                    id = 1,
                    name = "Ma Boutique Test",
                    ownerName = "Propriétaire Test",
                    phoneNumber = "677112233",
                    operator = "MTN",
                    momoNumber = "677112233",
                    managerCode = "MANAGER123456"
                )
            )
        }

        inventoryRepository = InventoryRepository(
            db, db.stockDao(), db.priceHistoryDao(), db.stockMovementDao(),
            db.expenseDao(), db.actionLogDao(), db.subscriptionDao(), db.auditDao(),
            db.supplyDao(), db.supplierDao(), db.categoryDao(), db.recipeDao(),
            db.venteDao(), db.boutiqueDao()
        )
        mainRepository = MainRepository(db, context, inventoryRepository)
        preferencesManager = PreferencesManager(context)

        fakeRemoteStorageClient = FakeRemoteStorageClient()
        fakeAuthManager = FakeGoogleDriveAuthManager(connected = true)

        dummyDbFile = context.getDatabasePath("caisse.db").apply {
            parentFile?.mkdirs()
            writeText("DUMMY_LOCAL_SQLITE_DATABASE_DATA_FOR_TESTING")
        }

        repository = RemoteBackupRepository(
            context = context,
            remoteStorageClient = fakeRemoteStorageClient,
            authManager = fakeAuthManager,
            boutiqueDao = db.boutiqueDao(),
            preferencesManager = preferencesManager,
            mainRepository = mainRepository
        )
    }

    @After
    fun tearDown() {
        if (dummyDbFile.exists()) {
            dummyDbFile.delete()
        }
    }

    @Test
    fun `performRemoteBackup exports local db and uploads encrypted archive`() = runBlocking {
        val result = repository.performRemoteBackup("Password123")

        assertTrue("performRemoteBackup should succeed: ${result.exceptionOrNull()?.message}", result.isSuccess)
        val item = result.getOrThrow()
        assertNotNull(item.fileId)
        assertEquals(1, fakeRemoteStorageClient.items.size)
        assertEquals("Succès", preferencesManager.getLastRemoteBackupStatus())

        val logs = db.actionLogDao().getRecentLogs().first()
        val backupLog = logs.find { it.actionType == RemoteBackupRepository.ACTION_TYPE_REMOTE_BACKUP }
        assertNotNull("Action log must be recorded", backupLog)
        assertTrue(backupLog!!.details.contains("Sauvegarde distante réussie"))
        assertEquals("INFO", backupLog.severity)
    }

    @Test
    fun `performRemoteBackup fails gracefully when Google Auth is disconnected`() = runBlocking {
        fakeAuthManager.connected = false

        val result = repository.performRemoteBackup("Password123")

        assertTrue(result.isFailure)
        assertEquals(0, fakeRemoteStorageClient.items.size)

        val logs = db.actionLogDao().getRecentLogs().first()
        val backupLog = logs.find { it.actionType == RemoteBackupRepository.ACTION_TYPE_REMOTE_BACKUP }
        assertNotNull("Action log must be recorded", backupLog)
        assertTrue(backupLog!!.details.contains("non connecté"))
        assertEquals("CRITICAL", backupLog.severity)
    }

    @Test
    fun `performRemoteBackup fails when password is invalid`() = runBlocking {
        val result = repository.performRemoteBackup("weak")

        assertTrue(result.isFailure)
        assertEquals(0, fakeRemoteStorageClient.items.size)
    }

    @Test
    fun `performRemoteBackup fails when RemoteStorageClient upload fails`() = runBlocking {
        fakeRemoteStorageClient.shouldFailUpload = true

        val result = repository.performRemoteBackup("Password123")

        assertTrue(result.isFailure)

        val logs = db.actionLogDao().getRecentLogs().first()
        val backupLog = logs.find { it.actionType == RemoteBackupRepository.ACTION_TYPE_REMOTE_BACKUP }
        assertNotNull("Action log must be recorded", backupLog)
        assertTrue(backupLog!!.details.contains("Échec"))
        assertEquals("CRITICAL", backupLog.severity)
    }

    @Test
    fun `cleanOldRemoteBackups deduplicates same day backups and enforces retention policy`() = runBlocking {
        val sampleItems = listOf(
            RemoteBackupItem("id_1", "backup_2026-09-28_1000.db.enc", "2026-09-28T10:00:00Z", 100),
            RemoteBackupItem("id_2", "backup_2026-09-28_0800.db.enc", "2026-09-28T08:00:00Z", 100),
            RemoteBackupItem("id_3", "backup_2026-09-27_1000.db.enc", "2026-09-27T10:00:00Z", 100),
            RemoteBackupItem("id_4", "backup_2026-09-26_1000.db.enc", "2026-09-26T10:00:00Z", 100),
            RemoteBackupItem("id_5", "backup_2026-09-25_1000.db.enc", "2026-09-25T10:00:00Z", 100),
            RemoteBackupItem("id_6", "backup_2026-09-24_1000.db.enc", "2026-09-24T10:00:00Z", 100),
            RemoteBackupItem("id_7", "backup_2026-09-23_1000.db.enc", "2026-09-23T10:00:00Z", 100),
            RemoteBackupItem("id_8", "backup_2026-09-22_1000.db.enc", "2026-09-22T10:00:00Z", 100),
            RemoteBackupItem("id_9", "backup_2026-09-21_1000.db.enc", "2026-09-21T10:00:00Z", 100),
            RemoteBackupItem("id_10", "backup_2026-08-15_1000.db.enc", "2026-08-15T10:00:00Z", 100),
            RemoteBackupItem("id_11", "backup_2026-07-15_1000.db.enc", "2026-07-15T10:00:00Z", 100),
            RemoteBackupItem("id_12", "backup_2026-06-15_1000.db.enc", "2026-06-15T10:00:00Z", 100)
        )

        fakeRemoteStorageClient.items.addAll(sampleItems)

        val result = repository.cleanOldRemoteBackups()

        assertTrue(result.isSuccess)
        val remainingIds = fakeRemoteStorageClient.items.map { it.fileId }

        assertTrue("Duplicate same day backup id_2 must be deleted", fakeRemoteStorageClient.deletedFileIds.contains("id_2"))
        assertTrue("8th daily backup in current month id_9 must be deleted", fakeRemoteStorageClient.deletedFileIds.contains("id_9"))
        assertTrue("7 daily backups id_1 must be kept", remainingIds.contains("id_1"))
        assertTrue("Monthly backup id_10 must be kept", remainingIds.contains("id_10"))
    }

    @Test
    fun `cleanOldRemoteBackups ignores delete failure and completes`() = runBlocking {
        fakeRemoteStorageClient.items.addAll(
            listOf(
                RemoteBackupItem("id_1", "b1.db.enc", "2026-09-28T10:00:00Z", 100),
                RemoteBackupItem("id_2", "b2.db.enc", "2026-09-28T08:00:00Z", 100)
            )
        )
        fakeRemoteStorageClient.shouldFailDelete = true

        val result = repository.cleanOldRemoteBackups()

        assertTrue(result.isSuccess)
    }

    @Test
    fun `security check confirms zero passwords or OAuth tokens in logged action details`() = runBlocking {
        val derivedPassword = RemoteBackupRepository.deriveRemoteBackupPassword("MANAGER123456")
        repository.performRemoteBackup(derivedPassword)

        val logs = db.actionLogDao().getRecentLogs().first()
        val backupLog = logs.find { it.actionType == RemoteBackupRepository.ACTION_TYPE_REMOTE_BACKUP }
        assertNotNull(backupLog)

        val logText = backupLog!!.details
        assertFalse("Log details must never contain backup password", logText.contains("Password123"))
        assertFalse("Log details must never contain derived password", logText.contains(derivedPassword))
        assertFalse("Log details must never contain OAuth token", logText.contains("fake_access_token"))
    }

    @Test
    fun `deriveRemoteBackupPassword produces password passing BackupManager validation for all managerCode formats`() {
        val uppercaseCode = "MANAGER123456"
        val lowercaseCode = "manager123456"
        val numericCode = "123456789012"

        val derivedUpper = RemoteBackupRepository.deriveRemoteBackupPassword(uppercaseCode)
        val derivedLower = RemoteBackupRepository.deriveRemoteBackupPassword(lowercaseCode)
        val derivedNumeric = RemoteBackupRepository.deriveRemoteBackupPassword(numericCode)

        // Validate that all derived passwords satisfy BackupManager password strength rules
        BackupManager.validatePasswordStrength(derivedUpper)
        BackupManager.validatePasswordStrength(derivedLower)
        BackupManager.validatePasswordStrength(derivedNumeric)

        // Validate determinism
        assertEquals(derivedUpper, RemoteBackupRepository.deriveRemoteBackupPassword(uppercaseCode))
        assertEquals(derivedLower, RemoteBackupRepository.deriveRemoteBackupPassword(lowercaseCode))
        assertEquals(derivedNumeric, RemoteBackupRepository.deriveRemoteBackupPassword(numericCode))
    }
}
