package com.reconsiliation.caisse.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.reconsiliation.caisse.ui.theme.Primary
import com.reconsiliation.caisse.ui.viewmodel.MainViewModel
import com.reconsiliation.caisse.utils.FormatUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionLogScreen(navController: NavController) {
    val viewModel: MainViewModel = viewModel()
    val logs by viewModel.recentLogs.collectAsState(initial = emptyList())
    
    var filterLevel by remember { mutableStateOf("ALL") }

    val filteredLogs = remember(logs, filterLevel) {
        if (filterLevel == "ALL") logs
        else logs.filter { it.severity == filterLevel || (filterLevel == "CRITICAL" && it.severity == "WARNING") }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Journal d'Audit") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(selected = filterLevel == "ALL", onClick = { filterLevel = "ALL" }, label = { Text("Tout") })
                FilterChip(selected = filterLevel == "INFO", onClick = { filterLevel = "INFO" }, label = { Text("Infos") })
                FilterChip(selected = filterLevel == "CRITICAL", onClick = { filterLevel = "CRITICAL" }, label = { Text("Alertes") })
            }

            if (filteredLogs.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Text("Aucun log pour ce filtre", color = Color.Gray)
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(filteredLogs) { log ->
                        val color = when(log.severity) {
                            "CRITICAL" -> com.reconsiliation.caisse.ui.theme.RedError
                            "WARNING" -> com.reconsiliation.caisse.ui.theme.OrangeWarning
                            else -> Primary
                        }
                        ListItem(
                            headlineContent = { Text(log.actionType, fontWeight = FontWeight.Bold, color = color) },
                            supportingContent = { Text(log.details) },
                            trailingContent = { 
                                Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                                    Text(FormatUtil.formatDate(log.date), style = MaterialTheme.typography.labelSmall)
                                    Text(log.userRole, color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
