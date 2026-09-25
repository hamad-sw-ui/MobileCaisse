package com.reconsiliation.caisse.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.reconsiliation.caisse.ui.state.BackupUiState
import com.reconsiliation.caisse.ui.theme.Primary
import com.reconsiliation.caisse.ui.theme.RedError
import com.reconsiliation.caisse.ui.viewmodel.BackupViewModel
import com.reconsiliation.caisse.ui.viewmodel.MainViewModel
import java.io.File
import java.io.FileOutputStream
import androidx.compose.ui.res.stringResource
import com.reconsiliation.caisse.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestorationWizardScreen(navController: NavController) {
    val context = LocalContext.current
    val viewModel: BackupViewModel = hiltViewModel()
    val mainViewModel: MainViewModel = hiltViewModel()
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var step by remember { mutableStateOf(1) } // 1: choix du fichier, 2: confirmation
    var stagedFile by remember { mutableStateOf<File?>(null) }
    var isEncrypted by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    val backupState by viewModel.backupState.collectAsState()
    
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            selectedUri = uri
            val tmp = File(context.cacheDir, "temp_restore.bin")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tmp).use { output -> input.copyTo(output) }
            }
            stagedFile = tmp
            isEncrypted = viewModel.isBackupEncrypted(tmp)
            step = 2
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(com.reconsiliation.caisse.R.string.title_restoration)) },
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
                .padding(24.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            if (step == 1) {
                Icon(Icons.Default.FileOpen, null, modifier = Modifier.size(64.dp), tint = Primary)
                Text(
                    stringResource(com.reconsiliation.caisse.R.string.restoration_step1_title),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
                Text(
                    stringResource(com.reconsiliation.caisse.R.string.restoration_step1_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = androidx.compose.ui.graphics.Color.Gray
                )
                Button(onClick = { launcher.launch("application/octet-stream") }) {
                    Text(stringResource(com.reconsiliation.caisse.R.string.action_choose_file))
                }
            } else {
                Icon(Icons.Default.Warning, null, modifier = Modifier.size(64.dp), tint = RedError)
                Text(
                    stringResource(com.reconsiliation.caisse.R.string.restoration_step2_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = RedError
                )
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, null, tint = RedError)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            stringResource(com.reconsiliation.caisse.R.string.restoration_warning_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                
                Text(
                    text = if (isEncrypted) {
                        stringResource(com.reconsiliation.caisse.R.string.restoration_encrypted_info)
                    } else {
                        stringResource(com.reconsiliation.caisse.R.string.restoration_legacy_info)
                    },
                    style = MaterialTheme.typography.bodySmall
                )

                Button(
                    onClick = {
                        val file = stagedFile ?: return@Button
                        if (isEncrypted) {
                            showPasswordDialog = true
                        } else {
                            viewModel.importEncryptedBackup(context, file, null, mainViewModel.userRole.value) {
                                navController.navigate("splash") { popUpTo(0) }
                            }
                        }
                    },
                    enabled = stagedFile != null && backupState !is BackupUiState.Working,
                    colors = ButtonDefaults.buttonColors(containerColor = RedError),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (backupState is BackupUiState.Working) stringResource(com.reconsiliation.caisse.R.string.status_restoring)
                        else stringResource(com.reconsiliation.caisse.R.string.action_confirm_restore)
                    )
                }

                (backupState as? BackupUiState.Error)?.let {
                    Text(it.message, color = RedError, style = MaterialTheme.typography.bodySmall)
                }
                
                TextButton(onClick = {
                    step = 1
                    selectedUri = null
                    stagedFile = null
                    viewModel.clearBackupState()
                }) {
                    Text(stringResource(com.reconsiliation.caisse.R.string.action_choose_another))
                }
            }

            if (showPasswordDialog) {
                com.reconsiliation.caisse.ui.components.BackupPasswordDialog(
                    isExport = false,
                    onConfirm = { password ->
                        showPasswordDialog = false
                        stagedFile?.let { file ->
                            viewModel.importEncryptedBackup(context, file, password, mainViewModel.userRole.value) {
                                navController.navigate("splash") { popUpTo(0) }
                            }
                        }
                    },
                    onDismiss = { showPasswordDialog = false }
                )
            }
        }
    }
}
