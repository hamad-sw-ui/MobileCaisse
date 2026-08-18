package com.reconsiliation.caisse.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reconsiliation.caisse.data.local.dao.AuditWithItems
import com.reconsiliation.caisse.data.local.entity.*
import com.reconsiliation.caisse.data.repository.MainRepository
import com.reconsiliation.caisse.ui.state.InventoryUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InventoryViewModel @Inject constructor(
    private val repository: MainRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<InventoryUiState>(InventoryUiState.Idle)
    val uiState: StateFlow<InventoryUiState> = _uiState.asStateFlow()

    // Data Flows
    val allStock: StateFlow<List<StockEntity>> = repository.allStock
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val lowStockAlerts: StateFlow<List<StockEntity>> = repository.lowStockAlerts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val stockValuation: StateFlow<Pair<Double, Double>> = repository.getStockValuation()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Pair(0.0, 0.0))

    val topProducts: StateFlow<List<StockEntity>> = repository.getTopProfitableProducts(5)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allAudits: StateFlow<List<AuditWithItems>> = repository.allAudits
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allSuppliers: StateFlow<List<SupplierEntity>> = repository.allSuppliers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allSupplies: StateFlow<List<SupplyEntity>> = repository.allSupplies
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Stock Operations
    fun adjustStock(stock: StockEntity, newQuantity: Double, reason: String, role: String?) {
        viewModelScope.launch {
            _uiState.value = InventoryUiState.Processing
            try {
                repository.adjustStock(stock, newQuantity, reason, role)
                _uiState.value = InventoryUiState.Success
            } catch (e: Exception) {
                _uiState.value = InventoryUiState.Error(e.message ?: "Erreur d'ajustement")
            }
        }
    }

    fun updateStock(stock: StockEntity, role: String?) {
        viewModelScope.launch {
            _uiState.value = InventoryUiState.Processing
            try {
                repository.updateStock(stock, role)
                _uiState.value = InventoryUiState.Success
            } catch (e: Exception) {
                _uiState.value = InventoryUiState.Error(e.message ?: "Erreur de mise à jour")
            }
        }
    }

    fun updateStockQuantity(id: Long, delta: Double) {
        viewModelScope.launch {
            repository.updateStockQuantity(id, delta)
        }
    }

    fun deleteStock(stock: StockEntity) {
        viewModelScope.launch {
            _uiState.value = InventoryUiState.Processing
            try {
                repository.deleteStock(stock)
                _uiState.value = InventoryUiState.Success
            } catch (e: Exception) {
                _uiState.value = InventoryUiState.Error(e.message ?: "Erreur de suppression")
            }
        }
    }

    suspend fun getProductByBarcode(barcode: String) = repository.getProductByBarcode(barcode)

    // Audits
    fun performAudit(note: String?, items: List<AuditItemEntity>) {
        viewModelScope.launch {
            _uiState.value = InventoryUiState.Processing
            try {
                repository.performAudit(note, items)
                _uiState.value = InventoryUiState.Success
            } catch (e: Exception) {
                _uiState.value = InventoryUiState.Error(e.message ?: "Erreur d'audit")
            }
        }
    }

    // Suppliers & Supplies
    fun addSupplier(supplier: SupplierEntity) {
        viewModelScope.launch {
            _uiState.value = InventoryUiState.Processing
            try {
                repository.addSupplier(supplier)
                _uiState.value = InventoryUiState.Success
            } catch (e: Exception) {
                _uiState.value = InventoryUiState.Error(e.message ?: "Erreur fournisseur")
            }
        }
    }

    fun deleteSupplier(supplier: SupplierEntity) {
        viewModelScope.launch {
            _uiState.value = InventoryUiState.Processing
            try {
                repository.deleteSupplier(supplier)
                _uiState.value = InventoryUiState.Success
            } catch (e: Exception) {
                _uiState.value = InventoryUiState.Error(e.message ?: "Erreur suppression fournisseur")
            }
        }
    }

    fun updateSupplierDebt(supplierId: Long, delta: Double) {
        viewModelScope.launch {
            _uiState.value = InventoryUiState.Processing
            try {
                repository.updateSupplierDebt(supplierId, delta)
                _uiState.value = InventoryUiState.Success
            } catch (e: Exception) {
                _uiState.value = InventoryUiState.Error(e.message ?: "Erreur mise à jour dette")
            }
        }
    }

    fun addSupply(supply: SupplyEntity, role: String?) {
        viewModelScope.launch {
            _uiState.value = InventoryUiState.Processing
            try {
                repository.addSupply(supply, role)
                _uiState.value = InventoryUiState.Success
            } catch (e: Exception) {
                _uiState.value = InventoryUiState.Error(e.message ?: "Erreur d'arrivage")
            }
        }
    }

    // Recipes
    fun getRecipeForProduct(parentId: Long) = repository.getRecipeForProduct(parentId)
    
    fun addRecipeComponent(parentProductId: Long, ingredientProductId: Long, quantity: Double) {
        viewModelScope.launch {
            repository.addRecipeComponent(RecipeEntity(parentProductId = parentProductId, ingredientProductId = ingredientProductId, quantityRequired = quantity))
        }
    }

    fun deleteRecipeComponent(recipe: RecipeEntity) {
        viewModelScope.launch {
            repository.deleteRecipeComponent(recipe)
        }
    }

    // History
    fun getPriceHistory(productId: Long) = repository.getPriceHistory(productId)
    fun getStockMovements(productId: Long) = repository.getStockMovements(productId)

    // Utils
    fun getCategories(type: String) = repository.getCategories(type)
    
    fun addCategory(name: String, type: String) {
        viewModelScope.launch {
            repository.addCategory(CategoryEntity(name = name, type = type))
        }
    }

    fun clearUiState() {
        _uiState.value = InventoryUiState.Idle
    }
}
