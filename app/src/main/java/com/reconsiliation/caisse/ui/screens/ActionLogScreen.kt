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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.reconsiliation.caisse.R
import com.reconsiliation.caisse.data.local.entity.ActionLogEntity
import com.reconsiliation.caisse.ui.theme.Primary
import com.reconsiliation.caisse.ui.viewmodel.MaintenanceViewModel
import com.reconsiliation.caisse.utils.FormatUtil

fun filterActionLogs(logs: List<ActionLogEntity>, filterLevel: String): List<ActionLogEntity> {
    return when (filterLevel) {
        "ALL" -> logs
        "ANOMALIES" -> logs.filter { it.actionType == "DATE_ANOMALY" || it.actionType == "ANOMALY_DETECTED" }
        "INFO" -> logs.filter { it.severity == "INFO" }
        "CRITICAL" -> logs.filter { it.severity == "CRITICAL" || it.severity == "WARNING" }
        else -> logs.filter { it.severity == filterLevel }
    }
}

@Composable
fun formatLogDetails(actionType: String, details: String): String {
    return when {
        details == "FUTURE" -> stringResource(R.string.anomaly_future_sale)
        details == "DUPLICATE" -> stringResource(R.string.anomaly_duplicate_sale)
        details == "HIGH_AMOUNT" -> stringResource(R.string.anomaly_high_amount)
        details == "DATE_ANOMALY" || (actionType == "DATE_ANOMALY" && (details.isBlank() || details.startsWith("Écart de temps") || details == "DATE_ANOMALY")) -> {
            stringResource(R.string.log_date_anomaly)
        }
        else -> details
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionLogScreen(navController: NavController) {
    val viewModel: MaintenanceViewModel = hiltViewModel()
    val logs by viewModel.recentLogs.collectAsState(initial = emptyList())
    
    var filterLevel by remember { mutableStateOf("ALL") }

    val filteredLogs = remember(logs, filterLevel) {
        filterActionLogs(logs, filterLevel)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_audit_log)) },
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
                FilterChip(selected = filterLevel == "ALL", onClick = { filterLevel = "ALL" }, label = { Text(stringResource(R.string.filter_all)) })
                FilterChip(selected = filterLevel == "ANOMALIES", onClick = { filterLevel = "ANOMALIES" }, label = { Text(stringResource(R.string.filter_anomalies)) })
                FilterChip(selected = filterLevel == "INFO", onClick = { filterLevel = "INFO" }, label = { Text(stringResource(R.string.filter_infos)) })
                FilterChip(selected = filterLevel == "CRITICAL", onClick = { filterLevel = "CRITICAL" }, label = { Text(stringResource(R.string.filter_alerts)) })
            }

            if (filteredLogs.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Text(stringResource(R.string.error_no_log_found), color = Color.Gray)
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
                            supportingContent = { Text(formatLogDetails(log.actionType, log.details)) },
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
