package com.reconsiliation.caisse.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.reconsiliation.caisse.data.local.entity.StockEntity
import com.reconsiliation.caisse.data.local.entity.SupplierEntity
import com.reconsiliation.caisse.data.local.entity.SupplyEntity
import com.reconsiliation.caisse.ui.components.CaisseTextFieldDefaults
import com.reconsiliation.caisse.ui.navigation.Screen
import com.reconsiliation.caisse.ui.theme.Primary
import com.reconsiliation.caisse.ui.viewmodel.MainViewModel
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupplierInflowScreen(navController: NavController) {
    val viewModel: MainViewModel = viewModel()
    val allStock by viewModel.allStock.collectAsState()
    val allSuppliers by viewModel.allSuppliers.collectAsState()
    
    var selectedSupplier by remember { mutableStateOf<SupplierEntity?>(null) }
    var selectedProduct by remember { mutableStateOf<StockEntity?>(null) }
    var quantity by remember { mutableStateOf("") }
    var purchasePrice by remember { mutableStateOf("") }
    var expiryDateStr by remember { mutableStateOf("") }
    var isPaid by remember { mutableStateOf(true) }
    
    var showSupplierPicker by remember { mutableStateOf(false) }
    var showAddSupplierDialog by remember { mutableStateOf(false) }
    var showProductPicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nouvel Arrivage") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        if (allStock.isEmpty()) {
            Column(
                modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Inventory, null, modifier = Modifier.size(64.dp), tint = Color.LightGray)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Votre stock est vide.", style = MaterialTheme.typography.titleMedium)
                Text("Ajoutez d'abord des produits pour enregistrer un arrivage.", textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = Color.Gray)
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = { navController.navigate(Screen.Stock.route) }) {
                    Text("Aller au Stock")
                }
            }
        } else {
            Column(modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                val textFieldColors = CaisseTextFieldDefaults.outlinedTextFieldColors()
                
                Text("Informations sur l'arrivage", style = MaterialTheme.typography.titleMedium, color = Primary)

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = selectedSupplier?.name ?: "Choisir un fournisseur...",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Fournisseur") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Business, null) },
                        enabled = false,
                        colors = textFieldColors,
                        trailingIcon = {
                            IconButton(onClick = { navController.navigate(Screen.Suppliers.route) }) {
                                Icon(Icons.Default.Business, "Liste")
                            }
                        }
                    )
                    Surface(
                        color = Color.Transparent,
                        modifier = Modifier.fillMaxWidth().height(64.dp).clickable { showSupplierPicker = true },
                        content = {}
                    )
                }

                // Product Selection
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = selectedProduct?.productName ?: "Choisir un produit...",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Produit") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Inventory, null) },
                        enabled = false,
                        colors = textFieldColors
                    )
                    Surface(
                        color = Color.Transparent,
                        modifier = Modifier.fillMaxWidth().height(64.dp).clickable { showProductPicker = true },
                        content = {}
                    )
                }

                var isRetailSupply by remember { mutableStateOf(false) }

                if (selectedProduct?.isBulk == true && selectedProduct?.retailUnit != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = isRetailSupply, onCheckedChange = { isRetailSupply = it })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Achat au détail (${selectedProduct?.retailUnit})", style = MaterialTheme.typography.bodySmall)
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = quantity,
                        onValueChange = { quantity = it },
                        label = { Text(if (isRetailSupply) "Quantité (${selectedProduct?.retailUnit})" else "Quantité (${selectedProduct?.unit ?: "Unité"})") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = textFieldColors
                    )
                    OutlinedTextField(
                        value = purchasePrice,
                        onValueChange = { purchasePrice = it },
                        label = { Text(if (isRetailSupply) "Prix d'achat (Unité détail)" else "Prix d'achat (Unité gros)") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = textFieldColors
                    )
                }

                OutlinedTextField(
                    value = expiryDateStr,
                    onValueChange = { expiryDateStr = it },
                    label = { Text("Date d'expiration (JJ/MM/AAAA)") },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Ex: 31/12/2025") },
                    colors = textFieldColors
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isPaid, onCheckedChange = { isPaid = it })
                    Text("Stock payé au fournisseur (Sinon, ajout en dette)")
                }

                Spacer(modifier = Modifier.weight(1f))

                Button(
                    onClick = {
                        if (selectedProduct != null && quantity.isNotEmpty()) {
                            val qtyValRaw = quantity.toDoubleOrNull() ?: 0.0
                            if (qtyValRaw <= 0) return@Button
                            
                            // Normalized quantity to main unit if retail supply
                            val qtyVal = if (isRetailSupply && selectedProduct?.isBulk == true) {
                                qtyValRaw / selectedProduct!!.conversionFactor
                            } else qtyValRaw

                            val finalPurchasePrice = if (isRetailSupply && selectedProduct?.isBulk == true) {
                                (purchasePrice.toDoubleOrNull() ?: selectedProduct!!.purchasePrice) * selectedProduct!!.conversionFactor
                            } else {
                                purchasePrice.toDoubleOrNull() ?: selectedProduct!!.purchasePrice
                            }

                            val expiry = try {
                                val parts = expiryDateStr.split("/")
                                if (parts.size == 3) {
                                    val day = parts[0].toIntOrNull() ?: 0
                                    val month = (parts[1].toIntOrNull() ?: 1) - 1
                                    val year = parts[2].toIntOrNull() ?: 0
                                    if (year < 2000 || year > 2100 || month < 0 || month > 11 || day < 1 || day > 31) null
                                    else {
                                        Calendar.getInstance().apply {
                                            set(Calendar.YEAR, year)
                                            set(Calendar.MONTH, month)
                                            set(Calendar.DAY_OF_MONTH, day)
                                        }.time
                                    }
                                } else null
                            } catch (e: Exception) { 
                                android.util.Log.e("SupplierInflow", "Date parsing failed", e)
                                null 
                            }

                            viewModel.addSupply(SupplyEntity(
                                productId = selectedProduct!!.id,
                                productName = selectedProduct!!.productName,
                                supplierId = selectedSupplier?.id,
                                supplierName = selectedSupplier?.name,
                                quantity = qtyVal,
                                purchasePrice = finalPurchasePrice,
                                date = Date(),
                                expiryDate = expiry,
                                isPaid = isPaid
                            ))
                            navController.popBackStack()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = selectedProduct != null && quantity.isNotEmpty() && (isPaid || selectedSupplier != null)
                ) {
                    Icon(Icons.Default.Add, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Valider l'entrée en stock")
                }
            }
        }

        if (showSupplierPicker) {
            AlertDialog(
                onDismissRequest = { showSupplierPicker = false },
                title = { Text("Sélectionner un fournisseur") },
                text = {
                    LazyColumn {
                        items(allSuppliers) { supplier ->
                            ListItem(
                                headlineContent = { Text(supplier.name) },
                                modifier = Modifier.fillMaxWidth().clickable {
                                    selectedSupplier = supplier
                                    showSupplierPicker = false
                                }
                            )
                        }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { showSupplierPicker = false }) { Text("Annuler") } }
            )
        }

        if (showProductPicker) {
            AlertDialog(
                onDismissRequest = { showProductPicker = false },
                title = { Text("Sélectionner un produit") },
                text = {
                    LazyColumn {
                        items(allStock) { product ->
                            ListItem(
                                headlineContent = { Text(product.productName) },
                                modifier = Modifier.fillMaxWidth().clickable {
                                    selectedProduct = product
                                    purchasePrice = product.purchasePrice.toString()
                                    showProductPicker = false
                                }
                            )
                        }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { showProductPicker = false }) { Text("Annuler") } }
            )
        }

        if (showAddSupplierDialog) {
            var newName by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showAddSupplierDialog = false },
                title = { Text("Nouveau Fournisseur") },
                text = {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Nom du fournisseur") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = CaisseTextFieldDefaults.outlinedTextFieldColors()
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (newName.isNotBlank()) {
                            viewModel.addSupplier(SupplierEntity(name = newName))
                            showAddSupplierDialog = false
                        }
                    }) { Text("Ajouter") }
                },
                dismissButton = { TextButton(onClick = { showAddSupplierDialog = false }) { Text("Annuler") } }
            )
        }
    }
}
