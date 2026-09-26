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
import androidx.hilt.navigation.compose.hiltViewModel
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
import com.reconsiliation.caisse.ui.viewmodel.InventoryViewModel
import com.reconsiliation.caisse.ui.viewmodel.ReportsViewModel
import com.reconsiliation.caisse.utils.FormatUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesListScreen(navController: NavController) {
    val mainViewModel: MainViewModel = hiltViewModel()
    val inventoryViewModel: InventoryViewModel = hiltViewModel()
    val viewModel: ReportsViewModel = hiltViewModel()
    
    val boutique by mainViewModel.boutique.collectAsState()
    val operatorName = boutique?.operator ?: "MoMo"
    val ventes by viewModel.selectedMonthVentes.collectAsState()
    val stockItems by inventoryViewModel.allStock.collectAsState()
    val isManager = mainViewModel.isManager()
    
    val context = androidx.compose.ui.platform.LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val uiError by mainViewModel.uiError.collectAsState()
    val actionErrorMessage = stringResource(R.string.error_generic)
    
    var selectedDate by remember { mutableStateOf(java.util.Date()) }

    LaunchedEffect(selectedDate) {
        viewModel.loadVentesForMonth(selectedDate)
    }
    LaunchedEffect(uiError) {
        if (uiError != null) {
            snackbarHostState.showSnackbar(actionErrorMessage)
            mainViewModel.clearError()
        }
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
                    Text(stringResource(R.string.action_previous))
                }
                
                val sdf = java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault())
                Text(sdf.format(selectedDate).replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.Bold)

                TextButton(onClick = {
                    val cal = java.util.Calendar.getInstance()
                    cal.time = selectedDate
                    cal.add(java.util.Calendar.MONTH, 1)
                    selectedDate = cal.time
                }) {
                    Text(stringResource(R.string.action_next_arrow))
                }
            }

            if (ventes.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.error_no_sale_period))
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
                                val updated = it.copy(status = "CONFIRMED", reconciliationStatus = "OK", description = it.description + context.getString(R.string.suffix_manual))
                                mainViewModel.updateVente(updated)
                            }
                        )
                    }
                }
            }
        }

        if (venteToDelete != null) {
            AlertDialog(
                onDismissRequest = { venteToDelete = null },
                title = { Text(stringResource(R.string.dialog_delete_sale_title)) },
                text = { Text(stringResource(R.string.dialog_delete_sale_desc)) },
                confirmButton = {
                    Button(onClick = {
                        mainViewModel.deleteVente(venteToDelete!!)
                        venteToDelete = null
                    }, colors = ButtonDefaults.buttonColors(containerColor = RedError)) {
                        Text(stringResource(R.string.action_delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { venteToDelete = null }) { Text(stringResource(R.string.action_cancel)) }
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
                    mainViewModel.confirmOrphanSale(venteToAdoption!!, product)
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
                Text(vente.description.ifEmpty { stringResource(R.string.label_sale_untitled) }, style = MaterialTheme.typography.titleMedium)
                Text(FormatUtil.formatDate(vente.date), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                val methodDisplay = if (vente.paymentMethod == "MOMO") operatorName else vente.paymentMethod
                Text(stringResource(R.string.label_via_colon, methodDisplay), style = MaterialTheme.typography.labelMedium)
                
                if (vente.reconciliationStatus == "ORPHAN") {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onAdopt, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp), colors = ButtonDefaults.buttonColors(containerColor = OrangeWarning)) {
                        Icon(Icons.Default.ShoppingBag, null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.action_assign_product), fontSize = 10.sp)
                    }
                }

                if (vente.status == "PENDING" && (vente.paymentMethod == "MOMO" || vente.paymentMethod == "MIXED") && isManager) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = { onForceConfirm(vente) }, colors = ButtonDefaults.textButtonColors(contentColor = GreenSuccess)) {
                        Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.action_force_confirm), fontSize = 10.sp)
                    }
                }
                
                if (vente.status == "PENDING_DELETION" && isManager) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onDelete, colors = ButtonDefaults.buttonColors(containerColor = RedError), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                        Icon(Icons.Default.Delete, null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.action_validate_delete), fontSize = 10.sp)
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
                    val statusLabel = when (vente.status) {
                        "CONFIRMED" -> stringResource(R.string.status_confirmed)
                        "PENDING" -> stringResource(R.string.status_pending)
                        "CANCELLED" -> stringResource(R.string.status_cancelled)
                        "PENDING_DELETION" -> stringResource(R.string.status_deleting)
                        else -> vente.status
                    }
                    Text(statusLabel, color = statusColor, style = MaterialTheme.typography.labelSmall)
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
        title = { Text(stringResource(R.string.action_assign_product)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.orphan_sale_desc, FormatUtil.formatCurrency(vente.amount), operatorName))
                Text(stringResource(R.string.dialog_select_product_sold), fontWeight = FontWeight.Bold)
                
                LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                    items(stockItems) { item ->
                        ListItem(
                            headlineContent = { Text(item.productName) },
                            supportingContent = { Text(stringResource(R.string.label_qty_unit_price, 1, item.unitPrice.toString(), stringResource(R.string.label_unit_fcfa).trim())) },
                            modifier = Modifier.fillMaxWidth().clickable { onConfirm(item) }
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}
