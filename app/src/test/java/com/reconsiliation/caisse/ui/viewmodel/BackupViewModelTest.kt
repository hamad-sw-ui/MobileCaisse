package com.reconsiliation.caisse.ui.viewmodel

import com.reconsiliation.caisse.data.local.entity.BoutiqueEntity
import com.reconsiliation.caisse.data.prefs.PreferencesManager
import com.reconsiliation.caisse.data.repository.*
import com.reconsiliation.caisse.platform.EmailSender
import com.reconsiliation.caisse.platform.ShareManager
import com.reconsiliation.caisse.platform.remote.FakeGoogleDriveAuthManager
import com.reconsiliation.caisse.platform.remote.FakeRemoteStorageClient
import com.reconsiliation.caisse.platform.remote.RemoteBackupItem
import com.reconsiliation.caisse.ui.state.BackupUiState
import com.reconsiliation.caisse.utils.BackupManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class BackupViewModelTest {

    private lateinit var viewModel: BackupViewModel
    private lateinit var repository: MainRepository
    private lateinit var remoteBackupRepository: RemoteBackupRepository
    private lateinit var fakeAuthManager: FakeGoogleDriveAuthManager
    private lateinit var fakeRemoteStorageClient: FakeRemoteStorageClient
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var shareManager: FakeShareManager
    private val testDispatcher = StandardTestDispatcher()

    class FakeEmailSender : EmailSender(TestContext()) {
        override val isConfigured: Boolean = true
        override fun send(to: String, subject: String, body: String): Result<Unit> = Result.success(Unit)
    }

    class FakeShareManager : ShareManager(TestContext()) {
        var lastSharedFile: File? = null
        override fun shareFile(file: File, mimeType: String, title: String) {
            lastSharedFile = file
        }
    }

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val context = TestContext()
        val db = FakeAppDatabase()

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

        val inventoryRepository = InventoryRepository(
            db, db.stockDao(), db.priceHistoryDao(), db.stockMovementDao(),
            db.expenseDao(), db.actionLogDao(), db.subscriptionDao(), db.auditDao(),
            db.supplyDao(), db.supplierDao(), db.categoryDao(), db.recipeDao(),
            db.venteDao(), db.boutiqueDao()
        )
        repository = MainRepository(db, context, inventoryRepository)
        preferencesManager = PreferencesManager(context)
        fakeRemoteStorageClient = FakeRemoteStorageClient()
        fakeAuthManager = FakeGoogleDriveAuthManager(connected = true)

        val dummyDbFile = context.getDatabasePath("caisse.db").apply {
            parentFile?.mkdirs()
            writeText("DUMMY_LOCAL_SQLITE_DATABASE_DATA_FOR_TESTING")
        }

        remoteBackupRepository = RemoteBackupRepository(
            context = context,
            remoteStorageClient = fakeRemoteStorageClient,
            authManager = fakeAuthManager,
            boutiqueDao = db.boutiqueDao(),
            preferencesManager = preferencesManager,
            mainRepository = repository
        )

        shareManager = FakeShareManager()
        viewModel = BackupViewModel(
            application = context,
            repository = repository,
            shareManager = shareManager,
            remoteBackupRepository = remoteBackupRepository,
            authManager = fakeAuthManager,
            preferencesManager = preferencesManager,
            emailSender = FakeEmailSender()
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state should be Idle`() = runTest {
        assertEquals(BackupUiState.Idle, viewModel.backupState.value)
    }

    @Test
    fun `clearBackupState should reset state to Idle`() = runTest {
        viewModel.clearBackupState()
        advanceUntilIdle()
        assertEquals(BackupUiState.Idle, viewModel.backupState.value)
    }

    @Test
    fun `isBackupEncrypted should return false for fake file`() = runTest {
        val file = File("test.db")
        val result = viewModel.isBackupEncrypted(file)
        assertFalse(result)
    }

    @Test
    fun `hasMirrorBackup should return value from repository`() = runTest {
        val context = TestContext()
        val result = viewModel.hasMirrorBackup(context)
        assertFalse(result)
    }

    @Test
    fun `toggleRemoteBackup should update preference`() = runTest {
        viewModel.toggleRemoteBackup(true)
        assertTrue(viewModel.isRemoteBackupEnabled.value)
        assertTrue(preferencesManager.isRemoteBackupEnabled())

        viewModel.toggleRemoteBackup(false)
        assertFalse(viewModel.isRemoteBackupEnabled.value)
        assertFalse(preferencesManager.isRemoteBackupEnabled())
    }

    @Test
    fun `toggleWifiOnlyBackup should update preference`() = runTest {
        viewModel.toggleWifiOnlyBackup(false)
        assertFalse(viewModel.isWifiOnlyBackupEnabled.value)
        assertFalse(preferencesManager.isWifiOnlyBackupEnabled())

        viewModel.toggleWifiOnlyBackup(true)
        assertTrue(viewModel.isWifiOnlyBackupEnabled.value)
        assertTrue(preferencesManager.isWifiOnlyBackupEnabled())
    }

    @Test
    fun `signOutGoogleDrive should disconnect authManager and refresh state`() = runTest {
        assertTrue(viewModel.isGoogleConnected.value)

        val job = viewModel.signOutGoogleDrive()
        job.join()

        assertFalse(viewModel.isGoogleConnected.value)
        assertNull(viewModel.googleAccountEmail.value)
    }

    @Test
    fun `triggerManualRemoteBackup should execute backup and set Success state`() = runTest {
        val job = viewModel.triggerManualRemoteBackup()
        job.join()

        assertTrue("backupState should be Success but was ${viewModel.backupState.value}", viewModel.backupState.value is BackupUiState.Success)
        assertEquals("Succès", preferencesManager.getLastRemoteBackupStatus())
    }

    @Test
    fun `fetchCloudBackups should populate cloudBackups StateFlow`() = runTest {
        fakeRemoteStorageClient.items.add(RemoteBackupItem("item_1", "backup1.db.enc", "2026-10-02T10:00:00Z", 1000))

        val job = viewModel.fetchCloudBackups()
        job.join()

        assertEquals(1, viewModel.cloudBackups.value.size)
        assertEquals("item_1", viewModel.cloudBackups.value[0].fileId)
    }

    @Test
    fun `restoreCloudBackup should download remote backup and clean up temp file`() = runTest {
        val item = RemoteBackupItem("item_1", "backup1.db.enc", "2026-10-02T10:00:00Z", 1000)
        fakeRemoteStorageClient.items.add(item)
        val testContext = TestContext()

        var restoredCalled = false
        val job = viewModel.restoreCloudBackup(
            context = testContext,
            remoteItem = item,
            userRole = "MANAGER",
            onRestored = { restoredCalled = true }
        )
        job.join()

        val tempFiles = testContext.cacheDir.listFiles { _, name -> name.startsWith("temp_cloud_restore_") }
        assertTrue("Temp cloud restore files must be cleaned up in finally", tempFiles.isNullOrEmpty())
    }
}
