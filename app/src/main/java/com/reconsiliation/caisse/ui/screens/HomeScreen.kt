package com.reconsiliation.caisse.ui.screens

import android.app.Application
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.reconsiliation.caisse.R
import com.reconsiliation.caisse.ui.components.BigButton
import com.reconsiliation.caisse.ui.navigation.Screen
import com.reconsiliation.caisse.ui.state.SessionUiState
import com.reconsiliation.caisse.ui.theme.GreenSuccess
import com.reconsiliation.caisse.ui.theme.RedError
import com.reconsiliation.caisse.ui.viewmodel.InventoryViewModel
import com.reconsiliation.caisse.ui.viewmodel.MainViewModel
import com.reconsiliation.caisse.ui.viewmodel.SessionAmounts
import com.reconsiliation.caisse.ui.viewmodel.SessionViewModel
import com.reconsiliation.caisse.utils.FormatUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController, viewModel: MainViewModel) {
    val inventoryViewModel: InventoryViewModel = hiltViewModel()
    val sessionViewModel: SessionViewModel = hiltViewModel()
    val context = LocalContext.current
    
    val userRole by viewModel.userRole.collectAsState()
    val boutique by viewModel.boutique.collectAsState()
    val allStock by inventoryViewModel.allStock.collectAsState()
    val smsErrors by viewModel.smsErrors.collectAsState()
    val lowStock by inventoryViewModel.lowStockAlerts.collectAsState()
    val isDataLoaded by viewModel.isDataLoaded.collectAsState()
    val subscription by viewModel.subscription.collectAsState()
    
    val totalRevenue by sessionViewModel.todayRevenue.collectAsState()
    val netProfit by sessionViewModel.todayProfit.collectAsState()
    val currency by viewModel.currency.collectAsState()
    val activeSession by sessionViewModel.activeSession.collectAsState()

    LaunchedEffect(userRole) {
        sessionViewModel.setUserRole(userRole)
    }

    val onProtectedClick = { route: String ->
        viewModel.recordActivity()
        viewModel.requestAccess(route)
        val currentRole = viewModel.userRole.value
        val isManagerRequired = Screen.isManagerRequired(route)
        
        if (currentRole == "MANAGER" || (currentRole == "STAFF" && !isManagerRequired)) {
            navController.navigate(route)
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val daysLeft = if (subscription != null && subscription!!.isActive) {
        val diff = subscription!!.endDate.time - java.util.Date().time
        (diff / (1000 * 60 * 60 * 24)).toInt()
    } else -1

    val isStaffRestricted = boutique?.hideProfitsFromStaff == true

    var showSessionDialog by remember { mutableStateOf(false) }
    var sellerName by remember { mutableStateOf("") }
    var openingBalance by remember { mutableStateOf("") }
    var sessionFormErrorRes by remember { mutableStateOf<Int?>(null) }
    val staffId by viewModel.staffId.collectAsState()
    val suggestedSeller by sessionViewModel.suggestedSeller.collectAsState()
    val sessionPreview by sessionViewModel.sessionPreview.collectAsState()
    val sessionUiState by sessionViewModel.uiState.collectAsState()

    LaunchedEffect(staffId) {
        sessionViewModel.loadSellerSuggestion(staffId)
    }
    LaunchedEffect(suggestedSeller) {
        val suggestion = suggestedSeller
        if (sellerName.isBlank() && suggestion != null) sellerName = suggestion
    }
    LaunchedEffect(activeSession, isDataLoaded, sessionUiState) {
        if (!isDataLoaded) return@LaunchedEffect
        when (sessionUiState) {
            SessionUiState.AlreadyOpen -> showSessionDialog = true
            SessionUiState.Processing, SessionUiState.Success -> Unit
            else -> showSessionDialog = activeSession == null
        }
    }
    LaunchedEffect(sessionUiState) {
        when (sessionUiState) {
            SessionUiState.Success -> {
                showSessionDialog = false
                sessionViewModel.clearUiState()
            }
            SessionUiState.AlreadyOpen -> sessionViewModel.loadSessionPreview()
            else -> Unit
        }
    }

    if (showSessionDialog) {
        AlertDialog(
            onDismissRequest = {
                if (sessionUiState is SessionUiState.AlreadyOpen) {
                    showSessionDialog = false
                    sessionViewModel.clearUiState()
                }
            },
            title = { Text(stringResource(R.string.session_opening)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.session_opening_desc))
                    if (sessionUiState is SessionUiState.AlreadyOpen) {
                        Text(stringResource(R.string.session_already_open), color = MaterialTheme.colorScheme.error)
                        sessionPreview?.let { preview ->
                            Text(stringResource(R.string.label_seller_colon, preview.sellerName))
                            Text("${stringResource(R.string.label_opening_float)} : ${FormatUtil.formatCurrency(preview.openingBalance)}")
                            Text("${stringResource(R.string.label_session_cash)} : ${FormatUtil.formatCurrency(preview.cashSales)}")
                            Text("${stringResource(R.string.label_session_expenses)} : ${FormatUtil.formatCurrency(preview.expenses)}")
                            Text("${stringResource(R.string.label_expected_system)} ${FormatUtil.formatCurrency(preview.expectedCash)}")
                        }
                    }
                    OutlinedTextField(value = sellerName, onValueChange = { sellerName = it }, label = { Text(stringResource(R.string.label_seller_name)) }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = openingBalance, onValueChange = { openingBalance = it }, label = { Text(stringResource(R.string.label_initial_balance)) }, modifier = Modifier.fillMaxWidth(), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
                    sessionFormErrorRes?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (sessionUiState is SessionUiState.Processing) return@Button
                        val seller = SessionAmounts.sellerName(sellerName)
                        val balance = SessionAmounts.openingBalance(openingBalance)
                        sessionFormErrorRes = when {
                            seller == null -> R.string.session_seller_required
                            balance == null -> R.string.session_balance_required
                            else -> null
                        }
                        if (seller != null && balance != null) {
                            sessionViewModel.openSession(seller, balance, staffId)
                        }
                    },
                    enabled = sessionUiState !is SessionUiState.Processing
                ) { Text(stringResource(R.string.action_open)) }
            }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(boutique?.name ?: stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        activeSession?.let { Text(stringResource(R.string.label_seller_colon, it.sellerName), style = MaterialTheme.typography.labelSmall) }
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.catchUpSms(context.applicationContext as android.app.Application) }) {
                        Icon(Icons.Default.Sync, null)
                    }
                    IconButton(onClick = {
                        viewModel.logout()
                        navController.navigate(Screen.Pin.route) { popUpTo(0) { inclusive = true } }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.Logout, null)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = MaterialTheme.colorScheme.onPrimary, actionIconContentColor = MaterialTheme.colorScheme.onPrimary)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (daysLeft in 0..5 || lowStock.isNotEmpty() || smsErrors.isNotEmpty()) {
                Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp), tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (daysLeft in 0..5) AlertItem(icon = Icons.Default.Timer, text = stringResource(R.string.label_subscription), subtext = stringResource(R.string.subscription_expires_in, daysLeft), color = com.reconsiliation.caisse.ui.theme.OrangeWarning, onClick = { navController.navigate(Screen.Support.route) })
                        if (smsErrors.isNotEmpty()) AlertItem(icon = Icons.Default.SmsFailed, text = stringResource(R.string.label_unreconciled_sms), subtext = stringResource(R.string.sms_to_process, smsErrors.size), onClick = { navController.navigate(Screen.SmsErrors.route) })
                        if (lowStock.isNotEmpty()) AlertItem(icon = Icons.Default.Inventory, text = stringResource(R.string.stock_low), subtext = stringResource(R.string.products_to_order, lowStock.size), onClick = { navController.navigate(Screen.Stock.route) })
                    }
                }
            }

            ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.AccountBalanceWallet, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(8.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(stringResource(R.string.label_revenue), style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(onClick = { navController.navigate(Screen.Reports.route) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, null, modifier = Modifier.size(16.dp))
                        }
                    }
                    Text(FormatUtil.formatCurrency(totalRevenue, currency), style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold)
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        SummaryMiniItem(label = stringResource(R.string.label_net_profit), value = if (isStaffRestricted && userRole == "STAFF") "****" else FormatUtil.formatCurrency(netProfit, currency), color = GreenSuccess)
                        SummaryMiniItem(label = stringResource(R.string.label_sessions), value = if (activeSession != null) stringResource(R.string.status_active) else stringResource(R.string.status_closed), color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }

            BigButton(text = stringResource(R.string.action_new_sale), onClick = { viewModel.recordActivity(); navController.navigate(Screen.NewSale.route) }, containerColor = MaterialTheme.colorScheme.secondary, modifier = Modifier.height(72.dp))

            Text(stringResource(R.string.management_tools), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ModernQuickActionCard(title = stringResource(R.string.title_history), icon = Icons.Default.History, onClick = { viewModel.recordActivity(); navController.navigate(Screen.History.route) }, modifier = Modifier.weight(1f))
                    ModernQuickActionCard(title = stringResource(R.string.title_stock), icon = Icons.Default.Inventory, onClick = { onProtectedClick(Screen.Stock.route) }, modifier = Modifier.weight(1f))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ModernQuickActionCard(title = stringResource(R.string.title_audit), icon = Icons.Default.Checklist, onClick = { onProtectedClick(Screen.Audit.route) }, modifier = Modifier.weight(1f))
                    ModernQuickActionCard(title = stringResource(R.string.title_suppliers), icon = Icons.Default.Business, onClick = { onProtectedClick(Screen.Suppliers.route) }, modifier = Modifier.weight(1f))
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun SummaryMiniItem(label: String, value: String, color: Color) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.Gray)
        Text(value, style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun AlertItem(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, subtext: String, color: Color = RedError, onClick: () -> Unit) {
    Surface(onClick = onClick, color = color.copy(alpha = 0.08f), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
            Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(text, style = MaterialTheme.typography.titleSmall, color = color, fontWeight = FontWeight.Bold)
                Text(subtext, style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.8f))
            }
            Spacer(modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, null, tint = color, modifier = Modifier.size(12.dp))
        }
    }
}

@Composable
fun ModernQuickActionCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ElevatedCard(onClick = onClick, modifier = modifier.height(110.dp), shape = RoundedCornerShape(20.dp)) {
        Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f), shape = CircleShape, modifier = Modifier.size(48.dp)) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(12.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
        }
    }
}
