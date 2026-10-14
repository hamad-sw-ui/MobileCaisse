package com.reconsiliation.caisse.ui.screens

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.reconsiliation.caisse.R
import com.reconsiliation.caisse.data.local.entity.StockEntity
import com.reconsiliation.caisse.ui.state.NewSaleUiState
import com.reconsiliation.caisse.ui.viewmodel.CartItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NewSaleScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val targetContext
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val titleNewSaleText: String
        get() = targetContext.getString(R.string.title_new_sale)

    private val cartEmptyText: String
        get() = targetContext.getString(R.string.cart_empty)

    private val actionValidateSaleText: String
        get() = targetContext.getString(R.string.action_validate_sale)

    private fun createProduct(id: Long = 100L, name: String = "Jus d'Orange") = StockEntity(
        id = id,
        productName = name,
        quantity = 50.0,
        unitPrice = 500.0,
        purchasePrice = 350.0,
        alertThreshold = 5.0,
        category = "BOISSONS",
        unit = "pcs"
    )

    private fun createCartItem(product: StockEntity, quantity: Double = 2.0) = CartItem(
        product = product,
        quantity = quantity,
        priceAtSale = product.unitPrice
    )

    @Test
    fun newSaleContent_displaysEmptyCartInitialState() {
        composeTestRule.setContent {
            NewSaleContent(
                cart = emptyList(),
                stockItems = emptyList(),
                currency = "FCFA",
                boutique = null,
                operatorName = "MTN/OM",
                uiState = NewSaleUiState.Idle,
                snackbarHostState = SnackbarHostState(),
                onUpdateCartQuantity = { _, _ -> },
                onRemoveFromCart = {},
                onAddToCart = { _, _, _, _, _ -> },
                onUpdateCartPrices = {},
                onGetCustomerByPhone = { null },
                onValidateSaleSubmit = { _, _ -> },
                onNavigateBack = {},
                onScanClick = {}
            )
        }

        // Verify title & empty cart message are displayed
        composeTestRule.onNodeWithText(titleNewSaleText).assertIsDisplayed()
        composeTestRule.onNodeWithText(cartEmptyText).assertIsDisplayed()

        // Verify empty cart container tag
        composeTestRule.onNodeWithTag("cart_empty_view").assertIsDisplayed()

        // Verify validate sale button exists
        composeTestRule.onNodeWithText(actionValidateSaleText).assertExists()
    }

    @Test
    fun newSaleContent_displaysCartItems_whenCartIsNotEmpty() {
        val product = createProduct(1L, "Eau Minérale")
        val item = createCartItem(product, quantity = 3.0)

        composeTestRule.setContent {
            NewSaleContent(
                cart = listOf(item),
                stockItems = listOf(product),
                currency = "FCFA",
                boutique = null,
                operatorName = "MTN/OM",
                uiState = NewSaleUiState.Idle,
                snackbarHostState = SnackbarHostState(),
                onUpdateCartQuantity = { _, _ -> },
                onRemoveFromCart = {},
                onAddToCart = { _, _, _, _, _ -> },
                onUpdateCartPrices = {},
                onGetCustomerByPhone = { null },
                onValidateSaleSubmit = { _, _ -> },
                onNavigateBack = {},
                onScanClick = {}
            )
        }

        // Verify product name is displayed
        composeTestRule.onNodeWithText("Eau Minérale").assertIsDisplayed()

        // Verify cart items container tag
        composeTestRule.onNodeWithTag("cart_items_list").assertIsDisplayed()

        // Verify quantity text "3" is displayed in cart row
        composeTestRule.onNodeWithTag("cart_item_qty_${product.id}").assertIsDisplayed()
        composeTestRule.onNodeWithText("3").assertIsDisplayed()
    }

    @Test
    fun newSaleContent_triggersQuantityCallbacks_whenPlusMinusClicked() {
        val product = createProduct(1L, "Biscuit")
        val item = createCartItem(product, quantity = 2.0)
        var updatedQuantity = 0.0

        composeTestRule.setContent {
            NewSaleContent(
                cart = listOf(item),
                stockItems = listOf(product),
                currency = "FCFA",
                boutique = null,
                operatorName = "MTN/OM",
                uiState = NewSaleUiState.Idle,
                snackbarHostState = SnackbarHostState(),
                onUpdateCartQuantity = { _, newQty -> updatedQuantity = newQty },
                onRemoveFromCart = {},
                onAddToCart = { _, _, _, _, _ -> },
                onUpdateCartPrices = {},
                onGetCustomerByPhone = { null },
                onValidateSaleSubmit = { _, _ -> },
                onNavigateBack = {},
                onScanClick = {}
            )
        }

        // Click plus button
        composeTestRule.onNodeWithTag("cart_item_plus_${product.id}").performClick()
        assertEquals(3.0, updatedQuantity, 0.01)

        // Click minus button
        composeTestRule.onNodeWithTag("cart_item_minus_${product.id}").performClick()
        assertEquals(1.0, updatedQuantity, 0.01)
    }

    @Test
    fun newSaleContent_triggersRemoveCallback_whenRemoveClicked() {
        val product = createProduct(1L, "Savon")
        val item = createCartItem(product, quantity = 1.0)
        var removed = false

        composeTestRule.setContent {
            NewSaleContent(
                cart = listOf(item),
                stockItems = listOf(product),
                currency = "FCFA",
                boutique = null,
                operatorName = "MTN/OM",
                uiState = NewSaleUiState.Idle,
                snackbarHostState = SnackbarHostState(),
                onUpdateCartQuantity = { _, _ -> },
                onRemoveFromCart = { removed = true },
                onAddToCart = { _, _, _, _, _ -> },
                onUpdateCartPrices = {},
                onGetCustomerByPhone = { null },
                onValidateSaleSubmit = { _, _ -> },
                onNavigateBack = {},
                onScanClick = {}
            )
        }

        // Click remove button
        composeTestRule.onNodeWithTag("cart_item_remove_${product.id}").performClick()
        assertTrue(removed)
    }

    @Test
    fun newSaleContent_triggersValidateSaleCallback_whenValidateButtonClicked() {
        val product = createProduct(1L, "Pain")
        val item = createCartItem(product, quantity = 1.0)
        var submitted = false

        composeTestRule.setContent {
            NewSaleContent(
                cart = listOf(item),
                stockItems = listOf(product),
                currency = "FCFA",
                boutique = null,
                operatorName = "MTN/OM",
                uiState = NewSaleUiState.Idle,
                snackbarHostState = SnackbarHostState(),
                onUpdateCartQuantity = { _, _ -> },
                onRemoveFromCart = {},
                onAddToCart = { _, _, _, _, _ -> },
                onUpdateCartPrices = {},
                onGetCustomerByPhone = { null },
                onValidateSaleSubmit = { _, _ -> submitted = true },
                onNavigateBack = {},
                onScanClick = {}
            )
        }

        // Click validate sale button
        composeTestRule.onNodeWithText(actionValidateSaleText).performClick()
        assertTrue(submitted)
    }
}
