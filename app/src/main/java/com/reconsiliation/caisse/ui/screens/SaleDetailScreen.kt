package com.reconsiliation.caisse.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Delete
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.reconsiliation.caisse.data.local.dao.VenteWithItems
import com.reconsiliation.caisse.ui.components.CaisseTextFieldDefaults
import com.reconsiliation.caisse.ui.theme.Primary
import com.reconsiliation.caisse.ui.viewmodel.MainViewModel
import com.reconsiliation.caisse.utils.FormatUtil
import android.content.Intent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaleDetailScreen(navController: NavController, venteId: Long) {
    val context = LocalContext.current
    val viewModel: MainViewModel = viewModel()
    val boutique by viewModel.boutique.collectAsState()
    val operatorName = boutique?.operator ?: "MoMo"
    val currency by viewModel.currency.collectAsState()
    val userRole by viewModel.userRole.collectAsState()
    val scope = rememberCoroutineScope()
    var venteWithItems by remember { mutableStateOf<VenteWithItems?>(null) }
    
    var showCancelDialog by remember { mutableStateOf(false) }
    var showReturnDialog by remember { mutableStateOf<com.reconsiliation.caisse.data.local.entity.VenteItemEntity?>(null) }
    var pendingReturnItem by remember { mutableStateOf<com.reconsiliation.caisse.data.local.entity.VenteItemEntity?>(null) }
    var showPinDialog by remember { mutableStateOf(false) }
    var pinAction by remember { mutableStateOf("") } // "CANCEL" or "RETURN"
    var pinInput by remember { mutableStateOf("") }

    LaunchedEffect(venteId) {
        venteWithItems = viewModel.getVenteWithItems(venteId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Détail de la Vente") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        boutique?.printerAddress?.let { address ->
                            viewModel.printTicket(venteId, address)
                        } ?: run {
                            // Show message to configure printer
                        }
                    }) {
                        Icon(Icons.Default.Print, "Imprimer")
                    }
                    IconButton(onClick = {
                        venteWithItems?.let { data ->
                            boutique?.let { b ->
                                val text = FormatUtil.generateReceiptText(
                                    boutique = b,
                                    vente = data.vente,
                                    items = data.items,
                                    currency = currency
                                )
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, text)
                                }
                                context.startActivity(Intent.createChooser(intent, "Partager le reçu"))
                            }
                        }
                    }) {
                        Icon(Icons.Default.Share, "Partager le reçu")
                    }
                }
            )
        }
    ) { padding ->
        venteWithItems?.let { data ->
            val vente = data.vente
            Column(modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Chiffre d'Affaires", style = MaterialTheme.typography.labelMedium)
                        Text(FormatUtil.formatCurrency(vente.amount), style = MaterialTheme.typography.headlineMedium, color = Primary, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Date: ${FormatUtil.formatDate(vente.date)}", style = MaterialTheme.typography.bodySmall)
                        val methodDisplay = if (vente.paymentMethod == "MOMO") operatorName else vente.paymentMethod
                        Text("Mode: $methodDisplay", style = MaterialTheme.typography.bodySmall)
                        if (vente.discount > 0) Text("Remise: -${FormatUtil.formatCurrency(vente.discount, currency)}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        if (vente.taxAmount > 0) Text("Taxe: ${FormatUtil.formatCurrency(vente.taxAmount, currency)}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        if (vente.fees > 0) Text("Frais: ${FormatUtil.formatCurrency(vente.fees, currency)}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        Text("Statut: ${vente.status}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                Text("Articles achetés", style = MaterialTheme.typography.titleMedium, color = Primary)
                Spacer(modifier = Modifier.height(8.dp))

                if (data.items.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("Aucun détail d'article disponible", color = Color.Gray)
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(data.items) { item ->
                            ListItem(
                                headlineContent = { Text(item.productName) },
                                supportingContent = { Text("${item.quantity.toInt()} x ${item.unitPrice} $currency") },
                                trailingContent = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(FormatUtil.formatCurrency(item.quantity * item.unitPrice, currency), fontWeight = FontWeight.Bold)
                                        if (vente.status != "CANCELLED" && !vente.isLocked) {
                                            IconButton(onClick = {
                                                if (userRole == "MANAGER") showReturnDialog = item
                                                else {
                                                    pendingReturnItem = item
                                                    showPinDialog = true
                                                    pinAction = "RETURN"
                                                }
                                            }) {
                                                Icon(Icons.Default.Delete, "Retourner", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                            }
                                        }
                                    }
                                }
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }

                if (vente.transactionId != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, null, tint = Primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Réf $operatorName: ${vente.transactionId}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                if (vente.status != "CANCELLED" && !vente.isLocked) {
                    Spacer(modifier = Modifier.weight(1f))
                    Button(
                        onClick = {
                            if (userRole == "MANAGER") showCancelDialog = true
                            else {
                                showPinDialog = true
                                pinAction = "CANCEL"
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Delete, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Annuler la Vente")
                    }
                }
            }
        } ?: Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        if (showCancelDialog) {
            AlertDialog(
                onDismissRequest = { showCancelDialog = false },
                title = { Text("Confirmer l'annulation ?") },
                text = { Text("Les articles seront remis en stock et le montant sera déduit du chiffre d'affaires.") },
                confirmButton = {
                    TextButton(onClick = {
                        venteWithItems?.let { viewModel.cancelVente(it.vente) }
                        showCancelDialog = false
                        navController.popBackStack()
                    }) { Text("Confirmer", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { showCancelDialog = false }) { Text("Annuler") }
                }
            )
        }

        if (showReturnDialog != null) {
            val item = showReturnDialog!!
            var returnQty by remember { mutableStateOf(item.quantity.toString()) }
            var refundMethod by remember { mutableStateOf("AVOIR") }
            
            AlertDialog(
                onDismissRequest = { showReturnDialog = null },
                title = { Text("Retourner ${item.productName}?") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Quantité à retourner (Max: ${item.quantity})")
                        OutlinedTextField(
                            value = returnQty,
                            onValueChange = { returnQty = it },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                            colors = CaisseTextFieldDefaults.outlinedTextFieldColors()
                        )
                        
                        Text("Mode de remboursement")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = refundMethod == "AVOIR", onClick = { refundMethod = "AVOIR" }, label = { Text("Avoir (Crédit)") })
                            FilterChip(selected = refundMethod == "CASH", onClick = { refundMethod = "CASH" }, label = { Text("Espèces") })
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val qty = returnQty.toDoubleOrNull() ?: 0.0
                        if (qty > 0 && qty <= item.quantity) {
                            viewModel.returnVenteItem(venteId, item.productId, qty, qty * item.unitPrice, refundMethod)
                            showReturnDialog = null
                            navController.popBackStack()
                        }
                    }) { Text("Confirmer le Retour", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { showReturnDialog = null }) { Text("Annuler") }
                }
            )
        }

        if (showPinDialog) {
            AlertDialog(
                onDismissRequest = { showPinDialog = false },
                title = { Text("Code PIN Manager requis") },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        var isError by remember { mutableStateOf(false) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 16.dp)) {
                            repeat(4) { index ->
                                val filled = pinInput.length > index
                                Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(if (isError) MaterialTheme.colorScheme.error else if (filled) Primary else Color.LightGray))
                            }
                        }
                        com.reconsiliation.caisse.ui.components.NumericKeypad(
                            onNumberClick = {
                                if (pinInput.length < 4) {
                                    isError = false
                                    pinInput += it
                                    if (pinInput.length == 4) {
                                        scope.launch {
                                            val result = viewModel.checkPin(pinInput)
                                            if (result?.first == "MANAGER") {
                                                showPinDialog = false
                                                if (pinAction == "CANCEL") showCancelDialog = true
                                                if (pinAction == "RETURN") showReturnDialog = pendingReturnItem
                                                pinInput = ""
                                            } else {
                                                pinInput = ""
                                                isError = true
                                            }
                                        }
                                    }
                                }
                            },
                            onDeleteClick = {
                                isError = false
                                if (pinInput.isNotEmpty()) pinInput = pinInput.dropLast(1)
                            }
                        )
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { showPinDialog = false }) { Text("Fermer") } }
            )
        }
    }
}
