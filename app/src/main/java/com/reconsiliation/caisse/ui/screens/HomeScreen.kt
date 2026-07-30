package com.reconsiliation.caisse.ui.screens

import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import com.reconsiliation.caisse.R
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.reconsiliation.caisse.ui.components.BigButton
import com.reconsiliation.caisse.ui.components.CaisseTextFieldDefaults
import com.reconsiliation.caisse.ui.navigation.Screen
import com.reconsiliation.caisse.ui.theme.GreenSuccess
import com.reconsiliation.caisse.ui.theme.RedError
import com.reconsiliation.caisse.ui.viewmodel.MainViewModel
import com.reconsiliation.caisse.utils.FormatUtil

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(navController: NavController, viewModel: MainViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    
    val permissionsToCheck = listOf(
        android.Manifest.permission.RECEIVE_SMS,
        android.Manifest.permission.READ_SMS
    )
    var missingPermissions by remember { mutableStateOf(emptyList<String>()) }
    
    LaunchedEffect(Unit) {
        missingPermissions = permissionsToCheck.filter {
            androidx.core.content.ContextCompat.checkSelfPermission(context, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    val boutique by viewModel.boutique.collectAsState()
    val ventesWithItems by viewModel.allVentesWithItems.collectAsState()
    val allStock by viewModel.allStock.collectAsState()
    val smsErrors by viewModel.smsErrors.collectAsState()
    val lowStock by viewModel.lowStockAlerts.collectAsState()
    val valuation by viewModel.stockValuation.collectAsState()
    val uiError by viewModel.uiError.collectAsState()
    val isDataLoaded by viewModel.isDataLoaded.collectAsState()
    val subscription by viewModel.subscription.collectAsState()
    val totalRevenue by viewModel.todayRevenue.collectAsState()
    val netProfit by viewModel.todayProfit.collectAsState()
    val currency by viewModel.currency.collectAsState()
    val activeSession by viewModel.activeSession.collectAsState()

    val userRole by viewModel.userRole.collectAsState()
    val isLoggedIn = userRole != null

    // Remove the aggressive redirection here.
    // SplashScreen and SetupScreen handle transitions.
    // We only keep a safety check for critical missing data.

    val onProtectedClick = { route: String ->
        viewModel.recordActivity()
        viewModel.requestAccess(route)
        // If access was already granted, the UI might need to navigate.
        // But requestAccess handles the PIN dialog if not granted.
        // To be safe, if the role allows it, we navigate.
        val currentRole = viewModel.userRole.value
        if (currentRole == "MANAGER") {
            navController.navigate(route)
        } else if (currentRole == "STAFF" && route !in listOf(Screen.Audit.route, Screen.Suppliers.route, Screen.Debts.route, Screen.ClosureHistory.route)) {
            // Note: Screen.Stock.route is special because of parameters, let's simplify check
            navController.navigate(route)
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val isExpired = subscription == null || !subscription!!.isActive || subscription!!.endDate.before(java.util.Date())
    
    val daysLeft = if (subscription != null && subscription!!.isActive) {
        val diff = subscription!!.endDate.time - java.util.Date().time
        (diff / (1000 * 60 * 60 * 24)).toInt()
    } else -1

    LaunchedEffect(uiError) {
        uiError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    val confirmedItems = ventesWithItems.filter { it.vente.status == "CONFIRMED" && android.text.format.DateUtils.isToday(it.vente.date.time) }
    val orphanCount = confirmedItems.count { it.vente.reconciliationStatus == "ORPHAN" }

    var showSessionDialog by remember { mutableStateOf(false) }
    var sellerName by remember { mutableStateOf("") }
    var openingBalance by remember { mutableStateOf("") }

    LaunchedEffect(activeSession, isDataLoaded) {
        if (isDataLoaded) {
            showSessionDialog = activeSession == null
        }
    }

    if (showSessionDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Ouverture de Session", style = MaterialTheme.typography.titleLarge) },
            text = {
                val textFieldColors = CaisseTextFieldDefaults.outlinedTextFieldColors()
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Veuillez ouvrir une session pour commencer à vendre.", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = sellerName,
                        onValueChange = { sellerName = it },
                        label = { Text("Nom du vendeur") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = textFieldColors
                    )
                    OutlinedTextField(
                        value = openingBalance,
                        onValueChange = { openingBalance = it },
                        label = { Text("Fond de caisse initial") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = textFieldColors
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (sellerName.isNotBlank()) {
                            viewModel.openSession(sellerName, openingBalance.toDoubleOrNull() ?: 0.0)
                            showSessionDialog = false
                        }
                    },
                    enabled = sellerName.isNotBlank(),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Ouvrir la Session") }
            }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(boutique?.name ?: stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        activeSession?.let {
                            Text("Vendeur: ${it.sellerName}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f))
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.catchUpSms(context.applicationContext as android.app.Application) }) {
                        Icon(Icons.Default.Sync, contentDescription = null)
                    }
                    IconButton(onClick = {
                        viewModel.logout()
                        navController.navigate(Screen.Pin.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Alerts Section
            if (missingPermissions.isNotEmpty() || (daysLeft in 0..5) || orphanCount > 0 || lowStock.isNotEmpty() || smsErrors.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (missingPermissions.isNotEmpty()) {
                            AlertItem(
                                icon = Icons.Default.SecurityUpdateWarning,
                                text = "Permissions manquantes",
                                subtext = "L'écoute des SMS est désactivée",
                                onClick = {
                                    val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = android.net.Uri.fromParts("package", context.packageName, null)
                                    }
                                    context.startActivity(intent)
                                }
                            )
                        }
                        if (daysLeft in 0..5) {
                            AlertItem(
                                icon = Icons.Default.Timer,
                                text = "Abonnement",
                                subtext = "Expire dans $daysLeft jour(s)",
                                color = com.reconsiliation.caisse.ui.theme.OrangeWarning,
                                onClick = { navController.navigate(Screen.Support.route) }
                            )
                        }
                        if (smsErrors.isNotEmpty()) {
                            AlertItem(
                                icon = Icons.Default.SmsFailed,
                                text = "SMS non réconciliés",
                                subtext = "${smsErrors.size} messages à traiter",
                                onClick = { navController.navigate(Screen.SmsErrors.route) }
                            )
                        }
                        if (lowStock.isNotEmpty()) {
                            AlertItem(
                                icon = Icons.Default.Inventory,
                                text = "Stock Bas",
                                subtext = "${lowStock.size} produits à commander",
                                onClick = { navController.navigate(Screen.Stock.route) }
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = allStock.isEmpty() && ventesWithItems.isEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                WelcomeCard(onAddProduct = { navController.navigate(Screen.Stock.route) })
            }

            // Financial Summary Card
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = CircleShape,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Default.AccountBalanceWallet, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(8.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Chiffre d'Affaires", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(onClick = { navController.navigate(Screen.Reports.route) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, null, modifier = Modifier.size(16.dp))
                        }
                    }
                    
                    Text(
                        FormatUtil.formatCurrency(totalRevenue, currency),
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.ExtraBold
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        SummaryMiniItem(
                            label = "Bénéfice Net",
                            value = if (viewModel.isStaffRestricted()) "****" else FormatUtil.formatCurrency(netProfit, currency),
                            color = GreenSuccess
                        )
                        SummaryMiniItem(
                            label = "Paiements MoMo",
                            value = confirmedItems.count { it.vente.paymentMethod == "MOMO" }.toString(),
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }

            // Main Action
            BigButton(
                text = "NOUVELLE VENTE",
                onClick = {
                    viewModel.recordActivity()
                    navController.navigate(Screen.NewSale.route)
                },
                containerColor = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.height(72.dp)
            )

            // Grid Actions
            Text("Gestion & Outils", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ModernQuickActionCard(
                        title = "Historique",
                        icon = Icons.Default.History,
                        onClick = {
                            viewModel.recordActivity()
                            navController.navigate(Screen.History.route)
                        },
                        modifier = Modifier.weight(1f)
                    )
                    ModernQuickActionCard(
                        title = "Stock",
                        icon = Icons.Default.Inventory,
                        onClick = { onProtectedClick(Screen.Stock.route) },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ModernQuickActionCard(
                        title = "Audit",
                        icon = Icons.Default.Checklist,
                        onClick = { onProtectedClick(Screen.Audit.route) },
                        modifier = Modifier.weight(1f)
                    )
                    ModernQuickActionCard(
                        title = "Fournisseurs",
                        icon = Icons.Default.Business,
                        onClick = { onProtectedClick(Screen.Suppliers.route) },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ModernQuickActionCard(
                        title = "Dettes",
                        icon = Icons.Default.Payments,
                        onClick = { onProtectedClick(Screen.Debts.route) },
                        modifier = Modifier.weight(1f)
                    )
                    ModernQuickActionCard(
                        title = "Archives",
                        icon = Icons.Default.Inventory2,
                        onClick = { onProtectedClick(Screen.ClosureHistory.route) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun WelcomeCard(onAddProduct: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + expandVertically()
    ) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth().animateContentSize(),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                Icon(Icons.Default.Storefront, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Bienvenue dans votre Caisse !", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Commencez par ajouter vos premiers produits pour voir vos statistiques s'afficher ici.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onAddProduct,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Ajouter mon premier produit")
                }
            }
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
    Surface(
        onClick = onClick,
        color = color.copy(alpha = 0.08f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
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
    ElevatedCard(
        onClick = onClick,
        modifier = modifier.height(110.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                shape = CircleShape,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(12.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
        }
    }
}
