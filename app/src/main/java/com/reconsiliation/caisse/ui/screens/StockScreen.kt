package com.reconsiliation.caisse.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import androidx.navigation.NavController
import androidx.compose.ui.res.stringResource
import com.reconsiliation.caisse.R
import com.reconsiliation.caisse.data.local.entity.StockEntity
import com.reconsiliation.caisse.data.local.entity.RecipeEntity
import com.reconsiliation.caisse.ui.components.AddCategoryDialog
import com.reconsiliation.caisse.ui.components.CaisseTextFieldDefaults
import com.reconsiliation.caisse.ui.navigation.Screen
import com.reconsiliation.caisse.ui.scanner.BarcodeScannerDialog
import com.reconsiliation.caisse.ui.theme.OrangeWarning
import com.reconsiliation.caisse.ui.theme.Primary
import com.reconsiliation.caisse.ui.theme.RedError
import com.reconsiliation.caisse.ui.viewmodel.MainViewModel
import com.reconsiliation.caisse.utils.FormatUtil

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun StockScreen(navController: NavController, initialBarcode: String? = null) {
    val viewModel: MainViewModel = viewModel()
    val boutique by viewModel.boutique.collectAsState()
    val stockItems by viewModel.allStock.collectAsState()
    val isManager = viewModel.isManager()
    
    var selectedCategory by remember { mutableStateOf("Tout") }
    var searchQuery by remember { mutableStateOf("") }
    val categories by viewModel.getCategories("PRODUCT").collectAsState(initial = emptyList())
    
    val filteredItems = remember(stockItems, selectedCategory, searchQuery) {
        stockItems.filter { 
            (selectedCategory == "Tout" || it.category == selectedCategory) &&
            (it.productName.contains(searchQuery, ignoreCase = true) || it.barcode?.contains(searchQuery) == true)
        }
    }

    val totalPurchaseValue = remember(filteredItems) { filteredItems.sumOf { it.quantity * it.purchasePrice } }
    val totalSaleValue = remember(filteredItems) { filteredItems.sumOf { it.quantity * it.unitPrice } }
    
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    
    var showAddDialog by remember { mutableStateOf(initialBarcode != null) }
    var itemToEdit by remember { mutableStateOf<StockEntity?>(null) }
    var itemToDelete by remember { mutableStateOf<StockEntity?>(null) }
    var itemToReportLoss by remember { mutableStateOf<StockEntity?>(null) }
    var itemForRecipe by remember { mutableStateOf<StockEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_stock)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FloatingActionButton(onClick = { navController.navigate(Screen.SupplierInflow.route) }, containerColor = MaterialTheme.colorScheme.secondary) {
                    Icon(Icons.Default.Add, contentDescription = "Arrivage", tint = Color.White)
                }
                if (isManager) {
                    FloatingActionButton(onClick = { showAddDialog = true }, containerColor = Primary) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_save), tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Search and Summary Row
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Rechercher un produit...") },
                    modifier = Modifier.weight(1f),
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = if (searchQuery.isNotEmpty()) {
                        { IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Clear, null) } }
                    } else null,
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = CaisseTextFieldDefaults.outlinedTextFieldColors()
                )
            }

            // Stock Value Summary
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Valeur Achat", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        Text(FormatUtil.formatCurrency(totalPurchaseValue), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Text("Valeur Vente", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        Text(FormatUtil.formatCurrency(totalSaleValue), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Primary)
                    }
                }
            }

            // Category Filter Bar
            ScrollableTabRow(
                selectedTabIndex = if (selectedCategory == "Tout") 0 else categories.indexOfFirst { it.name == selectedCategory } + 1,
                edgePadding = 16.dp,
                containerColor = MaterialTheme.colorScheme.surface,
                divider = {}
            ) {
                Tab(
                    selected = selectedCategory == "Tout",
                    onClick = { selectedCategory = "Tout" },
                    text = { Text("Tout") }
                )
                categories.forEach { category ->
                    Tab(
                        selected = selectedCategory == category.name,
                        onClick = { selectedCategory = category.name },
                        text = { Text(category.name) }
                    )
                }
            }

            if (filteredItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Aucun article correspondant")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredItems) { item ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = { /* Normal select? */ },
                                    onLongClick = {
                                        // Rapid Checkout Logic (Point 4)
                                        val vente = com.reconsiliation.caisse.data.local.entity.VenteEntity(
                                            amount = item.unitPrice,
                                            description = "${item.productName} x1 (Vente Rapide)",
                                            date = java.util.Date(),
                                            paymentMethod = "CASH",
                                            status = "CONFIRMED",
                                            amountCash = item.unitPrice
                                        )
                                        val venteItem = com.reconsiliation.caisse.data.local.entity.VenteItemEntity(
                                            venteId = 0,
                                            productId = item.id,
                                            productName = item.productName,
                                            quantity = 1.0,
                                            unitPrice = item.unitPrice,
                                            purchasePrice = item.purchasePrice
                                        )
                                        viewModel.addVenteWithItems(vente, listOf(venteItem))
                                        scope.launch { snackbarHostState.showSnackbar("Vente Rapide : ${item.productName} encaissé !") }
                                    }
                                )
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(item.productName, style = MaterialTheme.typography.titleMedium)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = item.category,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    Text("Stock: ${item.quantity} ${item.unit}", color = if (item.quantity <= item.alertThreshold) RedError else MaterialTheme.colorScheme.onSurface)
                                    Text("Code: ${item.barcode ?: "Non défini"}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                }
                                
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(FormatUtil.formatCurrency(item.unitPrice), style = MaterialTheme.typography.bodyLarge, color = Primary, fontWeight = FontWeight.Bold)
                                    Row {
                                        IconButton(onClick = {
                                            // Point 6: Print Shelf Label
                                            boutique?.printerAddress?.let { address ->
                                                viewModel.testPrint(address) // Or dedicated printShelfLabel logic
                                            }
                                        }) {
                                            Icon(Icons.Default.Label, "Étiquette", tint = Color.Gray)
                                        }
                                        IconButton(onClick = { navController.navigate(Screen.StockMovements.createRoute(item.id)) }) {
                                            Icon(Icons.Default.Info, "Mouvements", tint = Color.Gray)
                                        }
                                        IconButton(onClick = { navController.navigate(Screen.PriceHistory.createRoute(item.id)) }) {
                                            Icon(Icons.Default.History, "Historique des prix", tint = Color.Gray)
                                        }
                                        IconButton(onClick = { itemForRecipe = item }) {
                                            Icon(Icons.Default.Kitchen, "Composition", tint = Primary)
                                        }
                                        IconButton(onClick = { itemToReportLoss = item }) {
                                            Icon(Icons.Default.WarningAmber, stringResource(R.string.loss_declared), tint = OrangeWarning)
                                        }
                                        if (isManager) {
                                            IconButton(onClick = { itemToEdit = item }) {
                                                Icon(Icons.Default.Edit, null, tint = Primary)
                                            }
                                            IconButton(onClick = { itemToDelete = item }) {
                                                Icon(Icons.Default.Delete, null, tint = RedError)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showAddDialog) {
            AddProductDialog(
                initialItem = if (initialBarcode != null) StockEntity(productName = "", quantity = 0.0, unitPrice = 0.0, alertThreshold = 5.0, barcode = initialBarcode) else null,
                onDismiss = { showAddDialog = false },
                onConfirm = { stock ->
                    viewModel.updateStock(stock)
                    showAddDialog = false
                }
            )
        }

        if (itemToEdit != null) {
            AddProductDialog(
                initialItem = itemToEdit,
                onDismiss = { itemToEdit = null },
                onConfirm = { stock ->
                    viewModel.updateStock(stock)
                    itemToEdit = null
                }
            )
        }

        if (itemToReportLoss != null) {
            ReportLossDialog(
                productName = itemToReportLoss!!.productName,
                onDismiss = { itemToReportLoss = null },
                onConfirm = { qty, reason ->
                    viewModel.adjustStock(itemToReportLoss!!, itemToReportLoss!!.quantity - qty, reason)
                    itemToReportLoss = null
                    val msg = "Perte enregistrée"
                    scope.launch { snackbarHostState.showSnackbar(msg) }
                }
            )
        }

        if (itemForRecipe != null) {
            RecipeDialog(
                parentProduct = itemForRecipe!!,
                allStock = stockItems,
                onDismiss = { itemForRecipe = null }
            )
        }

        if (itemToDelete != null) {
            AlertDialog(
                onDismissRequest = { itemToDelete = null },
                title = { Text("Supprimer le produit ?") },
                text = { Text("Voulez-vous vraiment supprimer ${itemToDelete!!.productName} ?") },
                confirmButton = {
                    Button(onClick = { viewModel.deleteStock(itemToDelete!!); itemToDelete = null }, colors = ButtonDefaults.buttonColors(containerColor = RedError)) {
                        Text("Supprimer")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { itemToDelete = null }) { Text("Annuler") }
                }
            )
        }
    }
}

@Composable
fun RecipeDialog(parentProduct: StockEntity, allStock: List<StockEntity>, onDismiss: () -> Unit) {
    val viewModel: MainViewModel = viewModel()
    val recipeItems by viewModel.getRecipeForProduct(parentProduct.id).collectAsState(initial = emptyList())
    var showAddIngredient by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Composition de : ${parentProduct.productName}") },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                if (recipeItems.isEmpty()) {
                    Text("Ce produit n'a pas encore d'ingrédients définis.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                } else {
                    LazyColumn(modifier = Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(recipeItems) { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.ingredient.productName, fontWeight = FontWeight.Bold)
                                    Text("Quantité à déduire : ${item.recipe.quantityRequired} ${item.ingredient.unit}", style = MaterialTheme.typography.labelSmall)
                                }
                                IconButton(onClick = { viewModel.deleteRecipeComponent(item.recipe) }) {
                                    Icon(Icons.Default.Delete, null, tint = RedError)
                                }
                            }
                        }
                    }
                }
                
                Button(
                    onClick = { showAddIngredient = true },
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                ) {
                    Icon(Icons.Default.Add, null)
                    Text("Ajouter un ingrédient")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Fermer") }
        }
    )

    if (showAddIngredient) {
        AddIngredientDialog(
            allStock = allStock.filter { it.id != parentProduct.id },
            onDismiss = { showAddIngredient = false },
            onConfirm = { ingredientId, qty ->
                viewModel.addRecipeComponent(parentProduct.id, ingredientId, qty)
                showAddIngredient = false
            }
        )
    }
}

