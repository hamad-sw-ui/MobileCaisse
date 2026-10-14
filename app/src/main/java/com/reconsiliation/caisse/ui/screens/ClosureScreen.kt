package com.reconsiliation.caisse.ui.screens

import android.text.format.DateUtils
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.reconsiliation.caisse.ui.components.BackupPasswordDialog
import com.reconsiliation.caisse.ui.components.CaisseTextFieldDefaults
import com.reconsiliation.caisse.ui.navigation.Screen
import com.reconsiliation.caisse.ui.state.SessionUiState
import com.reconsiliation.caisse.ui.theme.GreenSuccess
import com.reconsiliation.caisse.ui.theme.Primary
import com.reconsiliation.caisse.ui.theme.RedError
import com.reconsiliation.caisse.ui.viewmodel.*
import com.reconsiliation.caisse.utils.FormatUtil
import java.util.*
import androidx.compose.ui.res.stringResource
import com.reconsiliation.caisse.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClosureScreen(navController: NavController) {
    val context = LocalContext.current
    val viewModel: MainViewModel = hiltViewModel()
    val sessionViewModel: SessionViewModel = hiltViewModel()
    val expenseViewModel: ExpenseViewModel = hiltViewModel()
    val reportsViewModel: ReportsViewModel = hiltViewModel()
    val backupViewModel: BackupViewModel = hiltViewModel()
    
    var showBackupPasswordDialog by remember { mutableStateOf(false) }
    val boutique by viewModel.boutique.collectAsState()
    val operatorName = boutique?.operator ?: "MoMo"
    val ventes by viewModel.allVentes.collectAsState()
    val allVentesWithItems by viewModel.allVentesWithItems.collectAsState()
    val sessionUiState by sessionViewModel.uiState.collectAsState()
    val sessionPreview by sessionViewModel.sessionPreview.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val sessionCloseError = stringResource(R.string.error_session_close)

    LaunchedEffect(Unit) {
        sessionViewModel.loadSessionPreview()
    }
    LaunchedEffect(sessionUiState) {
        when (sessionUiState) {
            is SessionUiState.Error -> {
                snackbarHostState.showSnackbar(sessionCloseError)
                sessionViewModel.clearUiState()
            }
            SessionUiState.Success -> sessionViewModel.clearUiState()
            else -> Unit
        }
    }
    
    val todayVentes = ventes.filter { DateUtils.isToday(it.date.time) }
    val momoTotal = todayVentes.filter { it.paymentMethod == "MOMO" && it.status == "CONFIRMED" }.sumOf { it.amount }
    val creditTotal = todayVentes.filter { it.paymentMethod == "CREDIT" }.sumOf { it.amount }
    val isLocked = todayVentes.any { it.isLocked }

    ClosureContent(
        todayVentes = todayVentes,
        momoTotal = momoTotal,
        creditTotal = creditTotal,
        isLocked = isLocked,
        sessionPreview = sessionPreview,
        operatorName = operatorName,
        sessionUiState = sessionUiState,
        snackbarHostState = snackbarHostState,
        onExportSalesCsvClick = {
            val todayVentesWithItems = allVentesWithItems.filter {
                DateUtils.isToday(it.vente.date.time)
            }
            reportsViewModel.exportSalesData(todayVentesWithItems)
        },
        onTotalBackupClick = { showBackupPasswordDialog = true },
        onCloseSessionSubmit = { counted ->
            val gap = SessionAmounts.cashGap(counted, sessionPreview?.expectedCash)
            val shortageLabel = context.getString(R.string.expense_cash_missing, FormatUtil.formatDate(Date()))
            val shortageCategory = context.getString(R.string.category_losses)
            sessionViewModel.closeSession(counted, viewModel.userRole.value) {
                viewModel.lockSalesForToday()
                if (gap != null && gap < 0) {
                    expenseViewModel.addExpense(
                        label = shortageLabel,
                        amount = kotlin.math.abs(gap),
                        category = shortageCategory
                    )
                }
            }
        },
        onNavigateToNewSale = { navController.navigate(Screen.NewSale.route) },
        onNavigateBack = { navController.popBackStack() }
    )

    if (showBackupPasswordDialog) {
        BackupPasswordDialog(
            isExport = true,
            subtitle = stringResource(R.string.backup_password_notice),
            onConfirm = { password ->
                showBackupPasswordDialog = false
                backupViewModel.exportEncryptedBackup(context, password, viewModel.userRole.value)
            },
            onDismiss = { showBackupPasswordDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClosureContent(
    todayVentes: List<com.reconsiliation.caisse.data.local.entity.VenteEntity>,
    momoTotal: Double,
    creditTotal: Double,
    isLocked: Boolean,
    sessionPreview: com.reconsiliation.caisse.data.repository.SessionPreview?,
    operatorName: String,
    sessionUiState: SessionUiState,
    snackbarHostState: SnackbarHostState,
    onExportSalesCsvClick: () -> Unit,
    onTotalBackupClick: () -> Unit,
    onCloseSessionSubmit: (countedCash: Double) -> Unit,
    onNavigateToNewSale: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var realCash by remember { mutableStateOf("") }
    val countedCash = SessionAmounts.countedCash(realCash)
    val cashGap = SessionAmounts.cashGap(countedCash, sessionPreview?.expectedCash)

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_closure)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        if (todayVentes.isEmpty()) {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(16.dp)
                    .testTag("closure_empty_view"),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Summarize, null, modifier = Modifier.size(64.dp), tint = Color.LightGray)
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.error_no_activity_today), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.error_no_activity_desc), textAlign = TextAlign.Center, color = Color.Gray)
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = onNavigateToNewSale) {
                    Text(stringResource(R.string.action_make_sale))
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .padding(16.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .testTag("closure_summary_content"),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(stringResource(R.string.closure_summary_date, FormatUtil.formatDate(Date())), style = MaterialTheme.typography.titleLarge, color = Primary)
                
                ClosureCard(stringResource(R.string.closure_confirmed_momo, operatorName), momoTotal)
                ClosureCard(stringResource(R.string.closure_debts_today), creditTotal)
                
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.closure_cash_validation), style = MaterialTheme.typography.titleMedium)
                        sessionPreview?.let { preview ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(stringResource(R.string.label_opening_float))
                                Text(FormatUtil.formatCurrency(preview.openingBalance))
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(stringResource(R.string.label_session_cash))
                                Text(FormatUtil.formatCurrency(preview.cashSales))
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(stringResource(R.string.label_session_expenses))
                                Text(FormatUtil.formatCurrency(preview.expenses))
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(stringResource(R.string.label_expected_system))
                                Text(FormatUtil.formatCurrency(preview.expectedCash), fontWeight = FontWeight.Bold)
                            }
                        }
                        OutlinedTextField(
                            value = realCash,
                            onValueChange = { realCash = it },
                            label = { Text(stringResource(R.string.label_actual_counted)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("closure_cash_input"),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            prefix = { Text(stringResource(R.string.label_unit_fcfa)) },
                            colors = CaisseTextFieldDefaults.outlinedTextFieldColors()
                        )
                        if (countedCash == null) {
                            Text(stringResource(R.string.session_count_required), color = RedError)
                        } else if (cashGap != null) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(stringResource(R.string.label_diff))
                                Text(
                                    FormatUtil.formatCurrency(cashGap),
                                    color = when {
                                        cashGap < 0 -> RedError
                                        cashGap > 0 -> GreenSuccess
                                        else -> Color.Gray
                                    },
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                HorizontalDivider()
                
                Text(stringResource(R.string.closure_end_actions), style = MaterialTheme.typography.titleMedium)
                
                Button(
                    onClick = onExportSalesCsvClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_export_csv"),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    Icon(Icons.Default.Description, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.action_export_sales_csv))
                }

                Button(
                    onClick = onTotalBackupClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_total_backup"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Icon(Icons.Default.Backup, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.action_total_backup))
                }
                
                if (!isLocked) {
                    Button(
                        onClick = {
                            val counted = SessionAmounts.countedCash(realCash) ?: return@Button
                            onCloseSessionSubmit(counted)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_close_and_lock"),
                        enabled = countedCash != null && sessionUiState !is SessionUiState.Processing,
                        colors = ButtonDefaults.buttonColors(containerColor = RedError)
                    ) {
                        Icon(Icons.Default.Lock, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.action_close_and_lock))
                    }
                } else {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("closure_locked_card"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.Center) {
                            Icon(Icons.Default.Lock, null, tint = Color.Gray)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.status_day_closed), color = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ClosureCard(label: String, amount: Double) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label)
            Text(FormatUtil.formatCurrency(amount), fontWeight = FontWeight.Bold, color = Primary)
        }
    }
}
