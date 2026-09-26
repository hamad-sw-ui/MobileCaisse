package com.reconsiliation.caisse.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reconsiliation.caisse.data.local.entity.*
import com.reconsiliation.caisse.data.repository.MainRepository
import com.reconsiliation.caisse.data.repository.SalesRepository
import com.reconsiliation.caisse.platform.PrinterManager
import com.reconsiliation.caisse.platform.ShareManager
import com.reconsiliation.caisse.ui.state.NewSaleUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.Serializable
import javax.inject.Inject

data class CartItem(
    val product: StockEntity,
    val quantity: Double,
    val priceAtSale: Double,
    val isRetail: Boolean = false,
    val parentId: Long? = null,
    val conversionFactor: Double = 1.0
) : Serializable

@HiltViewModel
class NewSaleViewModel @Inject constructor(
    private val salesRepository: SalesRepository,
    private val mainRepository: MainRepository,
    private val printerManager: PrinterManager,
    private val shareManager: ShareManager,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow<NewSaleUiState>(NewSaleUiState.Idle)
    val uiState: StateFlow<NewSaleUiState> = _uiState.asStateFlow()

    // Panier avec persistance SavedStateHandle (Process Death Protection)
    private val _cart = savedStateHandle.getStateFlow<List<CartItem>>("cart_items", emptyList())
    val cart: StateFlow<List<CartItem>> = _cart

    private val _lastConfirmedVenteId = MutableStateFlow<Long?>(null)
    val lastConfirmedVenteId = _lastConfirmedVenteId.asStateFlow()

    val allStock: Flow<List<StockEntity>> = mainRepository.allStock
    val currency: Flow<String> = mainRepository.boutique.map { it?.currency ?: "FCFA" }

    fun addToCart(product: StockEntity, price: Double, isRetail: Boolean = false, parentId: Long? = null, factor: Double = 1.0) {
        val current = _cart.value.toMutableList()
        val existing = current.find { it.product.id == product.id && it.isRetail == isRetail }
        if (existing != null) {
            val updatedList = current.map {
                if (it.product.id == product.id && it.isRetail == isRetail)
                    it.copy(quantity = it.quantity + 1, priceAtSale = price)
                else it
            }
            savedStateHandle["cart_items"] = updatedList
        } else {
            val newList = current + CartItem(product, 1.0, price, isRetail, parentId, factor)
            savedStateHandle["cart_items"] = newList
        }
    }

    fun removeFromCart(item: CartItem) {
        val current = _cart.value.toMutableList()
        current.remove(item)
        savedStateHandle["cart_items"] = current
    }

    fun updateCartItemQuantity(item: CartItem, newQuantity: Double) {
        val current = _cart.value.toMutableList()
        val updatedList = current.map {
            if (it.product.id == item.product.id && it.isRetail == item.isRetail)
                it.copy(quantity = newQuantity)
            else it
        }
        savedStateHandle["cart_items"] = updatedList
    }

    fun clearCart() {
        savedStateHandle["cart_items"] = emptyList<CartItem>()
    }

    fun updateCartPrices(isVip: Boolean) {
        val current = _cart.value
        val updated = current.map { item ->
            val price = if (isVip && item.product.vipPrice > 0) item.product.vipPrice else item.product.unitPrice
            item.copy(priceAtSale = price)
        }
        savedStateHandle["cart_items"] = updated
    }

    fun addVenteWithItems(vente: VenteEntity, items: List<VenteItemEntity>, userRole: String?, isTaxEnabled: Boolean, taxRate: Double) {
        viewModelScope.launch {
            _uiState.value = NewSaleUiState.Processing
            try {
                val finalVente = if (isTaxEnabled) {
                    val tax = vente.amount * taxRate / 100.0
                    vente.copy(taxAmount = tax, amount = vente.amount + tax)
                } else vente

                val processedItems = items.map { it.copy(venteId = 0) }

                val venteId = salesRepository.insertVenteWithItems(finalVente, processedItems, userRole)
                clearCart()
                _lastConfirmedVenteId.value = venteId
                _uiState.value = NewSaleUiState.Success(venteId)
            } catch (e: Exception) {
                _uiState.value = NewSaleUiState.Error(e.message ?: "")
            }
        }
    }

    fun clearUiState() {
        _uiState.value = NewSaleUiState.Idle
    }

    fun clearLastVenteId() {
        _lastConfirmedVenteId.value = null
        _uiState.value = NewSaleUiState.Idle
    }

    suspend fun getProductByBarcode(barcode: String) = mainRepository.getProductByBarcode(barcode)
    suspend fun getCustomerByPhone(phone: String) = salesRepository.getCustomerByPhone(phone)
    fun getCategories(type: String) = mainRepository.getCategories(type)
    suspend fun getVenteWithItems(venteId: Long) = salesRepository.getVenteWithItems(venteId)

    fun printTicket(venteId: Long, printerAddress: String) = viewModelScope.launch(Dispatchers.IO) {
        val venteWithItems = salesRepository.getVenteWithItems(venteId) ?: return@launch
        val boutique = mainRepository.getBoutiqueOnce() ?: return@launch
        printerManager.printReceipt(boutique, venteWithItems.vente, venteWithItems.items, printerAddress)
    }

    fun shareReceipt(text: String) {
        shareManager.shareText(text, "Partager le reçu", "com.whatsapp")
    }
}
