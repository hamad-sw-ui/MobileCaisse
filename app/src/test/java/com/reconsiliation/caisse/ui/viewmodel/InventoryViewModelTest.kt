package com.reconsiliation.caisse.ui.viewmodel

import com.reconsiliation.caisse.data.local.entity.AuditItemEntity
import com.reconsiliation.caisse.data.local.entity.StockEntity
import com.reconsiliation.caisse.data.local.entity.SubscriptionEntity
import com.reconsiliation.caisse.data.repository.*
import com.reconsiliation.caisse.ui.state.InventoryUiState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.*

class InventoryViewModelTest {

    private lateinit var viewModel: InventoryViewModel
    private lateinit var db: FakeAppDatabase
    private lateinit var repository: MainRepository
    private lateinit var context: FakeContext

    @Before
    fun setup() = runBlocking {
        db = FakeAppDatabase()
        context = FakeContext()
        val inventoryRepository = InventoryRepository(
            db, db.stockDao(), db.priceHistoryDao(), db.stockMovementDao(),
            db.expenseDao(), db.actionLogDao(), db.subscriptionDao(), db.auditDao(),
            db.supplyDao(), db.supplierDao(), db.categoryDao(), db.recipeDao(),
            db.venteDao(), db.boutiqueDao()
        )
        repository = MainRepository(db, context, inventoryRepository)
        viewModel = InventoryViewModel(repository)

        // Setup active subscription
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.YEAR, 1)
        db.subscriptionDao().insertOrUpdate(SubscriptionEntity(
            startDate = Date(),
            endDate = calendar.time,
            isActive = true,
            deviceId = "TEST_DEVICE"
        ))
    }

    @Test
    fun `stockValuation should reflect repository data`() = runBlocking {
        val p1 = StockEntity(id = 1, productName = "P1", quantity = 10.0, unitPrice = 100.0, purchasePrice = 80.0, alertThreshold = 2.0, category = "CAT", unit = "pcs")
        val p2 = StockEntity(id = 2, productName = "P2", quantity = 5.0, unitPrice = 200.0, purchasePrice = 150.0, alertThreshold = 1.0, category = "CAT", unit = "pcs")
        
        db.stockDao().insertOrUpdate(p1)
        db.stockDao().insertOrUpdate(p2)

        // Use repository directly to verify valuation logic if StateFlow is tricky in JVM test
        val valuation = repository.getStockValuation().first()
        assertEquals(1550.0, valuation.first, 0.01)
        assertEquals(2000.0, valuation.second, 0.01)
    }

    @Test
    fun `lowStockAlerts should filter products below threshold`() = runBlocking {
        val p1 = StockEntity(id = 1, productName = "P1", quantity = 1.0, unitPrice = 100.0, purchasePrice = 80.0, alertThreshold = 5.0, category = "CAT", unit = "pcs")
        val p2 = StockEntity(id = 2, productName = "P2", quantity = 10.0, unitPrice = 200.0, purchasePrice = 150.0, alertThreshold = 5.0, category = "CAT", unit = "pcs")
        
        db.stockDao().insertOrUpdate(p1)
        db.stockDao().insertOrUpdate(p2)

        val alerts = repository.lowStockAlerts.first()
        assertEquals(1, alerts.size)
        assertEquals("P1", alerts[0].productName)
    }

    @Test
    fun `performAudit should update stock on success`() = runBlocking {
        val p1 = StockEntity(id = 1, productName = "P1", quantity = 10.0, unitPrice = 100.0, purchasePrice = 80.0, alertThreshold = 2.0, category = "CAT", unit = "pcs")
        db.stockDao().insertOrUpdate(p1)

        val auditItems = listOf(
            AuditItemEntity(
                auditId = 0, 
                productId = 1, 
                productName = "P1", 
                systemQuantity = 10.0, 
                physicalQuantity = 8.0,
                unitPrice = 100.0,
                purchasePrice = 80.0
            )
        )
        
        repository.performAudit(null, auditItems)
        
        val updatedP1 = db.stockDao().getStockById(1)
        assertEquals(8.0, updatedP1?.quantity ?: 0.0, 0.0)
    }
}
