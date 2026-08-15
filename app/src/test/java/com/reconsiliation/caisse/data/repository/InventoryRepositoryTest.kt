package com.reconsiliation.caisse.data.repository

import com.reconsiliation.caisse.data.local.entity.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.*

class InventoryRepositoryTest {

    private lateinit var db: FakeAppDatabase
    private lateinit var repository: InventoryRepository

    @Before
    fun setup() {
        db = FakeAppDatabase()
        repository = InventoryRepository(
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
    }

    private suspend fun setupBasicData() {
        db.boutiqueDao().insertOrUpdate(BoutiqueEntity(id = 1, name = "Test", ownerName = "Owner", phoneNumber = "677112233", operator = "MTN", momoNumber = "677112233"))
        db.subscriptionDao().insertOrUpdate(SubscriptionEntity(id = 1, startDate = Date(), endDate = Date(System.currentTimeMillis() + 86400000), isActive = true, deviceId = "TEST"))
    }

    @Test
    fun `adjusting stock should update quantity and create expense if loss`() = runBlocking {
        val product = StockEntity(id = 1, productName = "Pain", quantity = 10.0, unitPrice = 100.0, purchasePrice = 80.0, alertThreshold = 2.0, category = "Divers")
        db.stockDao().insertOrUpdate(product)

        repository.adjustStock(product, 5.0, "Perte")

        val updatedStock = db.stockDao().getStockById(1)
        assertEquals(5.0, updatedStock!!.quantity, 0.0)
        
        val expenses = db.expenseDao().getAllExpenses().first()
        assertTrue(expenses.any { it.category == "Pertes" && it.amount == 400.0 })
    }

    @Test
    fun `addSupply should update PMP and quantity`() = runBlocking {
        setupBasicData()
        val product = StockEntity(id = 1, productName = "Pain", quantity = 10.0, unitPrice = 100.0, purchasePrice = 80.0, alertThreshold = 2.0, category = "Divers")
        db.stockDao().insertOrUpdate(product)

        val supply = SupplyEntity(productId = 1, productName = "Pain", quantity = 10.0, purchasePrice = 100.0, date = Date(), isPaid = true)
        repository.addSupply(supply)

        val updatedStock = db.stockDao().getStockById(1)
        assertEquals(20.0, updatedStock!!.quantity, 0.0)
        assertEquals(90.0, updatedStock.purchasePrice, 0.0)
    }

    @Test
    fun `addSupply with debt should update supplier debt`() = runBlocking {
        setupBasicData()
        val supplier = SupplierEntity(id = 1, name = "Boulangerie", phone = "123", totalDebt = 0.0)
        db.supplierDao().insert(supplier)
        
        val product = StockEntity(id = 1, productName = "Pain", quantity = 0.0, unitPrice = 100.0, purchasePrice = 80.0, alertThreshold = 2.0, category = "Divers")
        db.stockDao().insertOrUpdate(product)

        val supply = SupplyEntity(productId = 1, productName = "Pain", quantity = 50.0, purchasePrice = 80.0, date = Date(), isPaid = false, supplierId = 1, supplierName = "Boulangerie")
        repository.addSupply(supply)

        val updatedSupplier = db.supplierDao().getAllSuppliers().first().find { it.id == 1L }
        assertEquals(4000.0, updatedSupplier!!.totalDebt, 0.0)
    }

    @Test
    fun `performAudit should update quantity and record expense for discrepancy`() = runBlocking {
        setupBasicData()
        val product = StockEntity(id = 1, productName = "Pain", quantity = 10.0, unitPrice = 100.0, purchasePrice = 80.0, alertThreshold = 2.0, category = "Divers")
        db.stockDao().insertOrUpdate(product)

        val auditItems = listOf(
            AuditItemEntity(auditId = 0, productId = 1, productName = "Pain", systemQuantity = 10.0, physicalQuantity = 8.0, unitPrice = 100.0, purchasePrice = 80.0)
        )
        
        repository.performAudit("Inventaire hebdo", auditItems)

        val updatedStock = db.stockDao().getStockById(1)
        assertEquals(8.0, updatedStock!!.quantity, 0.0)
        
        val expenses = db.expenseDao().getAllExpenses().first()
        assertTrue(expenses.any { it.category == "Pertes" && it.amount == 160.0 })
    }

    @Test
    fun `deleteStock should fail if product is used in sales`() = runBlocking {
        val product = StockEntity(id = 1, productName = "Pain", quantity = 10.0, unitPrice = 100.0, purchasePrice = 80.0, alertThreshold = 2.0, category = "Divers")
        db.stockDao().insertOrUpdate(product)

        val item = VenteItemEntity(id = 0, venteId = 1, productId = 1, productName = "Pain", quantity = 1.0, unitPrice = 100.0, purchasePrice = 80.0)
        db.venteDao().insertVenteItems(listOf(item))

        try {
            repository.deleteStock(product)
            fail("Should have thrown exception")
        } catch (e: Exception) {
            assertEquals("Historique présent", e.message)
        }
        
        assertNotNull(db.stockDao().getStockById(1))
    }
}
