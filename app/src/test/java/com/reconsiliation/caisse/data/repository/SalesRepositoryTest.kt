package com.reconsiliation.caisse.data.repository

import com.reconsiliation.caisse.data.local.entity.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.Collections
import java.util.Date
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class SalesRepositoryTest {

    private lateinit var db: FakeAppDatabase
    private lateinit var mainRepository: MainRepository
    private lateinit var salesRepository: SalesRepository

    @Before
    fun setup() {
        db = FakeAppDatabase()
        val context = FakeContext()
        val inventoryRepository = InventoryRepository(
            db = db,
            stockDao = db.stockDao(),
            priceHistoryDao = db.priceHistoryDao(),
            movementDao = db.stockMovementDao(),
            expenseDao = db.expenseDao(),
            actionLogDao = db.actionLogDao(),
            subscriptionDao = db.subscriptionDao(),
            auditDao = db.auditDao(),
            supplyDao = db.supplyDao(),
            supplierDao = db.supplierDao(),
            categoryDao = db.categoryDao(),
            recipeDao = db.recipeDao(),
            venteDao = db.venteDao(),
            boutiqueDao = db.boutiqueDao()
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
    }

    private suspend fun setupBasicData() {
        db.boutiqueDao().insertOrUpdate(BoutiqueEntity(id = 1, name = "Test", ownerName = "Owner", phoneNumber = "677112233", operator = "MTN", momoNumber = "677112233"))
        db.subscriptionDao().insertOrUpdate(SubscriptionEntity(id = 1, startDate = Date(), endDate = Date(System.currentTimeMillis() + 86400000), isActive = true, deviceId = "TEST"))
    }

    @Test
    fun `inserting a sale should work`() = runBlocking {
        setupBasicData()
        db.sessionDao().openSession(SessionEntity(sellerName = "Test", startTime = Date(), openingBalance = 0.0))
        val product = StockEntity(id = 1, productName = "Pain", quantity = 10.0, unitPrice = 100.0, purchasePrice = 80.0, alertThreshold = 2.0, category = "Divers")
        db.stockDao().insertOrUpdate(product)
        val vente = VenteEntity(amount = 200.0, description = "Test", date = Date(), paymentMethod = "CASH", status = "CONFIRMED")
        val items = listOf(VenteItemEntity(id = 0, venteId = 0, productId = 1, productName = "Pain", quantity = 2.0, unitPrice = 100.0, purchasePrice = 80.0))
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
        setupBasicData()
        
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

    @Test
    fun `concurrent openSession creates a single active session`() {
        val parallelism = 8
        val results = Collections.synchronizedList(mutableListOf<Boolean>())
        val start = CountDownLatch(1)
        val done = CountDownLatch(parallelism)
        repeat(parallelism) { index ->
            Thread {
                start.await()
                runBlocking {
                    results.add(salesRepository.openSession("Seller$index", 1000.0 + index, index.toLong()))
                }
                done.countDown()
            }.start()
        }
        start.countDown()
        assertTrue(done.await(10, TimeUnit.SECONDS))

        assertEquals(1, results.count { it })
        assertEquals(parallelism - 1, results.count { !it })
        val sessions = runBlocking { db.sessionDao().getAllSessions().first() }
        assertEquals(1, sessions.size)
        assertTrue(sessions.single().isActive)
    }

    @Test
    fun `future sale is blocked with surviving anomaly log and no persisted sale`() = runBlocking {
        setupBasicData()
        val vente = VenteEntity(
            amount = 1000.0,
            description = "Future",
            date = Date(System.currentTimeMillis() + 3_600_000),
            paymentMethod = "CASH",
            status = "CONFIRMED"
        )
        try {
            salesRepository.insertVenteWithItems(vente, emptyList(), "ADMIN")
            fail("Future sale must throw")
        } catch (error: Exception) {
            assertTrue(error.message!!.contains("futur"))
        }
        assertTrue(db.venteDao().getAllVentes().first().isEmpty())
        val anomalyLog = db.actionLogDao().getRecentLogs().first()
            .single { it.actionType == "ANOMALY_DETECTED" }
        assertEquals("FUTURE", anomalyLog.details)
        assertEquals("CRITICAL", anomalyLog.severity)
        assertEquals("ADMIN", anomalyLog.userRole)
    }

    @Test
    fun `duplicate sale is blocked with surviving anomaly log and no second sale`() = runBlocking {
        setupBasicData()
        val baseTime = System.currentTimeMillis() - 60_000
        salesRepository.insertVenteWithItems(
            VenteEntity(
                amount = 5000.0,
                description = "First",
                date = Date(baseTime),
                paymentMethod = "CASH",
                status = "CONFIRMED"
            ),
            emptyList()
        )
        try {
            salesRepository.insertVenteWithItems(
                VenteEntity(
                    amount = 5000.0,
                    description = "Duplicate",
                    date = Date(baseTime + 5_000),
                    paymentMethod = "CASH",
                    status = "CONFIRMED"
                ),
                emptyList(),
                "STAFF"
            )
            fail("Duplicate sale must throw")
        } catch (error: Exception) {
            assertTrue(error.message!!.contains("doublon", ignoreCase = true))
        }
        assertEquals(1, db.venteDao().getAllVentes().first().size)
        val anomalyLogs = db.actionLogDao().getRecentLogs().first()
            .filter { it.actionType == "ANOMALY_DETECTED" }
        assertEquals(1, anomalyLogs.size)
        assertEquals("DUPLICATE", anomalyLogs.single().details)
        assertEquals("CRITICAL", anomalyLogs.single().severity)
        assertEquals("STAFF", anomalyLogs.single().userRole)
    }

    @Test
    fun `high amount sale is persisted with warning log and is not blocked`() = runBlocking {
        setupBasicData()
        val venteId = salesRepository.insertVenteWithItems(
            VenteEntity(
                amount = 600000.0,
                description = "High",
                date = Date(),
                paymentMethod = "CASH",
                status = "CONFIRMED"
            ),
            emptyList(),
            "ADMIN"
        )
        assertTrue(venteId > 0)
        assertEquals(1, db.venteDao().getAllVentes().first().size)
        val anomalyLog = db.actionLogDao().getRecentLogs().first()
            .single { it.actionType == "ANOMALY_DETECTED" }
        assertEquals("HIGH_AMOUNT", anomalyLog.details)
        assertEquals("WARNING", anomalyLog.severity)
        assertEquals("ADMIN", anomalyLog.userRole)
    }
}
