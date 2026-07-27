package com.reconsiliation.caisse.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.reconsiliation.caisse.data.local.entity.BoutiqueEntity
import com.reconsiliation.caisse.ui.components.BigButton
import com.reconsiliation.caisse.ui.components.CaisseTextFieldDefaults
import com.reconsiliation.caisse.ui.theme.Primary
import com.reconsiliation.caisse.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditBoutiqueScreen(navController: NavController) {
    val viewModel: MainViewModel = viewModel()
    val boutique by viewModel.boutique.collectAsState()

    var name by remember { mutableStateOf("") }
    var owner by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("FCFA") }
    var operator by remember { mutableStateOf("MTN") }
    var momoNumber by remember { mutableStateOf("") }
    var printerAddress by remember { mutableStateOf("") }
    var allowNegativeStock by remember { mutableStateOf(true) }
    var taxName by remember { mutableStateOf("TVA") }
    var taxRate by remember { mutableStateOf("0") }
    var isTaxEnabled by remember { mutableStateOf(false) }

    var address by remember { mutableStateOf("") }
    var receiptFooter by remember { mutableStateOf("") }
    
    val pairedPrinters = viewModel.getPairedPrinters()
    var showPrinterDropdown by remember { mutableStateOf(false) }

    LaunchedEffect(boutique) {
        boutique?.let {
            name = it.name
            owner = it.ownerName
            currency = it.currency
            operator = it.operator
            momoNumber = it.momoNumber
            printerAddress = it.printerAddress ?: ""
            allowNegativeStock = it.allowNegativeStock
            taxName = it.taxName
            taxRate = it.taxRate.toString()
            isTaxEnabled = it.isTaxEnabled
            address = it.address ?: ""
            receiptFooter = it.receiptFooter ?: ""
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Modifier la Boutique") },
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
                .imePadding()
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val textFieldColors = CaisseTextFieldDefaults.outlinedTextFieldColors()

            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nom de la boutique") }, modifier = Modifier.fillMaxWidth(), colors = textFieldColors)
            OutlinedTextField(value = owner, onValueChange = { owner = it }, label = { Text("Nom du propriétaire") }, modifier = Modifier.fillMaxWidth(), colors = textFieldColors)
            OutlinedTextField(value = currency, onValueChange = { currency = it }, label = { Text("Devise (ex: FCFA, $, €)") }, modifier = Modifier.fillMaxWidth(), colors = textFieldColors)
            
            Text("Opérateur MoMo principal", style = MaterialTheme.typography.titleMedium, color = Primary)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = operator == "MTN", onClick = { operator = "MTN" }, label = { Text("MTN/OM") })
                FilterChip(selected = operator == "ORANGE", onClick = { operator = "ORANGE" }, label = { Text("Orange Money") })
            }
            OutlinedTextField(
                value = momoNumber, 
                onValueChange = { momoNumber = it }, 
                label = { Text("Numéro MoMo de réception") }, 
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                colors = textFieldColors
            )

            HorizontalDivider()
            Text("Matériel & Stock", style = MaterialTheme.typography.titleMedium, color = Primary)
            
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = printerAddress, 
                    onValueChange = { printerAddress = it }, 
                    label = { Text("Adresse de l'imprimante (MAC)") }, 
                    placeholder = { Text("Sélectionnez ou saisissez") },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = { showPrinterDropdown = true }) {
                            Icon(Icons.Default.Print, contentDescription = "Liste")
                        }
                    },
                    colors = textFieldColors
                )
                DropdownMenu(
                    expanded = showPrinterDropdown,
                    onDismissRequest = { showPrinterDropdown = false },
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    if (pairedPrinters.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("Aucune imprimante Bluetooth associée") },
                            onClick = { showPrinterDropdown = false }
                        )
                    } else {
                        pairedPrinters.forEach { device ->
                            DropdownMenuItem(
                                text = { 
                                    Column {
                                        @android.annotation.SuppressLint("MissingPermission")
                                        Text(device.name ?: "Inconnu")
                                        Text(device.address, style = MaterialTheme.typography.labelSmall, color = androidx.compose.ui.graphics.Color.Gray)
                                    }
                                },
                                onClick = {
                                    printerAddress = device.address
                                    viewModel.updateBoutique(boutique!!.copy(printerAddress = device.address))
                                    showPrinterDropdown = false
                                }
                            )
                        }
                    }
                }
            }

            if (printerAddress.isNotBlank()) {
                Button(
                    onClick = { viewModel.testPrint(printerAddress) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Text("Tester l'impression")
                }
            }

            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checked = allowNegativeStock, onCheckedChange = { allowNegativeStock = it })
                Text("Autoriser les ventes en stock négatif")
            }

            HorizontalDivider()
            Text("Paramètres de Taxe", style = MaterialTheme.typography.titleMedium, color = Primary)
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Switch(checked = isTaxEnabled, onCheckedChange = { isTaxEnabled = it })
                Spacer(modifier = Modifier.width(8.dp))
                Text("Activer les taxes (TVA/AIR)")
            }
            if (isTaxEnabled) {
                OutlinedTextField(value = taxName, onValueChange = { taxName = it }, label = { Text("Nom de la Taxe") }, modifier = Modifier.fillMaxWidth(), colors = textFieldColors)
                OutlinedTextField(
                    value = taxRate, 
                    onValueChange = { taxRate = it }, 
                    label = { Text("Taux (%)") }, 
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = textFieldColors
                )
            }

            HorizontalDivider()
            Text("Personnalisation Reçus", style = MaterialTheme.typography.titleMedium, color = Primary)
            OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Adresse de la boutique") }, modifier = Modifier.fillMaxWidth(), colors = textFieldColors)
            OutlinedTextField(value = receiptFooter, onValueChange = { receiptFooter = it }, label = { Text("Message de fin (ex: Merci !)") }, modifier = Modifier.fillMaxWidth(), colors = textFieldColors)

            Spacer(modifier = Modifier.weight(1f))
            
            BigButton(
                text = "Enregistrer les modifications",
                onClick = {
                    boutique?.let {
                        viewModel.updateBoutique(it.copy(
                            name = name,
                            ownerName = owner,
                            currency = currency,
                            operator = operator,
                            momoNumber = momoNumber,
                            printerAddress = printerAddress.ifBlank { null },
                            allowNegativeStock = allowNegativeStock,
                            taxName = taxName,
                            taxRate = taxRate.toDoubleOrNull() ?: 0.0,
                            isTaxEnabled = isTaxEnabled,
                            address = address.ifBlank { null },
                            receiptFooter = receiptFooter.ifBlank { null },
                            isSetupComplete = true // Ensure it stays complete
                        ))
                        navController.popBackStack()
                    }
                }
            )
        }
    }
}
