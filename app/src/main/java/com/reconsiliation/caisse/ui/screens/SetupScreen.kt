package com.reconsiliation.caisse.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.reconsiliation.caisse.ui.components.BigButton
import com.reconsiliation.caisse.ui.components.CaisseTextFieldDefaults
import com.reconsiliation.caisse.ui.navigation.Screen
import com.reconsiliation.caisse.ui.theme.Primary
import com.reconsiliation.caisse.ui.viewmodel.BackupViewModel
import com.reconsiliation.caisse.ui.viewmodel.MainViewModel
import androidx.compose.ui.res.stringResource
import com.reconsiliation.caisse.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(navController: NavController) {
    val context = LocalContext.current
    val viewModel: MainViewModel = hiltViewModel()
    val backupViewModel: BackupViewModel = hiltViewModel()

    var name by remember { mutableStateOf("") }
    var owner by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var operator by remember { mutableStateOf("MTN") }
    var momoNumber by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    
    var showRestoreDialog by remember { mutableStateOf(false) }
    var setupError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        showRestoreDialog = backupViewModel.hasMirrorBackup(context)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.title_setup_boutique)) }) }
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

            setupError?.let { message ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(text = message, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
                }
            }

            Text(stringResource(R.string.setup_general_info), style = MaterialTheme.typography.titleMedium, color = Primary)
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.setup_shop_name)) }, modifier = Modifier.fillMaxWidth(), colors = textFieldColors)
            OutlinedTextField(value = owner, onValueChange = { owner = it }, label = { Text(stringResource(R.string.setup_owner_name)) }, modifier = Modifier.fillMaxWidth(), colors = textFieldColors)
            OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text(stringResource(R.string.setup_main_phone)) }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), colors = textFieldColors)
            
            Text(stringResource(R.string.setup_mobile_payments), style = MaterialTheme.typography.titleMedium, color = Primary)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = operator == "MTN", onClick = { operator = "MTN" }, label = { Text(stringResource(R.string.label_operator_mtn)) })
                FilterChip(selected = operator == "ORANGE", onClick = { operator = "ORANGE" }, label = { Text(stringResource(R.string.label_operator_orange)) })
            }
            OutlinedTextField(value = momoNumber, onValueChange = { momoNumber = it }, label = { Text(stringResource(R.string.setup_momo_reception_format, operator)) }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), colors = textFieldColors)
            
            Text(stringResource(R.string.setup_security), style = MaterialTheme.typography.titleMedium, color = Primary)
            OutlinedTextField(
                value = pin,
                onValueChange = { if (it.length <= 4) pin = it },
                label = { Text(stringResource(R.string.setup_pin_hint)) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = PasswordVisualTransformation(),
                colors = textFieldColors
            )

            Spacer(modifier = Modifier.height(24.dp))
            BigButton(
                text = stringResource(R.string.setup_action_finish),
                onClick = {
                    if (name.isNotBlank() && pin.length == 4) {
                        setupError = null
                        viewModel.completeSetup(name = name, owner = owner, phone = phone, operator = operator, momoNumber = momoNumber, pin = pin) { error ->
                            if (error == null) {
                                navController.navigate(Screen.Home.route) { popUpTo(Screen.Setup.route) { inclusive = true } }
                            } else {
                                setupError = error
                            }
                        }
                    }
                }
            )
        }

        if (showRestoreDialog) {
            AlertDialog(
                onDismissRequest = { showRestoreDialog = false },
                title = { Text(stringResource(R.string.dialog_restore_title)) },
                text = { Text(stringResource(R.string.dialog_restore_desc)) },
                confirmButton = {
                    TextButton(onClick = {
                        showRestoreDialog = false
                        backupViewModel.restoreMirrorBackup(context) { restored ->
                            if (restored) {
                                navController.navigate(Screen.Splash.route) { popUpTo(Screen.Setup.route) { inclusive = true } }
                            } else {
                                setupError = context.getString(R.string.error_restoration_failed)
                            }
                        }
                    }) { Text(stringResource(R.string.action_restore)) }
                },
                dismissButton = {
                    TextButton(onClick = { showRestoreDialog = false }) { Text(stringResource(R.string.action_ignore)) }
                }
            )
        }
    }
}
