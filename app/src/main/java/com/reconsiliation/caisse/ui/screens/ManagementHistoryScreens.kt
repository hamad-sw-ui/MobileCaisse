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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.reconsiliation.caisse.data.local.entity.SessionEntity
import com.reconsiliation.caisse.ui.components.CaisseTextFieldDefaults
import com.reconsiliation.caisse.ui.navigation.Screen
import com.reconsiliation.caisse.ui.theme.GreenSuccess
import com.reconsiliation.caisse.ui.theme.Primary
import com.reconsiliation.caisse.ui.theme.RedError
import com.reconsiliation.caisse.ui.viewmodel.SessionAmounts
import com.reconsiliation.caisse.ui.viewmodel.MainViewModel
import com.reconsiliation.caisse.ui.viewmodel.InventoryViewModel
import com.reconsiliation.caisse.ui.viewmodel.SessionViewModel
import com.reconsiliation.caisse.ui.viewmodel.ExpenseViewModel
import com.reconsiliation.caisse.utils.FormatUtil
import androidx.compose.ui.res.stringResource
import com.reconsiliation.caisse.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClosureHistoryScreen(navController: NavController) {
    val sessionViewModel: SessionViewModel = hiltViewModel()
    val closures by sessionViewModel.allClosures.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_management_archives)) },
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
                Tab(selected = true, onClick = { }, text = { Text(stringResource(R.string.tab_closures)) })
                Tab(selected = false, onClick = { navController.navigate(Screen.SessionHistory.route) }, text = { Text(stringResource(R.string.tab_sessions)) })
                Tab(selected = false, onClick = { navController.navigate(Screen.AuditHistory.route) }, text = { Text(stringResource(R.string.tab_audits)) })
                Tab(selected = false, onClick = { navController.navigate(Screen.SupplyHistory.route) }, text = { Text(stringResource(R.string.tab_supplies)) })
            }

            if (closures.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.no_closure_archived), color = Color.Gray)
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(closures) { closure ->
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.closure_on_date, FormatUtil.formatDate(closure.date))) },
                            supportingContent = {
                                Text(stringResource(R.string.label_theoretical_actual, FormatUtil.formatCurrency(closure.theoreticalCash), FormatUtil.formatCurrency(closure.actualCash)))
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
    val sessionViewModel: SessionViewModel = hiltViewModel()
    val sessions by sessionViewModel.allSessions.collectAsState()
    
    var showCommissionDialog by remember { mutableStateOf<SessionEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_session_registry)) },
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
                Tab(selected = false, onClick = { navController.navigate(Screen.ClosureHistory.route) }, text = { Text(stringResource(R.string.tab_closures)) })
                Tab(selected = true, onClick = { }, text = { Text(stringResource(R.string.tab_sessions)) })
                Tab(selected = false, onClick = { navController.navigate(Screen.AuditHistory.route) }, text = { Text(stringResource(R.string.tab_audits)) })
                Tab(selected = false, onClick = { navController.navigate(Screen.SupplyHistory.route) }, text = { Text(stringResource(R.string.tab_supplies)) })
            }

            if (sessions.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.no_session_found), color = Color.Gray)
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(sessions) { session ->
                        ListItem(
                            headlineContent = { Text(session.sellerName) },
                            supportingContent = {
                                val start = FormatUtil.formatDate(session.startTime)
                                val end = session.endTime?.let { FormatUtil.formatDate(it) } ?: stringResource(R.string.status_in_progress)
                                Text(stringResource(R.string.label_start_end_date, start, end))
                            },
                            trailingContent = {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(stringResource(R.string.label_opening_balance, FormatUtil.formatCurrency(session.openingBalance)), style = MaterialTheme.typography.labelSmall)
                                    session.closingBalance?.let {
                                        Text(stringResource(R.string.label_closing_balance, FormatUtil.formatCurrency(it)), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    }
                                    SessionAmounts.cashGap(session.closingBalance, session.expectedBalance)?.let { gap ->
                                        Text(
                                            stringResource(R.string.label_session_gap, FormatUtil.formatCurrency(gap)),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = when {
                                                gap < 0 -> RedError
                                                gap > 0 -> GreenSuccess
                                                else -> Color.Gray
                                            }
                                        )
                                    }
                                    if (session.endTime != null) {
                                        IconButton(onClick = { showCommissionDialog = session }, modifier = Modifier.size(24.dp)) {
                                            Icon(Icons.Default.Calculate, stringResource(R.string.action_calculate), tint = Primary, modifier = Modifier.size(16.dp))
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
    val expenseViewModel: ExpenseViewModel = hiltViewModel()
    val context = androidx.compose.ui.platform.LocalContext.current
    var rate by remember { mutableStateOf("2") }
    val revenue = (session.closingBalance ?: 0.0) - session.openingBalance
    val commission = (revenue * (rate.toDoubleOrNull() ?: 0.0)) / 100.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_commission_title, session.sellerName)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.label_revenue_amount, FormatUtil.formatCurrency(revenue)))
                OutlinedTextField(
                    value = rate,
                    onValueChange = { rate = it },
                    label = { Text(stringResource(R.string.label_commission_rate)) },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    colors = CaisseTextFieldDefaults.outlinedTextFieldColors()
                )
                Text(
                    stringResource(R.string.label_amount_to_pay, FormatUtil.formatCurrency(commission)),
                    fontWeight = FontWeight.Bold,
                    color = Primary,
                    style = MaterialTheme.typography.titleMedium
                )
                
                Button(
                    onClick = {
                        expenseViewModel.addExpense(
                            label = context.getString(
                                R.string.label_commission,
                                session.sellerName,
                                FormatUtil.formatDate(session.startTime)
                            ),
                            amount = commission,
                            category = context.getString(R.string.category_salaries)
                        )
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = com.reconsiliation.caisse.ui.theme.GreenSuccess)
                ) {
                    Text(stringResource(R.string.action_record_as_paid))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditHistoryScreen(navController: NavController) {
    val viewModel: InventoryViewModel = hiltViewModel()
    val audits by viewModel.allAudits.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_audit_history)) },
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
                Tab(selected = false, onClick = { navController.navigate(Screen.ClosureHistory.route) }, text = { Text(stringResource(R.string.tab_closures)) })
                Tab(selected = false, onClick = { navController.navigate(Screen.SessionHistory.route) }, text = { Text(stringResource(R.string.tab_sessions)) })
                Tab(selected = true, onClick = { }, text = { Text(stringResource(R.string.tab_audits)) })
                Tab(selected = false, onClick = { navController.navigate(Screen.SupplyHistory.route) }, text = { Text(stringResource(R.string.tab_supplies)) })
            }

            if (audits.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.no_audit_recorded), color = Color.Gray)
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(audits) { data ->
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.audit_on_date, FormatUtil.formatDate(data.audit.date))) },
                            supportingContent = { Text(data.audit.note ?: stringResource(R.string.label_no_note)) },
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
    val viewModel: InventoryViewModel = hiltViewModel()
    val supplies by viewModel.allSupplies.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_supply_history)) },
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
                Tab(selected = false, onClick = { navController.navigate(Screen.ClosureHistory.route) }, text = { Text(stringResource(R.string.tab_closures)) })
                Tab(selected = false, onClick = { navController.navigate(Screen.SessionHistory.route) }, text = { Text(stringResource(R.string.tab_sessions)) })
                Tab(selected = false, onClick = { navController.navigate(Screen.AuditHistory.route) }, text = { Text(stringResource(R.string.tab_audits)) })
                Tab(selected = true, onClick = { }, text = { Text(stringResource(R.string.tab_supplies)) })
            }

            if (supplies.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.no_supply_recorded), color = Color.Gray)
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(supplies) { supply ->
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.label_inflow_headline, supply.productName, supply.quantity.toString())) },
                            supportingContent = {
                                Text(stringResource(R.string.label_supplier_date, supply.supplierName ?: stringResource(R.string.label_unknown), FormatUtil.formatDate(supply.date)))
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
