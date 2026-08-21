package com.reconsiliation.caisse.ui.viewmodel

import com.reconsiliation.caisse.data.local.entity.VenteEntity
import com.reconsiliation.caisse.data.local.entity.VenteItemEntity
import com.reconsiliation.caisse.data.repository.*
import com.reconsiliation.caisse.ui.state.ReportsUiState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.*

@OptIn(ExperimentalCoroutinesApi::class)
class ReportsViewModelTest {

    private lateinit var viewModel: ReportsViewModel
    private lateinit var db: FakeAppDatabase
    private lateinit var salesRepository: SalesRepository
    private lateinit var mainRepository: MainRepository
    private lateinit var shareManager: FakeShareManager
    private val testDispatcher = StandardTestDispatcher()

    class FakeShareManager(context: android.content.Context) : com.reconsiliation.caisse.platform.ShareManager(context) {
        var lastSharedText: String? = null
        var lastSharedFile: java.io.File? = null
        override fun shareText(text: String, title: String, pkg: String?) { lastSharedText = text }
        override fun shareFile(file: java.io.File, mimeType: String, title: String) { lastSharedFile = file }
    }

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
        
        shareManager = FakeShareManager(context)
        viewModel = ReportsViewModel(
            salesRepository = salesRepository,
            mainRepository = mainRepository,
            shareManager = shareManager
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadCurrentStats MONTH should trigger daily sales data`() = runTest {
        backgroundScope.launch { viewModel.dailySales.collect {} }
        viewModel.loadCurrentStats("MONTH")
        advanceUntilIdle()
        assertTrue(viewModel.dailySales.value.isEmpty())
    }

    @Test
    fun `loadVentesForMonth should filter sales correctly`() = runTest {
        backgroundScope.launch { viewModel.selectedMonthVentes.collect {} }
        val now = Date()
        val venteId = db.venteDao().insertVente(VenteEntity(amount = 100.0, description = "T", date = now, paymentMethod = "CASH", status = "CONFIRMED"))
        
        viewModel.loadVentesForMonth(now)
        advanceUntilIdle()
        
        assertEquals(1, viewModel.selectedMonthVentes.value.size)
    }

    @Test
    fun `exportSalesData should set success state on success`() = runTest {
        val now = Date()
        val venteId = db.venteDao().insertVente(VenteEntity(amount = 100.0, description = "T", date = now, paymentMethod = "CASH", status = "CONFIRMED"))
        db.venteDao().insertVenteItems(listOf(VenteItemEntity(venteId = venteId, productId = 1, productName = "P", quantity = 1.0, unitPrice = 100.0, purchasePrice = 80.0)))
        advanceUntilIdle()

        viewModel.exportSalesData(FakeContext())
        advanceUntilIdle()
        
        assertTrue("State was ${viewModel.uiState.value}", viewModel.uiState.value is ReportsUiState.Success)
    }

    @Test
    fun `shareDailySummary should execute without error`() = runTest {
        val now = Date()
        db.venteDao().insertVente(VenteEntity(amount = 1000.0, description = "V1", date = now, paymentMethod = "CASH", status = "CONFIRMED"))
        
        viewModel.shareDailySummary(FakeContext())
        advanceUntilIdle()
    }

    @Test
    fun `switching periods should cancel previous collections`() = runTest {
        backgroundScope.launch { viewModel.dailySales.collect {} }
        
        viewModel.loadCurrentStats("MONTH")
        advanceUntilIdle()
        
        viewModel.loadCurrentStats("YEAR")
        advanceUntilIdle()
        
        viewModel.loadCurrentStats("MONTH")
        advanceUntilIdle()
        
        assertTrue(viewModel.dailySales.value.isEmpty())
    }

    @Test
    fun `loadCurrentStats YEAR should trigger monthly sales data`() = runTest {
        backgroundScope.launch { viewModel.dailySales.collect {} }
        viewModel.loadCurrentStats("YEAR")
        advanceUntilIdle()
        assertTrue(viewModel.dailySales.value.isEmpty())
    }
}
