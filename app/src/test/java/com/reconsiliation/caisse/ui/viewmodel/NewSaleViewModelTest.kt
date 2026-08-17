package com.reconsiliation.caisse.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.reconsiliation.caisse.data.local.entity.StockEntity
import com.reconsiliation.caisse.data.repository.*
import com.reconsiliation.caisse.platform.PrinterManager
import com.reconsiliation.caisse.platform.ShareManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NewSaleViewModelTest {

    private lateinit var viewModel: NewSaleViewModel
    private lateinit var db: FakeAppDatabase
    private lateinit var salesRepository: SalesRepository
    private lateinit var mainRepository: MainRepository
    private lateinit var context: FakeContext

    @Before
    fun setup() {
        db = FakeAppDatabase()
        context = FakeContext()
        val inventoryRepository = InventoryRepository(
            db, db.stockDao(), db.priceHistoryDao(), db.stockMovementDao(),
            db.expenseDao(), db.actionLogDao(), db.subscriptionDao(), db.auditDao(),
            db.supplyDao(), db.supplierDao(), db.categoryDao(), db.recipeDao(),
            db.venteDao(), db.boutiqueDao()
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
        
        viewModel = NewSaleViewModel(
            salesRepository = salesRepository,
            mainRepository = mainRepository,
            printerManager = PrinterManager(context),
            shareManager = ShareManager(context),
            savedStateHandle = SavedStateHandle()
        )
    }

    private fun createProduct(id: Long) = StockEntity(
        id = id,
        productName = "Product $id",
        quantity = 10.0,
        unitPrice = 100.0,
        purchasePrice = 80.0,
        alertThreshold = 2.0,
        category = "CAT",
        unit = "pcs"
    )

    @Test
    fun `addToCart should add new item or increment quantity`() = runBlocking {
        val product = createProduct(1)
        
        viewModel.addToCart(product, 100.0)
        assertEquals(1, viewModel.cart.value.size)
        assertEquals(1.0, viewModel.cart.value[0].quantity, 0.0)

        viewModel.addToCart(product, 100.0)
        assertEquals(1, viewModel.cart.value.size)
        assertEquals(2.0, viewModel.cart.value[0].quantity, 0.0)
    }

    @Test
    fun `removeFromCart should remove item`() = runBlocking {
        val product = createProduct(1)
        viewModel.addToCart(product, 100.0)
        val item = viewModel.cart.value[0]
        
        viewModel.removeFromCart(item)
        assertTrue(viewModel.cart.value.isEmpty())
    }

    @Test
    fun `updateCartItemQuantity should update correctly`() = runBlocking {
        val product = createProduct(1)
        viewModel.addToCart(product, 100.0)
        val item = viewModel.cart.value[0]

        viewModel.updateCartItemQuantity(item, 5.0)
        assertEquals(5.0, viewModel.cart.value[0].quantity, 0.0)
    }

    @Test
    fun `clearCart should empty the list`() = runBlocking {
        val product = createProduct(1)
        viewModel.addToCart(product, 100.0)
        
        viewModel.clearCart()
        assertTrue(viewModel.cart.value.isEmpty())
    }
}
