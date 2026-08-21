package com.reconsiliation.caisse.ui.viewmodel

import com.reconsiliation.caisse.data.repository.*
import com.reconsiliation.caisse.ui.state.ExpenseUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExpenseViewModelTest {

    private lateinit var viewModel: ExpenseViewModel
    private lateinit var db: FakeAppDatabase
    private lateinit var mainRepository: MainRepository
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
        viewModel = ExpenseViewModel(mainRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `addExpense should insert entity when valid`() = runTest {
        backgroundScope.launch { viewModel.allExpenses.collect {} }
        viewModel.addExpense("Taxi", 1000.0, "Transport")
        advanceUntilIdle()
        
        val expenses = viewModel.allExpenses.value
        assertEquals(1, expenses.size)
        assertEquals("Taxi", expenses[0].label)
        assertEquals(1000.0, expenses[0].amount, 0.0)
        assertTrue(viewModel.uiState.value is ExpenseUiState.Success)
    }

    @Test
    fun `addExpense should fail when amount is zero`() = runTest {
        backgroundScope.launch { viewModel.allExpenses.collect {} }
        viewModel.addExpense("Taxi", 0.0, "Transport")
        advanceUntilIdle()
        
        assertTrue(viewModel.uiState.value is ExpenseUiState.Error)
        assertEquals(0, viewModel.allExpenses.value.size)
    }

    @Test
    fun `deleteExpense should call repository`() = runTest {
        backgroundScope.launch { viewModel.allExpenses.collect {} }
        viewModel.addExpense("Taxi", 1000.0, "Transport")
        advanceUntilIdle()
        val expense = viewModel.allExpenses.value[0]
        
        viewModel.deleteExpense(expense)
        advanceUntilIdle()
        
        assertEquals(0, viewModel.allExpenses.value.size)
    }

    @Test
    fun `getCategories should only return EXPENSE type`() = runTest {
        mainRepository.addCategory(com.reconsiliation.caisse.data.local.entity.CategoryEntity(name = "Cat1", type = "PRODUCT"))
        mainRepository.addCategory(com.reconsiliation.caisse.data.local.entity.CategoryEntity(name = "Cat2", type = "EXPENSE"))
        advanceUntilIdle()
        
        val categories = viewModel.getCategories().first()
        assertEquals(1, categories.size)
        assertEquals("EXPENSE", categories[0].type)
    }
}
