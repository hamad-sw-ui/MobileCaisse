package com.reconsiliation.caisse.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.reconsiliation.caisse.data.local.entity.StockEntity
import com.reconsiliation.caisse.data.local.entity.VenteEntity
import com.reconsiliation.caisse.data.local.entity.VenteItemEntity
import com.reconsiliation.caisse.ui.components.BigButton
import com.reconsiliation.caisse.ui.components.CaisseTextFieldDefaults
import com.reconsiliation.caisse.ui.scanner.BarcodeScannerDialog
import com.reconsiliation.caisse.ui.navigation.Screen
import com.reconsiliation.caisse.ui.theme.GreenSuccess
import com.reconsiliation.caisse.ui.theme.OrangeWarning
import com.reconsiliation.caisse.ui.theme.Primary
import com.reconsiliation.caisse.ui.viewmodel.MainViewModel
import com.reconsiliation.caisse.utils.FormatUtil
import androidx.compose.ui.res.stringResource
import com.reconsiliation.caisse.R
import kotlinx.coroutines.launch
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewSaleScreen(navController: NavController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val viewModel: MainViewModel = viewModel()
    val boutique by viewModel.boutique.collectAsState()
    val operatorName = if (boutique?.operator == "MTN") "MTN/OM" else boutique?.operator ?: "MTN/OM"
    val stockItems by viewModel.allStock.collectAsState()
    val cart by viewModel.cart.collectAsState()
    val currency by viewModel.currency.collectAsState()
    val lastVenteId by viewModel.lastConfirmedVenteId.collectAsState()

    val isDataLoaded by viewModel.isDataLoaded.collectAsState()
    val activeSession by viewModel.activeSession.collectAsState()

    LaunchedEffect(activeSession, isDataLoaded) {
        if (isDataLoaded && activeSession == null) {
            navController.popBackStack()
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var customerPhone by remember { mutableStateOf("") }
    var paymentMethod by remember { mutableStateOf("CASH") }
    var operationType by remember { mutableStateOf("MARCHAND") } // MARCHAND, TRANSFERT, RETRAIT
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
        viewModel.updateCartPrices(isCustomerVip)
    }

    LaunchedEffect(customerPhone) {
        if (customerPhone.length >= 8) {
            val c = viewModel.getCustomerByPhone(customerPhone)
            customerLoyalty = c?.loyaltyPoints
            customerCreditBalance = c?.creditBalance ?: 0.0
            isCustomerVip = c?.isVip ?: false
        } else {
            customerLoyalty = null
            customerCreditBalance = 0.0
            isCustomerVip = false
        }
    }

    var showScanner by remember { mutableStateOf(false) }
    var continuousScan by remember { mutableStateOf(false) }
    var unknownBarcode by remember { mutableStateOf<String?>(null) }

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
        topBar = { 
            TopAppBar(
                title = { Text(stringResource(R.string.title_new_sale)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
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
                    value = "Rechercher...",
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
                                viewModel.addToCart(retailItem, product.retailPrice, isRetail = true, parentId = product.id, factor = product.conversionFactor)
                            } else {
                                val price = if (isCustomerVip && product.vipPrice > 0) product.vipPrice else product.unitPrice
                                viewModel.addToCart(product, price)
                            }
                            showProductPicker = false
                        }
                    )
                }
                
                IconButton(
                    onClick = { 
                        viewModel.recordActivity()
                        showScanner = true 
                    },
                    modifier = Modifier.size(48.dp).background(Primary, RoundedCornerShape(12.dp)),
                    colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan", modifier = Modifier.size(20.dp))
                }
            }

            // 2. MIDDLE: Cart List
            if (cart.isEmpty()) {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.ShoppingCart, null, tint = Color.LightGray, modifier = Modifier.size(48.dp))
                    Text(stringResource(R.string.cart_empty), color = Color.Gray)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    cart.forEach { item ->
                        CartRow(
                            item = item, 
                            onUpdateQuantity = { newQty -> 
                                viewModel.recordActivity()
                                if (newQty > 0) viewModel.updateCartItemQuantity(item, newQty)
                                else viewModel.removeFromCart(item)
                            },
                            onRemove = { 
                                viewModel.recordActivity()
                                viewModel.removeFromCart(item) 
                            }
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
                        onValueChange = { 
                            customerPhone = it
                            if (it.length >= 8) {
                                scope.launch {
                                    val c = viewModel.getCustomerByPhone(it)
                                    customerLoyalty = c?.loyaltyPoints
                                    isCustomerVip = c?.isVip ?: false
                                }
                            } else {
                                customerLoyalty = null
                                isCustomerVip = false
                            }
                        },
                        label = { Text("Client") },
                        modifier = Modifier.weight(1.3f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        colors = CaisseTextFieldDefaults.outlinedTextFieldColors(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = discountAmount,
                        onValueChange = { discountAmount = it },
                        label = { Text("Remise") },
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
                    
                    Text("TOTAL: ", style = MaterialTheme.typography.titleMedium)
                    Text(FormatUtil.formatCurrency(finalTotal, currency), style = MaterialTheme.typography.headlineSmall, color = Primary, fontWeight = FontWeight.Bold)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf("CASH", operatorName, "MIXED", "CREDIT").forEach { method ->
                        FilterChip(
                            selected = paymentMethod == method || (method == operatorName && paymentMethod == "MOMO"),
                            onClick = { 
                                paymentMethod = if (method == operatorName) "MOMO" else method 
                                if (paymentMethod != "MIXED") {
                                    amountCashStr = ""
                                    amountMomoStr = ""
                                }
                            },
                            label = { Text(if (method == "MOMO" || method == operatorName) operatorName else method, fontSize = 12.sp) },
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
                        Text("Type :", style = MaterialTheme.typography.labelMedium)
                        AssistChip(
                            onClick = { operationType = "MARCHAND" },
                            label = { Text("Marchand", fontSize = 11.sp) },
                            leadingIcon = { Icon(Icons.Default.Storefront, null, modifier = Modifier.size(14.dp)) },
                            colors = if (operationType == "MARCHAND") AssistChipDefaults.assistChipColors(containerColor = Primary.copy(alpha = 0.1f)) else AssistChipDefaults.assistChipColors()
                        )
                        AssistChip(
                            onClick = { operationType = "TRANSFERT" },
                            label = { Text("Envoi", fontSize = 11.sp) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Send, null, modifier = Modifier.size(14.dp)) },
                            colors = if (operationType == "TRANSFERT") AssistChipDefaults.assistChipColors(containerColor = Primary.copy(alpha = 0.1f)) else AssistChipDefaults.assistChipColors()
                        )
                        AssistChip(
                            onClick = { operationType = "RETRAIT" },
                            label = { Text("Retrait", fontSize = 11.sp) },
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
                            label = { Text("Cash") },
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
                    text = "VALIDER LA VENTE",
                    onClick = {
                        viewModel.recordActivity()
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
                                        scope.launch { snackbarHostState.showSnackbar("Total incorrect") }
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
                            viewModel.addVenteWithItems(vente, items)
                        }
                    },
                    containerColor = GreenSuccess
                )
            }
        }

        if (lastVenteId != null) {
            AlertDialog(
                onDismissRequest = { viewModel.clearLastVenteId() },
                title = { Text("Vente Confirmée") },
                text = { Text("Voulez-vous imprimer ou envoyer le reçu par WhatsApp ?") },
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
                            Text("Imprimer")
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
                                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(android.content.Intent.EXTRA_TEXT, text)
                                            `package` = "com.whatsapp"
                                        }
                                        try {
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            context.startActivity(android.content.Intent.createChooser(intent, "Partager via"))
                                        }
                                    }
                                    viewModel.clearLastVenteId()
                                    navController.popBackStack()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Share, null)
                            Spacer(Modifier.width(8.dp))
                            Text("WhatsApp")
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { 
                        viewModel.clearLastVenteId()
                        navController.popBackStack()
                    }) { Text("Terminer") }
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
                            val price = if (isCustomerVip && product.vipPrice > 0) product.vipPrice else product.unitPrice
                            viewModel.addToCart(product, price)
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
                title = { Text("Produit Inconnu") },
                text = { Text("Le code-barres $unknownBarcode n'est pas dans votre stock.") },
                confirmButton = {
                    Button(onClick = {
                        navController.navigate(Screen.Stock.route + "?barcode=$unknownBarcode")
                        unknownBarcode = null
                    }) { Text("Créer") }
                },
                dismissButton = {
                    TextButton(onClick = { unknownBarcode = null }) { Text("Annuler") }
                }
            )
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
    val viewModel: MainViewModel = viewModel()
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Tout") }
    val categories by viewModel.getCategories("PRODUCT").collectAsState(initial = emptyList())

    val filteredItems = remember(searchQuery, selectedCategory, stockItems) {
        stockItems.filter { 
            (it.productName.contains(searchQuery, ignoreCase = true) || it.category.contains(searchQuery, ignoreCase = true)) &&
            (selectedCategory == "Tout" || it.category == selectedCategory)
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
                    placeholder = { Text("Rechercher...") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true,
                    colors = CaisseTextFieldDefaults.outlinedTextFieldColors()
                )
                
                ScrollableTabRow(
                    selectedTabIndex = if (selectedCategory == "Tout") 0 else categories.indexOfFirst { it.name == selectedCategory } + 1,
                    edgePadding = 0.dp,
                    containerColor = Color.Transparent,
                    divider = {},
                    indicator = {}
                ) {
                    Tab(
                        selected = selectedCategory == "Tout",
                        onClick = { selectedCategory = "Tout" },
                        text = { Text("Tout", style = MaterialTheme.typography.labelSmall) }
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
                    Text("Aucun produit trouvé", modifier = Modifier.padding(16.dp), color = Color.Gray)
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
                                    Text("$price $currency / ${item.unit}", style = MaterialTheme.typography.bodySmall)
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
                                            Text("Détail: ${item.retailUnit}", style = MaterialTheme.typography.labelMedium)
                                            Text("${item.retailPrice} $currency", style = MaterialTheme.typography.labelSmall)
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
            TextButton(onClick = onDismiss) { Text("Fermer") }
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
                Text("${FormatUtil.formatCurrency(item.priceAtSale)} / ${if (item.isRetail) item.product.retailUnit else item.product.unit}", style = MaterialTheme.typography.bodySmall)
            }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onUpdateQuantity(item.quantity - 1) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Remove, null, tint = Primary)
                }
                
                Text(
                    text = if (item.quantity % 1.0 == 0.0) item.quantity.toInt().toString() else "%.2f".format(item.quantity),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                IconButton(onClick = { onUpdateQuantity(item.quantity + 1) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Add, null, tint = Primary)
                }
            }

            Spacer(modifier = Modifier.width(8.dp))
            
            Column(horizontalAlignment = Alignment.End) {
                Text(FormatUtil.formatCurrency(item.priceAtSale * item.quantity), color = Primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, null, tint = Color.Gray, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
