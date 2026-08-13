package com.reconsiliation.caisse.data.repository

import com.reconsiliation.caisse.data.local.entity.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.Date

class SalesRepositoryTest {

    private lateinit var db: FakeAppDatabase
    private lateinit var mainRepository: MainRepository
    private lateinit var salesRepository: SalesRepository

    @Before
    fun setup() {
        db = FakeAppDatabase()
        val context = FakeContext()
        val inventoryRepository = InventoryRepository(
            db,
            db.stockDao(),
            db.priceHistoryDao(),
            db.stockMovementDao(),
            db.expenseDao(),
            db.actionLogDao(),
            db.subscriptionDao()
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
            expenseDao = db.expenseDao(),
            context = context
        )
    }

    private suspend fun setupBasicData() {
        db.boutiqueDao().insertOrUpdate(BoutiqueEntity(id = 1, name = "Test", ownerName = "Owner", phoneNumber = "677112233", operator = "MTN", momoNumber = "677112233"))
        db.subscriptionDao().insertOrUpdate(SubscriptionEntity(id = 1, startDate = Date(), endDate = Date(System.currentTimeMillis() + 86400000), isActive = true, deviceId = "TEST"))
        db.sessionDao().openSession(SessionEntity(sellerName = "Test", startTime = Date(), openingBalance = 0.0))
    }

    @Test
    fun `inserting a sale should work`() = runBlocking {
        setupBasicData()
        val product = StockEntity(id = 1, productName = "Pain", quantity = 10.0, unitPrice = 100.0, purchasePrice = 80.0, alertThreshold = 2.0)
        db.stockDao().insertOrUpdate(product)
        val vente = VenteEntity(amount = 200.0, description = "Test", date = Date(), paymentMethod = "CASH", status = "CONFIRMED")
        val items = listOf(VenteItemEntity(venteId = 0, productId = 1, productName = "Pain", quantity = 2.0, unitPrice = 100.0, purchasePrice = 80.0))
        val venteId = salesRepository.insertVenteWithItems(vente, items)
        assertTrue(venteId > 0)
        assertEquals(8.0, db.stockDao().getStockById(1)!!.quantity, 0.0)
    }

    @Test
    fun `inserting sale with new customer phone should create customer`() = runBlocking {
        setupBasicData()
        val vente = VenteEntity(amount = 500.0, description = "New customer", date = Date(), paymentMethod = "CASH", status = "CONFIRMED", customerPhone = "677889900")
        salesRepository.insertVenteWithItems(vente, emptyList())
        val customer = db.customerDao().getCustomerByPhone("677889900")
        assertNotNull(customer)
        assertEquals("Client 677889900", customer!!.name)
    }

    @Test
    fun `sale with credit should increase customer debt`() = runBlocking {
        setupBasicData()
        val customerId = db.customerDao().insertOrUpdate(CustomerEntity(name = "Debtor", phone = "699001122"))
        val vente = VenteEntity(amount = 1000.0, description = "Credit", date = Date(), paymentMethod = "CREDIT", status = "CONFIRMED", customerId = customerId, customerPhone = "699001122")
        salesRepository.insertVenteWithItems(vente, emptyList())
        val updatedCustomer = db.customerDao().getCustomerById(customerId)
        assertEquals(1000.0, updatedCustomer!!.totalDebt, 0.0)
    }

    @Test
    fun `adding repayment should decrease debt`() = runBlocking {
        setupBasicData()
        val customerId = db.customerDao().insertOrUpdate(CustomerEntity(name = "Debtor", phone = "699001122", totalDebt = 1000.0))
        salesRepository.addRepayment(customerId, 400.0, "CASH")
        val updatedCustomer = db.customerDao().getCustomerById(customerId)
        assertEquals(600.0, updatedCustomer!!.totalDebt, 0.0)
    }

    @Test
    fun `processMoMoRepayment should identify debtor and reduce debt`() = runBlocking {
        setupBasicData()
        db.customerDao().insertOrUpdate(CustomerEntity(name = "Debtor", phone = "699001122", totalDebt = 5000.0))
        salesRepository.processMoMoRepayment("699001122", 2000.0, "TRANS123")
        val debtor = db.customerDao().getCustomerByPhone("699001122")
        assertEquals(3000.0, debtor!!.totalDebt, 0.0)
    }

    @Test
    fun `closing session should calculate expected balance correctly`() = runBlocking {
        // Setup boutique and sub
        db.boutiqueDao().insertOrUpdate(BoutiqueEntity(id = 1, name = "Test", ownerName = "Owner", phoneNumber = "677112233", operator = "MTN", momoNumber = "677112233"))
        db.subscriptionDao().insertOrUpdate(SubscriptionEntity(id = 1, startDate = Date(), endDate = Date(System.currentTimeMillis() + 86400000), isActive = true, deviceId = "TEST"))
        
        // Open Alice session
        db.sessionDao().openSession(SessionEntity(sellerName = "Alice", startTime = Date(), openingBalance = 5000.0))
        
        val vente = VenteEntity(amount = 2000.0, amountCash = 2000.0, description = "Sale", date = Date(), paymentMethod = "CASH", status = "CONFIRMED")
        salesRepository.insertVenteWithItems(vente, emptyList())
        
        db.expenseDao().insert(ExpenseEntity(label = "Exp", amount = 500.0, date = Date(), category = "Divers"))
        
        salesRepository.closeSession(6500.0)

        val session = db.sessionDao().getAllSessions().first().first { it.sellerName == "Alice" }
        assertEquals(2000.0, session.totalCashSales, 0.0)
        assertEquals(500.0, session.totalExpenses, 0.0)
        assertEquals(6500.0, session.expectedBalance ?: 0.0, 0.0)
    }
}
