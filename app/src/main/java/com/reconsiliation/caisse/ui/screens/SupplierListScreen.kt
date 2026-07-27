package com.reconsiliation.caisse.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
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
import com.reconsiliation.caisse.ui.components.CaisseTextFieldDefaults
import com.reconsiliation.caisse.ui.theme.Primary
import com.reconsiliation.caisse.ui.theme.RedError
import com.reconsiliation.caisse.ui.viewmodel.MainViewModel
import com.reconsiliation.caisse.utils.FormatUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupplierListScreen(navController: NavController) {
    val viewModel: MainViewModel = viewModel()
    val suppliers by viewModel.allSuppliers.collectAsState()
    
    var showAddDialog by remember { mutableStateOf(false) }
    var supplierToPay by remember { mutableStateOf<SupplierEntity?>(null) }
    var supplierToReturn by remember { mutableStateOf<SupplierEntity?>(null) }
    var supplierToDelete by remember { mutableStateOf<SupplierEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mes Fournisseurs") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, "Nouveau Fournisseur")
                    }
                }
            )
        }
    ) { padding ->
        if (suppliers.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Aucun fournisseur enregistré", color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(suppliers) { supplier ->
                    SupplierCard(
                        supplier = supplier,
                        onPay = { supplierToPay = supplier },
                        onReturn = { supplierToReturn = supplier },
                        onDelete = { supplierToDelete = supplier }
                    )
                }
            }
        }

        if (showAddDialog) {
            AddSupplierDialog(
                onDismiss = { showAddDialog = false },
                onConfirm = { name, phone ->
                    viewModel.addSupplier(SupplierEntity(name = name, phone = phone))
                    showAddDialog = false
                }
            )
        }

        if (supplierToPay != null) {
            RepaySupplierDialog(
                supplier = supplierToPay!!,
                onDismiss = { supplierToPay = null },
                onConfirm = { amount ->
                    viewModel.updateSupplierDebt(supplierToPay!!.id, -amount)
                    supplierToPay = null
                }
            )
        }

        if (supplierToReturn != null) {
            ReturnToSupplierDialog(
                supplier = supplierToReturn!!,
                onDismiss = { supplierToReturn = null },
                onConfirm = { product, qty, price ->
                    viewModel.adjustStock(product, product.quantity - qty, "Retour Fournisseur: ${supplierToReturn!!.name}")
                    viewModel.updateSupplierDebt(supplierToReturn!!.id, -(qty * price))
                    supplierToReturn = null
                }
            )
        }

        if (supplierToDelete != null) {
            AlertDialog(
                onDismissRequest = { supplierToDelete = null },
                title = { Text("Supprimer le fournisseur ?") },
                text = { Text("Voulez-vous vraiment supprimer ${supplierToDelete!!.name} ?") },
                confirmButton = {
                    Button(onClick = { viewModel.deleteSupplier(supplierToDelete!!); supplierToDelete = null }, colors = ButtonDefaults.buttonColors(containerColor = RedError)) {
                        Text("Supprimer")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { supplierToDelete = null }) { Text("Annuler") }
                }
            )
        }
    }
}

@Composable
fun SupplierCard(supplier: SupplierEntity, onPay: () -> Unit, onReturn: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Business, null, tint = Primary, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(supplier.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (!supplier.phone.isNullOrBlank()) Text(supplier.phone, style = MaterialTheme.typography.bodySmall)
                Text("Dette: ${FormatUtil.formatCurrency(supplier.totalDebt)}", color = if (supplier.totalDebt > 0) RedError else Color.Gray, fontWeight = FontWeight.Bold)
            }
            Row {
                IconButton(onClick = onReturn) {
                    Icon(Icons.Default.RemoveShoppingCart, "Retour", tint = RedError)
                }
                IconButton(onClick = onPay) {
                    Icon(Icons.Default.Payments, "Payer", tint = Primary)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, "Supprimer", tint = Color.Gray)
                }
            }
        }
    }
}

@Composable
fun AddSupplierDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nouveau Fournisseur") },
        text = {
            val textFieldColors = CaisseTextFieldDefaults.outlinedTextFieldColors()
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nom") }, modifier = Modifier.fillMaxWidth(), colors = textFieldColors)
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Téléphone") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth(), colors = textFieldColors)
            }
        },
        confirmButton = {
            Button(onClick = { if (name.isNotBlank()) onConfirm(name, phone) }) { Text("Ajouter") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

@Composable
fun RepaySupplierDialog(supplier: SupplierEntity, onDismiss: () -> Unit, onConfirm: (Double) -> Unit) {
    var amount by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rembourser ${supplier.name}") },
        text = {
            Column {
                Text("Dette actuelle: ${FormatUtil.formatCurrency(supplier.totalDebt)}")
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Montant payé") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    colors = CaisseTextFieldDefaults.outlinedTextFieldColors()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val amt = amount.toDoubleOrNull() ?: 0.0
                if (amt > 0) onConfirm(amt)
            }) { Text("Confirmer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReturnToSupplierDialog(supplier: SupplierEntity, onDismiss: () -> Unit, onConfirm: (StockEntity, Double, Double) -> Unit) {
    val viewModel: MainViewModel = viewModel()
    val stockItems by viewModel.allStock.collectAsState()
    
    var selectedProduct by remember { mutableStateOf<StockEntity?>(null) }
    var qty by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Retour Fournisseur : ${supplier.name}") },
        text = {
            val textFieldColors = CaisseTextFieldDefaults.outlinedTextFieldColors()
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    OutlinedTextField(
                        value = selectedProduct?.productName ?: "Choisir produit...",
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth().clickable { expanded = true },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
                        colors = textFieldColors
                    )
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        stockItems.forEach { item ->
                            DropdownMenuItem(text = { Text(item.productName) }, onClick = { 
                                selectedProduct = item
                                price = item.purchasePrice.toString()
                                expanded = false 
                            })
                        }
                    }
                }
                OutlinedTextField(value = qty, onValueChange = { qty = it }, label = { Text("Quantité retournée") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), colors = textFieldColors)
                OutlinedTextField(value = price, onValueChange = { price = it }, label = { Text("Prix d'achat unitaire (déduction)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), colors = textFieldColors)
            }
        },
        confirmButton = {
            Button(onClick = {
                val q = qty.toDoubleOrNull() ?: 0.0
                val p = price.toDoubleOrNull() ?: 0.0
                if (selectedProduct != null && q > 0) onConfirm(selectedProduct!!, q, p)
            }) { Text("Valider le Retour") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}
