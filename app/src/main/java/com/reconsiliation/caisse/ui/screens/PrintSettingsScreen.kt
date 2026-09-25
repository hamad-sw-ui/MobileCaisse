package com.reconsiliation.caisse.ui.screens

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.reconsiliation.caisse.ui.theme.Primary
import com.reconsiliation.caisse.ui.viewmodel.MainViewModel
import com.reconsiliation.caisse.ui.viewmodel.PrinterViewModel
import androidx.compose.ui.res.stringResource
import com.reconsiliation.caisse.R

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrintSettingsScreen(navController: NavController) {
    val viewModel: PrinterViewModel = hiltViewModel()
    val mainViewModel: MainViewModel = hiltViewModel()
    val boutique by mainViewModel.boutique.collectAsState()
    
    val pairedPrinters = remember { viewModel.getPairedPrinters() }
    var showPrinterDropdown by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_print_settings)) },
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
                Text(stringResource(R.string.label_printer_config), style = MaterialTheme.typography.titleMedium, color = Primary)
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.label_selected_printer), style = MaterialTheme.typography.labelMedium)
                        
                        Box {
                            OutlinedButton(
                                onClick = { showPrinterDropdown = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                @SuppressLint("MissingPermission")
                                val currentPrinter = try {
                                    pairedPrinters.find { it.address == b.printerAddress }
                                } catch (e: SecurityException) {
                                    null
                                }
                                @SuppressLint("MissingPermission")
                                val printerName = try {
                                    currentPrinter?.name
                                } catch (e: SecurityException) {
                                    null
                                }
                                Text(printerName ?: b.printerAddress ?: stringResource(R.string.no_printer_selected))
                            }
                            
                            DropdownMenu(
                                expanded = showPrinterDropdown,
                                onDismissRequest = { showPrinterDropdown = false }
                            ) {
                                // Add "Integrated" option if applicable
                                val model = android.os.Build.MODEL.lowercase()
                                if (model.contains("v2") || model.contains("pax") || model.contains("sunmi")) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.integrated_printer)) },
                                        onClick = {
                                            mainViewModel.updateBoutique(b.copy(printerAddress = "INTEGRATED"))
                                            showPrinterDropdown = false
                                        }
                                    )
                                }

                                pairedPrinters.forEach { device ->
                                    DropdownMenuItem(
                                        text = {
                                            @SuppressLint("MissingPermission")
                                            val name = try { device.name } catch (e: SecurityException) { null }
                                            Text(name ?: device.address)
                                        },
                                        onClick = {
                                            mainViewModel.updateBoutique(b.copy(printerAddress = device.address))
                                            showPrinterDropdown = false
                                        }
                                    )
                                }
                                if (pairedPrinters.isEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.no_paired_device)) },
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
                            Text(stringResource(R.string.action_test_print))
                        }
                    }
                }

                HorizontalDivider()

                Text(stringResource(R.string.label_ticket_options), style = MaterialTheme.typography.titleMedium, color = Primary)
                
                ToggleOption(
                    label = stringResource(R.string.option_show_customer_phone),
                    checked = b.showCustomerPhoneOnReceipt,
                    onCheckedChange = { mainViewModel.updateBoutique(b.copy(showCustomerPhoneOnReceipt = it)) }
                )
                
                ToggleOption(
                    label = stringResource(R.string.option_show_taxes),
                    checked = b.showTaxesOnReceipt,
                    onCheckedChange = { mainViewModel.updateBoutique(b.copy(showTaxesOnReceipt = it)) }
                )
                
                ToggleOption(
                    label = stringResource(R.string.option_show_total_qty),
                    checked = b.showTotalQuantityOnReceipt,
                    onCheckedChange = { mainViewModel.updateBoutique(b.copy(showTotalQuantityOnReceipt = it)) }
                )
                
                ToggleOption(
                    label = stringResource(R.string.option_print_merchant_copy),
                    checked = b.printMerchantCopy,
                    onCheckedChange = { mainViewModel.updateBoutique(b.copy(printMerchantCopy = it)) }
                )
            }
        }
    }
}

@Composable
fun ToggleOption(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
