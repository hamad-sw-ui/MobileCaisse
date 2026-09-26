package com.reconsiliation.caisse.ui.viewmodel

import com.reconsiliation.caisse.data.local.entity.BoutiqueEntity
import com.reconsiliation.caisse.data.local.entity.ExpenseEntity
import com.reconsiliation.caisse.data.local.entity.StaffEntity
import com.reconsiliation.caisse.data.local.entity.VenteEntity
import com.reconsiliation.caisse.data.repository.*
import com.reconsiliation.caisse.ui.state.SessionUiState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    @Test
    fun `opening balance rejects blank invalid and negative values`() {
        assertNull(SessionAmounts.openingBalance(""))
        assertNull(SessionAmounts.openingBalance("   "))
        assertNull(SessionAmounts.openingBalance("abc"))
        assertNull(SessionAmounts.openingBalance("-5"))
        assertNull(SessionAmounts.sellerName(""))
        assertNull(SessionAmounts.sellerName("   "))
        assertNull(SessionAmounts.countedCash(""))
        assertNull(SessionAmounts.countedCash("abc"))
    }

    @Test
    fun `opening balance accepts zero and comma decimal`() {
        assertEquals(0.0, SessionAmounts.openingBalance("0")!!, 0.0)
        assertEquals(1500.5, SessionAmounts.openingBalance("1500,5")!!, 0.0)
        assertEquals(1500.5, SessionAmounts.countedCash("1500,5")!!, 0.0)
        assertEquals("Awa", SessionAmounts.sellerName("  Awa  "))
    }

    @Test
    fun `cash gap is counted minus expected`() {
        assertEquals(-200.0, SessionAmounts.cashGap(800.0, 1000.0)!!, 0.0)
        assertEquals(0.0, SessionAmounts.cashGap(1000.0, 1000.0)!!, 0.0)
        assertEquals(200.0, SessionAmounts.cashGap(1200.0, 1000.0)!!, 0.0)
        assertNull(SessionAmounts.cashGap(null, 1000.0))
    }

    @Test
    fun `seller suggestion comes from connected staff and stays empty when missing`() = runBlocking {
        db.staffDao().insertOrUpdate(StaffEntity(id = 7, name = "Awa", pinHash = "hash"))
        viewModel.loadSellerSuggestion(7)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("Awa", viewModel.suggestedSeller.value)

        viewModel.loadSellerSuggestion(99)
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.suggestedSeller.value)
    }

    @Test
    fun `second open keeps the first session and reports already open`() = runBlocking {
        viewModel.openSession("Alice", 5000.0, 1L)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(SessionUiState.Success, viewModel.uiState.value)

        viewModel.openSession("Bob", 1000.0, 2L)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(SessionUiState.AlreadyOpen, viewModel.uiState.value)

        val sessions = db.sessionDao().getAllSessions().first()
        assertEquals(1, sessions.size)
        assertEquals("Alice", sessions.single().sellerName)
        assertEquals(5000.0, sessions.single().openingBalance, 0.0)
        assertTrue(sessions.single().isActive)
    }

    @Test
    fun `preview uses session cash and expenses not the daily total`() = runBlocking {
        viewModel.openSession("Alice", 5000.0, null)
        testDispatcher.scheduler.advanceUntilIdle()
        db.venteDao().insertVente(
            VenteEntity(
                amount = 1000.0,
                amountCash = 1000.0,
                description = "Sale",
                date = Date(),
                paymentMethod = "CASH",
                status = "CONFIRMED"
            )
        )
        db.expenseDao().insert(ExpenseEntity(label = "Exp", amount = 200.0, date = Date(), category = "Pertes"))

        viewModel.loadSessionPreview()
        testDispatcher.scheduler.advanceUntilIdle()

        val preview = viewModel.sessionPreview.value
        assertNotNull(preview)
        assertEquals(5000.0, preview!!.openingBalance, 0.0)
        assertEquals(1000.0, preview.cashSales, 0.0)
        assertEquals(200.0, preview.expenses, 0.0)
        assertEquals(5800.0, preview.expectedCash, 0.0)
        assertEquals("Alice", preview.sellerName)
    }

    @Test
    fun `close stores counted cash and expected balance for shortage exact and surplus`() = runBlocking {
        closeAndAssert(counted = 800.0, expectedGap = -200.0)
        closeAndAssert(counted = 1000.0, expectedGap = 0.0)
        closeAndAssert(counted = 1200.0, expectedGap = 200.0)
    }

    private suspend fun closeAndAssert(counted: Double, expectedGap: Double) {
        viewModel.openSession("Alice", 1000.0, null)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.closeSession(counted, "MANAGER")
        testDispatcher.scheduler.advanceUntilIdle()

        val session = db.sessionDao().getAllSessions().first().last { it.sellerName == "Alice" && !it.isActive }
        assertEquals(counted, session.closingBalance!!, 0.0)
        assertEquals(1000.0, session.expectedBalance!!, 0.0)
        assertEquals(expectedGap, SessionAmounts.cashGap(session.closingBalance, session.expectedBalance)!!, 0.0)
    }

    @Test
    fun `shortage recorded after close keeps the real gap`() = runBlocking {
        viewModel.openSession("Alice", 1000.0, null)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.closeSession(800.0, "MANAGER") {
            mainRepository.insertExpense(
                ExpenseEntity(label = "Manque", amount = 200.0, date = Date(), category = "Pertes")
            )
        }
        testDispatcher.scheduler.advanceUntilIdle()

        val session = db.sessionDao().getAllSessions().first().single()
        assertEquals(800.0, session.closingBalance!!, 0.0)
        assertEquals(1000.0, session.expectedBalance!!, 0.0)
        assertEquals(0.0, session.totalExpenses, 0.0)
        assertEquals(-200.0, SessionAmounts.cashGap(session.closingBalance, session.expectedBalance)!!, 0.0)

        val expenses = db.expenseDao().getAllExpenses().first()
        assertEquals(1, expenses.size)
        assertEquals(200.0, expenses.single().amount, 0.0)
        val reread = db.sessionDao().getAllSessions().first().single()
        assertEquals(1000.0, reread.expectedBalance!!, 0.0)
    }

    @Test
    fun `a second open call while processing does not start another session`() = runBlocking {
        viewModel.openSession("Alice", 5000.0, 1L)
        viewModel.openSession("Bob", 1000.0, 2L)
        testDispatcher.scheduler.advanceUntilIdle()

        val sessions = db.sessionDao().getAllSessions().first()
        assertEquals(1, sessions.size)
        assertEquals("Alice", sessions.single().sellerName)
        assertEquals(SessionUiState.Success, viewModel.uiState.value)
    }
}
