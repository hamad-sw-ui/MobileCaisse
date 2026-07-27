package com.reconsiliation.caisse.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
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
import com.reconsiliation.caisse.ui.theme.RedError
import com.reconsiliation.caisse.ui.viewmodel.MainViewModel
import com.reconsiliation.caisse.utils.FormatUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PriceHistoryScreen(navController: NavController, productId: Long) {
    val viewModel: MainViewModel = viewModel()
    val history by viewModel.getPriceHistory(productId).collectAsState(initial = emptyList())
    val stockItems by viewModel.allStock.collectAsState()
    val product = stockItems.find { it.id == productId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Historique des Prix: ${product?.productName ?: ""}") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        if (history.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Aucun changement de prix enregistré", color = Color.Gray)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(history) { log ->
                    PriceHistoryRow(log)
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}

@Composable
fun PriceHistoryRow(log: com.reconsiliation.caisse.data.local.entity.PriceHistoryEntity) {
    val isIncrease = log.newPrice > log.oldPrice
    val typeLabel = if (log.type == "PURCHASE") "Achat" else "Vente"
    
    ListItem(
        headlineContent = { Text("Changement de prix de $typeLabel", fontWeight = FontWeight.Bold) },
        supportingContent = { 
            Text("${FormatUtil.formatCurrency(log.oldPrice)} ➔ ${FormatUtil.formatCurrency(log.newPrice)}") 
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(FormatUtil.formatDate(log.date), style = MaterialTheme.typography.labelSmall)
                Icon(
                    if (isIncrease) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                    contentDescription = null,
                    tint = if (isIncrease) RedError else GreenSuccess,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    )
}
