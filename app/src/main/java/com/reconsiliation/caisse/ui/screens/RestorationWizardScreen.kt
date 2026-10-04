package com.reconsiliation.caisse.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.reconsiliation.caisse.R
import com.reconsiliation.caisse.platform.remote.RemoteBackupItem
import com.reconsiliation.caisse.ui.state.BackupUiState
import com.reconsiliation.caisse.ui.theme.Primary
import com.reconsiliation.caisse.ui.theme.RedError
import com.reconsiliation.caisse.ui.viewmodel.BackupViewModel
import com.reconsiliation.caisse.ui.viewmodel.MainViewModel
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestorationWizardScreen(navController: NavController) {
    val context = LocalContext.current
    val viewModel: BackupViewModel = hiltViewModel()
    val mainViewModel: MainViewModel = hiltViewModel()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Local, 1: Cloud
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var step by remember { mutableStateOf(1) } // 1: choix du fichier, 2: confirmation
    var stagedFile by remember { mutableStateOf<File?>(null) }
    var selectedCloudItem by remember { mutableStateOf<RemoteBackupItem?>(null) }
    var isEncrypted by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }

    val backupState by viewModel.backupState.collectAsState()
    val isGoogleConnected by viewModel.isGoogleConnected.collectAsState()
    val cloudBackups by viewModel.cloudBackups.collectAsState()

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
            selectedCloudItem = null
            isEncrypted = viewModel.isBackupEncrypted(tmp)
            step = 2
        }
    }

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
        viewModel.refreshRemoteBackupState()
    }

    LaunchedEffect(selectedTab, isGoogleConnected) {
        if (selectedTab == 1 && isGoogleConnected) {
            viewModel.fetchCloudBackups()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_restoration)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (step == 1) {
                TabRow(
                    selectedTabIndex = selectedTab,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        modifier = Modifier.heightIn(min = 48.dp),
                        text = { Text(stringResource(R.string.restoration_tab_local)) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = {
                            selectedTab = 1
                            if (isGoogleConnected) {
                                viewModel.fetchCloudBackups()
                            }
                        },
                        modifier = Modifier.heightIn(min = 48.dp),
                        text = { Text(stringResource(R.string.restoration_tab_cloud)) }
                    )
                }

                if (selectedTab == 0) {
                    // Mode Fichier Local
                    Spacer(modifier = Modifier.height(16.dp))
                    Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(64.dp), tint = Primary)
                    Text(
                        stringResource(R.string.restoration_step1_title),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        stringResource(R.string.restoration_step1_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { launcher.launch("application/octet-stream") },
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text(stringResource(R.string.action_choose_file))
                    }
                } else {
                    // Mode Google Drive Cloud
                    if (!isGoogleConnected) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Icon(Icons.Default.Cloud, contentDescription = null, modifier = Modifier.size(64.dp), tint = Primary)
                        Text(
                            stringResource(R.string.remote_backup_disconnected),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = {
                                val intent = viewModel.getGoogleSignInIntent()
                                if (intent != null) {
                                    googleSignInLauncher.launch(intent)
                                }
                            },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Default.Cloud, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.action_connect_google_drive))
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                stringResource(R.string.label_cloud_backups_title),
                                style = MaterialTheme.typography.titleSmall,
                                color = Primary
                            )
                            IconButton(
                                onClick = { viewModel.fetchCloudBackups() },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.action_refresh_cloud_list))
                            }
                        }

                        if (backupState is BackupUiState.Working) {
                            CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                        } else if (cloudBackups.isEmpty()) {
                            Text(
                                stringResource(R.string.label_no_cloud_backups),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 24.dp)
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(cloudBackups) { item ->
                                    val formattedDate = try {
                                        val cleanIso = item.createdAtIso.trim().removeSuffix("Z")
                                        val date = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                                            timeZone = TimeZone.getTimeZone("UTC")
                                        }.parse(cleanIso) ?: Date(0L)
                                        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(date)
                                    } catch (e: Exception) {
                                        item.createdAtIso
                                    }

                                    OutlinedCard(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 48.dp)
                                            .clickable {
                                                selectedCloudItem = item
                                                stagedFile = null
                                                isEncrypted = true
                                                step = 2
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .padding(16.dp)
                                                .fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    item.fileName,
                                                    style = MaterialTheme.typography.titleSmall
                                                )
                                                Text(
                                                    "$formattedDate • ${item.sizeBytes / 1024} Ko",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Icon(Icons.Default.CloudDownload, contentDescription = null, tint = Primary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Étape 2 : Confirmation d'écrasement de la base
                Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(64.dp), tint = RedError)
                Text(
                    stringResource(R.string.restoration_step2_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = RedError
                )
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = RedError)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            stringResource(R.string.restoration_warning_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                Text(
                    text = if (selectedCloudItem != null) {
                        stringResource(R.string.label_last_cloud_sync, selectedCloudItem?.fileName ?: "", "Google Drive")
                    } else if (isEncrypted) {
                        stringResource(R.string.restoration_encrypted_info)
                    } else {
                        stringResource(R.string.restoration_legacy_info)
                    },
                    style = MaterialTheme.typography.bodySmall
                )

                Button(
                    onClick = {
                        val cloudItem = selectedCloudItem
                        val localFile = stagedFile
                        if (cloudItem != null) {
                            viewModel.restoreCloudBackup(
                                context = context,
                                remoteItem = cloudItem,
                                userRole = mainViewModel.userRole.value
                            ) {
                                navController.navigate("splash") { popUpTo(0) }
                            }
                        } else if (localFile != null) {
                            if (isEncrypted) {
                                showPasswordDialog = true
                            } else {
                                viewModel.importEncryptedBackup(context, localFile, null, mainViewModel.userRole.value) {
                                    navController.navigate("splash") { popUpTo(0) }
                                }
                            }
                        }
                    },
                    enabled = (selectedCloudItem != null || stagedFile != null) && backupState !is BackupUiState.Working,
                    colors = ButtonDefaults.buttonColors(containerColor = RedError),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Text(
                        if (backupState is BackupUiState.Working) {
                            if (selectedCloudItem != null) stringResource(R.string.status_downloading_cloud)
                            else stringResource(R.string.status_restoring)
                        } else {
                            stringResource(R.string.action_confirm_restore)
                        }
                    )
                }

                (backupState as? BackupUiState.Error)?.let { errorState ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(errorState.message, color = RedError, style = MaterialTheme.typography.bodySmall)

                        // If password failed during cloud restore, allow entering custom password
                        if (selectedCloudItem != null) {
                            OutlinedButton(
                                onClick = { showPasswordDialog = true },
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text(stringResource(R.string.dialog_backup_password_title))
                            }
                        }
                    }
                }

                TextButton(onClick = {
                    step = 1
                    selectedUri = null
                    stagedFile = null
                    selectedCloudItem = null
                    viewModel.clearBackupState()
                }) {
                    Text(stringResource(R.string.action_choose_another))
                }
            }

            if (showPasswordDialog) {
                com.reconsiliation.caisse.ui.components.BackupPasswordDialog(
                    isExport = false,
                    onConfirm = { password ->
                        showPasswordDialog = false
                        val cloudItem = selectedCloudItem
                        val localFile = stagedFile
                        if (cloudItem != null) {
                            viewModel.restoreCloudBackup(
                                context = context,
                                remoteItem = cloudItem,
                                userRole = mainViewModel.userRole.value,
                                customPassword = password
                            ) {
                                navController.navigate("splash") { popUpTo(0) }
                            }
                        } else localFile?.let { file ->
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