@Composable
fun AddIngredientDialog(allStock: List<StockEntity>, onDismiss: () -> Unit, onConfirm: (Long, Double) -> Unit) {
    var selectedProduct by remember { mutableStateOf<StockEntity?>(null) }
    var qty by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choisir l'ingrédient") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box {
                    OutlinedTextField(
                        value = selectedProduct?.productName ?: "Sélectionner...",
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth().clickable { expanded = true },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
                        colors = CaisseTextFieldDefaults.outlinedTextFieldColors()
                    )
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        allStock.forEach { product ->
                            DropdownMenuItem(
                                text = { Text(product.productName) },
                                onClick = { selectedProduct = product; expanded = false }
                            )
                        }
                    }
                }
                
                OutlinedTextField(
                    value = qty,
                    onValueChange = { qty = it },
                    label = { Text("Quantité nécessaire") },
                    placeholder = { Text("Ex: 0.1 pour 100g/ml") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    colors = CaisseTextFieldDefaults.outlinedTextFieldColors()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val q = qty.toDoubleOrNull() ?: 0.0
                    if (selectedProduct != null && q > 0) {
                        onConfirm(selectedProduct!!.id, q)
                    }
                },
                enabled = selectedProduct != null && qty.isNotBlank()
            ) { Text("Ajouter") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}

