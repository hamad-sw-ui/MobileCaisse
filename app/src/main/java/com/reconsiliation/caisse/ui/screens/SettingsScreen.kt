package com.reconsiliation.caisse.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import androidx.navigation.NavController
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import com.reconsiliation.caisse.ui.navigation.Screen
import com.reconsiliation.caisse.ui.theme.Primary
import com.reconsiliation.caisse.ui.theme.RedError
import com.reconsiliation.caisse.ui.viewmodel.MainViewModel
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    val viewModel: MainViewModel = viewModel()
    val boutique by viewModel.boutique.collectAsState()
    val allStock by viewModel.allStock.collectAsState()
    val allVentes by viewModel.allVentes.collectAsState()
    val operatorName = boutique?.operator ?: "MoMo"
    
    var showResetDialog by remember { mutableStateOf(false) }
    var showBackupPasswordDialog by remember { mutableStateOf(false) }
    val backupState by viewModel.backupState.collectAsState()
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }

    Scaffold(
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Paramètres") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(androidx.compose.foundation.rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Configuration Boutique", style = MaterialTheme.typography.titleMedium, color = Primary)
                TextButton(onClick = { navController.navigate(Screen.EditBoutique.route) }) {
                    Text("Modifier")
                }
            }
            
            ListItem(
                headlineContent = { Text("Nom de la boutique") },
                supportingContent = { Text(boutique?.name ?: "-") }
            )
            
            ListItem(
                headlineContent = { Text("Propriétaire") },
                supportingContent = { Text(boutique?.ownerName ?: "-") }
            )

            ListItem(
                headlineContent = { Text("Réception $operatorName (${boutique?.operator})") },
                supportingContent = { Text(boutique?.momoNumber ?: "-") }
            )

            Text("Matériel & Impression", style = MaterialTheme.typography.titleMedium, color = Primary)
            Button(onClick = { navController.navigate("print_settings") }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Print, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Personnaliser les Tickets")
            }

            HorizontalDivider()
            
            Text("Sécurité & Audit", style = MaterialTheme.typography.titleMedium, color = Primary)
            Button(onClick = { navController.navigate("action_logs") }, modifier = Modifier.fillMaxWidth()) {
                Text("Voir le Journal d'Audit")
            }
            Button(onClick = { navController.navigate("categories") }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)) {
                Text("Gérer les Catégories")
            }
            Button(onClick = { navController.navigate("maintenance") }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)) {
                Text("Maintenance & Purge")
            }
            Button(onClick = { /* Change PIN logic */ }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)) {
                Text("Changer le Code PIN")
            }
            
            Button(
                onClick = { navController.navigate("support") },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
            ) {
                Text("Abonnement & Support Technique")
            }

            HorizontalDivider()
            
            Text("Sauvegarde & Export", style = MaterialTheme.typography.titleMedium, color = Primary)
            val contextForSync = androidx.compose.ui.platform.LocalContext.current
            Button(
                onClick = { showBackupPasswordDialog = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = backupState !is com.reconsiliation.caisse.ui.viewmodel.BackupUiState.Working,
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                Icon(Icons.Default.Backup, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (backupState is com.reconsiliation.caisse.ui.viewmodel.BackupUiState.Working) {
                        "Sauvegarde en cours…"
                    } else {
                        "Exporter et partager la base"
                    }
                )
            }
            Text(
                "La sauvegarde est chiffrée par un mot de passe que vous choisissez.",
                style = MaterialTheme.typography.bodySmall
            )

            // Mode démo réservé aux builds de développement (BUG-014) : injecter
            // une boutique fictive dans une installation réelle polluerait des
            // données comptables non reconstituables.
            if (com.reconsiliation.caisse.BuildConfig.DEBUG &&
                allStock.isEmpty() && allVentes.isEmpty()
            ) {
                Button(
                    onClick = { viewModel.seedDemoData() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                ) {
                    Text("Mode Démo (Injecter données)")
                }
            } else if (allStock.isNotEmpty() || allVentes.isNotEmpty()) {
                Button(
                    onClick = { showResetDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = RedError)
                ) {
                    Text("Quitter le Mode Démo / Réinitialiser")
                }
            }

            if (showResetDialog) {
                AlertDialog(
                    onDismissRequest = { showResetDialog = false },
                    title = { Text("Réinitialisation Complète") },
                    text = { Text("Voulez-vous vraiment effacer toutes les données (Ventes, Stocks, Clients) ? Cette action est irréversible et vous devrez reconfigurer votre boutique.") },
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
                            Text("Tout effacer", color = RedError)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showResetDialog = false }) { Text("Annuler") }
                    }
                )
            }

            Button(
                onClick = { navController.navigate("restoration_wizard") },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Icon(Icons.Default.FileUpload, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Restaurer une sauvegarde")
            }

            Spacer(modifier = Modifier.weight(1f))
            
            Text("Mobile Caisse v1.0.0", modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = androidx.compose.ui.graphics.Color.Gray)
        }

        if (showBackupPasswordDialog) {
            com.reconsiliation.caisse.ui.components.BackupPasswordDialog(
                isExport = true,
                onConfirm = { password ->
                    showBackupPasswordDialog = false
                    viewModel.exportEncryptedBackup(contextForSync, password)
                },
                onDismiss = { showBackupPasswordDialog = false }
            )
        }

        // Retour utilisateur : succès et erreurs passent par le Snackbar du
        // Scaffold plutôt que par un Toast (ANDROID_RULES §10).
        androidx.compose.runtime.LaunchedEffect(backupState) {
            when (val state = backupState) {
                is com.reconsiliation.caisse.ui.viewmodel.BackupUiState.Success -> {
                    snackbarHostState.showSnackbar(state.message)
                    viewModel.clearBackupState()
                }
                is com.reconsiliation.caisse.ui.viewmodel.BackupUiState.Error -> {
                    snackbarHostState.showSnackbar(state.message)
                    viewModel.clearBackupState()
                }
                else -> Unit
            }
        }
    }
}
