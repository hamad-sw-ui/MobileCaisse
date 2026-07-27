package com.reconsiliation.caisse.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.reconsiliation.caisse.ui.theme.GreenSuccess
import com.reconsiliation.caisse.ui.theme.Primary
import com.reconsiliation.caisse.ui.theme.RedError
import com.reconsiliation.caisse.ui.viewmodel.MainViewModel
import com.reconsiliation.caisse.utils.FormatUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockMovementScreen(navController: NavController, productId: Long) {
    val viewModel: MainViewModel = viewModel()
    val movements by viewModel.getStockMovements(productId).collectAsState(initial = emptyList())
    val stockItems by viewModel.allStock.collectAsState()
    val product = stockItems.find { it.id == productId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mouvements: ${product?.productName ?: ""}") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        if (movements.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Aucun mouvement enregistré", color = Color.Gray)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(movements) { movement ->
                    MovementRow(movement)
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}

@Composable
fun MovementRow(movement: com.reconsiliation.caisse.data.local.entity.StockMovementEntity) {
    val icon = when(movement.type) {
        "IN" -> Icons.Default.ArrowUpward
        "OUT" -> Icons.Default.ArrowDownward
        "LOSS" -> Icons.Default.Warning
        else -> Icons.Default.ArrowUpward
    }
    val color = when(movement.type) {
        "IN" -> GreenSuccess
        "OUT" -> Primary
        "LOSS" -> RedError
        else -> Color.Gray
    }

    ListItem(
        leadingContent = { Icon(icon, null, tint = color) },
        headlineContent = { Text(movement.reason, fontWeight = FontWeight.Bold) },
        supportingContent = { Text(FormatUtil.formatDate(movement.date)) },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text("${if (movement.type == "IN") "+" else "-"}${movement.quantity}", color = color, fontWeight = FontWeight.Bold)
                Text("Solde: ${movement.balanceAfter}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
        }
    )
}
