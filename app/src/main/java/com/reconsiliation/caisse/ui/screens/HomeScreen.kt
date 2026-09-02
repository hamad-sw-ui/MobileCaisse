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
import com.reconsiliation.caisse.ui.theme.GreenSuccess
import com.reconsiliation.caisse.ui.theme.RedError
import com.reconsiliation.caisse.ui.viewmodel.InventoryViewModel
import com.reconsiliation.caisse.ui.viewmodel.MainViewModel
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
        if (currentRole == "MANAGER") {
            navController.navigate(route)
        } else if (currentRole == "STAFF" && route !in listOf(Screen.Audit.route, Screen.Suppliers.route, Screen.Debts.route, Screen.ClosureHistory.route)) {
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

    LaunchedEffect(activeSession, isDataLoaded) {
        if (isDataLoaded) showSessionDialog = activeSession == null
    }

    if (showSessionDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Ouverture de Session") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Veuillez ouvrir une session pour commencer à vendre.")
                    OutlinedTextField(value = sellerName, onValueChange = { sellerName = it }, label = { Text("Nom du vendeur") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = openingBalance, onValueChange = { openingBalance = it }, label = { Text("Fond de caisse initial") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (sellerName.isNotBlank()) {
                        sessionViewModel.openSession(sellerName, openingBalance.toDoubleOrNull() ?: 0.0, viewModel.staffId.value)
                        showSessionDialog = false
                    }
                }) { Text("Ouvrir") }
            }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(boutique?.name ?: stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        activeSession?.let { Text("Vendeur: ${it.sellerName}", style = MaterialTheme.typography.labelSmall) }
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.catchUpSms(context.applicationContext as Application) }) {
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
                        if (daysLeft in 0..5) AlertItem(icon = Icons.Default.Timer, text = "Abonnement", subtext = "Expire dans $daysLeft jour(s)", color = com.reconsiliation.caisse.ui.theme.OrangeWarning, onClick = { navController.navigate(Screen.Support.route) })
                        if (smsErrors.isNotEmpty()) AlertItem(icon = Icons.Default.SmsFailed, text = "SMS non réconciliés", subtext = "${smsErrors.size} messages à traiter", onClick = { navController.navigate(Screen.SmsErrors.route) })
                        if (lowStock.isNotEmpty()) AlertItem(icon = Icons.Default.Inventory, text = "Stock Bas", subtext = "${lowStock.size} produits à commander", onClick = { navController.navigate(Screen.Stock.route) })
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
                        Text("Chiffre d'Affaires", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(onClick = { navController.navigate(Screen.Reports.route) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, null, modifier = Modifier.size(16.dp))
                        }
                    }
                    Text(FormatUtil.formatCurrency(totalRevenue, currency), style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold)
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        SummaryMiniItem(label = "Bénéfice Net", value = if (isStaffRestricted && userRole == "STAFF") "****" else FormatUtil.formatCurrency(netProfit, currency), color = GreenSuccess)
                        SummaryMiniItem(label = "Sessions", value = if (activeSession != null) "Active" else "Close", color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }

            BigButton(text = "NOUVELLE VENTE", onClick = { viewModel.recordActivity(); navController.navigate(Screen.NewSale.route) }, containerColor = MaterialTheme.colorScheme.secondary, modifier = Modifier.height(72.dp))

            Text("Gestion & Outils", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ModernQuickActionCard(title = "Historique", icon = Icons.Default.History, onClick = { viewModel.recordActivity(); navController.navigate(Screen.History.route) }, modifier = Modifier.weight(1f))
                    ModernQuickActionCard(title = "Stock", icon = Icons.Default.Inventory, onClick = { onProtectedClick(Screen.Stock.route) }, modifier = Modifier.weight(1f))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ModernQuickActionCard(title = "Audit", icon = Icons.Default.Checklist, onClick = { onProtectedClick(Screen.Audit.route) }, modifier = Modifier.weight(1f))
                    ModernQuickActionCard(title = "Fournisseurs", icon = Icons.Default.Business, onClick = { onProtectedClick(Screen.Suppliers.route) }, modifier = Modifier.weight(1f))
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
