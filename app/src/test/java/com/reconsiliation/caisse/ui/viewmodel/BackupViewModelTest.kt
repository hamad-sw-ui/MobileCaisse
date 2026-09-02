package com.reconsiliation.caisse.ui.viewmodel

import com.reconsiliation.caisse.data.repository.*
import com.reconsiliation.caisse.platform.ShareManager
import com.reconsiliation.caisse.ui.state.BackupUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class BackupViewModelTest {

    private lateinit var viewModel: BackupViewModel
    private lateinit var repository: MainRepository
    private lateinit var shareManager: FakeShareManager
    private val testDispatcher = StandardTestDispatcher()

    class FakeShareManager : ShareManager(FakeContext()) {
        var lastSharedFile: File? = null
        override fun shareFile(file: File, mimeType: String, title: String) {
            lastSharedFile = file
        }
    }

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val db = FakeAppDatabase()
        val inventoryRepository = InventoryRepository(
            db, db.stockDao(), db.priceHistoryDao(), db.stockMovementDao(),
            db.expenseDao(), db.actionLogDao(), db.subscriptionDao(), db.auditDao(),
            db.supplyDao(), db.supplierDao(), db.categoryDao(), db.recipeDao(),
            db.venteDao(), db.boutiqueDao()
        )
        repository = MainRepository(db, FakeContext(), inventoryRepository)
        shareManager = FakeShareManager()
        viewModel = BackupViewModel(FakeContext(), repository, shareManager)
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
        val context = FakeContext()
        val result = viewModel.hasMirrorBackup(context)
        assertFalse(result)
    }
}
