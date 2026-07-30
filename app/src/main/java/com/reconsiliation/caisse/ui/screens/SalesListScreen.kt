package com.reconsiliation.caisse.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pending
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import androidx.navigation.NavController
import androidx.compose.ui.res.stringResource
import com.reconsiliation.caisse.R
import com.reconsiliation.caisse.data.local.entity.StockEntity
import com.reconsiliation.caisse.data.local.entity.VenteEntity
import com.reconsiliation.caisse.ui.navigation.Screen
import com.reconsiliation.caisse.ui.theme.GreenSuccess
import com.reconsiliation.caisse.ui.theme.OrangeWarning
import com.reconsiliation.caisse.ui.theme.RedError
import com.reconsiliation.caisse.ui.viewmodel.MainViewModel
import com.reconsiliation.caisse.utils.FormatUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesListScreen(navController: NavController) {
    val viewModel: MainViewModel = viewModel()
    val boutique by viewModel.boutique.collectAsState()
    val operatorName = boutique?.operator ?: "MoMo"
    val ventes by viewModel.selectedMonthVentes.collectAsState()
    val stockItems by viewModel.allStock.collectAsState()
    val isManager = viewModel.isManager()
    
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    
    var selectedDate by remember { mutableStateOf(java.util.Date()) }

    LaunchedEffect(selectedDate) {
        viewModel.loadVentesForMonth(selectedDate)
    }
    
    var venteToDelete by remember { mutableStateOf<VenteEntity?>(null) }
    var venteToAdoption by remember { mutableStateOf<VenteEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_history)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Month Selector (Point 1)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = {
                    val cal = java.util.Calendar.getInstance()
                    cal.time = selectedDate
                    cal.add(java.util.Calendar.MONTH, -1)
                    selectedDate = cal.time
                }) {
                    Text("< Précédent")
                }
                
                val sdf = java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.FRANCE)
                Text(sdf.format(selectedDate).replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.Bold)

                TextButton(onClick = {
                    val cal = java.util.Calendar.getInstance()
                    cal.time = selectedDate
                    cal.add(java.util.Calendar.MONTH, 1)
                    selectedDate = cal.time
                }) {
                    Text("Suivant >")
                }
            }

            if (ventes.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Aucune vente pour ce mois")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(ventes) { vente ->
                        SaleItem(
                            vente = vente,
                            operatorName = operatorName,
                            isManager = isManager,
                            onClick = { navController.navigate(Screen.SaleDetail.createRoute(vente.id)) },
                            onDelete = { venteToDelete = vente },
                            onAdopt = { venteToAdoption = vente },
                            onForceConfirm = {
                                // Point 1: Force Confirm logic
                                val updated = it.copy(status = "CONFIRMED", reconciliationStatus = "OK", description = it.description + " (Manuel)")
                                viewModel.updateVente(updated)
                            }
                        )
                    }
                }
            }
        }

        if (venteToDelete != null) {
            AlertDialog(
                onDismissRequest = { venteToDelete = null },
                title = { Text("Supprimer la vente ?") },
                text = { Text("Cette action est irréversible.") },
                confirmButton = {
                    Button(onClick = {
                        viewModel.deleteVente(venteToDelete!!)
                        venteToDelete = null
                        scope.launch { snackbarHostState.showSnackbar("Vente supprimée") }
                    }, colors = ButtonDefaults.buttonColors(containerColor = RedError)) {
                        Text(stringResource(R.string.action_delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { venteToDelete = null }) { Text("Annuler") }
                }
            )
        }

        if (venteToAdoption != null) {
            AdoptionDialog(
                vente = venteToAdoption!!,
                operatorName = operatorName,
                stockItems = stockItems,
                onDismiss = { venteToAdoption = null },
                onConfirm = { product ->
                    viewModel.confirmOrphanSale(venteToAdoption!!, product)
                    venteToAdoption = null
                }
            )
        }
    }
}

@Composable
fun SaleItem(vente: VenteEntity, operatorName: String, isManager: Boolean, onClick: () -> Unit, onDelete: () -> Unit, onAdopt: () -> Unit, onForceConfirm: (VenteEntity) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(vente.description.ifEmpty { "Vente sans titre" }, style = MaterialTheme.typography.titleMedium)
                Text(FormatUtil.formatDate(vente.date), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                val methodDisplay = if (vente.paymentMethod == "MOMO") operatorName else vente.paymentMethod
                Text("Via: $methodDisplay", style = MaterialTheme.typography.labelMedium)
                
                if (vente.reconciliationStatus == "ORPHAN") {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onAdopt, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp), colors = ButtonDefaults.buttonColors(containerColor = OrangeWarning)) {
                        Icon(Icons.Default.ShoppingBag, null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Attribuer un produit", fontSize = 10.sp)
                    }
                }

                if (vente.status == "PENDING" && (vente.paymentMethod == "MOMO" || vente.paymentMethod == "MIXED") && isManager) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = { onForceConfirm(vente) }, colors = ButtonDefaults.textButtonColors(contentColor = GreenSuccess)) {
                        Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Forcer la confirmation", fontSize = 10.sp)
                    }
                }
                
                if (vente.status == "PENDING_DELETION" && isManager) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onDelete, colors = ButtonDefaults.buttonColors(containerColor = RedError), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                        Icon(Icons.Default.Delete, null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Valider la suppression", fontSize = 10.sp)
                    }
                }
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Text(FormatUtil.formatCurrency(vente.amount), style = MaterialTheme.typography.titleLarge)
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val statusColor = when(vente.status) {
                        "CONFIRMED" -> GreenSuccess
                        "PENDING" -> OrangeWarning
                        "PENDING_DELETION" -> RedError
                        else -> RedError
                    }
                    Icon(
                        when(vente.status) {
                            "CONFIRMED" -> Icons.Default.CheckCircle
                            "PENDING", "PENDING_DELETION" -> Icons.Default.Pending
                            else -> Icons.Default.Error
                        },
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (vente.status == "PENDING_DELETION") "SUPPRESSION..." else vente.status, color = statusColor, style = MaterialTheme.typography.labelSmall)
                }
                
                if (!vente.isLocked && isManager) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp))
                    }
                } else if (vente.isLocked) {
                    Icon(Icons.Default.Lock, null, tint = Color.LightGray, modifier = Modifier.size(14.dp).padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
fun AdoptionDialog(vente: VenteEntity, operatorName: String, stockItems: List<StockEntity>, onDismiss: () -> Unit, onConfirm: (StockEntity) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Attribuer un produit") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Ce paiement de ${FormatUtil.formatCurrency(vente.amount)} a été reçu par $operatorName mais n'est lié à aucune vente.")
                Text("Sélectionnez le produit vendu :", fontWeight = FontWeight.Bold)
                
                LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                    items(stockItems) { item ->
                        ListItem(
                            headlineContent = { Text(item.productName) },
                            supportingContent = { Text("${item.unitPrice} FCFA") },
                            modifier = Modifier.fillMaxWidth().clickable { onConfirm(item) }
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}
