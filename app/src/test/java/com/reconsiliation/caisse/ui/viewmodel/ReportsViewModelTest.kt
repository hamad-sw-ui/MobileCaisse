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
    private lateinit var reportManager: FakeReportManager
    private lateinit var shareManager: FakeShareManager
    private val testDispatcher = StandardTestDispatcher()

    class FakeShareManager(context: android.content.Context) : com.reconsiliation.caisse.platform.ShareManager(context) {
        var lastSharedText: String? = null
        var lastSharedFile: java.io.File? = null
        override fun shareText(text: String, title: String, pkg: String?) { lastSharedText = text }
        override fun shareFile(file: java.io.File, mimeType: String, title: String) { lastSharedFile = file }
    }

    class FakeReportManager(context: android.content.Context) : com.reconsiliation.caisse.platform.ReportManager(context) {
        var salesPassedToGenerateCsv: List<com.reconsiliation.caisse.data.local.dao.VenteWithItems>? = null
        override fun generateSalesCsv(ventes: List<com.reconsiliation.caisse.data.local.dao.VenteWithItems>): java.io.File? {
            salesPassedToGenerateCsv = ventes
            return java.io.File("fake.csv")
        }
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
        reportManager = FakeReportManager(context)
        viewModel = ReportsViewModel(
            salesRepository = salesRepository,
            mainRepository = mainRepository,
            reportManager = reportManager,
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
    fun `loadCurrentStats YEAR should trigger monthly sales data`() = runTest {
        backgroundScope.launch { viewModel.dailySales.collect {} }
        viewModel.loadCurrentStats("YEAR")
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
    fun `exportSalesData should delegate to reportManager and shareManager`() = runTest {
        val now = Date()
        val venteId = db.venteDao().insertVente(VenteEntity(amount = 100.0, description = "T", date = now, paymentMethod = "CASH", status = "CONFIRMED"))
        db.venteDao().insertVenteItems(listOf(VenteItemEntity(venteId = venteId, productId = 1, productName = "P", quantity = 1.0, unitPrice = 100.0, purchasePrice = 80.0)))
        advanceUntilIdle()

        viewModel.exportSalesData()
        advanceUntilIdle()
        
        assertTrue(viewModel.uiState.value is ReportsUiState.Success)
        assertEquals(1, reportManager.salesPassedToGenerateCsv?.size)
        assertEquals("fake.csv", shareManager.lastSharedFile?.name)
    }

    @Test
    fun `exportSalesData with filtered list should only export those`() = runTest {
        val list = listOf(com.reconsiliation.caisse.data.local.dao.VenteWithItems(
            VenteEntity(id = 99, amount = 100.0, description = "F", date = Date(), paymentMethod = "CASH", status = "CONFIRMED"),
            emptyList()
        ))
        
        viewModel.exportSalesData(list)
        advanceUntilIdle()
        
        assertEquals(1, reportManager.salesPassedToGenerateCsv?.size)
        assertEquals(99L, reportManager.salesPassedToGenerateCsv?.get(0)?.vente?.id)
    }

    @Test
    fun `shareDailySummary should execute without error`() = runTest {
        val now = Date()
        db.venteDao().insertVente(VenteEntity(amount = 1000.0, description = "V1", date = now, paymentMethod = "CASH", status = "CONFIRMED"))
        
        viewModel.shareDailySummary()
        advanceUntilIdle()
        assertTrue(shareManager.lastSharedText?.contains("Boutique") == true)
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
}
