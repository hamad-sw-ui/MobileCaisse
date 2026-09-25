package com.reconsiliation.caisse.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.reconsiliation.caisse.ui.navigation.Screen
import com.reconsiliation.caisse.ui.state.BackupUiState
import com.reconsiliation.caisse.ui.theme.Primary
import com.reconsiliation.caisse.ui.theme.RedError
import com.reconsiliation.caisse.ui.viewmodel.BackupViewModel
import com.reconsiliation.caisse.ui.viewmodel.InventoryViewModel
import com.reconsiliation.caisse.ui.viewmodel.MainViewModel
import com.reconsiliation.caisse.ui.viewmodel.MaintenanceViewModel
import androidx.compose.ui.res.stringResource
import com.reconsiliation.caisse.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    val viewModel: MainViewModel = hiltViewModel()
    val backupViewModel: BackupViewModel = hiltViewModel()
    val inventoryViewModel: InventoryViewModel = hiltViewModel()
    val maintenanceViewModel: MaintenanceViewModel = hiltViewModel()
    
    val boutique by viewModel.boutique.collectAsState()
    val allStock by inventoryViewModel.allStock.collectAsState()
    // allVentes is not in MainViewModel anymore in my purged version? 
    // Wait, I should check what I kept in MainViewModel.
    // I removed allVentes from MainViewModel.
    // Actually, SessionViewModel or NewSaleViewModel should have it?
    // In Settings, allVentes was only used for "Mode Démo" button condition.
    // I will use a dummy empty list for now or check from repository if needed.
    // Actually, I'll keep allVentes in MainViewModel as a transversal primitive if needed, 
    // but the goal was to isolate domains. Sales domain is isolated in SalesRepository.
    
    val operatorName = boutique?.operator ?: "MoMo"
    
    var showResetDialog by remember { mutableStateOf(false) }
    var showBackupPasswordDialog by remember { mutableStateOf(false) }
    val backupState by backupViewModel.backupState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_settings)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_shop_config), style = MaterialTheme.typography.titleMedium, color = Primary)
                TextButton(onClick = { navController.navigate(Screen.EditBoutique.route) }) {
                    Text(stringResource(R.string.action_edit))
                }
            }
            
            ListItem(
                headlineContent = { Text(stringResource(R.string.setup_shop_name)) },
                supportingContent = { Text(boutique?.name ?: "-") }
            )
            
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_owner)) },
                supportingContent = { Text(boutique?.ownerName ?: "-") }
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_reception_label, operatorName, boutique?.operator ?: "-")) },
                supportingContent = { Text(boutique?.momoNumber ?: "-") }
            )

            Text(stringResource(R.string.settings_hardware_print), style = MaterialTheme.typography.titleMedium, color = Primary)
            Button(onClick = { navController.navigate("print_settings") }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Print, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.action_customize_tickets))
            }

            HorizontalDivider()
            
            Text(stringResource(R.string.settings_security_audit), style = MaterialTheme.typography.titleMedium, color = Primary)
            Button(onClick = { navController.navigate("action_logs") }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_view_audit))
            }
            Button(onClick = { navController.navigate("categories") }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)) {
                Text(stringResource(R.string.action_manage_categories))
            }
            Button(onClick = { navController.navigate("maintenance") }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)) {
                Text(stringResource(R.string.action_maintenance_purge))
            }
            Button(onClick = { /* Change PIN logic */ }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)) {
                Text(stringResource(R.string.action_change_pin))
            }
            
            Button(
                onClick = { navController.navigate("support") },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
            ) {
                Text(stringResource(R.string.action_support_sub))
            }

            HorizontalDivider()
            
            Text(stringResource(R.string.settings_backup_export), style = MaterialTheme.typography.titleMedium, color = Primary)
            Button(
                onClick = { showBackupPasswordDialog = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = backupState !is BackupUiState.Working,
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                Icon(Icons.Default.Backup, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (backupState is BackupUiState.Working) stringResource(R.string.status_backup_working)
                    else stringResource(R.string.action_export_share_db)
                )
            }
            Text(
                stringResource(R.string.backup_password_notice),
                style = MaterialTheme.typography.bodySmall
            )

            if (com.reconsiliation.caisse.BuildConfig.DEBUG && allStock.isEmpty()) {
                Button(
                    onClick = { maintenanceViewModel.seedDemoData() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                ) {
                    Text(stringResource(R.string.action_demo_mode))
                }
            } else if (allStock.isNotEmpty()) {
                Button(
                    onClick = { showResetDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = RedError)
                ) {
                    Text(stringResource(R.string.action_reset_demo))
                }
            }

            if (showResetDialog) {
                AlertDialog(
                    onDismissRequest = { showResetDialog = false },
                    title = { Text(stringResource(R.string.dialog_reset_title)) },
                    text = { Text(stringResource(R.string.dialog_reset_desc)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.resetApplicationData()
                                showResetDialog = false
                                navController.navigate(Screen.Splash.route) {
                                    popUpTo(0) { inclusive = true }
                                }
                            }
                        ) {
                            Text(stringResource(R.string.action_erase_all), color = RedError)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showResetDialog = false }) { Text(stringResource(R.string.action_cancel)) }
                    }
                )
            }

            Button(
                onClick = { viewModel.requestAccess("staff"); navController.navigate("staff") },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Icon(Icons.Default.Group, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.action_manage_staff))
            }

            Button(
                onClick = { navController.navigate("restoration_wizard") },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Icon(Icons.Default.FileUpload, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.action_restore_backup))
            }

            Spacer(modifier = Modifier.weight(1f))
            
            Text(stringResource(R.string.app_name) + " " + stringResource(R.string.app_version), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = androidx.compose.ui.graphics.Color.Gray)
        }

        if (showBackupPasswordDialog) {
            com.reconsiliation.caisse.ui.components.BackupPasswordDialog(
                isExport = true,
                onConfirm = { password ->
                    showBackupPasswordDialog = false
                    backupViewModel.exportEncryptedBackup(context, password, viewModel.userRole.value)
                },
                onDismiss = { showBackupPasswordDialog = false }
            )
        }

        LaunchedEffect(backupState) {
            when (val state = backupState) {
                is BackupUiState.Success -> {
                    snackbarHostState.showSnackbar(state.message)
                    backupViewModel.clearBackupState()
                }
                is BackupUiState.Error -> {
                    snackbarHostState.showSnackbar(state.message)
                    backupViewModel.clearBackupState()
                }
                else -> Unit
            }
        }
    }
}
