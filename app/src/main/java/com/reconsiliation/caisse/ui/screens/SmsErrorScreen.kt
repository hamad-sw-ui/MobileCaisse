package com.reconsiliation.caisse.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SmsFailed
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.reconsiliation.caisse.ui.components.CaisseTextFieldDefaults
import com.reconsiliation.caisse.ui.theme.OrangeWarning
import com.reconsiliation.caisse.ui.viewmodel.MainViewModel
import com.reconsiliation.caisse.utils.FormatUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsErrorScreen(navController: NavController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val viewModel: MainViewModel = viewModel()
    val boutique by viewModel.boutique.collectAsState()
    val operatorName = boutique?.operator ?: "MoMo"
    val errors by viewModel.smsErrors.collectAsState()
    val allVentes by viewModel.allVentes.collectAsState()
    
    val pendingVentes = allVentes.filter { (it.paymentMethod == "MOMO" || it.paymentMethod == "MIXED") && it.status == "PENDING" }

    var selectedError by remember { mutableStateOf<com.reconsiliation.caisse.data.local.entity.SmsErrorEntity?>(null) }
    var showResolveDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Journal des SMS $operatorName") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        if (errors.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.SmsFailed, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color.LightGray)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Aucun SMS non reconnu", color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text("Ces SMS $operatorName n'ont pas pu être traités automatiquement (Format inconnu ou sécurité).", style = MaterialTheme.typography.bodyMedium, color = OrangeWarning)
                    
                    var clipboardText by remember { mutableStateOf("") }
                    OutlinedTextField(
                        value = clipboardText,
                        onValueChange = { clipboardText = it },
                        label = { Text("Coller un texte MoMo (WhatsApp/SMS)") },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        trailingIcon = {
                            if (clipboardText.isNotBlank()) {
                                IconButton(onClick = {
                                    com.reconsiliation.caisse.sms.SmsReceiver.processSms(context, listOf(clipboardText to "MANUAL_CLIPBOARD"))
                                    clipboardText = ""
                                }) {
                                    Icon(Icons.Default.Send, "Analyser")
                                }
                            }
                        },
                        colors = CaisseTextFieldDefaults.outlinedTextFieldColors()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                items(errors) { error ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text(error.sender ?: "Inconnu", style = MaterialTheme.typography.labelLarge, color = Color.Gray)
                                Text(FormatUtil.formatDate(error.date), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(error.body, style = MaterialTheme.typography.bodyMedium)
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                                TextButton(
                                    onClick = { 
                                        selectedError = error
                                        showResolveDialog = true 
                                    }
                                ) {
                                    Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Lier à une vente")
                                }
                            }
                        }
                    }
                }
            }
        }
        
        if (showResolveDialog && selectedError != null) {
            AlertDialog(
                onDismissRequest = { showResolveDialog = false },
                title = { Text("Sélectionner la vente") },
                text = {
                    if (pendingVentes.isEmpty()) {
                        Text("Aucune vente MoMo en attente trouvée.")
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Choisissez la vente correspondant à ce SMS :")
                            LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                                items(pendingVentes) { vente ->
                                    ListItem(
                                        headlineContent = { Text(vente.description) },
                                        supportingContent = { Text("${FormatUtil.formatCurrency(vente.amount)} - ${FormatUtil.formatDate(vente.date)}") },
                                        modifier = Modifier.fillMaxWidth().clickable {
                                            selectedError?.let { err ->
                                                viewModel.resolveSmsError(err, vente.id)
                                            }
                                            showResolveDialog = false
                                        },
                                        leadingContent = { Icon(Icons.Default.ListAlt, null) }
                                    )
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showResolveDialog = false }) { Text("Annuler") }
                }
            )
        }
    }
}
