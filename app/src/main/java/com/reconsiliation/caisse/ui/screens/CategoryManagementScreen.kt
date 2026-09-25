package com.reconsiliation.caisse.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.reconsiliation.caisse.ui.components.AddCategoryDialog
import com.reconsiliation.caisse.ui.theme.Primary
import com.reconsiliation.caisse.ui.viewmodel.CategoryViewModel
import androidx.compose.ui.res.stringResource
import com.reconsiliation.caisse.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryManagementScreen(navController: NavController) {
    val viewModel: CategoryViewModel = hiltViewModel()
    var selectedType by remember { mutableStateOf("PRODUCT") }
    val categories by viewModel.getCategories(selectedType).collectAsState(initial = emptyList())
    
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_category_mgmt)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }, containerColor = Primary) {
                Icon(Icons.Default.Add, null, tint = Color.White)
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = if (selectedType == "PRODUCT") 0 else 1) {
                Tab(selected = selectedType == "PRODUCT", onClick = { selectedType = "PRODUCT" }, text = { Text(stringResource(R.string.tab_products)) })
                Tab(selected = selectedType == "EXPENSE", onClick = { selectedType = "EXPENSE" }, text = { Text(stringResource(R.string.tab_expenses)) })
            }

            if (categories.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.no_category_registered), color = Color.Gray)
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categories) { category ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Category, null, tint = Primary)
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(category.name, modifier = Modifier.weight(1f))
                                IconButton(onClick = { viewModel.deleteCategory(category) }) {
                                    Icon(Icons.Default.Delete, null, tint = Color.Gray)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showAddDialog) {
            AddCategoryDialog(
                type = selectedType,
                onDismiss = { showAddDialog = false },
                onConfirm = { name ->
                    viewModel.addCategory(name, selectedType)
                    showAddDialog = false
                }
            )
        }
    }
}
