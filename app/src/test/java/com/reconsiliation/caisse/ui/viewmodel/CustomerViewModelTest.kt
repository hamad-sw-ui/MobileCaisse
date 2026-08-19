package com.reconsiliation.caisse.ui.viewmodel

import com.reconsiliation.caisse.data.local.entity.CustomerEntity
import com.reconsiliation.caisse.data.repository.*
import com.reconsiliation.caisse.ui.state.CustomerUiState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Mock minimal pour éviter les dépendances au framework Android dans les tests JVM.
 */
class MockReportManager : com.reconsiliation.caisse.platform.ReportManager(FakeContext()) {
    override fun generateCustomerStatementPdf(customer: CustomerEntity, ventes: List<com.reconsiliation.caisse.data.local.dao.VenteWithItems>): File? = null
}

class MockShareManager : com.reconsiliation.caisse.platform.ShareManager(FakeContext()) {
    var lastSmsPhone: String? = null
    override fun sendSms(phone: String, message: String) {
        lastSmsPhone = phone
    }
    override fun shareFile(file: File, mimeType: String, title: String) {}
    override fun shareText(text: String, title: String, pkg: String?) {}
}

class CustomerViewModelTest {

    private lateinit var viewModel: CustomerViewModel
    private lateinit var db: FakeAppDatabase
    private lateinit var salesRepository: SalesRepository
    private lateinit var shareManager: MockShareManager

    @Before
    fun setup() {
        db = FakeAppDatabase()
        val context = FakeContext()
        val inventoryRepository = InventoryRepository(
            db, db.stockDao(), db.priceHistoryDao(), db.stockMovementDao(),
            db.expenseDao(), db.actionLogDao(), db.subscriptionDao(), db.auditDao(),
            db.supplyDao(), db.supplierDao(), db.categoryDao(), db.recipeDao(),
            db.venteDao(), db.boutiqueDao()
        )
        val mainRepository = MainRepository(db, context, inventoryRepository)
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
        
        shareManager = MockShareManager()
        
        // Setup active subscription for repo calls
        runBlocking {
            val calendar = java.util.Calendar.getInstance()
            calendar.add(java.util.Calendar.YEAR, 1)
            db.subscriptionDao().insertOrUpdate(com.reconsiliation.caisse.data.local.entity.SubscriptionEntity(
                startDate = java.util.Date(),
                endDate = calendar.time,
                isActive = true,
                deviceId = "TEST"
            ))
        }

        // On n'instancie pas le ViewModel si on veut tester uniquement la logique Repository scellée,
        // mais pour J5.8.2 on veut valider le ViewModel. On utilise des Mocks JVM-safe.
        viewModel = CustomerViewModel(
            salesRepository = salesRepository,
            reportManager = MockReportManager(),
            shareManager = shareManager
        )
    }

    @Test
    fun `debtors should only contain customers with positive debt`() = runBlocking {
        val c1 = CustomerEntity(id = 1, name = "C1", phone = "1", totalDebt = 100.0)
        val c2 = CustomerEntity(id = 2, name = "C2", phone = "2", totalDebt = 0.0)
        
        db.customerDao().insertOrUpdate(c1)
        db.customerDao().insertOrUpdate(c2)

        val list = salesRepository.debtors.first()
        assertEquals(1, list.size)
        assertEquals("C1", list[0].name)
    }

    @Test
    fun `addRepayment should update debt`() = runBlocking {
        val c1 = CustomerEntity(id = 1, name = "C1", phone = "1", totalDebt = 1000.0)
        db.customerDao().insertOrUpdate(c1)

        salesRepository.addRepayment(1, 400.0, "CASH", "MANAGER")
        
        val updated = db.customerDao().getCustomerById(1)
        assertEquals(600.0, updated?.totalDebt ?: 0.0, 0.0)
    }

    @Test
    fun `updateVipStatus should update customer correctly`() = runBlocking {
        val c1 = CustomerEntity(id = 1, name = "C1", phone = "1", isVip = false)
        db.customerDao().insertOrUpdate(c1)

        salesRepository.updateCustomerVipStatus(1, true)
        
        val updated = db.customerDao().getCustomerById(1)
        assertTrue(updated?.isVip ?: false)
    }

    @Test
    fun `findOrCreateCustomer should reuse existing customer`() = runBlocking {
        val c1 = CustomerEntity(id = 1, name = "C1", phone = "12345")
        db.customerDao().insertOrUpdate(c1)

        val id = salesRepository.findOrCreateCustomer("New Name", "12345")
        assertEquals(1L, id)
    }
}
