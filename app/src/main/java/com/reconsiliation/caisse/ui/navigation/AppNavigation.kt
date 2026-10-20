package com.reconsiliation.caisse.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.reconsiliation.caisse.ui.screens.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.hilt.navigation.compose.hiltViewModel
import com.reconsiliation.caisse.ui.viewmodel.MainViewModel
import com.reconsiliation.caisse.ui.theme.Primary
import com.reconsiliation.caisse.ui.theme.RedError
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import com.reconsiliation.caisse.R

sealed class Screen(
    val route: String,
    @StringRes val titleRes: Int? = null,
    val icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    val isManagerOnly: Boolean = false
) {
    data object Splash : Screen("splash")
    data object Onboarding : Screen("onboarding")
    data object Setup : Screen("setup")
    data object Pin : Screen("pin")
    data object Home : Screen("home", R.string.title_home, Icons.Default.Home)
    data object NewSale : Screen("new_sale")
    data object History : Screen("history", R.string.nav_sales, Icons.Default.History)
    data object Stock : Screen("stock?barcode={barcode}", R.string.nav_stock, Icons.Default.Inventory) {
        fun createRoute(barcode: String? = null) = if (barcode != null) "stock?barcode=$barcode" else "stock"
    }
    data object SmsErrors : Screen("sms_errors")
    data object Closure : Screen("closure")
    data object Debts : Screen("debts")
    data object Expenses : Screen("expenses")
    data object Settings : Screen("settings", R.string.title_settings, Icons.Default.Settings, isManagerOnly = true)
    data object Support : Screen("support")
    data object Reports : Screen("reports", R.string.nav_reports, Icons.Default.BarChart, isManagerOnly = true)
    data object EditBoutique : Screen("edit_boutique", isManagerOnly = true)
    data object PrintSettings : Screen("print_settings")
    data object RestorationWizard : Screen("restoration_wizard", isManagerOnly = true)
    data object Audit : Screen("audit", isManagerOnly = true)
    data object AuditHistory : Screen("audit_history")
    data object SessionHistory : Screen("session_history")
    data object SupplyHistory : Screen("supply_history")
    data object ClosureHistory : Screen("closure_history", isManagerOnly = true)
    data object ActionLogs : Screen("action_logs", isManagerOnly = true)
    data object Suppliers : Screen("suppliers", isManagerOnly = true)
    data object Categories : Screen("categories", isManagerOnly = true)
    data object Staff : Screen("staff", isManagerOnly = true)
    data object Maintenance : Screen("maintenance", isManagerOnly = true)
    data object StockMovements : Screen("stock_movements/{productId}") {
        fun createRoute(productId: Long) = "stock_movements/$productId"
    }
    data object PriceHistory : Screen("price_history/{productId}") {
        fun createRoute(productId: Long) = "price_history/$productId"
    }
    data object CustomerProfile : Screen("customer_profile/{customerId}") {
        fun createRoute(customerId: Long) = "customer_profile/$customerId"
    }
    data object SaleDetail : Screen("sale_detail/{venteId}") {
        fun createRoute(venteId: Long) = "sale_detail/$venteId"
    }
    data object SupplierInflow : Screen("supplier_inflow")

    companion object {
        private val ALL_SCREENS = listOf(
            Splash, Onboarding, Setup, Pin, Home, NewSale, History, Stock,
            SmsErrors, Closure, Debts, Expenses, Settings, Support, Reports,
            EditBoutique, PrintSettings, RestorationWizard, Audit, AuditHistory,
            SessionHistory, SupplyHistory, ClosureHistory, ActionLogs, Suppliers,
            Categories, Staff, Maintenance, StockMovements, PriceHistory,
            CustomerProfile, SaleDetail, SupplierInflow
        )

        fun isManagerRequired(route: String?): Boolean {
            if (route == null) return false
            val baseRoute = route.substringBefore("?").substringBefore("/")
            return ALL_SCREENS.any { 
                it.isManagerOnly && it.route.substringBefore("?").substringBefore("/") == baseRoute 
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    // Use Activity-scoped ViewModel to share state (like showPinDialog) across screens
    val viewModel: MainViewModel = hiltViewModel()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val userRole by viewModel.userRole.collectAsState()
    val isLoggedIn = userRole != null

    val showPinDialog by viewModel.showPinDialog.collectAsState()
    var pinInput by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val items = listOf(
        Screen.Home,
        Screen.History,
        Screen.Stock,
        Screen.Settings
    )

    val showBottomBar = items.any { it.route == currentDestination?.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    items.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon!!, contentDescription = null) },
                            label = { Text(stringResource(screen.titleRes!!)) },
                            selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                            onClick = {
                                viewModel.recordActivity()
                                val isManagerRequired = Screen.isManagerRequired(screen.route)
                                val currentRole = viewModel.userRole.value
                                val canAccess = if (isManagerRequired) currentRole == "MANAGER" else currentRole != null
                                if (!canAccess) {
                                    viewModel.requestAccess(screen.route)
                                } else {
                                    navController.navigate(screen.route) {
                                        popUpTo(Screen.Home.route) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            NavHost(
                navController = navController,
                startDestination = Screen.Splash.route
            ) {
            composable(Screen.Splash.route) { SplashScreen(navController) }
                composable(Screen.Onboarding.route) { OnboardingScreen(navController) }
                composable(Screen.Setup.route) { SetupScreen(navController) }
                composable(Screen.Pin.route) { PinScreen(navController) }
                composable(Screen.Home.route) { HomeScreen(navController, viewModel) }
                composable(Screen.NewSale.route) { NewSaleScreen(navController) }
                composable(Screen.History.route) { SalesListScreen(navController) }
                composable(
                    route = Screen.Stock.route,
                    arguments = listOf(androidx.navigation.navArgument("barcode") {
                        type = androidx.navigation.NavType.StringType
                        nullable = true
                        defaultValue = null
                    })
                ) { backStackEntry ->
                    val barcode = backStackEntry.arguments?.getString("barcode")
                    StockScreen(navController, barcode)
                }
                composable(Screen.SmsErrors.route) { SmsErrorScreen(navController) }
                composable(Screen.Closure.route) { ClosureScreen(navController) }
                composable(Screen.Debts.route) { DebtScreen(navController) }
                composable(Screen.Expenses.route) { ExpenseScreen(navController) }
                composable(Screen.Settings.route) {
                    if (viewModel.userRole.value != "MANAGER") {
                        viewModel.requestAccess(Screen.Settings.route)
                    }
                    if (viewModel.userRole.value == "MANAGER") {
                        SettingsScreen(navController)
                    }
                }
                composable(Screen.Support.route) { SupportScreen(navController) }
                composable(Screen.Reports.route) {
                    if (viewModel.userRole.value != "MANAGER") {
                        viewModel.requestAccess(Screen.Reports.route)
                    }
                    if (viewModel.userRole.value == "MANAGER") {
                        ReportsScreen(navController)
                    }
                }
                composable(Screen.EditBoutique.route) {
                    if (viewModel.userRole.value != "MANAGER") {
                        viewModel.requestAccess(Screen.EditBoutique.route)
                    }
                    if (viewModel.userRole.value == "MANAGER") {
                        EditBoutiqueScreen(navController)
                    }
                }
                composable(Screen.PrintSettings.route) { PrintSettingsScreen(navController) }
                composable(Screen.RestorationWizard.route) {
    if (viewModel.userRole.value != "MANAGER") {
        viewModel.requestAccess(Screen.RestorationWizard.route)
    }
    if (viewModel.userRole.value == "MANAGER") {
        RestorationWizardScreen(navController)
    }
}
                composable(Screen.Audit.route) {
    if (viewModel.userRole.value != "MANAGER") {
        viewModel.requestAccess(Screen.Audit.route)
    }
    if (viewModel.userRole.value == "MANAGER") {
        AuditScreen(navController)
    }
}
                composable(Screen.AuditHistory.route) { AuditHistoryScreen(navController) }
                composable(Screen.SessionHistory.route) { SessionHistoryScreen(navController) }
                composable(Screen.SupplyHistory.route) { SupplyHistoryScreen(navController) }
                composable(Screen.ClosureHistory.route) {
                    if (viewModel.userRole.value != "MANAGER") {
                        viewModel.requestAccess(Screen.ClosureHistory.route)
                    }
                    if (viewModel.userRole.value == "MANAGER") {
                        ClosureHistoryScreen(navController)
                    }
                }
                composable(Screen.ActionLogs.route) {
                    if (viewModel.userRole.value != "MANAGER") {
                        viewModel.requestAccess(Screen.ActionLogs.route)
                    }
                    if (viewModel.userRole.value == "MANAGER") {
                        ActionLogScreen(navController)
                    }
                }
                composable(Screen.Suppliers.route) {
                    if (viewModel.userRole.value != "MANAGER") {
                        viewModel.requestAccess(Screen.Suppliers.route)
                    }
                    if (viewModel.userRole.value == "MANAGER") {
                        SupplierListScreen(navController)
                    }
                }
                composable(Screen.Categories.route) {
                    if (viewModel.userRole.value != "MANAGER") {
                        viewModel.requestAccess(Screen.Categories.route)
                    }
                    if (viewModel.userRole.value == "MANAGER") {
                        CategoryManagementScreen(navController)
                    }
                }
                composable(Screen.Staff.route) {
                    if (viewModel.userRole.value != "MANAGER") {
                        viewModel.requestAccess(Screen.Staff.route)
                    }
                    if (viewModel.userRole.value == "MANAGER") {
                        StaffManagementScreen(navController)
                    }
                }
                composable(Screen.Maintenance.route) {
                    if (viewModel.userRole.value != "MANAGER") {
                        viewModel.requestAccess(Screen.Maintenance.route)
                    }
                    if (viewModel.userRole.value == "MANAGER") {
                        MaintenanceScreen(navController)
                    }
                }
                composable(
                    route = Screen.StockMovements.route,
                    arguments = listOf(androidx.navigation.navArgument("productId") { type = androidx.navigation.NavType.LongType })
                ) { backStackEntry ->
                    val productId = backStackEntry.arguments?.getLong("productId") ?: 0L
                    StockMovementScreen(navController, productId)
                }
                composable(
                    route = Screen.PriceHistory.route,
                    arguments = listOf(androidx.navigation.navArgument("productId") { type = androidx.navigation.NavType.LongType })
                ) { backStackEntry ->
                    val productId = backStackEntry.arguments?.getLong("productId") ?: 0L
                    PriceHistoryScreen(navController, productId)
                }
                composable(
                    route = Screen.CustomerProfile.route,
                    arguments = listOf(androidx.navigation.navArgument("customerId") { type = androidx.navigation.NavType.LongType })
                ) { backStackEntry ->
                    val customerId = backStackEntry.arguments?.getLong("customerId") ?: 0L
                    CustomerProfileScreen(navController, customerId)
                }
                composable(
                    route = Screen.SaleDetail.route,
                    arguments = listOf(androidx.navigation.navArgument("venteId") { type = androidx.navigation.NavType.LongType })
                ) { backStackEntry ->
                    val venteId = backStackEntry.arguments?.getLong("venteId") ?: 0L
                    SaleDetailScreen(navController, venteId)
                }
                composable(Screen.SupplierInflow.route) { SupplierInflowScreen(navController) }
            }

            if (showPinDialog) {
                Dialog(onDismissRequest = {
                    viewModel.dismissPinDialog()
                    pinInput = ""
                }) {
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 6.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(stringResource(R.string.pin_required), style = MaterialTheme.typography.titleLarge)
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            var isError by remember { mutableStateOf(false) }
                            
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(vertical = 16.dp)
                            ) {
                                repeat(4) { index ->
                                    val filled = pinInput.length > index
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(if (isError) RedError else if (filled) Primary else Color.LightGray)
                                    )
                                }
                            }
                            
                            com.reconsiliation.caisse.ui.components.NumericKeypad(
                                onNumberClick = {
                                    viewModel.recordActivity()
                                    if (pinInput.length < 4) {
                                        isError = false
                                        pinInput += it
                                        if (pinInput.length == 4) {
                                            scope.launch {
                                                if (viewModel.handlePinInput(pinInput)) {
                                                    val route = viewModel.getPendingRouteAndClear()
                                                    if (route != null) {
                                                        navController.navigate(route) {
                                                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                                            launchSingleTop = true
                                                            restoreState = true
                                                        }
                                                    }
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
                                    viewModel.recordActivity()
                                    isError = false
                                    if (pinInput.isNotEmpty()) pinInput = pinInput.dropLast(1)
                                }
                            )
                            
                            TextButton(onClick = {
                                viewModel.dismissPinDialog()
                                pinInput = ""
                            }) {
                                Text(stringResource(R.string.action_cancel))
                            }
                        }
                    }
                }
            }
        }
    }
}
