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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.reconsiliation.caisse.ui.theme.Primary
import com.reconsiliation.caisse.ui.theme.RedError
import com.reconsiliation.caisse.ui.viewmodel.MainViewModel
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestorationWizardScreen(navController: NavController) {
    val context = LocalContext.current
    val viewModel: MainViewModel = viewModel()
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
            // Le fichier est copié immédiatement : l'URI d'un ContentProvider
            // peut expirer, et la détection de format exige un accès local.
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
                title = { Text("Restauration de Données") },
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
                    "Étape 1 : Sélectionnez votre fichier de sauvegarde",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
                Text(
                    "Cherchez le fichier '.db' que vous avez sauvegardé précédemment (ex: dans votre dossier Téléchargements ou Google Drive).",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = androidx.compose.ui.graphics.Color.Gray
                )
                Button(onClick = { launcher.launch("application/octet-stream") }) {
                    Text("Choisir le fichier")
                }
            } else {
                Icon(Icons.Default.Warning, null, modifier = Modifier.size(64.dp), tint = RedError)
                Text(
                    "Étape 2 : Confirmation de l'écrasement",
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
                            "ATTENTION : Cette opération supprimera toutes vos données actuelles pour les remplacer par celles du fichier sélectionné.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                
                // Le format est indiqué : l'utilisateur sait s'il devra fournir
                // un mot de passe avant de confirmer l'écrasement.
                Text(
                    text = if (isEncrypted) {
                        "🔒 Sauvegarde chiffrée : le mot de passe sera demandé."
                    } else {
                        "Sauvegarde non chiffrée (format ancien)."
                    },
                    style = MaterialTheme.typography.bodySmall
                )

                Button(
                    onClick = {
                        val file = stagedFile ?: return@Button
                        if (isEncrypted) {
                            showPasswordDialog = true
                        } else {
                            viewModel.importEncryptedBackup(context, file, null) {
                                navController.navigate("splash") { popUpTo(0) }
                            }
                        }
                    },
                    enabled = stagedFile != null &&
                        backupState !is com.reconsiliation.caisse.ui.viewmodel.BackupUiState.Working,
                    colors = ButtonDefaults.buttonColors(containerColor = RedError),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (backupState is com.reconsiliation.caisse.ui.viewmodel.BackupUiState.Working) {
                            "Restauration en cours…"
                        } else {
                            "Confirmer la restauration"
                        }
                    )
                }

                (backupState as? com.reconsiliation.caisse.ui.viewmodel.BackupUiState.Error)?.let {
                    Text(it.message, color = RedError, style = MaterialTheme.typography.bodySmall)
                }
                
                TextButton(onClick = {
                    step = 1
                    selectedUri = null
                    stagedFile = null
                    viewModel.clearBackupState()
                }) {
                    Text("Choisir un autre fichier")
                }
            }

            if (showPasswordDialog) {
                com.reconsiliation.caisse.ui.components.BackupPasswordDialog(
                    isExport = false,
                    onConfirm = { password ->
                        showPasswordDialog = false
                        stagedFile?.let { file ->
                            viewModel.importEncryptedBackup(context, file, password) {
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
