package com.reconsiliation.caisse.ui.viewmodel

import com.reconsiliation.caisse.data.local.entity.ActionLogEntity
import com.reconsiliation.caisse.data.repository.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
class MaintenanceViewModelTest {

    private lateinit var viewModel: MaintenanceViewModel
    private lateinit var db: FakeAppDatabase
    private lateinit var repository: MainRepository
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        db = FakeAppDatabase()
        val inventoryRepository = InventoryRepository(
            db, db.stockDao(), db.priceHistoryDao(), db.stockMovementDao(),
            db.expenseDao(), db.actionLogDao(), db.subscriptionDao(), db.auditDao(),
            db.supplyDao(), db.supplierDao(), db.categoryDao(), db.recipeDao(),
            db.venteDao(), db.boutiqueDao()
        )
        repository = MainRepository(db, FakeContext(), inventoryRepository)
        viewModel = MaintenanceViewModel(FakeContext(), repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `recentLogs should reflect database logs`() = runTest {
        val log = ActionLogEntity(actionType = "TEST", details = "Details", userRole = "MANAGER", date = Date())
        db.actionLogDao().insert(log)
        
        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.recentLogs.collect {}
        }
        
        advanceUntilIdle()
        
        val logs = viewModel.recentLogs.value
        assertEquals(1, logs.size)
        assertEquals("TEST", logs[0].actionType)
        
        collectJob.cancel()
    }

    @Test
    fun `purgeOldData should call repository purge`() = runTest {
        val result = viewModel.purgeOldData(12, "MANAGER")
        assertEquals(0, result) 
    }
}
