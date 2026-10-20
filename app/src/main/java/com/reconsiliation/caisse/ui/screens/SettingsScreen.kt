package com.reconsiliation.caisse.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.reconsiliation.caisse.R
import com.reconsiliation.caisse.ui.components.CaisseTextFieldDefaults
import com.reconsiliation.caisse.ui.navigation.Screen
import com.reconsiliation.caisse.ui.state.BackupUiState
import com.reconsiliation.caisse.ui.theme.Primary
import com.reconsiliation.caisse.ui.theme.RedError
import com.reconsiliation.caisse.ui.viewmodel.BackupViewModel
import com.reconsiliation.caisse.ui.viewmodel.InventoryViewModel
import com.reconsiliation.caisse.ui.viewmodel.MainViewModel
import com.reconsiliation.caisse.ui.viewmodel.MaintenanceViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    val viewModel: MainViewModel = hiltViewModel()
    val backupViewModel: BackupViewModel = hiltViewModel()
    val inventoryViewModel: InventoryViewModel = hiltViewModel()
    val maintenanceViewModel: MaintenanceViewModel = hiltViewModel()

    val boutique by viewModel.boutique.collectAsState()
    val allStock by inventoryViewModel.allStock.collectAsState()

    val isGoogleConnected by backupViewModel.isGoogleConnected.collectAsState()
    val googleAccountEmail by backupViewModel.googleAccountEmail.collectAsState()
    val isRemoteBackupEnabled by backupViewModel.isRemoteBackupEnabled.collectAsState()
    val isWifiOnlyBackupEnabled by backupViewModel.isWifiOnlyBackupEnabled.collectAsState()
    val lastRemoteBackupTime by backupViewModel.lastRemoteBackupTime.collectAsState()
    val lastRemoteBackupStatus by backupViewModel.lastRemoteBackupStatus.collectAsState()

    val operatorName = boutique?.operator ?: "MoMo"

    var showResetDialog by remember { mutableStateOf(false) }
    var showBackupPasswordDialog by remember { mutableStateOf(false) }
    var showChangePinDialog by remember { mutableStateOf(false) }
    val backupState by backupViewModel.backupState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val intent = result.data
        if (intent != null) {
            try {
                GoogleSignIn.getSignedInAccountFromIntent(intent)
            } catch (e: Exception) {
                // Ignore exception, refresh state
            }
        }
        backupViewModel.refreshRemoteBackupState()
    }

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.settings_shop_config), style = MaterialTheme.typography.titleMedium, color = Primary)
                TextButton(onClick = { viewModel.requestAccess(Screen.EditBoutique.route) }) {
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
            Button(onClick = { viewModel.requestAccess("action_logs") }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_view_audit))
            }
            Button(
                onClick = { viewModel.requestAccess("categories") },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Text(stringResource(R.string.action_manage_categories))
            }
            Button(
                onClick = { viewModel.requestAccess("maintenance") },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
            ) {
                Text(stringResource(R.string.action_maintenance_purge))
            }
            Button(
                onClick = { showChangePinDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
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

            // Carte Sauvegarde Distante Google Drive (J6.7b LOT 3B)
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Cloud, contentDescription = null, tint = Primary)
                            Text(
                                text = stringResource(R.string.remote_backup_card_title),
                                style = MaterialTheme.typography.titleSmall,
                                color = Primary
                            )
                        }
                    }

                    if (isGoogleConnected) {
                        Text(
                            text = stringResource(R.string.remote_backup_connected_as, googleAccountEmail ?: "-"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.label_auto_remote_backup),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = isRemoteBackupEnabled,
                                onCheckedChange = { backupViewModel.toggleRemoteBackup(it) }
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.label_wifi_only_backup),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = isWifiOnlyBackupEnabled,
                                enabled = isRemoteBackupEnabled,
                                onCheckedChange = { backupViewModel.toggleWifiOnlyBackup(it) }
                            )
                        }

                        val formattedLastTime = if (lastRemoteBackupTime > 0L) {
                            java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(lastRemoteBackupTime))
                        } else {
                            stringResource(R.string.status_last_sync_never)
                        }

                        Text(
                            text = stringResource(
                                R.string.label_last_cloud_sync,
                                formattedLastTime,
                                if (lastRemoteBackupStatus.isBlank()) "OK" else lastRemoteBackupStatus
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = androidx.compose.ui.graphics.Color.Gray
                        )

                        Button(
                            onClick = { backupViewModel.triggerManualRemoteBackup() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                            enabled = backupState !is BackupUiState.Working
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.action_cloud_sync_now))
                        }

                        OutlinedButton(
                            onClick = { backupViewModel.signOutGoogleDrive() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                        ) {
                            Text(stringResource(R.string.action_disconnect_google_drive))
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.remote_backup_disconnected),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Button(
                            onClick = {
                                val intent = backupViewModel.getGoogleSignInIntent()
                                if (intent != null) {
                                    googleSignInLauncher.launch(intent)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Default.Cloud, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.action_connect_google_drive))
                        }
                    }
                }
            }

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
                onClick = { viewModel.requestAccess("staff") },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Icon(Icons.Default.Group, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.action_manage_staff))
            }

            Button(
                onClick = { viewModel.requestAccess("restoration_wizard") },
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

        if (showChangePinDialog) {
            val dialogContext = LocalContext.current
            var oldPin by remember { mutableStateOf("") }
            var newPin by remember { mutableStateOf("") }
            var confirmPin by remember { mutableStateOf("") }
            var pinError by remember { mutableStateOf<String?>(null) }
            val changePinTitle = stringResource(R.string.change_pin_title)
            val changePinSuccess = stringResource(R.string.change_pin_success)
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showChangePinDialog = false },
                title = { Text(changePinTitle) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        val textFieldColors = CaisseTextFieldDefaults.outlinedTextFieldColors()
                        OutlinedTextField(
                            value = oldPin,
                            onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) oldPin = it },
                            label = { Text(stringResource(R.string.change_pin_old)) },
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
                            modifier = Modifier.fillMaxWidth(),
                            colors = textFieldColors
                        )
                        OutlinedTextField(
                            value = newPin,
                            onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) newPin = it },
                            label = { Text(stringResource(R.string.change_pin_new)) },
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
                            modifier = Modifier.fillMaxWidth(),
                            colors = textFieldColors
                        )
                        OutlinedTextField(
                            value = confirmPin,
                            onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) confirmPin = it },
                            label = { Text(stringResource(R.string.change_pin_confirm)) },
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
                            modifier = Modifier.fillMaxWidth(),
                            colors = textFieldColors
                        )
                        pinError?.let { Text(it, color = RedError, style = MaterialTheme.typography.bodySmall) }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        pinError = when {
                            oldPin.length != 4 -> dialogContext.getString(R.string.pin_enter_desc)
                            newPin.length != 4 -> dialogContext.getString(R.string.error_pin_format)
                            newPin != confirmPin -> dialogContext.getString(R.string.change_pin_mismatch)
                            else -> null
                        }
                        if (pinError == null) {
                            viewModel.changePin(oldPin, newPin) { error ->
                                if (error == "") {
                                    showChangePinDialog = false
                                    scope.launch { snackbarHostState.showSnackbar(changePinSuccess) }
                                } else {
                                    pinError = error
                                }
                            }
                        }
                    }) { Text(stringResource(R.string.action_confirm)) }
                },
                dismissButton = {
                    TextButton(onClick = { showChangePinDialog = false }) { Text(stringResource(R.string.action_cancel)) }
                }
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
