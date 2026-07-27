package com.reconsiliation.caisse.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.filled.Share
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.reconsiliation.caisse.ui.theme.Primary
import com.reconsiliation.caisse.ui.viewmodel.MainViewModel
import com.reconsiliation.caisse.utils.FormatUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(navController: NavController) {
    val context = LocalContext.current
    val viewModel: MainViewModel = viewModel()
    val categorySales by viewModel.categorySales.collectAsState()
    val dailySales by viewModel.dailySales.collectAsState()
    val currency by viewModel.currency.collectAsState()
    val userRole by viewModel.userRole.collectAsState()
    val valuation by viewModel.stockValuation.collectAsState()
    val topProducts by viewModel.topProducts.collectAsState()
    
    var period by remember { mutableStateOf("MONTH") } // MONTH, YEAR, CUSTOM

    LaunchedEffect(period) {
        viewModel.loadCurrentStats(period)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Statistiques & Rapports") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.exportSalesData(context) }) {
                        Icon(Icons.Default.Share, contentDescription = "Exporter")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = period == "MONTH", onClick = { period = "MONTH" }, label = { Text("Ce mois") })
                FilterChip(selected = period == "YEAR", onClick = { period = "YEAR" }, label = { Text("Cette année") })
            }

            if (userRole == "MANAGER") {
                Text("Indicateurs de Performance (Manager)", style = MaterialTheme.typography.titleMedium, color = Primary)
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ElevatedCard(modifier = Modifier.weight(1f)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Valeur Stock (Achat)", style = MaterialTheme.typography.labelSmall)
                            Text(FormatUtil.formatCurrency(valuation.first, currency), style = MaterialTheme.typography.titleMedium, color = Primary)
                        }
                    }
                    ElevatedCard(modifier = Modifier.weight(1f)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Valeur Stock (Vente)", style = MaterialTheme.typography.labelSmall)
                            Text(FormatUtil.formatCurrency(valuation.second, currency), style = MaterialTheme.typography.titleMedium, color = Primary)
                        }
                    }
                }
                
                Text("Produits les plus rentables", style = MaterialTheme.typography.titleSmall)
                topProducts.forEach { product ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(product.productName, style = MaterialTheme.typography.bodyMedium)
                        Text(FormatUtil.formatCurrency(product.unitPrice - product.purchasePrice, currency), color = com.reconsiliation.caisse.ui.theme.GreenSuccess)
                    }
                }
                
                HorizontalDivider()
            }

            Text(
                if (period == "MONTH") "Ventes Quotidiennes" else "Ventes Mensuelles",
                style = MaterialTheme.typography.titleMedium, 
                color = Primary
            )
            if (dailySales.isEmpty()) {
                Box(modifier = Modifier.height(200.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Aucune donnée pour cette période", color = Color.Gray)
                }
            } else {
                DetailedSalesChart(
                    data = dailySales,
                    currency = currency,
                    modifier = Modifier.height(250.dp).fillMaxWidth()
                )
            }

            HorizontalDivider()

            val expenseBreakdown by viewModel.expenseBreakdown.collectAsState()
            Text("Répartition des Dépenses", style = MaterialTheme.typography.titleMedium, color = Primary)
            if (expenseBreakdown.isEmpty()) {
                Text("Aucune dépense sur cette période", color = Color.Gray)
            } else {
                expenseBreakdown.forEach { report ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(report.category, modifier = Modifier.weight(1f))
                        LinearProgressIndicator(
                            progress = { if (expenseBreakdown.sumOf { it.total } > 0) (report.total / expenseBreakdown.sumOf { it.total }).toFloat() else 0f },
                            modifier = Modifier.weight(2f).height(8.dp).padding(horizontal = 8.dp),
                            color = com.reconsiliation.caisse.ui.theme.RedError,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                        Text(FormatUtil.formatCurrency(report.total, currency), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            HorizontalDivider()
            
            Text("Analyse des Heures de Pointe", style = MaterialTheme.typography.titleMedium, color = Primary)
            val hourlySales by viewModel.hourlySales.collectAsState()
            if (hourlySales.isEmpty()) {
                Text("Données insuffisantes", color = Color.Gray)
            } else {
                DetailedSalesChart(
                    data = hourlySales,
                    currency = currency,
                    modifier = Modifier.height(200.dp).fillMaxWidth()
                )
            }

            HorizontalDivider()

            Text("Ventes par Catégorie", style = MaterialTheme.typography.titleMedium, color = Primary)
            if (categorySales.isEmpty()) {
                Box(modifier = Modifier.height(200.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Aucune vente enregistrée", color = Color.Gray)
                }
            } else {
                categorySales.forEach { report ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(report.category, modifier = Modifier.weight(1f))
                        LinearProgressIndicator(
                            progress = { if (categorySales.sumOf { it.totalSales } > 0) (report.totalSales / categorySales.sumOf { it.totalSales }).toFloat() else 0f },
                            modifier = Modifier.weight(2f).height(8.dp).padding(horizontal = 8.dp),
                            color = Primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                        Text(FormatUtil.formatCurrency(report.totalSales, currency), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
fun DetailedSalesChart(
    data: List<com.reconsiliation.caisse.data.local.dao.DailySales>,
    currency: String,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val maxVal = (data.maxOfOrNull { it.total.toFloat() } ?: 1f).coerceAtLeast(1f)
    
    Canvas(modifier = modifier.padding(start = 45.dp, end = 16.dp, bottom = 30.dp, top = 20.dp)) {
        val width = size.width
        val height = size.height
        val barWidth = if (data.isNotEmpty()) width / data.size else width
        
        // 1. Y-Axis Labels and Grid Lines
        val gridLines = 4
        for (i in 0..gridLines) {
            val y = height - (i * height / gridLines)
            val value = (i * maxVal / gridLines).toInt()
            
            drawLine(
                color = Color.LightGray.copy(alpha = 0.5f),
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 1f
            )
            
            val label = when {
                value >= 1_000_000 -> "%.1fM".format(value / 1_000_000f)
                value >= 1000 -> "${value / 1000}k"
                else -> value.toString()
            }
            
            drawText(
                textMeasurer = textMeasurer,
                text = label,
                topLeft = Offset(-42.dp.toPx(), y - 10.dp.toPx()),
                style = TextStyle(fontSize = 10.sp, color = Color.Gray)
            )
        }
        
        // Y-axis Unit Label
        drawText(
            textMeasurer = textMeasurer,
            text = currency,
            topLeft = Offset(-40.dp.toPx(), -15.dp.toPx()),
            style = TextStyle(fontSize = 9.sp, color = Color.Gray, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        )
        
        // 2. Bars and X-Axis Labels
        data.forEachIndexed { index, item ->
            val barHeight = (item.total.toFloat() / maxVal) * height
            val x = index * barWidth
            
            drawRect(
                color = Primary,
                topLeft = Offset(x = x + (barWidth * 0.15f), y = height - barHeight),
                size = Size(width = barWidth * 0.7f, height = barHeight)
            )
            
            // X Label
            val shouldShowLabel = data.size <= 12 || 
                                 index % (data.size / 6).coerceAtLeast(1) == 0 || 
                                 index == data.size - 1
            
            if (shouldShowLabel) {
                drawText(
                    textMeasurer = textMeasurer,
                    text = item.day,
                    topLeft = Offset(x + (barWidth / 2) - 6.dp.toPx(), height + 8.dp.toPx()),
                    style = TextStyle(fontSize = 10.sp, color = Color.Gray)
                )
            }
        }
        
        // 3. Axes
        drawLine(color = Color.Gray, start = Offset(0f, height), end = Offset(width, height), strokeWidth = 2f)
        drawLine(color = Color.Gray, start = Offset(0f, 0f), end = Offset(0f, height), strokeWidth = 2f)
    }
}

