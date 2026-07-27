package com.reconsiliation.caisse.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.reconsiliation.caisse.data.local.dao.AuditWithItems
import com.reconsiliation.caisse.data.local.entity.ClosureEntity
import com.reconsiliation.caisse.data.local.entity.SessionEntity
import com.reconsiliation.caisse.data.local.entity.SupplyEntity
import com.reconsiliation.caisse.ui.components.CaisseTextFieldDefaults
import com.reconsiliation.caisse.ui.navigation.Screen
import com.reconsiliation.caisse.ui.theme.Primary
import com.reconsiliation.caisse.ui.theme.RedError
import com.reconsiliation.caisse.ui.viewmodel.MainViewModel
import com.reconsiliation.caisse.utils.FormatUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClosureHistoryScreen(navController: NavController) {
    val viewModel: MainViewModel = viewModel()
    val closures by viewModel.allClosures.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Archives de Gestion") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ScrollableTabRow(
                selectedTabIndex = 0,
                edgePadding = 16.dp,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = Primary
            ) {
                Tab(selected = true, onClick = { }, text = { Text("Clôtures") })
                Tab(selected = false, onClick = { navController.navigate(Screen.SessionHistory.route) }, text = { Text("Sessions") })
                Tab(selected = false, onClick = { navController.navigate(Screen.AuditHistory.route) }, text = { Text("Audits") })
                Tab(selected = false, onClick = { navController.navigate(Screen.SupplyHistory.route) }, text = { Text("Arrivages") })
            }

            if (closures.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Aucune clôture archivée", color = Color.Gray)
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(closures) { closure ->
                        ListItem(
                            headlineContent = { Text("Clôture du ${FormatUtil.formatDate(closure.date)}") },
                            supportingContent = {
                                Text("Système: ${FormatUtil.formatCurrency(closure.theoreticalCash)}\nRéel: ${FormatUtil.formatCurrency(closure.actualCash)}")
                            },
                            trailingContent = {
                                val diff = closure.actualCash - closure.theoreticalCash
                                Text(
                                    FormatUtil.formatCurrency(diff),
                                    color = if (diff < 0.0) RedError else com.reconsiliation.caisse.ui.theme.GreenSuccess,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionHistoryScreen(navController: NavController) {
    val viewModel: MainViewModel = viewModel()
    val sessions by viewModel.allSessions.collectAsState()
    
    var showCommissionDialog by remember { mutableStateOf<SessionEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Registre des Sessions") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ScrollableTabRow(selectedTabIndex = 1, edgePadding = 16.dp) {
                Tab(selected = false, onClick = { navController.navigate(Screen.ClosureHistory.route) }, text = { Text("Clôtures") })
                Tab(selected = true, onClick = { }, text = { Text("Sessions") })
                Tab(selected = false, onClick = { navController.navigate(Screen.AuditHistory.route) }, text = { Text("Audits") })
                Tab(selected = false, onClick = { navController.navigate(Screen.SupplyHistory.route) }, text = { Text("Arrivages") })
            }

            if (sessions.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Aucune session trouvée", color = Color.Gray)
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(sessions) { session ->
                        ListItem(
                            headlineContent = { Text(session.sellerName) },
                            supportingContent = {
                                val start = FormatUtil.formatDate(session.startTime)
                                val end = session.endTime?.let { FormatUtil.formatDate(it) } ?: "En cours"
                                Text("Début: $start\nFin: $end")
                            },
                            trailingContent = {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Fond: ${FormatUtil.formatCurrency(session.openingBalance)}", style = MaterialTheme.typography.labelSmall)
                                    session.closingBalance?.let {
                                        Text("Fin: ${FormatUtil.formatCurrency(it)}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    }
                                    if (session.endTime != null) {
                                        IconButton(onClick = { showCommissionDialog = session }, modifier = Modifier.size(24.dp)) {
                                            Icon(Icons.Default.Calculate, "Commissions", tint = Primary, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
        
        if (showCommissionDialog != null) {
            CommissionDialog(
                session = showCommissionDialog!!,
                onDismiss = { showCommissionDialog = null }
            )
        }
    }
}

@Composable
fun CommissionDialog(session: SessionEntity, onDismiss: () -> Unit) {
    val viewModel: MainViewModel = viewModel()
    var rate by remember { mutableStateOf("2") }
    val revenue = (session.closingBalance ?: 0.0) - session.openingBalance
    val commission = (revenue * (rate.toDoubleOrNull() ?: 0.0)) / 100.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Calcul Commission - ${session.sellerName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Chiffre d'affaires : ${FormatUtil.formatCurrency(revenue)}")
                OutlinedTextField(
                    value = rate,
                    onValueChange = { rate = it },
                    label = { Text("Taux de commission (%)") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    colors = CaisseTextFieldDefaults.outlinedTextFieldColors()
                )
                Text(
                    "Montant à payer : ${FormatUtil.formatCurrency(commission)}",
                    fontWeight = FontWeight.Bold,
                    color = Primary,
                    style = MaterialTheme.typography.titleMedium
                )
                
                Button(
                    onClick = {
                        viewModel.addExpense(com.reconsiliation.caisse.data.local.entity.ExpenseEntity(
                            label = "Commission : ${session.sellerName} (${FormatUtil.formatDate(session.startTime)})",
                            amount = commission,
                            date = java.util.Date(),
                            category = "Salaires"
                        ))
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = com.reconsiliation.caisse.ui.theme.GreenSuccess)
                ) {
                    Text("Enregistrer comme payé (Dépense)")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditHistoryScreen(navController: NavController) {
    val viewModel: MainViewModel = viewModel()
    val audits by viewModel.allAudits.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Historique des Audits") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ScrollableTabRow(selectedTabIndex = 2, edgePadding = 16.dp) {
                Tab(selected = false, onClick = { navController.navigate(Screen.ClosureHistory.route) }, text = { Text("Clôtures") })
                Tab(selected = false, onClick = { navController.navigate(Screen.SessionHistory.route) }, text = { Text("Sessions") })
                Tab(selected = true, onClick = { }, text = { Text("Audits") })
                Tab(selected = false, onClick = { navController.navigate(Screen.SupplyHistory.route) }, text = { Text("Arrivages") })
            }

            if (audits.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Aucun audit enregistré", color = Color.Gray)
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(audits) { data ->
                        ListItem(
                            headlineContent = { Text("Audit du ${FormatUtil.formatDate(data.audit.date)}") },
                            supportingContent = { Text(data.audit.note ?: "Pas de note") },
                            trailingContent = {
                                Text(
                                    FormatUtil.formatCurrency(data.audit.totalDiscrepancy),
                                    color = if (data.audit.totalDiscrepancy < 0.0) RedError else Primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupplyHistoryScreen(navController: NavController) {
    val viewModel: MainViewModel = viewModel()
    val supplies by viewModel.allSupplies.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Historique des Arrivages") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ScrollableTabRow(selectedTabIndex = 3, edgePadding = 16.dp) {
                Tab(selected = false, onClick = { navController.navigate(Screen.ClosureHistory.route) }, text = { Text("Clôtures") })
                Tab(selected = false, onClick = { navController.navigate(Screen.SessionHistory.route) }, text = { Text("Sessions") })
                Tab(selected = false, onClick = { navController.navigate(Screen.AuditHistory.route) }, text = { Text("Audits") })
                Tab(selected = true, onClick = { }, text = { Text("Arrivages") })
            }

            if (supplies.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Aucun arrivage enregistré", color = Color.Gray)
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(supplies) { supply ->
                        ListItem(
                            headlineContent = { Text("${supply.productName} (+${supply.quantity})") },
                            supportingContent = {
                                Text("Fournisseur: ${supply.supplierName ?: "Inconnu"}\nDate: ${FormatUtil.formatDate(supply.date)}")
                            },
                            trailingContent = {
                                Text(FormatUtil.formatCurrency(supply.quantity * supply.purchasePrice), fontWeight = FontWeight.Bold)
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
