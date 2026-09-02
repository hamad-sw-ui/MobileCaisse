package com.reconsiliation.caisse.ui.viewmodel

import com.reconsiliation.caisse.data.local.entity.CategoryEntity
import com.reconsiliation.caisse.data.repository.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CategoryViewModelTest {

    private lateinit var viewModel: CategoryViewModel
    private lateinit var db: FakeAppDatabase
    private lateinit var repository: MainRepository
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        db = FakeAppDatabase()
        val inventoryRepository = InventoryRepository(
            db, db.stockDao(), db.priceHistoryDao(), db.stockMovementDao(),
            db.expenseDao(), db.actionLogDao(), db.subscriptionDao(), db.auditDao(),
            db.supplyDao(), db.supplierDao(), db.categoryDao(), db.recipeDao(),
            db.venteDao(), db.boutiqueDao()
        )
        repository = MainRepository(db, FakeContext(), inventoryRepository)
        viewModel = CategoryViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `getCategories should return flow from repository`() = runTest {
        db.categoryDao().insert(CategoryEntity(name = "Cat 1", type = "PRODUCT"))
        advanceUntilIdle()
        
        val cats = viewModel.getCategories("PRODUCT").first()
        assertEquals(1, cats.size)
        assertEquals("Cat 1", cats[0].name)
    }

    @Test
    fun `addCategory should insert into database`() = runTest {
        viewModel.addCategory("New Cat", "PRODUCT")
        advanceUntilIdle()
        
        val cats = db.categoryDao().getCategoriesByType("PRODUCT").first()
        assertEquals(1, cats.size)
        assertEquals("New Cat", cats[0].name)
    }

    @Test
    fun `deleteCategory should remove from database`() = runTest {
        val cat = CategoryEntity(id = 1, name = "Cat 1", type = "PRODUCT")
        db.categoryDao().insert(cat)
        advanceUntilIdle()
        
        viewModel.deleteCategory(cat)
        advanceUntilIdle()
        
        val cats = db.categoryDao().getCategoriesByType("PRODUCT").first()
        assertEquals(0, cats.size)
    }
}
