package com.reconsiliation.caisse.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.reconsiliation.caisse.data.local.entity.ExpenseEntity
import com.reconsiliation.caisse.ui.components.AddCategoryDialog
import com.reconsiliation.caisse.ui.components.CaisseTextFieldDefaults
import com.reconsiliation.caisse.ui.theme.Primary
import com.reconsiliation.caisse.ui.theme.RedError
import com.reconsiliation.caisse.ui.viewmodel.MainViewModel
import com.reconsiliation.caisse.utils.FormatUtil
import androidx.compose.foundation.clickable
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseScreen(navController: NavController) {
    val viewModel: MainViewModel = viewModel()
    val expenses by viewModel.allExpenses.collectAsState()
    
    var selectedCategory by remember { mutableStateOf("Tout") }
    var searchQuery by remember { mutableStateOf("") }
    val categories by viewModel.getCategories("EXPENSE").collectAsState(initial = emptyList())

    val filteredExpenses = remember(expenses, selectedCategory, searchQuery) {
        expenses.filter {
            (selectedCategory == "Tout" || it.category == selectedCategory) &&
            (it.label.contains(searchQuery, ignoreCase = true))
        }
    }
    
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dépenses") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }, containerColor = Primary) {
                Icon(Icons.Default.Add, contentDescription = "Nouvelle Dépense", tint = Color.White)
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Rechercher une dépense...") },
                modifier = Modifier.fillMaxWidth().padding(16.dp, 8.dp),
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = CaisseTextFieldDefaults.outlinedTextFieldColors()
            )

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

            if (filteredExpenses.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Aucune dépense correspondante")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredExpenses) { expense ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(expense.label, style = MaterialTheme.typography.titleMedium)
                                    Text("${expense.category} • ${FormatUtil.formatDate(expense.date)}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                }
                                Text(FormatUtil.formatCurrency(expense.amount), color = RedError, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                IconButton(onClick = { viewModel.deleteExpense(expense) }) {
                                    Icon(Icons.Default.Delete, null, tint = Color.Gray)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showAddDialog) {
            AddExpenseDialog(
                onDismiss = { showAddDialog = false },
                onConfirm = { label, amt, cat ->
                    viewModel.addExpense(ExpenseEntity(label = label, amount = amt, category = cat, date = Date()))
                    showAddDialog = false
                }
            )
        }
    }
}

@Composable
fun AddExpenseDialog(onDismiss: () -> Unit, onConfirm: (String, Double, String) -> Unit) {
    val viewModel: MainViewModel = viewModel()
    var label by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Divers") }
    val expenseCategories by viewModel.getCategories("EXPENSE").collectAsState(initial = emptyList())
    var expandedCategory by remember { mutableStateOf(false) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nouvelle dépense") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val textFieldColors = CaisseTextFieldDefaults.outlinedTextFieldColors()
                OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("Libellé") }, modifier = Modifier.fillMaxWidth(), colors = textFieldColors)
                OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Montant (FCFA)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), colors = textFieldColors)
                
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = category,
                            onValueChange = { },
                            readOnly = true,
                            label = { Text("Catégorie") },
                            modifier = Modifier.fillMaxWidth().clickable { expandedCategory = true },
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
                            colors = textFieldColors
                        )
                        DropdownMenu(expanded = expandedCategory, onDismissRequest = { expandedCategory = false }) {
                            expenseCategories.forEach { cat ->
                                DropdownMenuItem(text = { Text(cat.name) }, onClick = { category = cat.name; expandedCategory = false })
                            }
                            if (expenseCategories.isEmpty()) {
                                DropdownMenuItem(text = { Text("Aucune catégorie") }, onClick = { expandedCategory = false })
                            }
                        }
                    }
                    IconButton(onClick = { showAddCategoryDialog = true }, modifier = Modifier.padding(top = 8.dp)) {
                        Icon(Icons.Default.Add, "Nouvelle catégorie", tint = Primary)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val amt = amount.toDoubleOrNull() ?: 0.0
                if (label.isNotBlank() && amt > 0) onConfirm(label, amt, category)
            }) { Text("Ajouter") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )

    if (showAddCategoryDialog) {
        AddCategoryDialog(
            type = "EXPENSE",
            onDismiss = { showAddCategoryDialog = false },
            onConfirm = { name ->
                viewModel.addCategory(name, "EXPENSE")
                category = name
                showAddCategoryDialog = false
            }
        )
    }
}
