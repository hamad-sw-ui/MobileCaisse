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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.reconsiliation.caisse.data.local.entity.ExpenseEntity
import com.reconsiliation.caisse.ui.components.AddCategoryDialog
import com.reconsiliation.caisse.ui.components.CaisseTextFieldDefaults
import com.reconsiliation.caisse.ui.theme.Primary
import com.reconsiliation.caisse.ui.theme.RedError
import com.reconsiliation.caisse.ui.viewmodel.MainViewModel
import com.reconsiliation.caisse.ui.viewmodel.ExpenseViewModel
import com.reconsiliation.caisse.ui.state.ExpenseUiState
import com.reconsiliation.caisse.utils.FormatUtil
import androidx.compose.foundation.clickable
import java.util.Date
import androidx.compose.ui.res.stringResource
import com.reconsiliation.caisse.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseScreen(navController: NavController) {
    val viewModel: ExpenseViewModel = hiltViewModel()
    val expenses by viewModel.allExpenses.collectAsState()
    
    val labelAll = stringResource(R.string.filter_all)
    var selectedCategory by remember { mutableStateOf(labelAll) }
    var searchQuery by remember { mutableStateOf("") }
    val categories by viewModel.getCategories().collectAsState(initial = emptyList())

    val filteredExpenses = remember(expenses, selectedCategory, searchQuery) {
        expenses.filter {
            (selectedCategory == labelAll || it.category == selectedCategory) &&
            (it.label.contains(searchQuery, ignoreCase = true))
        }
    }
    
    var showAddDialog by remember { mutableStateOf(false) }
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val errorMessage = stringResource(R.string.error_expense_add)

    LaunchedEffect(uiState) {
        when (uiState) {
            is ExpenseUiState.Error -> {
                snackbarHostState.showSnackbar(errorMessage)
                viewModel.clearUiState()
            }
            is ExpenseUiState.Success -> viewModel.clearUiState()
            else -> Unit
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_expenses)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }, containerColor = Primary) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.title_expenses), tint = Color.White)
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(stringResource(R.string.hint_search_expense)) },
                modifier = Modifier.fillMaxWidth().padding(16.dp, 8.dp),
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                colors = CaisseTextFieldDefaults.outlinedTextFieldColors()
            )

            // Category Filter Bar
            ScrollableTabRow(
                selectedTabIndex = if (selectedCategory == labelAll) 0 else categories.indexOfFirst { it.name == selectedCategory } + 1,
                edgePadding = 16.dp,
                containerColor = MaterialTheme.colorScheme.surface,
                divider = {}
            ) {
                Tab(
                    selected = selectedCategory == labelAll,
                    onClick = { selectedCategory = labelAll },
                    text = { Text(stringResource(R.string.filter_all)) }
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
                    Text(stringResource(R.string.error_no_expense_period))
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
                                    Text(stringResource(R.string.label_category_date_sep, expense.category, FormatUtil.formatDate(expense.date)), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
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
                    viewModel.addExpense(label, amt, cat)
                    showAddDialog = false
                }
            )
        }
    }
}

@Composable
fun AddExpenseDialog(onDismiss: () -> Unit, onConfirm: (String, Double, String) -> Unit) {
    val viewModel: ExpenseViewModel = hiltViewModel()
    var label by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    val defaultCategory = stringResource(R.string.category_misc)
    var category by remember { mutableStateOf(defaultCategory) }
    val expenseCategories by viewModel.getCategories().collectAsState(initial = emptyList())
    var expandedCategory by remember { mutableStateOf(false) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_new_expense_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val textFieldColors = CaisseTextFieldDefaults.outlinedTextFieldColors()
                OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text(stringResource(R.string.label_expense_label)) }, modifier = Modifier.fillMaxWidth(), colors = textFieldColors)
                OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text(stringResource(R.string.label_amount_fcfa)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), colors = textFieldColors)
                
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = category,
                            onValueChange = { },
                            readOnly = true,
                            label = { Text(stringResource(R.string.label_category)) },
                            modifier = Modifier.fillMaxWidth().clickable { expandedCategory = true },
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
                            colors = textFieldColors
                        )
                        DropdownMenu(expanded = expandedCategory, onDismissRequest = { expandedCategory = false }) {
                            expenseCategories.forEach { cat ->
                                DropdownMenuItem(text = { Text(cat.name) }, onClick = { category = cat.name; expandedCategory = false })
                            }
                            if (expenseCategories.isEmpty()) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.no_category_registered)) }, onClick = { expandedCategory = false })
                            }
                        }
                    }
                    IconButton(onClick = { showAddCategoryDialog = true }, modifier = Modifier.padding(top = 8.dp)) {
                        Icon(Icons.Default.Add, stringResource(R.string.dialog_new_category), tint = Primary)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val amt = amount.toDoubleOrNull() ?: 0.0
                if (label.isNotBlank() && amt > 0) onConfirm(label, amt, category)
            }) { Text(stringResource(R.string.action_add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )

    if (showAddCategoryDialog) {
        AddCategoryDialog(
            type = "EXPENSE",
            onDismiss = { showAddCategoryDialog = false },
            onConfirm = { name ->
                viewModel.addCategory(name)
                category = name
                showAddCategoryDialog = false
            }
        )
    }
}
