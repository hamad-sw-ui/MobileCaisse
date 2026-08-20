package com.reconsiliation.caisse.ui.viewmodel

import com.reconsiliation.caisse.data.local.entity.BoutiqueEntity
import com.reconsiliation.caisse.data.local.entity.VenteEntity
import com.reconsiliation.caisse.data.repository.*
import com.reconsiliation.caisse.ui.state.SessionUiState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.*

@OptIn(ExperimentalCoroutinesApi::class)
class SessionViewModelTest {

    private lateinit var viewModel: SessionViewModel
    private lateinit var db: FakeAppDatabase
    private lateinit var salesRepository: SalesRepository
    private lateinit var mainRepository: MainRepository
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        db = FakeAppDatabase()
        val context = FakeContext()
        val inventoryRepository = InventoryRepository(
            db, db.stockDao(), db.priceHistoryDao(), db.stockMovementDao(),
            db.expenseDao(), db.actionLogDao(), db.subscriptionDao(), db.auditDao(),
            db.supplyDao(), db.supplierDao(), db.categoryDao(), db.recipeDao(),
            db.venteDao(), db.boutiqueDao()
        )
        mainRepository = MainRepository(db, context, inventoryRepository)
        salesRepository = SalesRepository(
            db = db,
            mainRepository = mainRepository,
            venteDao = db.venteDao(),
            stockDao = db.stockDao(),
            customerDao = db.customerDao(),
            sessionDao = db.sessionDao(),
            boutiqueDao = db.boutiqueDao(),
            recipeDao = db.recipeDao(),
            movementDao = db.stockMovementDao(),
            smsErrorDao = db.smsErrorDao(),
            expenseDao = db.expenseDao()
        )
        
        viewModel = SessionViewModel(
            salesRepository = salesRepository,
            mainRepository = mainRepository
        )

        // Init boutique for profit calculation
        runBlocking {
            db.boutiqueDao().insertOrUpdate(BoutiqueEntity(
                id = 1,
                name = "Test", 
                ownerName = "Owner",
                phoneNumber = "123",
                momoNumber = "456",
                operator = "MTN", 
                currency = "FCFA", 
                hideProfitsFromStaff = true
            ))
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `openSession should create an active session with staffId`() = runBlocking {
        viewModel.openSession("Alice", 5000.0, 123L)
        testDispatcher.scheduler.advanceUntilIdle()
        
        val active = salesRepository.activeSession.firstOrNull { it != null }
        assertNotNull(active)
        assertEquals("Alice", active?.sellerName)
        assertEquals(123L, active?.staffId)
        assertTrue(active?.isActive == true)
    }

    @Test
    fun `closeSession should deactivate session and log action`() = runBlocking {
        viewModel.openSession("Alice", 0.0, null)
        testDispatcher.scheduler.advanceUntilIdle()
        
        viewModel.closeSession(1000.0, "MANAGER")
        testDispatcher.scheduler.advanceUntilIdle()
        
        val active = salesRepository.activeSession.firstOrNull { it == null }
        assertTrue(active == null)
        
        val logs = db.actionLogDao().getRecentLogs().firstOrNull { it.isNotEmpty() }
        assertTrue(logs?.any { it.actionType == "SESSION_CLOSE" } == true)
    }

    @Test
    fun `todayProfit should respect staff restrictions`() = runBlocking {
        // Start collection for stateIn WhileSubscribed
        val collectJob = launch(UnconfinedTestDispatcher(testDispatcher.scheduler)) {
            viewModel.todayProfit.collect {}
        }

        val vente = VenteEntity(
            amount = 1000.0, 
            amountCash = 1000.0,
            description = "Test",
            date = Date(), 
            paymentMethod = "CASH", 
            status = "CONFIRMED"
        )
        db.venteDao().insertVente(vente)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // When role is STAFF
        viewModel.setUserRole("STAFF")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(0.0, viewModel.todayProfit.value, 0.0)
        
        // When role is MANAGER
        viewModel.setUserRole("MANAGER")
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue("Expected profit > 0, got ${viewModel.todayProfit.value}", viewModel.todayProfit.value > 0.0)
        
        collectJob.cancel()
    }

    @Test
    fun `todayRevenue should sum cash and momo confirmed sales`() = runBlocking {
        // Start collection
        val collectJob = launch(UnconfinedTestDispatcher(testDispatcher.scheduler)) {
            viewModel.todayRevenue.collect {}
        }

        db.venteDao().insertVente(VenteEntity(amount = 1000.0, amountCash = 1000.0, description = "V1", date = Date(), paymentMethod = "CASH", status = "CONFIRMED"))
        db.venteDao().insertVente(VenteEntity(amount = 2000.0, amountMomo = 2000.0, description = "V2", date = Date(), paymentMethod = "MOMO", status = "CONFIRMED"))
        db.venteDao().insertVente(VenteEntity(amount = 5000.0, amountMomo = 5000.0, description = "V3", date = Date(), paymentMethod = "MOMO", status = "PENDING"))
        
        testDispatcher.scheduler.advanceUntilIdle()
        
        assertEquals(3000.0, viewModel.todayRevenue.value, 0.0)
        
        collectJob.cancel()
    }
}
