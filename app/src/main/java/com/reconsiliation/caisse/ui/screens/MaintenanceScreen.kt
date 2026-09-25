package com.reconsiliation.caisse.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.reconsiliation.caisse.ui.theme.Primary
import com.reconsiliation.caisse.ui.theme.RedError
import com.reconsiliation.caisse.ui.viewmodel.MainViewModel
import com.reconsiliation.caisse.ui.viewmodel.MaintenanceViewModel
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.reconsiliation.caisse.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaintenanceScreen(navController: NavController) {
    val viewModel: MaintenanceViewModel = hiltViewModel()
    val mainViewModel: MainViewModel = hiltViewModel()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    
    var showPurgeDialog by remember { mutableStateOf(false) }
    var purgeMonths by remember { mutableStateOf(12f) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(com.reconsiliation.caisse.R.string.title_maintenance)) },
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
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, null, tint = Primary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        stringResource(com.reconsiliation.caisse.R.string.maintenance_info_desc),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Text(stringResource(com.reconsiliation.caisse.R.string.label_data_cleanup), style = MaterialTheme.typography.titleMedium, color = Primary)
            
            ListItem(
                headlineContent = { Text(stringResource(com.reconsiliation.caisse.R.string.label_purge_archives)) },
                supportingContent = { Text(stringResource(com.reconsiliation.caisse.R.string.purge_archives_desc)) },
                leadingContent = { Icon(Icons.Default.DeleteSweep, null, tint = RedError) },
                trailingContent = {
                    Button(
                        onClick = { showPurgeDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = RedError)
                    ) {
                        Text(stringResource(com.reconsiliation.caisse.R.string.action_purge))
                    }
                }
            )

            HorizontalDivider()

            Text(stringResource(com.reconsiliation.caisse.R.string.label_storage_stats), style = MaterialTheme.typography.titleMedium, color = Primary)
            ListItem(
                headlineContent = { Text(stringResource(com.reconsiliation.caisse.R.string.label_database)) },
                supportingContent = { Text(stringResource(com.reconsiliation.caisse.R.string.label_active_local_storage)) },
                leadingContent = { Icon(Icons.Default.Storage, null) }
            )
        }

        if (showPurgeDialog) {
            val userRole by mainViewModel.userRole.collectAsState()
            AlertDialog(
                onDismissRequest = { showPurgeDialog = false },
                title = { Text(stringResource(com.reconsiliation.caisse.R.string.dialog_confirm_purge_title)) },
                text = {
                    Column {
                        Text(stringResource(com.reconsiliation.caisse.R.string.dialog_confirm_purge_desc, purgeMonths.toInt()))
                        Text(stringResource(com.reconsiliation.caisse.R.string.dialog_confirm_purge_warning), style = MaterialTheme.typography.bodySmall, color = RedError)
                        Spacer(modifier = Modifier.height(16.dp))
                        Slider(
                            value = purgeMonths,
                            onValueChange = { purgeMonths = it },
                            valueRange = 1f..36f,
                            steps = 35
                        )
                        Text(stringResource(com.reconsiliation.caisse.R.string.label_count_plural, purgeMonths.toInt(), stringResource(com.reconsiliation.caisse.R.string.filter_this_month).lowercase()), modifier = Modifier.align(Alignment.CenterHorizontally))
                    }
                },
                confirmButton = {
                    val successMsg = stringResource(com.reconsiliation.caisse.R.string.purge_success)
                    TextButton(
                        onClick = {
                            scope.launch {
                                val count = viewModel.purgeOldData(purgeMonths.toInt(), userRole)
                                snackbarHostState.showSnackbar(successMsg.format(count))
                                showPurgeDialog = false
                            }
                        }
                    ) {
                        Text(stringResource(com.reconsiliation.caisse.R.string.action_confirm_purge), color = RedError)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPurgeDialog = false }) { Text(stringResource(com.reconsiliation.caisse.R.string.action_cancel)) }
                }
            )
        }
    }
}
