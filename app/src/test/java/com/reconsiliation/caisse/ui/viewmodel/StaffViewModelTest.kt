package com.reconsiliation.caisse.ui.viewmodel

import com.reconsiliation.caisse.data.local.entity.StaffEntity
import com.reconsiliation.caisse.data.repository.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StaffViewModelTest {

    private lateinit var viewModel: StaffViewModel
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
        viewModel = StaffViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `uiState should reflect active staff`() = runTest {
        val s1 = StaffEntity(id = 1, name = "Staff 1", pinHash = "hash", role = "STAFF", isActive = true)
        db.staffDao().insertOrUpdate(s1)
        
        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.activeStaff.size)
        assertEquals("Staff 1", state.activeStaff[0].name)
        
        collectJob.cancel()
    }

    @Test
    fun `saveStaff should add new staff member`() = runTest {
        viewModel.saveStaff("New Guy", "1234", "STAFF")
        advanceUntilIdle()
        
        val active = db.staffDao().getAllActiveStaff().first()
        assertEquals(1, active.size)
        assertEquals("New Guy", active[0].name)
    }

    @Test
    fun `saveStaff should update existing staff member`() = runTest {
        val s1 = StaffEntity(id = 1, name = "Staff 1", pinHash = "hash", role = "STAFF", isActive = true)
        db.staffDao().insertOrUpdate(s1)
        
        viewModel.saveStaff("Updated Name", "1234", "MANAGER", existing = s1)
        advanceUntilIdle()
        
        val updated = db.staffDao().getStaffById(1)
        assertEquals("Updated Name", updated?.name)
        assertEquals("MANAGER", updated?.role)
    }

    @Test
    fun `deactivateStaff should make staff inactive`() = runTest {
        val s1 = StaffEntity(id = 1, name = "Staff 1", pinHash = "hash", role = "STAFF", isActive = true)
        db.staffDao().insertOrUpdate(s1)
        
        viewModel.deactivateStaff(s1)
        advanceUntilIdle()
        
        val active = db.staffDao().getAllActiveStaff().first()
        assertEquals(0, active.size)
    }
}
