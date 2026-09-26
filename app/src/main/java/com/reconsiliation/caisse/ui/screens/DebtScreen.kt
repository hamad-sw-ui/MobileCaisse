package com.reconsiliation.caisse.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.reconsiliation.caisse.data.local.entity.CustomerEntity
import com.reconsiliation.caisse.ui.components.CaisseTextFieldDefaults
import com.reconsiliation.caisse.ui.navigation.Screen
import com.reconsiliation.caisse.ui.theme.OrangeWarning
import com.reconsiliation.caisse.ui.theme.Primary
import com.reconsiliation.caisse.ui.theme.RedError
import com.reconsiliation.caisse.ui.viewmodel.MainViewModel
import com.reconsiliation.caisse.ui.viewmodel.CustomerViewModel
import com.reconsiliation.caisse.ui.state.CustomerUiState
import com.reconsiliation.caisse.utils.FormatUtil
import androidx.compose.ui.res.stringResource
import com.reconsiliation.caisse.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebtScreen(navController: NavController) {
    val context = LocalContext.current
    val mainViewModel: MainViewModel = hiltViewModel()
    val viewModel: CustomerViewModel = hiltViewModel()
    val boutique by mainViewModel.boutique.collectAsState()
    val operatorName = boutique?.operator ?: "MoMo"
    val debtors by viewModel.debtors.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val errorMessage = stringResource(R.string.error_debt_update)

    LaunchedEffect(uiState) {
        when (uiState) {
            is CustomerUiState.Error -> {
                snackbarHostState.showSnackbar(errorMessage)
                viewModel.clearUiState()
            }
            is CustomerUiState.Success -> viewModel.clearUiState()
            else -> Unit
        }
    }
    
    var selectedDebtor by remember { mutableStateOf<CustomerEntity?>(null) }
    var showRepayDialog by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_debts)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        debtors.forEach { viewModel.sendDebtReminder(it, boutique?.name, boutique?.currency ?: "FCFA") }
                    }) {
                        Icon(Icons.Default.NotificationsActive, stringResource(R.string.action_remind_all))
                    }
                }
            )
        }
    ) { padding ->
        if (debtors.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.no_debt_found), color = Color.Gray)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(debtors) { debtor ->
                    DebtorCard(
                        debtor = debtor,
                        onRepay = { selectedDebtor = debtor; showRepayDialog = true },
                        onRemind = {
                            viewModel.sendDebtReminder(debtor, boutique?.name, boutique?.currency ?: "FCFA")
                        },
                        onViewProfile = { navController.navigate(Screen.CustomerProfile.createRoute(debtor.id)) }
                    )
                }
            }
        }

        if (showRepayDialog && selectedDebtor != null) {
            RepayDialog(
                debtor = selectedDebtor!!,
                operatorName = operatorName,
                onDismiss = { showRepayDialog = false },
                onConfirm = { amount, method ->
                    viewModel.addRepayment(selectedDebtor!!.id, amount, method, mainViewModel.userRole.value)
                    showRepayDialog = false
                }
            )
        }
    }
}

@Composable
fun DebtorCard(debtor: CustomerEntity, onRepay: () -> Unit, onRemind: () -> Unit, onViewProfile: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(debtor.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(debtor.phone, style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(4.dp))
                Text(FormatUtil.formatCurrency(debtor.totalDebt), color = RedError, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            
            Row {
                IconButton(onClick = onViewProfile) {
                    Icon(Icons.Default.Person, contentDescription = stringResource(R.string.action_profile), tint = Color.Gray)
                }
                IconButton(onClick = onRemind) {
                    Icon(Icons.Default.NotificationsActive, contentDescription = stringResource(R.string.action_reminder), tint = OrangeWarning)
                }
                IconButton(onClick = onRepay) {
                    Icon(Icons.Default.Payments, contentDescription = stringResource(R.string.action_repay), tint = Primary)
                }
            }
        }
    }
}

@Composable
fun RepayDialog(debtor: CustomerEntity, operatorName: String, onDismiss: () -> Unit, onConfirm: (Double, String) -> Unit) {
    var amount by remember { mutableStateOf("") }
    var method by remember { mutableStateOf("CASH") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_repayment_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.label_customer_name, debtor.name))
                Text(stringResource(R.string.label_debt_balance, FormatUtil.formatCurrency(debtor.totalDebt)))
                
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text(stringResource(R.string.label_repayment_amount)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    colors = CaisseTextFieldDefaults.outlinedTextFieldColors()
                )
                
                Text(stringResource(R.string.label_payment_method))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = method == "CASH", onClick = { method = "CASH" }, label = { Text(stringResource(R.string.method_cash)) })
                    FilterChip(selected = method == "MOMO", onClick = { method = "MOMO" }, label = { Text(operatorName) })
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val amt = amount.toDoubleOrNull() ?: 0.0
                if (amt > 0) onConfirm(amt, method)
            }) { Text(stringResource(R.string.action_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
