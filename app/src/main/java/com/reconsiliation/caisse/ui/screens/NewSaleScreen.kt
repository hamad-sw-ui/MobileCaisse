package com.reconsiliation.caisse.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.reconsiliation.caisse.data.local.entity.StockEntity
import com.reconsiliation.caisse.data.local.entity.VenteEntity
import com.reconsiliation.caisse.data.local.entity.VenteItemEntity
import com.reconsiliation.caisse.ui.components.BigButton
import com.reconsiliation.caisse.ui.components.CaisseTextFieldDefaults
import com.reconsiliation.caisse.ui.scanner.BarcodeScannerDialog
import com.reconsiliation.caisse.ui.navigation.Screen
import com.reconsiliation.caisse.ui.theme.GreenSuccess
import com.reconsiliation.caisse.ui.theme.Primary
import com.reconsiliation.caisse.ui.viewmodel.MainViewModel
import com.reconsiliation.caisse.ui.viewmodel.NewSaleViewModel
import com.reconsiliation.caisse.ui.viewmodel.SessionViewModel
import com.reconsiliation.caisse.ui.state.NewSaleUiState
import com.reconsiliation.caisse.utils.FormatUtil
import androidx.compose.ui.res.stringResource
import com.reconsiliation.caisse.R
import kotlinx.coroutines.launch
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewSaleScreen(navController: NavController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val mainViewModel: MainViewModel = hiltViewModel()
    val viewModel: NewSaleViewModel = hiltViewModel()
    val sessionViewModel: SessionViewModel = hiltViewModel()
    
    val boutique by mainViewModel.boutique.collectAsState()
    val operatorName = if (boutique?.operator == "MTN") "MTN/OM" else boutique?.operator ?: "MTN/OM"
    val stockItems by viewModel.allStock.collectAsState(initial = emptyList())
    val cart by viewModel.cart.collectAsState()
    val currency by mainViewModel.currency.collectAsState()
    val lastVenteId by viewModel.lastConfirmedVenteId.collectAsState()
    val userRole by mainViewModel.userRole.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    val isDataLoaded by mainViewModel.isDataLoaded.collectAsState()
    val activeSession by sessionViewModel.activeSession.collectAsState()
    val isSessionLoaded by sessionViewModel.isLoaded.collectAsState()

    LaunchedEffect(activeSession, isDataLoaded, isSessionLoaded) {
        if (isDataLoaded && isSessionLoaded && activeSession == null) {
            navController.popBackStack()
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val saleErrorMessage = stringResource(R.string.error_generic)

    LaunchedEffect(uiState) {
        if (uiState is NewSaleUiState.Error) {
            val msg = (uiState as NewSaleUiState.Error).message.ifBlank { saleErrorMessage }
            snackbarHostState.showSnackbar(msg)
            viewModel.clearUiState()
        }
    }

    var showScanner by remember { mutableStateOf(false) }
    var continuousScan by remember { mutableStateOf(false) }
    var unknownBarcode by remember { mutableStateOf<String?>(null) }

    NewSaleContent(
        cart = cart,
        stockItems = stockItems,
        currency = currency,
        boutique = boutique,
        operatorName = operatorName,
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onUpdateCartQuantity = { item, newQty ->
            mainViewModel.recordActivity()
            if (newQty > 0) viewModel.updateCartItemQuantity(item, newQty)
            else viewModel.removeFromCart(item)
        },
        onRemoveFromCart = { item ->
            mainViewModel.recordActivity()
            viewModel.removeFromCart(item)
        },
        onAddToCart = { product, price, isRetail, parentId, factor ->
            if (isRetail) {
                viewModel.addToCart(product, price, isRetail = true, parentId = parentId, factor = factor ?: 1.0)
            } else {
                viewModel.addToCart(product, price)
            }
        },
        onUpdateCartPrices = { isVip ->
            viewModel.updateCartPrices(isVip)
        },
        onGetCustomerByPhone = { phone ->
            viewModel.getCustomerByPhone(phone)
        },
        onValidateSaleSubmit = { vente, items ->
            viewModel.addVenteWithItems(
                vente, items, userRole,
                boutique?.isTaxEnabled == true,
                boutique?.taxRate ?: 0.0
            )
        },
        onNavigateBack = { navController.popBackStack() },
        onScanClick = {
            mainViewModel.recordActivity()
            showScanner = true
        }
    )

    if (lastVenteId != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearLastVenteId() },
            title = { Text(stringResource(R.string.sale_confirmed_title)) },
            text = { Text(stringResource(R.string.sale_confirmed_desc)) },
            confirmButton = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            viewModel.printTicket(lastVenteId!!, boutique?.printerAddress ?: "")
                            viewModel.clearLastVenteId()
                            navController.popBackStack()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Print, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.action_print))
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                val venteWithItems = viewModel.getVenteWithItems(lastVenteId!!)
                                if (venteWithItems != null && boutique != null) {
                                    val text = FormatUtil.generateReceiptText(
                                        boutique!!,
                                        venteWithItems.vente,
                                        venteWithItems.items,
                                        currency
                                    )
                                    viewModel.shareReceipt(text)
                                }
                                viewModel.clearLastVenteId()
                                navController.popBackStack()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Share, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.action_whatsapp))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.clearLastVenteId()
                    navController.popBackStack()
                }) { Text(stringResource(R.string.action_finish)) }
            }
        )
    }

    if (showScanner) {
        BarcodeScannerDialog(
            onDismiss = {
                showScanner = false
                continuousScan = false
            },
            continuous = continuousScan,
            onBarcodeScanned = { code ->
                scope.launch {
                    val product = viewModel.getProductByBarcode(code)
                    if (product != null) {
                        viewModel.addToCart(product, product.unitPrice)
                    } else {
                        unknownBarcode = code
                        showScanner = false
                    }
                    if (!continuousScan && product != null) showScanner = false
                }
            }
        )
    }

    if (unknownBarcode != null) {
        AlertDialog(
            onDismissRequest = { unknownBarcode = null },
            title = { Text(stringResource(R.string.error_unknown_product_title)) },
            text = { Text(stringResource(R.string.error_unknown_product_desc, unknownBarcode!!)) },
            confirmButton = {
                Button(onClick = {
                    navController.navigate(Screen.Stock.route + "?barcode=$unknownBarcode")
                    unknownBarcode = null
                }) { Text(stringResource(R.string.action_create)) }
            },
            dismissButton = {
                TextButton(onClick = { unknownBarcode = null }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewSaleContent(
    cart: List<com.reconsiliation.caisse.ui.viewmodel.CartItem>,
    stockItems: List<StockEntity>,
    currency: String,
    boutique: com.reconsiliation.caisse.data.local.entity.BoutiqueEntity?,
    operatorName: String,
    uiState: NewSaleUiState,
    snackbarHostState: SnackbarHostState,
    onUpdateCartQuantity: (com.reconsiliation.caisse.ui.viewmodel.CartItem, Double) -> Unit,
    onRemoveFromCart: (com.reconsiliation.caisse.ui.viewmodel.CartItem) -> Unit,
    onAddToCart: (product: StockEntity, price: Double, isRetail: Boolean, parentId: Long?, factor: Double?) -> Unit,
    onUpdateCartPrices: (Boolean) -> Unit,
    onGetCustomerByPhone: suspend (String) -> com.reconsiliation.caisse.data.local.entity.CustomerEntity?,
    onValidateSaleSubmit: (VenteEntity, List<VenteItemEntity>) -> Unit,
    onNavigateBack: () -> Unit,
    onScanClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    var customerPhone by remember { mutableStateOf("") }
    var paymentMethod by remember { mutableStateOf("CASH") }
    var operationType by remember { mutableStateOf("MARCHAND") }
    var amountCashStr by remember { mutableStateOf("") }
    var amountMomoStr by remember { mutableStateOf("") }
    var amountCreditUsedStr by remember { mutableStateOf("") }
    var useLoyaltyPoints by remember { mutableStateOf(false) }

    var momoFees by remember { mutableStateOf("") }
    var discountAmount by remember { mutableStateOf("") }
    var customerLoyalty by remember { mutableStateOf<Int?>(null) }
    var customerCreditBalance by remember { mutableStateOf(0.0) }
    var isCustomerVip by remember { mutableStateOf(false) }

    LaunchedEffect(isCustomerVip) {
        onUpdateCartPrices(isCustomerVip)
    }

    LaunchedEffect(customerPhone) {
        if (customerPhone.length >= 8) {
            val c = onGetCustomerByPhone(customerPhone)
            customerLoyalty = c?.loyaltyPoints
            customerCreditBalance = c?.creditBalance ?: 0.0
            isCustomerVip = c?.isVip ?: false
        } else {
            customerLoyalty = null
            customerCreditBalance = 0.0
            isCustomerVip = false
        }
    }

    val totalAmount = cart.sumOf { it.priceAtSale * it.quantity }

    LaunchedEffect(paymentMethod, amountMomoStr, totalAmount, discountAmount, operationType) {
        val amountToCalculate = if (paymentMethod == "MIXED") {
            amountMomoStr.toDoubleOrNull() ?: 0.0
        } else if (paymentMethod == "MOMO") {
            val discount = discountAmount.toDoubleOrNull() ?: 0.0
            totalAmount - discount
        } else 0.0

        if (amountToCalculate > 0) {
            val op = boutique?.operator ?: "MTN"
            val calculated = com.reconsiliation.caisse.utils.FeeCalculator.calculatePrecisionFees(op, amountToCalculate, operationType)
            momoFees = if (calculated % 1.0 == 0.0) calculated.toInt().toString() else "%.0f".format(calculated)
        } else {
            momoFees = ""
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_new_sale)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. TOP: Search and Scan
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                var showProductPicker by remember { mutableStateOf(false) }
                
                OutlinedTextField(
                    value = stringResource(R.string.hint_search),
                    onValueChange = {},
                    modifier = Modifier.weight(1f).clickable { showProductPicker = true },
                    readOnly = true,
                    enabled = false,
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    colors = CaisseTextFieldDefaults.outlinedTextFieldColors(),
                    shape = RoundedCornerShape(12.dp)
                )
                
                if (showProductPicker) {
                    ProductPickerDialog(
                        stockItems = stockItems,
                        currency = currency,
                        isCustomerVip = isCustomerVip,
                        onDismiss = { showProductPicker = false },
                        onProductSelected = { product, isRetail ->
                            if (isRetail) {
                                val retailItem = product.copy(
                                    productName = "${product.productName} (${product.retailUnit})",
                                    unitPrice = product.retailPrice,
                                    unit = product.retailUnit!!,
                                    isBulk = false
                                )
                                onAddToCart(retailItem, product.retailPrice, true, product.id, product.conversionFactor)
                            } else {
                                val price = if (isCustomerVip && product.vipPrice > 0) product.vipPrice else product.unitPrice
                                onAddToCart(product, price, false, null, null)
                            }
                            showProductPicker = false
                        }
                    )
                }
                
                IconButton(
                    onClick = onScanClick,
                    modifier = Modifier.size(48.dp).background(Primary, RoundedCornerShape(12.dp)),
                    colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = stringResource(R.string.action_scan), modifier = Modifier.size(20.dp))
                }
            }

            // 2. MIDDLE: Cart List
            if (cart.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp)
                        .testTag("cart_empty_view"),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.ShoppingCart, null, tint = Color.LightGray, modifier = Modifier.size(48.dp))
                    Text(stringResource(R.string.cart_empty), color = Color.Gray)
                }
            } else {
                Column(
                    modifier = Modifier.testTag("cart_items_list"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    cart.forEach { item ->
                        CartRow(
                            item = item,
                            onUpdateQuantity = { newQty -> onUpdateCartQuantity(item, newQty) },
                            onRemove = { onRemoveFromCart(item) }
                        )
                    }
                }
            }

            HorizontalDivider()

            // 3. BOTTOM: Payment & Totals
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = customerPhone,
                        onValueChange = { customerPhone = it },
                        label = { Text(stringResource(R.string.label_customer)) },
                        modifier = Modifier.weight(1.3f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        colors = CaisseTextFieldDefaults.outlinedTextFieldColors(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = discountAmount,
                        onValueChange = { discountAmount = it },
                        label = { Text(stringResource(R.string.label_discount)) },
                        modifier = Modifier.weight(0.7f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = CaisseTextFieldDefaults.outlinedTextFieldColors(),
                        singleLine = true
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    val loyaltyDiscount = if (useLoyaltyPoints) 500.0 else 0.0
                    val subTotal = totalAmount - (discountAmount.toDoubleOrNull() ?: 0.0) - loyaltyDiscount
                    val tax = if (boutique?.isTaxEnabled == true) subTotal * (boutique?.taxRate ?: 0.0) / 100.0 else 0.0
                    val fees = momoFees.toDoubleOrNull() ?: 0.0
                    val finalTotal = subTotal + tax + fees
                    
                    Text(stringResource(R.string.label_total), style = MaterialTheme.typography.titleMedium)
                    Text(FormatUtil.formatCurrency(finalTotal, currency), style = MaterialTheme.typography.headlineSmall, color = Primary, fontWeight = FontWeight.Bold)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                    val cashLabel = stringResource(R.string.method_cash)
                    val mixedLabel = stringResource(R.string.method_mixed)
                    val creditLabel = stringResource(R.string.method_credit)
                    listOf(
                        "CASH" to cashLabel,
                        operatorName to operatorName,
                        "MIXED" to mixedLabel,
                        "CREDIT" to creditLabel
                    ).forEach { (method, labelText) ->
                        FilterChip(
                            selected = paymentMethod == method || (method == operatorName && paymentMethod == "MOMO"),
                            onClick = {
                                paymentMethod = if (method == operatorName) "MOMO" else method
                                if (paymentMethod != "MIXED") {
                                    amountCashStr = ""
                                    amountMomoStr = ""
                                }
                            },
                            label = { Text(labelText, fontSize = 12.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (paymentMethod == "MOMO" || paymentMethod == "MIXED") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.label_type), style = MaterialTheme.typography.labelMedium)
                        AssistChip(
                            onClick = { operationType = "MARCHAND" },
                            label = { Text(stringResource(R.string.type_merchant), fontSize = 11.sp) },
                            leadingIcon = { Icon(Icons.Default.Storefront, null, modifier = Modifier.size(14.dp)) },
                            colors = if (operationType == "MARCHAND") AssistChipDefaults.assistChipColors(containerColor = Primary.copy(alpha = 0.1f)) else AssistChipDefaults.assistChipColors()
                        )
                        AssistChip(
                            onClick = { operationType = "TRANSFERT" },
                            label = { Text(stringResource(R.string.type_send), fontSize = 11.sp) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Send, null, modifier = Modifier.size(14.dp)) },
                            colors = if (operationType == "TRANSFERT") AssistChipDefaults.assistChipColors(containerColor = Primary.copy(alpha = 0.1f)) else AssistChipDefaults.assistChipColors()
                        )
                        AssistChip(
                            onClick = { operationType = "RETRAIT" },
                            label = { Text(stringResource(R.string.type_withdraw), fontSize = 11.sp) },
                            leadingIcon = { Icon(Icons.Default.Payments, null, modifier = Modifier.size(14.dp)) },
                            colors = if (operationType == "RETRAIT") AssistChipDefaults.assistChipColors(containerColor = Primary.copy(alpha = 0.1f)) else AssistChipDefaults.assistChipColors()
                        )
                    }
                }

                if (paymentMethod == "MIXED") {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = amountCashStr,
                            onValueChange = { amountCashStr = it },
                            label = { Text(stringResource(R.string.label_cash)) },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = CaisseTextFieldDefaults.outlinedTextFieldColors(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = amountMomoStr,
                            onValueChange = { amountMomoStr = it },
                            label = { Text(operatorName) },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = CaisseTextFieldDefaults.outlinedTextFieldColors(),
                            singleLine = true
                        )
                    }
                }

                BigButton(
                    text = stringResource(R.string.action_validate_sale),
                    enabled = uiState !is NewSaleUiState.Processing,
                    onClick = {
                        if (cart.isNotEmpty()) {
                            val invalidItem = cart.find { it.product.unit == "pcs" && it.quantity % 1.0 != 0.0 }
                            if (invalidItem != null) {
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        context.getString(R.string.unit_error_integer, invalidItem.product.productName)
                                    )
                                }
                                return@BigButton
                            }

                            val finalMethod = if (paymentMethod == operatorName) "MOMO" else paymentMethod
                            if (finalMethod == "CREDIT" && customerPhone.isBlank()) return@BigButton

                            val finalDiscount = (discountAmount.toDoubleOrNull() ?: 0.0) + (if (useLoyaltyPoints) 500.0 else 0.0)
                            val finalFees = momoFees.toDoubleOrNull() ?: 0.0
                            val finalTotal = totalAmount - finalDiscount + finalFees
                            
                            var cashVal = 0.0
                            var momoVal = 0.0
                            val creditUsedRequested = amountCreditUsedStr.toDoubleOrNull() ?: 0.0
                            val creditUsed = if (creditUsedRequested > finalTotal) finalTotal else creditUsedRequested

                            when(finalMethod) {
                                "CASH" -> cashVal = finalTotal - creditUsed
                                "MOMO" -> momoVal = finalTotal - creditUsed
                                "MIXED" -> {
                                    cashVal = amountCashStr.toDoubleOrNull() ?: 0.0
                                    momoVal = amountMomoStr.toDoubleOrNull() ?: 0.0
                                    if (kotlin.math.abs((cashVal + momoVal + creditUsed) - finalTotal) > 0.1) {
                                        scope.launch {
                                            snackbarHostState.showSnackbar(context.getString(R.string.error_total_incorrect))
                                        }
                                        return@BigButton
                                    }
                                }
                            }
                            
                            val vente = VenteEntity(
                                amount = finalTotal,
                                description = cart.joinToString(", ") {
                                    val qtyStr = if (it.quantity % 1.0 == 0.0) it.quantity.toInt().toString() else "%.2f".format(it.quantity)
                                    "${it.product.productName} x$qtyStr"
                                },
                                date = Date(),
                                paymentMethod = finalMethod,
                                status = if (finalMethod == "CASH" || finalMethod == "MIXED") "CONFIRMED" else "PENDING",
                                customerPhone = customerPhone,
                                fees = finalFees,
                                discount = finalDiscount,
                                amountCash = cashVal,
                                amountMomo = momoVal
                            )
                            val items = cart.map {
                                VenteItemEntity(venteId = 0, productId = it.product.id, productName = it.product.productName, quantity = it.quantity, unitPrice = it.priceAtSale, purchasePrice = it.product.purchasePrice)
                            }
                            onValidateSaleSubmit(vente, items)
                        }
                    },
                    containerColor = GreenSuccess
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductPickerDialog(
    stockItems: List<StockEntity>,
    currency: String,
    isCustomerVip: Boolean,
    onDismiss: () -> Unit,
    onProductSelected: (StockEntity, Boolean) -> Unit
) {
    val viewModel: NewSaleViewModel = hiltViewModel()
    var searchQuery by remember { mutableStateOf("") }
    val labelAll = stringResource(R.string.label_all)
    var selectedCategory by remember { mutableStateOf(labelAll) }
    val categories by viewModel.getCategories("PRODUCT").collectAsState(initial = emptyList())

    val filteredItems = remember(searchQuery, selectedCategory, stockItems) {
        stockItems.filter {
            (it.productName.contains(searchQuery, ignoreCase = true) || it.category.contains(searchQuery, ignoreCase = true)) &&
            (selectedCategory == labelAll || it.category == selectedCategory)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.hint_search)) },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true,
                    colors = CaisseTextFieldDefaults.outlinedTextFieldColors()
                )
                
                ScrollableTabRow(
                    selectedTabIndex = if (selectedCategory == labelAll) 0 else categories.indexOfFirst { it.name == selectedCategory } + 1,
                    edgePadding = 0.dp,
                    containerColor = Color.Transparent,
                    divider = {},
                    indicator = {}
                ) {
                    Tab(
                        selected = selectedCategory == labelAll,
                        onClick = { selectedCategory = labelAll },
                        text = { Text(stringResource(R.string.label_all), style = MaterialTheme.typography.labelSmall) }
                    )
                    categories.forEach { category ->
                        Tab(
                            selected = selectedCategory == category.name,
                            onClick = { selectedCategory = category.name },
                            text = { Text(category.name, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        },
        text = {
            Box(modifier = Modifier.heightIn(max = 400.dp)) {
                if (filteredItems.isEmpty()) {
                    Text(stringResource(R.string.error_no_product_found), modifier = Modifier.padding(16.dp), color = Color.Gray)
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(filteredItems) { item ->
                            Card(
                                onClick = { onProductSelected(item, false) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    val price = if (isCustomerVip && item.vipPrice > 0) item.vipPrice else item.unitPrice
                                    Text(item.productName, fontWeight = FontWeight.Bold)
                                    Text(stringResource(R.string.label_price_unit_sep, price.toString(), currency, item.unit), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            if (item.isBulk && item.retailUnit != null) {
                                Card(
                                    onClick = { onProductSelected(item, true) },
                                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 4.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.SubdirectoryArrowRight, null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(stringResource(R.string.label_retail, item.retailUnit!!), style = MaterialTheme.typography.labelMedium)
                                            Text(stringResource(R.string.label_price_f, item.retailPrice.toString()), style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        }
    )
}

@Composable
fun CartRow(
    item: com.reconsiliation.caisse.ui.viewmodel.CartItem,
    onUpdateQuantity: (Double) -> Unit,
    onRemove: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.product.productName, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(
                    text = stringResource(
                        R.string.label_price_unit_sep,
                        FormatUtil.formatCurrency(item.priceAtSale),
                        "", // Currency is already in formatCurrency usually
                        if (item.isRetail) item.product.retailUnit!! else item.product.unit
                    ).replace("/ ", "/"),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { onUpdateQuantity(item.quantity - 1) },
                    modifier = Modifier.size(32.dp).testTag("cart_item_minus_${item.product.id}")
                ) {
                    Icon(Icons.Default.Remove, null, tint = Primary)
                }
                
                Text(
                    text = if (item.quantity % 1.0 == 0.0) item.quantity.toInt().toString() else "%.2f".format(item.quantity),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp).testTag("cart_item_qty_${item.product.id}")
                )

                IconButton(
                    onClick = { onUpdateQuantity(item.quantity + 1) },
                    modifier = Modifier.size(32.dp).testTag("cart_item_plus_${item.product.id}")
                ) {
                    Icon(Icons.Default.Add, null, tint = Primary)
                }
            }

            Spacer(modifier = Modifier.width(8.dp))
            
            Column(horizontalAlignment = Alignment.End) {
                Text(FormatUtil.formatCurrency(item.priceAtSale * item.quantity), color = Primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(32.dp).testTag("cart_item_remove_${item.product.id}")
                ) {
                    Icon(Icons.Default.Delete, null, tint = Color.Gray, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
