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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.reconsiliation.caisse.ui.theme.Primary
import com.reconsiliation.caisse.ui.theme.RedError
import com.reconsiliation.caisse.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaintenanceScreen(navController: NavController) {
    val viewModel: MainViewModel = viewModel()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    
    var showPurgeDialog by remember { mutableStateOf(false) }
    var purgeMonths by remember { mutableStateOf(12f) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Maintenance & Optimisation") },
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
                        "L'accumulation de données sur plusieurs années peut ralentir l'application. Utilisez ces outils pour garder votre caisse rapide.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Text("Nettoyage des données", style = MaterialTheme.typography.titleMedium, color = Primary)
            
            ListItem(
                headlineContent = { Text("Purge des archives") },
                supportingContent = { Text("Supprime les anciennes ventes clôturées et les logs système pour libérer de l'espace.") },
                leadingContent = { Icon(Icons.Default.DeleteSweep, null, tint = RedError) },
                trailingContent = {
                    Button(
                        onClick = { showPurgeDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = RedError)
                    ) {
                        Text("Purger")
                    }
                }
            )

            HorizontalDivider()

            Text("Statistiques de stockage", style = MaterialTheme.typography.titleMedium, color = Primary)
            // Here we could add DB size info if available
            ListItem(
                headlineContent = { Text("Base de données") },
                supportingContent = { Text("Stockage local actif") },
                leadingContent = { Icon(Icons.Default.Storage, null) }
            )
        }

        if (showPurgeDialog) {
            AlertDialog(
                onDismissRequest = { showPurgeDialog = false },
                title = { Text("Confirmer la purge") },
                text = {
                    Column {
                        Text("Supprimer les données de plus de ${purgeMonths.toInt()} mois ?")
                        Text("Cette action est irréversible. Assurez-vous d'avoir fait un export de sauvegarde avant.", style = MaterialTheme.typography.bodySmall, color = RedError)
                        Spacer(modifier = Modifier.height(16.dp))
                        Slider(
                            value = purgeMonths,
                            onValueChange = { purgeMonths = it },
                            valueRange = 1f..36f,
                            steps = 35
                        )
                        Text("${purgeMonths.toInt()} mois", modifier = Modifier.align(Alignment.CenterHorizontally))
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                val count = viewModel.purgeOldData(purgeMonths.toInt())
                                snackbarHostState.showSnackbar("$count entrées supprimées avec succès.")
                                showPurgeDialog = false
                            }
                        }
                    ) {
                        Text("Confirmer la Purge", color = RedError)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPurgeDialog = false }) { Text("Annuler") }
                }
            )
        }
    }
}
