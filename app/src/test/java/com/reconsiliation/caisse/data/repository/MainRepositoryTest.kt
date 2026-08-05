package com.reconsiliation.caisse.data.repository

import com.reconsiliation.caisse.data.local.entity.StockEntity
import com.reconsiliation.caisse.data.local.entity.VenteEntity
import com.reconsiliation.caisse.data.local.entity.VenteItemEntity
import com.reconsiliation.caisse.data.local.entity.BoutiqueEntity
import com.reconsiliation.caisse.data.local.entity.SubscriptionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.*

class MainRepositoryTest {

    private lateinit var db: FakeAppDatabase
    private lateinit var repository: MainRepository

    @Before
    fun setup() {
        db = FakeAppDatabase()
        // MainRepository needs a Context. We provide a dummy one.
        // In J3.1 (B-094A), we use JVM only.
        repository = MainRepository(db, FakeContext())
    }

    @Test
    fun `inserting a sale should decrement stock and log action`() = runBlocking {
        // 1. Setup Stock
        val product = StockEntity(
            id = 1, 
            productName = "Pain", 
            quantity = 10.0, 
            unitPrice = 100.0, 
            purchasePrice = 80.0, 
            alertThreshold = 2.0
        )
        db.stockDao().insertOrUpdate(product)
        
        // 2. Setup Boutique (required for checkSubscription and invoice numbering)
        val boutique = BoutiqueEntity(
            id = 1, 
            name = "Test", 
            ownerName = "Owner", 
            phoneNumber = "677112233", 
            operator = "MTN", 
            momoNumber = "677112233"
        )
        db.boutiqueDao().insertOrUpdate(boutique)
        
        // 3. Setup Subscription (required for checkSubscription)
        val sub = SubscriptionEntity(
            id = 1, 
            startDate = Date(), 
            endDate = Date(System.currentTimeMillis() + 86400000), 
            isActive = true, 
            deviceId = "TEST"
        )
        db.subscriptionDao().insertOrUpdate(sub)

        // 4. Create Sale
        val vente = VenteEntity(
            amount = 200.0, 
            description = "Test sale", 
            date = Date(), 
            paymentMethod = "CASH", 
            status = "CONFIRMED"
        )
        val items = listOf(
            VenteItemEntity(venteId = 0, productId = 1, productName = "Pain", quantity = 2.0, unitPrice = 100.0, purchasePrice = 80.0)
        )

        val venteId = repository.insertVenteWithItems(vente, items)

        // 5. Verifications
        assertTrue(venteId > 0)
        
        val updatedStock = db.stockDao().getStockById(1)
        assertEquals(8.0, updatedStock!!.quantity, 0.0)
    }

    @Test
    fun `adjusting stock should update quantity and create expense if loss`() = runBlocking {
        val product = StockEntity(
            id = 1, 
            productName = "Sucre", 
            quantity = 50.0, 
            unitPrice = 800.0, 
            purchasePrice = 600.0, 
            alertThreshold = 5.0
        )
        db.stockDao().insertOrUpdate(product)
        
        // Adjust to 40 (Loss of 10)
        repository.adjustStock(product, 40.0, "Casse")
        
        val updated = db.stockDao().getStockById(1)
        assertEquals(40.0, updated!!.quantity, 0.0)
    }
}

/** Dummy Context for JVM tests */
class FakeContext : android.content.ContextWrapper(null) {
    override fun getDatabasePath(name: String): java.io.File = java.io.File(name)
    override fun getPackageName(): String = "com.reconsiliation.caisse"
}
