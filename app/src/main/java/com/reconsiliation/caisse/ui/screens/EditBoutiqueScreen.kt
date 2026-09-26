package com.reconsiliation.caisse.ui.screens

import android.annotation.SuppressLint
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.reconsiliation.caisse.ui.components.BigButton
import com.reconsiliation.caisse.ui.components.CaisseTextFieldDefaults
import com.reconsiliation.caisse.ui.theme.Primary
import com.reconsiliation.caisse.ui.viewmodel.MainViewModel
import com.reconsiliation.caisse.ui.viewmodel.PrinterViewModel
import androidx.compose.ui.res.stringResource
import com.reconsiliation.caisse.R
import com.reconsiliation.caisse.permissions.PermissionDecision
import com.reconsiliation.caisse.permissions.PermissionPolicy
import com.reconsiliation.caisse.ui.permissions.RefreshOnResume
import com.reconsiliation.caisse.ui.permissions.findActivity
import com.reconsiliation.caisse.ui.permissions.openApplicationSettings

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditBoutiqueScreen(navController: NavController) {
    val viewModel: MainViewModel = hiltViewModel()
    val printerViewModel: PrinterViewModel = hiltViewModel()
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
    
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context.findActivity()
    val bluetoothPermission = PermissionPolicy.bluetoothConnectPermission(Build.VERSION.SDK_INT)
    var bluetoothGranted by remember { mutableStateOf(printerViewModel.hasBluetoothAccess()) }
    var bluetoothAsked by remember { mutableStateOf(false) }
    var pairedPrinters by remember { mutableStateOf(printerViewModel.getPairedPrinters()) }
    var showPrinterDropdown by remember { mutableStateOf(false) }

    fun refreshBluetooth() {
        bluetoothGranted = printerViewModel.hasBluetoothAccess()
        pairedPrinters = printerViewModel.getPairedPrinters()
    }
    RefreshOnResume { refreshBluetooth() }
    val bluetoothLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        bluetoothAsked = true
        bluetoothGranted = granted
        pairedPrinters = printerViewModel.getPairedPrinters()
    }
    val bluetoothRationale = bluetoothPermission != null &&
        activity?.shouldShowRequestPermissionRationale(bluetoothPermission) == true
    val bluetoothDecision = if (bluetoothPermission == null) PermissionDecision.Granted
    else PermissionPolicy.decide(bluetoothGranted, bluetoothRationale, bluetoothAsked)

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
                title = { Text(stringResource(R.string.title_edit_boutique)) },
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

            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.setup_shop_name)) }, modifier = Modifier.fillMaxWidth(), colors = textFieldColors)
            OutlinedTextField(value = owner, onValueChange = { owner = it }, label = { Text(stringResource(R.string.setup_owner_name)) }, modifier = Modifier.fillMaxWidth(), colors = textFieldColors)
            OutlinedTextField(value = currency, onValueChange = { currency = it }, label = { Text(stringResource(R.string.label_currency_hint)) }, modifier = Modifier.fillMaxWidth(), colors = textFieldColors)
            
            Text(stringResource(R.string.label_main_momo_operator), style = MaterialTheme.typography.titleMedium, color = Primary)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = operator == "MTN", onClick = { operator = "MTN" }, label = { Text(stringResource(R.string.label_operator_mtn)) })
                FilterChip(selected = operator == "ORANGE", onClick = { operator = "ORANGE" }, label = { Text(stringResource(R.string.label_operator_orange)) })
            }
            OutlinedTextField(
                value = momoNumber,
                onValueChange = { momoNumber = it },
                label = { Text(stringResource(R.string.label_momo_reception_number)) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                colors = textFieldColors
            )

            HorizontalDivider()
            Text(stringResource(R.string.settings_hardware_stock), style = MaterialTheme.typography.titleMedium, color = Primary)
            if (!bluetoothGranted && bluetoothPermission != null) {
                Text(stringResource(R.string.permission_bluetooth_rationale), color = Color.Gray)
                Button(onClick = {
                    if (bluetoothDecision == PermissionDecision.OpenSettings) context.openApplicationSettings()
                    else bluetoothLauncher.launch(bluetoothPermission)
                }) {
                    Text(
                        if (bluetoothDecision == PermissionDecision.OpenSettings) {
                            stringResource(R.string.action_open_app_settings)
                        } else {
                            stringResource(R.string.action_authorize)
                        }
                    )
                }
            }
            
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = printerAddress,
                    onValueChange = { printerAddress = it },
                    label = { Text(stringResource(R.string.label_printer_address)) },
                    placeholder = { Text(stringResource(R.string.label_select_or_enter)) },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = { showPrinterDropdown = true }) {
                            Icon(Icons.Default.Print, contentDescription = stringResource(R.string.action_list))
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
                            text = { Text(stringResource(R.string.no_bluetooth_printer)) },
                            onClick = { showPrinterDropdown = false }
                        )
                    } else {
                        pairedPrinters.forEach { device ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        @SuppressLint("MissingPermission")
                                        val deviceName = try {
                                            device.name ?: context.getString(R.string.label_unknown)
                                        } catch (e: SecurityException) {
                                            context.getString(R.string.label_unknown)
                                        }
                                        Text(deviceName)
                                        Text(device.address, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
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
                    onClick = { printerViewModel.testPrint(printerAddress) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Text(stringResource(R.string.action_test_print))
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = allowNegativeStock, onCheckedChange = { allowNegativeStock = it })
                Text(stringResource(R.string.option_allow_negative_stock))
            }

            HorizontalDivider()
            Text(stringResource(R.string.settings_tax_config), style = MaterialTheme.typography.titleMedium, color = Primary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = isTaxEnabled, onCheckedChange = { isTaxEnabled = it })
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.option_enable_taxes))
            }
            if (isTaxEnabled) {
                OutlinedTextField(value = taxName, onValueChange = { taxName = it }, label = { Text(stringResource(R.string.label_tax_name)) }, modifier = Modifier.fillMaxWidth(), colors = textFieldColors)
                OutlinedTextField(
                    value = taxRate,
                    onValueChange = { taxRate = it },
                    label = { Text(stringResource(R.string.label_tax_rate)) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = textFieldColors
                )
            }

            HorizontalDivider()
            Text(stringResource(R.string.settings_receipt_customization), style = MaterialTheme.typography.titleMedium, color = Primary)
            OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text(stringResource(R.string.label_shop_address)) }, modifier = Modifier.fillMaxWidth(), colors = textFieldColors)
            OutlinedTextField(value = receiptFooter, onValueChange = { receiptFooter = it }, label = { Text(stringResource(R.string.label_receipt_footer)) }, modifier = Modifier.fillMaxWidth(), colors = textFieldColors)

            Spacer(modifier = Modifier.weight(1f))
            
            BigButton(
                text = stringResource(R.string.action_save_changes),
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
                            isSetupComplete = true
                        ))
                        navController.popBackStack()
                    }
                }
            )
        }
    }
}
