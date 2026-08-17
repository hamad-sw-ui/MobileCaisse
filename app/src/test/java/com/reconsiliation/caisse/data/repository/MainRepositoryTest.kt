package com.reconsiliation.caisse.data.repository

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
        val context = FakeContext()
        repository = MainRepository(db, context, inventoryRepository)
    }

    @Test
    fun `checkSubscription should throw exception if no subscription`() = runBlocking {
        try {
            repository.checkSubscription()
            fail("Should have thrown exception")
        } catch (e: Exception) {
            assertTrue(e.message!!.contains("expiré"))
        }
    }
}
