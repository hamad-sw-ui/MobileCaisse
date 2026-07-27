package com.reconsiliation.caisse.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.reconsiliation.caisse.ui.theme.Primary
import com.reconsiliation.caisse.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrintSettingsScreen(navController: NavController) {
    val viewModel: MainViewModel = viewModel()
    val boutique by viewModel.boutique.collectAsState()
    
    val pairedPrinters = viewModel.getPairedPrinters()
    var showPrinterDropdown by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Paramètres d'Impression") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        boutique?.let { b ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .padding(16.dp)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Configuration de l'imprimante", style = MaterialTheme.typography.titleMedium, color = Primary)
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Imprimante sélectionnée", style = MaterialTheme.typography.labelMedium)
                        
                        Box {
                            OutlinedButton(
                                onClick = { showPrinterDropdown = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val currentPrinter = try {
                                    pairedPrinters.find { it.address == b.printerAddress }
                                } catch (e: SecurityException) {
                                    null
                                }
                                val printerName = try {
                                    currentPrinter?.name
                                } catch (e: SecurityException) {
                                    null
                                }
                                Text(printerName ?: b.printerAddress ?: "Aucune imprimante")
                            }
                            
                            DropdownMenu(
                                expanded = showPrinterDropdown,
                                onDismissRequest = { showPrinterDropdown = false }
                            ) {
                                // Add "Integrated" option if applicable
                                val model = android.os.Build.MODEL.lowercase()
                                if (model.contains("v2") || model.contains("pax") || model.contains("sunmi")) {
                                    DropdownMenuItem(
                                        text = { Text("Imprimante intégrée") },
                                        onClick = {
                                            viewModel.updateBoutique(b.copy(printerAddress = "INTEGRATED"))
                                            showPrinterDropdown = false
                                        }
                                    )
                                }

                                pairedPrinters.forEach { device ->
                                    DropdownMenuItem(
                                        text = {
                                            val name = try { device.name } catch (e: SecurityException) { null }
                                            Text(name ?: device.address)
                                        },
                                        onClick = {
                                            viewModel.updateBoutique(b.copy(printerAddress = device.address))
                                            showPrinterDropdown = false
                                        }
                                    )
                                }
                                if (pairedPrinters.isEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("Aucun appareil associé") },
                                        onClick = { showPrinterDropdown = false },
                                        enabled = false
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = { b.printerAddress?.let { viewModel.testPrint(it) } },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !b.printerAddress.isNullOrBlank()
                        ) {
                            Icon(Icons.Default.Print, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Tester l'impression")
                        }
                    }
                }

                HorizontalDivider()

                Text("Options du ticket", style = MaterialTheme.typography.titleMedium, color = Primary)
                
                ToggleOption(
                    label = "Afficher le téléphone du client",
                    checked = b.showCustomerPhoneOnReceipt,
                    onCheckedChange = { viewModel.updateBoutique(b.copy(showCustomerPhoneOnReceipt = it)) }
                )
                
                ToggleOption(
                    label = "Détailler les taxes",
                    checked = b.showTaxesOnReceipt,
                    onCheckedChange = { viewModel.updateBoutique(b.copy(showTaxesOnReceipt = it)) }
                )
                
                ToggleOption(
                    label = "Afficher la quantité totale",
                    checked = b.showTotalQuantityOnReceipt,
                    onCheckedChange = { viewModel.updateBoutique(b.copy(showTotalQuantityOnReceipt = it)) }
                )
                
                ToggleOption(
                    label = "Imprimer une copie marchand",
                    checked = b.printMerchantCopy,
                    onCheckedChange = { viewModel.updateBoutique(b.copy(printMerchantCopy = it)) }
                )
            }
        }
    }
}

@Composable
fun ToggleOption(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
