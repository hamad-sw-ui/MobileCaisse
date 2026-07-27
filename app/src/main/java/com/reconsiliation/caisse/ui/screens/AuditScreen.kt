package com.reconsiliation.caisse.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
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
import com.reconsiliation.caisse.data.local.entity.AuditItemEntity
import com.reconsiliation.caisse.ui.components.CaisseTextFieldDefaults
import com.reconsiliation.caisse.ui.theme.GreenSuccess
import com.reconsiliation.caisse.ui.theme.RedError
import com.reconsiliation.caisse.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditScreen(navController: NavController) {
    val viewModel: MainViewModel = viewModel()
    val stock by viewModel.allStock.collectAsState()
    
    // Local state for physical quantities
    val physicalQuantities = remember(stock) {
        mutableStateListOf<String>().apply {
            addAll(stock.map { it.quantity.toString() })
        }
    }

    var showConfirmDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Audit d'Inventaire") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                actions = {
                    IconButton(onClick = { showConfirmDialog = true }) {
                        Icon(Icons.Default.Save, contentDescription = "Enregistrer l'audit")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Text(
                "Saisissez les quantités réelles comptées physiquement.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(16.dp)
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(stock, key = { _, product -> product.id }) { index, product ->
                    AuditItemRow(
                        productName = product.productName,
                        systemQty = product.quantity,
                        physicalQty = physicalQuantities.getOrElse(index) { "0" },
                        onQtyChange = { physicalQuantities[index] = it }
                    )
                }
            }
            
            Button(
                onClick = { showConfirmDialog = true },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GreenSuccess)
            ) {
                Text("Finaliser l'Audit")
            }
        }

        if (showConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showConfirmDialog = false },
                title = { Text("Confirmer l'Audit") },
                text = { Text("Le stock sera ajusté selon les quantités saisies. Des dépenses de pertes seront créées si nécessaire.") },
                confirmButton = {
                    TextButton(onClick = {
                        val auditItems = stock.mapIndexed { index, product ->
                            val physical = physicalQuantities[index].toDoubleOrNull() ?: product.quantity
                            AuditItemEntity(
                                auditId = 0,
                                productId = product.id,
                                productName = product.productName,
                                systemQuantity = product.quantity,
                                physicalQuantity = physical,
                                unitPrice = product.unitPrice,
                                purchasePrice = product.purchasePrice
                            )
                        }
                        viewModel.performAudit(null, auditItems)
                        showConfirmDialog = false
                        navController.popBackStack()
                    }) {
                        Text("Confirmer")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirmDialog = false }) { Text("Annuler") }
                }
            )
        }
    }
}

@Composable
fun AuditItemRow(productName: String, systemQty: Double, physicalQty: String, onQtyChange: (String) -> Unit) {
    val physical = physicalQty.toDoubleOrNull() ?: 0.0
    val diff = physical - systemQty
    val diffColor = if (diff < 0) RedError else if (diff > 0) GreenSuccess else Color.Gray

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(productName, fontWeight = FontWeight.Bold)
                Text("Système: $systemQty", style = MaterialTheme.typography.bodySmall)
                if (diff != 0.0) {
                    Text("Écart: ${if (diff > 0) "+" else ""}$diff", color = diffColor, style = MaterialTheme.typography.labelSmall)
                }
            }
            
            OutlinedTextField(
                value = physicalQty,
                onValueChange = onQtyChange,
                modifier = Modifier.width(100.dp),
                label = { Text("Réel") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                colors = CaisseTextFieldDefaults.outlinedTextFieldColors()
            )
        }
    }
}
