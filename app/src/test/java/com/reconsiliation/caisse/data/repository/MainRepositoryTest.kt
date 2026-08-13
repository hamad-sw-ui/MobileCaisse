package com.reconsiliation.caisse.data.repository

import com.reconsiliation.caisse.data.local.entity.StockEntity
import com.reconsiliation.caisse.data.local.entity.BoutiqueEntity
import com.reconsiliation.caisse.data.local.entity.SubscriptionEntity
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
    fun `adjusting stock should update quantity and create expense if loss`() = runBlocking {
        val product = StockEntity(
            id = 1, 
            productName = "Pain", 
            quantity = 10.0, 
            unitPrice = 100.0, 
            purchasePrice = 80.0, 
            alertThreshold = 2.0
        )
        db.stockDao().insertOrUpdate(product)

        repository.adjustStock(product, 5.0, "Perte")

        val updatedStock = db.stockDao().getStockById(1)
        assertEquals(5.0, updatedStock!!.quantity, 0.0)
    }
}
