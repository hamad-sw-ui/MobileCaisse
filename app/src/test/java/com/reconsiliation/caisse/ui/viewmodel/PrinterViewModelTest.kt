package com.reconsiliation.caisse.ui.viewmodel

import com.reconsiliation.caisse.data.local.entity.BoutiqueEntity
import com.reconsiliation.caisse.data.local.entity.VenteEntity
import com.reconsiliation.caisse.data.repository.*
import com.reconsiliation.caisse.platform.PrinterManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.*

@OptIn(ExperimentalCoroutinesApi::class)
class PrinterViewModelTest {

    private lateinit var viewModel: PrinterViewModel
    private lateinit var mainRepository: MainRepository
    private lateinit var salesRepository: SalesRepository
    private lateinit var printerManager: PrinterManager
    private lateinit var db: FakeAppDatabase
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
            db, mainRepository, db.venteDao(), db.stockDao(), db.customerDao(),
            db.sessionDao(), db.boutiqueDao(), db.recipeDao(), db.stockMovementDao(),
            db.smsErrorDao(), db.expenseDao()
        )
        printerManager = PrinterManager(context)
        viewModel = PrinterViewModel(context, mainRepository, salesRepository, printerManager)

        runBlocking {
            mainRepository.completeSetup(BoutiqueEntity(
                id = 1, 
                name = "Test", 
                ownerName = "Owner",
                phoneNumber = "123",
                operator = "MTN",
                momoNumber = "456",
                isSetupComplete = true, 
                pinHash = "", 
                pinSalt = "", 
                managerPinHash = "", 
                managerPinSalt = ""
            ))
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `testPrint should call printerManager`() = runTest {
        viewModel.testPrint("address").join()
        assertTrue(printerManager.testConnectionCalled)
    }

    @Test
    fun `printTicket should call printerManager printReceipt`() = runTest {
        val vente = VenteEntity(
            id = 1, 
            amount = 100.0, 
            description = "Test",
            date = Date(), 
            paymentMethod = "CASH",
            status = "CONFIRMED"
        )
        db.venteDao().insertVente(vente)
        
        viewModel.printTicket(1, "address").join()
        assertTrue(printerManager.printReceiptCalled)
    }
}