@Composable
fun ReportLossDialog(productName: String, onDismiss: () -> Unit, onConfirm: (Double, String) -> Unit) {
    var qty by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("Casse/Perte") }
    val textFieldColors = CaisseTextFieldDefaults.outlinedTextFieldColors()
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Déclarer une perte") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Produit : $productName", fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = qty,
                    onValueChange = { qty = it },
                    label = { Text("Quantité perdue") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    colors = textFieldColors
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Raison (optionnel)") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = textFieldColors
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val q = qty.toDoubleOrNull() ?: 0.0
                    if (q > 0) onConfirm(q, reason)
                },
                colors = ButtonDefaults.buttonColors(containerColor = RedError)
            ) { Text("Confirmer la perte") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}

@Composable
fun AddProductDialog(initialItem: StockEntity? = null, onDismiss: () -> Unit, onConfirm: (StockEntity) -> Unit) {
    val viewModel: MainViewModel = viewModel()
    val textFieldColors = CaisseTextFieldDefaults.outlinedTextFieldColors()

    var name by remember { mutableStateOf(initialItem?.productName ?: "") }
    var qty by remember { mutableStateOf(initialItem?.quantity?.toString() ?: "") }
    var sPrice by remember { mutableStateOf(initialItem?.unitPrice?.toString() ?: "") }
    var pPrice by remember { mutableStateOf(initialItem?.purchasePrice?.toString() ?: "") }
    var vPrice by remember { mutableStateOf(initialItem?.vipPrice?.toString() ?: "0") }
    var alert by remember { mutableStateOf(initialItem?.alertThreshold?.toString() ?: "5") }
    var barcode by remember { mutableStateOf(initialItem?.barcode ?: "") }
    
    var category by remember { mutableStateOf(initialItem?.category ?: "Général") }
    val productCategories by viewModel.getCategories("PRODUCT").collectAsState(initial = emptyList())
    var expandedCategory by remember { mutableStateOf(false) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }

    var unit by remember { mutableStateOf(initialItem?.unit ?: "pcs") }
    
    var isBulk by remember { mutableStateOf(initialItem?.isBulk ?: false) }
    var retailUnit by remember { mutableStateOf(initialItem?.retailUnit ?: "") }
    var conversionFactor by remember { mutableStateOf(initialItem?.conversionFactor?.toString() ?: "1.0") }
    var retailPrice by remember { mutableStateOf(initialItem?.retailPrice?.toString() ?: "0") }
    
    var showScanner by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialItem == null) "Ajouter un produit" else "Modifier le produit") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nom du produit") }, colors = textFieldColors, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = category,
                            onValueChange = { },
                            readOnly = true,
                            label = { Text("Catégorie") },
                            colors = textFieldColors,
                            modifier = Modifier.fillMaxWidth().clickable { expandedCategory = true },
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) }
                        )
                        DropdownMenu(expanded = expandedCategory, onDismissRequest = { expandedCategory = false }) {
                            productCategories.forEach { cat ->
                                DropdownMenuItem(text = { Text(cat.name) }, onClick = { category = cat.name; expandedCategory = false })
                            }
                            if (productCategories.isEmpty()) {
                                DropdownMenuItem(text = { Text("Aucune catégorie") }, onClick = { expandedCategory = false })
                            }
                        }
                    }
                    IconButton(
                        onClick = { showAddCategoryDialog = true },
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Icon(Icons.Default.Add, "Nouvelle catégorie", tint = Primary)
                    }
                }
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isBulk, onCheckedChange = { isBulk = it })
                    Text("Vente au détail (ex: Sac -> Kg)", style = MaterialTheme.typography.bodySmall)
                }
                
                if (isBulk) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = retailUnit, onValueChange = { retailUnit = it }, label = { Text("Unité détail") }, colors = textFieldColors, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = conversionFactor, onValueChange = { conversionFactor = it }, label = { Text("Facteur conv.") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = textFieldColors, modifier = Modifier.weight(1f))
                    }
                    OutlinedTextField(value = retailPrice, onValueChange = { retailPrice = it }, label = { Text("Prix de vente au détail") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = textFieldColors, modifier = Modifier.fillMaxWidth())
                }
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                OutlinedTextField(value = qty, onValueChange = { qty = it }, label = { Text("Quantité en stock ($unit)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = textFieldColors, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = pPrice, onValueChange = { pPrice = it }, label = { Text("Prix d'achat") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = textFieldColors, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = sPrice, onValueChange = { sPrice = it }, label = { Text("Prix de vente") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = textFieldColors, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(value = vPrice, onValueChange = { vPrice = it }, label = { Text("Prix VIP (Optionnel)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = textFieldColors, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = alert, onValueChange = { alert = it }, label = { Text("Seuil d'alerte") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = textFieldColors, modifier = Modifier.fillMaxWidth())
                
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = barcode, onValueChange = { barcode = it }, label = { Text("Code-barres") }, modifier = Modifier.weight(1f), colors = textFieldColors)
                    IconButton(onClick = { showScanner = true }) {
                        Icon(Icons.Default.QrCodeScanner, null, tint = Primary)
                    }
                }
                
                OutlinedTextField(value = unit, onValueChange = { unit = it }, label = { Text("Unité (ex: pcs, kg)") }, colors = textFieldColors, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = {
                val pPriceDouble = pPrice.toDoubleOrNull() ?: 0.0
                val sPriceDouble = sPrice.toDoubleOrNull() ?: 0.0
                val vPriceDouble = vPrice.toDoubleOrNull() ?: 0.0
                val qtyDouble = qty.toDoubleOrNull() ?: 0.0
                val factorDouble = conversionFactor.toDoubleOrNull() ?: 1.0
                val rPriceDouble = retailPrice.toDoubleOrNull() ?: 0.0
                
                if (name.isNotBlank() && sPriceDouble > 0) {
                    onConfirm(
                        (initialItem ?: StockEntity(productName = name, quantity = qtyDouble, unitPrice = sPriceDouble, alertThreshold = 5.0)).copy(
                            productName = name,
                            quantity = qtyDouble,
                            unitPrice = sPriceDouble,
                            purchasePrice = pPriceDouble,
                            vipPrice = vPriceDouble,
                            alertThreshold = alert.toDoubleOrNull() ?: 5.0,
                            barcode = barcode.ifBlank { null },
                            category = category,
                            unit = unit,
                            isBulk = isBulk,
                            retailUnit = retailUnit.ifBlank { null },
                            conversionFactor = factorDouble,
                            retailPrice = rPriceDouble
                        )
                    )
                }
            }) { Text(if (initialItem == null) "Ajouter" else "Enregistrer") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )

    if (showScanner) {
        BarcodeScannerDialog(
            onDismiss = { showScanner = false },
            onBarcodeScanned = { code ->
                barcode = code
                showScanner = false
            }
        )
    }

    if (showAddCategoryDialog) {
        AddCategoryDialog(
            type = "PRODUCT",
            onDismiss = { showAddCategoryDialog = false },
            onConfirm = { name ->
                viewModel.addCategory(name, "PRODUCT")
                category = name
                showAddCategoryDialog = false
            }
        )
    }
}
